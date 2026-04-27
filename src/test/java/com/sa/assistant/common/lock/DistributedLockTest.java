package com.sa.assistant.common.lock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DistributedLock 单元测试")
class DistributedLockTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private DistributedLock distributedLock;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        distributedLock = new DistributedLock(stringRedisTemplate);
    }

    // ================================================================
    // 基础加锁/解锁测试
    // ================================================================

    @Nested
    @DisplayName("基础操作")
    class BasicOperations {

        @Test
        @DisplayName("tryLock 成功时应返回 true")
        void shouldReturnTrueWhenLockAcquired() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);

            boolean locked = distributedLock.tryLock("lock:test", "req-001", 10);

            assertThat(locked).isTrue();
        }

        @Test
        @DisplayName("tryLock 失败时应返回 false")
        void shouldReturnFalseWhenLockNotAcquired() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenReturn(false);

            boolean locked = distributedLock.tryLock("lock:test", "req-001", 10);

            assertThat(locked).isFalse();
        }

        @Test
        @DisplayName("unlock 应使用 Lua 脚本原子释放")
        void shouldUnlockAtomicallyWithLuaScript() {
            when(stringRedisTemplate.execute(
                    any(DefaultRedisScript.class),
                    eq(Collections.singletonList("lock:test")),
                    eq("req-001")
            )).thenReturn(1L);

            boolean released = distributedLock.unlock("lock:test", "req-001");

            assertThat(released).isTrue();
        }
    }

    // ================================================================
    // executeWithLock（无续期，向后兼容）
    // ================================================================

    @Nested
    @DisplayName("executeWithLock（无续期）")
    class ExecuteWithLock {

        @Test
        @DisplayName("成功获取锁后应执行操作并释放锁")
        void shouldExecuteActionAndReleaseLock() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);
            // unlock 返回 1 表示释放成功
            stubLuaScriptReturn(1L);

            AtomicInteger counter = new AtomicInteger(0);
            Integer result = distributedLock.executeWithLock("lock:test", 10, () -> {
                counter.incrementAndGet();
                return 42;
            });

            assertThat(result).isEqualTo(42);
            assertThat(counter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("executeWithLock 无法获取锁时应抛 LockAcquisitionException")
        void shouldThrowExceptionWhenLockCannotBeAcquired() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenReturn(false);

            assertThatThrownBy(() -> distributedLock.executeWithLock("lock:hot", 10, () -> "should not run"))
                    .isInstanceOf(LockAcquisitionException.class)
                    .hasMessageContaining("操作过于频繁");
        }

        @Test
        @DisplayName("业务抛异常时 finally 仍应执行 unlock")
        void shouldUnlockEvenWhenBusinessThrows() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);
            stubLuaScriptReturn(1L);

            assertThatThrownBy(() -> distributedLock.executeWithLock("lock:test", 10, () -> {
                throw new RuntimeException("business error");
            })).isInstanceOf(RuntimeException.class);

            // 验证 unlock 被调用（Lua 脚本执行了）
            verify(stringRedisTemplate, atLeastOnce()).execute(
                    any(DefaultRedisScript.class), anyList(), anyString());
        }
    }

    // ================================================================
    // executeWithLockWatchDog（续期锁）
    // ================================================================

    @Nested
    @DisplayName("executeWithLockWatchDog（看门狗续期）")
    class ExecuteWithLockWatchDog {

        @Test
        @DisplayName("获取锁后应执行业务逻辑并释放锁")
        void shouldExecuteBusinessAndReleaseLock() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);
            stubLuaScriptReturn(1L);

            AtomicInteger counter = new AtomicInteger(0);
            Integer result = distributedLock.executeWithLockWatchDog("lock:test", 10, () -> {
                counter.incrementAndGet();
                return 42;
            });

            assertThat(result).isEqualTo(42);
            assertThat(counter.get()).isEqualTo(1);
        }

        @Test
        @DisplayName("锁获取失败时应抛 LockAcquisitionException")
        void shouldThrowExceptionWhenWatchDogLockFails() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenReturn(false);

            assertThatThrownBy(() ->
                    distributedLock.executeWithLockWatchDog("lock:hot", 10, () -> "should not run"))
                    .isInstanceOf(LockAcquisitionException.class)
                    .hasMessageContaining("操作过于频繁");
        }

        @Test
        @DisplayName("WatchDog 应在业务执行期间触发续期（续期 Lua 脚本被调用）")
        void shouldRenewDuringLongBusinessExecution() throws Exception {
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);
            stubLuaScriptReturn(1L);

            // 使用 CountDownLatch 让业务阻塞，给 WatchDog 时间触发续期
            java.util.concurrent.CountDownLatch businessStarted = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch businessFinish = new java.util.concurrent.CountDownLatch(1);

            Thread testThread = new Thread(() -> {
                distributedLock.executeWithLockWatchDog("lock:test", 3, () -> {
                    businessStarted.countDown();
                    try {
                        // 业务耗时 2.5s，WatchDog 每 1s 续期一次（3/3=1s）
                        // 应该触发 2 次续期
                        Thread.sleep(2500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return "done";
                });
                businessFinish.countDown();
            });
            testThread.start();

            // 等待业务开始
            businessStarted.await(5, TimeUnit.SECONDS);
            // 等待业务完成
            businessFinish.await(10, TimeUnit.SECONDS);

            // 验证续期 Lua 脚本被调用（至少 1 次，正常应为 2-3 次）
            // renew脚本有4个参数: script, keys, requestId, ttlSeconds
            verify(stringRedisTemplate, atLeastOnce()).execute(
                    any(DefaultRedisScript.class),
                    eq(Collections.singletonList("lock:test")),
                    anyString(),
                    eq("3") // renew target TTL
            );
        }

        @Test
        @DisplayName("业务抛异常时 finally 仍应停止 WatchDog 并 unlock")
        void shouldStopWatchDogAndUnlockWhenBusinessThrows() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);
            stubLuaScriptReturn(1L);

            assertThatThrownBy(() ->
                    distributedLock.executeWithLockWatchDog("lock:test", 10, () -> {
                        throw new RuntimeException("business error");
                    })
            ).isInstanceOf(RuntimeException.class);

            // 验证 unlock 被调用
            verify(stringRedisTemplate, atLeastOnce()).execute(
                    any(DefaultRedisScript.class), anyList(), anyString());
        }
    }

    // ================================================================
    // 竞态条件测试：验证解锁安全性
    // ================================================================

    @Nested
    @DisplayName("竞态条件防护")
    class RaceConditionProtection {

        /**
         * 模拟场景：Thread A 的锁过期被 Thread B 抢占，Thread A 尝试释放
         * 预期：Lua 脚本检查 get(key) != requestId，返回 0，不删除 B 的锁
         */
        @Test
        @DisplayName("锁已过期被他人持有时，unlock 不应误删他人的锁")
        void shouldNotDeleteOthersLockWhenExpired() {
            // unlock 的 Lua 脚本返回 0 表示 value 不匹配（锁已被他人持有）
            stubLuaScriptReturn(0L);

            boolean released = distributedLock.unlock("lock:test", "old-request-id");

            assertThat(released).isFalse();
        }

        /**
         * 模拟场景：Thread A 的 WatchDog 尝试续期已过期的锁
         * 预期：续期 Lua 脚本检查 get(key) != requestId，返回 0，续期被拒绝，
         * WatchDog 自动停止
         */
        @Test
        @DisplayName("WatchDog 续期被拒时（锁已丢失）应自动停止续期")
        void shouldStopWatchDogWhenRenewalDenied() {
            when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);

            // 第一次 execute 是 unlock，返回 1；中间的 execute 是 renew，返回 0（续期失败）
            when(stringRedisTemplate.execute(
                    any(DefaultRedisScript.class),
                    eq(Collections.singletonList("lock:test")),
                    anyString()
            )).thenReturn(0L); // 续期失败 —— 锁已丢失

            // 后续对 unlock 的调用也返回 0（锁已不属于当前持有者）
            lenient().when(stringRedisTemplate.execute(
                    any(DefaultRedisScript.class),
                    eq(Collections.singletonList("lock:test")),
                    anyString(),
                    anyString() // renew script has 3 args
            )).thenReturn(0L);

            String result = distributedLock.executeWithLockWatchDog("lock:test", 3, () -> {
                // 模拟短暂业务，但续期已经失败
                return "done";
            });

            assertThat(result).isEqualTo("done");
            // 即使续期失败，业务仍应正常完成（不中断业务）
        }
    }

    // ================================================================
    // 辅助方法
    // ================================================================

    /**
     * 简化 Lua 脚本返回值的 Mock 设置。
     * stubLuaScriptReturn(1L) → 解锁/续期成功
     * stubLuaScriptReturn(0L) → 解锁/续期失败（锁不属于当前持有者）
     */
    @SuppressWarnings("unchecked")
    private void stubLuaScriptReturn(Long returnValue) {
        lenient().when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString()
        )).thenReturn(returnValue);

        // 续期脚本有 3 个参数（key, requestId, ttl）
        lenient().when(stringRedisTemplate.execute(
                any(DefaultRedisScript.class),
                anyList(),
                anyString(),
                anyString()
        )).thenReturn(returnValue);
    }
}
