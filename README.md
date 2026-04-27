# SA 智能对话助手

> 高性能 Spring AI 对话系统，支持闲聊与文档 RAG 双模式

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-brightgreen.svg)](https://spring.io/projects/spring-ai)

---

## 项目简介

SA 智能对话助手（SA Intelligent Assistant）是一个基于 **Spring Boot + Spring AI** 的高性能对话系统，独特之处在于支持**双模式切换**：

- **闲聊模式**：纯粹的 LLM 对话能力，适用于日常问答、创意生成等场景
- **RAG 模式**：基于上传文档的检索增强生成，文档经 Apache Tika 解析、向量嵌入后存储于 Chroma 向量数据库，LLM 仅基于检索到的相关内容作答，保证答案准确可溯源

系统面向生产级部署，在缓存、消息队列、分布式锁、可观测性等基础设施层面做了完整的高可用设计。

---

## 核心架构与技术亮点

### 1. Caffeine + Redis 多级缓存架构

系统实现 **L1 Caffeine（本地） → L2 Redis（分布式）** 二级缓存，兼顾低延迟与高扩展性：

```
请求 → Caffeine(L1) hit? → 直接返回（亚毫秒级）
     → Caffeine miss → Redis(L2) hit? → 回填L1后返回（毫秒级）
     → 皆miss → 查DB → 回填L1+L2 → 返回
```

**关键设计**：
- `ChatHistory` 缓存策略：L1 3分钟 TTL，L2 20分钟 TTL，通过 `CacheConfig` 独立配置
- 采用 **Cache-Aside 模式**：写操作不走缓存，通过 `CacheConsistencyManager` 保证写后缓存失效
- `CacheReconciliationScheduler` 定时任务兜底，解决并发场景下的缓存漂移问题
- `unless = "#result == null"` 防止缓存穿透（大量请求查询不存在ID时不会污染Redis）

### 2. RabbitMQ + Apache Tika 大文件异步解析

文档上传后经 **RabbitMQ 异步队列**处理，避免同步解析阻塞主线程：

| 组件 | 说明 |
|------|------|
| 主队列 `rag.document.queue` | 文档解析任务入口 |
| 死信队列 `rag.dlx.queue` | 解析失败/超时的消息暂存区，保留 7 天供人工排查 |
| 三级重试机制 | 消费者侧通过 `x-retry-count` header 追踪重试次数，MAX=3 次后进入 DLX |
| prefetch=1 | 消费者单次只取一条消息，避免 OOM 时影响多条 |
| 5 分钟解析超时 | `DocumentParseService` 通过 `CompletableFuture.get(timeout)` 实现 Tika 解析超时控制 |
| 10MB 内容上限 | Tika `WriteOutContentHandler` 限制单次输出字符数，防止大文件撑爆堆内存 |

### 3. Chroma 向量数据库 RAG 链路

RAG 模式检索链路设计：

```
用户提问 → VectorStore similaritySearch(topK=5, threshold=0.75)
         → 过滤出 documentId（如指定）
         → 构建 context prompt → LLM 生成答案
```

**降级策略（Fallback）**：
- 向量检索异常 → 捕获后返回基于模型自身知识的回答（标注 `source="ERROR"`）
- 检索结果为空（相似度 < 0.75）→ 返回"未找到相关文档"提示
- LLM 调用异常 → 返回带错误提示的降级回答

### 4. Redis 分布式锁 + WatchDog 续期机制

`DistributedLock` 基于 **Redis SET NX + Lua 脚本**实现：

**解决的问题**：
- **互斥**：SET NX 保证加锁原子性
- **解锁安全**：Lua 脚本 `get==requestId 时才 del`，防止误删他人持有的锁
- **续期安全**：Lua 脚本 `get==requestId 时才 expire`，防止续期已过期/被他人持有的锁

**WatchDog 看门狗机制**：
- 锁持有期间，每 `expireSeconds/3` 秒自动续期一次
- 业务执行完闭后显式释放锁，WatchDog 任务自动取消
- 调度线程池使用**守护线程**，JVM 退出时不会阻塞

典型使用场景：`ChatHistoryService.create()` 在写 DB 前后加锁，保证同一 sessionId 的并发创建请求串行化。

### 5. 可观测性设计

基于 **Spring Boot Actuator + Micrometer** 实现监控埋点：

- **健康检查**：`/actuator/health` 暴露 Redis、RabbitMQ、DB 连接状态
- **自定义指标**：通过 Micrometer API 埋点记录缓存命中率、文档解析耗时、RAG 查询延迟等业务指标
- **日志规范**：使用 Slf4j + Lombok `@Slf4j`，关键操作路径记录 INFO 级日志，异常路径记录 WARN/ERROR 并附带上下文参数

---

## 技术栈列表

| 层级 | 技术 | 版本 |
|------|------|------|
| 基础框架 | Spring Boot | 3.4.4 |
| AI 集成 | Spring AI | 1.0.0 |
| LLM | OpenAI GPT-4o | - |
| 向量数据库 | Chroma | - |
| 本地缓存 | Caffeine | 3.1.8 |
| 分布式缓存 | Redis (Spring Data Redis) | - |
| 消息队列 | RabbitMQ (Spring AMQP) | - |
| 文档解析 | Apache Tika | 2.9.3 |
| ORM | Spring Data JPA | - |
| 数据库 | MySQL | - |
| JSON | Jackson + JavaTimeModule | - |
| 对象映射 | MapStruct | 1.6.3 |
| 注解 | Lombok | - |
| 可观测性 | Micrometer + Actuator | - |

---

## 快速开始（Quick Start）

### 环境要求

- JDK 21+
- Maven 3.8+
- Redis 6+
- RabbitMQ 3.x
- MySQL 8+
- Chroma 向量数据库（可选，未安装时 RAG 模式降级运行）

### 环境变量配置

```bash
# OpenAI API
export OPENAI_API_KEY=your-api-key
export OPENAI_BASE_URL=https://api.openai.com  # 可选，默认为官方地址
export OPENAI_MODEL=gpt-4o                      # 可选，默认为 gpt-4o
export EMBEDDING_MODEL=text-embedding-3-small    # 可选，默认为 text-embedding-3-small

# Chroma（可选）
export CHROMA_TOKEN=your-chroma-token           # 可选
```

### 启动步骤

1. **克隆项目**
   ```bash
   git clone <repository-url>
   cd sa-intelligent-assistant
   ```

2. **配置数据库**
   ```bash
   cp src/main/resources/application.yml src/main/resources/application-local.yml
   # 编辑 application-local.yml，配置 MySQL / Redis / RabbitMQ 连接信息
   ```

3. **构建**
   ```bash
   mvn clean package -DskipTests
   ```

4. **启动**
   ```bash
   java -jar target/sa-intelligent-assistant-1.0.0-SNAPSHOT.jar --spring.profiles.active=local
   ```

5. **验证**
   ```bash
   curl http://localhost:8080/api/v1/actuator/health
   ```

### API 端点概览

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/rag/ask` | RAG 模式提问（支持指定文档ID） |
| POST | `/chat/history` | 创建对话记录 |
| GET | `/chat/history/{id}` | 查询单条对话（含多级缓存） |
| GET | `/chat/history/session/{sessionId}` | 查询会话下所有对话 |
| POST | `/document/upload` | 上传文档（触发异步解析） |
| GET | `/actuator/health` | 健康检查 |

---

## 项目结构

```
src/main/java/com/sa/assistant/
├── config/                    # 配置层
│   ├── CacheConfig.java       # Caffeine + Redis 多级缓存配置
│   ├── RabbitMQConfig.java    # RabbitMQ 队列/交换机/DLX 配置
│   ├── RedisConfig.java       # RedisTemplate 序列化配置
│   └── AsyncConfig.java       # 异步任务线程池配置
├── service/                   # 业务逻辑层
│   ├── RagService.java        # RAG 检索与生成
│   ├── ChatHistoryService.java # 对话历史（含缓存注解）
│   ├── DocumentParseService.java # Tika 文档解析
│   └── TextChunkService.java  # 文档分块
├── cache/                     # 缓存基础设施
│   ├── MultiLevelCacheManager.java
│   ├── MultiLevelCache.java
│   ├── CacheConsistencyManager.java
│   └── CacheReconciliationScheduler.java
├── common/
│   └── lock/                  # 分布式锁
│       ├── DistributedLock.java  # SET NX + Lua + WatchDog
│       └── LockAcquisitionException.java
├── controller/                # REST API
├── model/                     # DTO / Entity / Response
└── repository/                # JPA Repository
```

---

## License

MIT License
