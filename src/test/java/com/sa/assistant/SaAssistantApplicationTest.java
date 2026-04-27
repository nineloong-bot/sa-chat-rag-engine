package com.sa.assistant;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 全量上下文加载测试。
 * 依赖外部基础设施（MySQL, Redis, RabbitMQ, ChromaDB），
 * 默认禁用，CI 环境中通过 docker-compose 启动依赖后手动激活：
 * mvn test -Dtest=SaAssistantApplicationTest -Dspring.profiles.active=dev
 */
@SpringBootTest
@Disabled("Requires running infrastructure: MySQL, Redis, RabbitMQ, ChromaDB")
class SaAssistantApplicationTest {

    @Test
    void contextLoads() {
    }
}
