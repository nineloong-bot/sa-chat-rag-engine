# SA-Chat-RAG-Engine 核心开发指南

## 🌍 项目全景 (Project Overview)
- **定位**：基于 Spring Boot 3 + Spring AI 构建的高性能对话系统，支持“纯闲聊”与“文档 RAG”双模式。
- **架构形态**：前后端分离的 Monorepo（`/src` 为 Java 后端，`/frontend` 为 Vue3+TS 前端）。
- **核心组件**：Java 21, Spring Boot 3.4, Spring AI, ChromaDB, Redis, RabbitMQ, Caffeine, Tika。

## 🏗️ 架构与边界约定 (Architecture Constraints)
在为本项目编写或修改代码时，必须严格遵守以下系统设计：
1. **多级缓存 (L1+L2)**：任何涉及高频查询的业务（如聊天历史记录），必须走 L1(Caffeine) -> L2(Redis) -> DB 的 Cache-Aside 架构。写操作必须配合 `CacheConsistencyManager` 清理缓存。
2. **异步解析链路**：大文件解析绝不允许在主线程同步阻塞。必须将文档 URL 或流通过 RabbitMQ 放入 `rag.document.queue`，由消费者调用 Tika 处理，并严格控制超时与 DLX（死信队列）逻辑。
3. **并发安全**：涉及核心状态变更（如会话创建），必须使用我们自研的 Redis 分布式锁（SET NX + Lua + WatchDog），禁止裸奔。

## ⚠️ 核心避坑指南 (Critical Gotchas)
**这是本项目的硬性红线，绝对不能违反：**
- **向量数据库写入（极度重要）**：在处理向 ChromaDB 写入向量数据时，**严禁使用 Spring AI 默认的 `ChromaVectorStore.add()` 抽象**。该抽象在当前版本存在静默失败的隐患（不抛异常且不写入数据）。
- **解决方案**：必须通过构建 `RestClient`，直接调用 ChromaDB 的底层 upsert API 端点（并携带正确的 collection UUID）来进行向量数据的持久化。

## 💅 代码洁癖与工程规范 (Coding Standards)
### Backend (Java)
- 遵循 SOLID 原则。拒绝面条代码（Spaghetti code）。
- **可观测性**：关键的异常捕获区、缓存未命中区、RPC 调用区必须使用 Slf4j 记录带有上下文参数的日志，并考虑 Micrometer 指标埋点。
- **防御性编程**：永远警惕 NPE（空指针异常），对外部调用的结果必须做 Fallback（降级）处理。

### Frontend (React 18 + TS)
- 使用 React 18 + TSX。
- 严格进行 TypeScript 类型定义，禁止滥用 `any`。
- RAG 系统的流式输出（Streaming）组件必须处理好长文本的 DOM 性能问题，并在视觉上优雅解析 Markdown 和引用来源。

## 🤖 Agent 执行指令 (Agent Directives)
- 在执行重构或添加新功能前，先阅读这三个关键文件：`pom.xml`, `docker-compose.yml`, 以及对应模块的 `Config` 类，确保不引入冲突的依赖。
- 如果不确定某个架构设计的历史包袱，在动手写代码前，先向我提问确认。