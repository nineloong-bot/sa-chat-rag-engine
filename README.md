# ChatRAG Engine

> 基于 Spring AI + React 的智能对话助手，支持闲聊与文档 RAG 双模式

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-brightgreen.svg)](https://spring.io/projects/spring-ai)
[![React](https://img.shields.io/badge/React-18.3-blue.svg)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-6.0-brightgreen.svg)](https://vitejs.dev/)

---

## 项目简介

ChatRAG Engine 是一个基于 **Spring Boot + Spring AI + React** 的智能对话系统，支持**双模式切换**：

- **闲聊模式**：纯粹的 LLM 对话能力，适用于日常问答、创意生成等场景
- **RAG 模式**：基于上传文档的检索增强生成，文档经 Apache Tika 解析、向量嵌入后存储于 Chroma 向量数据库，LLM 仅基于检索到的相关内容作答，保证答案准确可溯源

系统面向生产级部署，在缓存、消息队列、分布式锁等基础设施层面做了完整的高可用设计。

---

## 技术架构

### 后端 (Spring Boot)

```
Spring Boot 3.4.4 + Spring AI 1.0.0
├── 双模型支持：OpenAI GPT-4o / Ollama (qwen2.5:3b)
├── 向量数据库：Chroma (本地部署)
├── 多级缓存：Caffeine (L1) + Redis (L2)
├── 消息队列：RabbitMQ (文档异步解析)
├── 文档解析：Apache Tika (PDF/Word/TXT)
└── 数据库：MySQL 8.0
```

### 前端 (React)

```
React 18.3 + TypeScript + Vite 6
├── UI 组件：Ant Design 5
├── 状态管理：Zustand 5
├── HTTP 客户端：Axios
└── 路由：React Router DOM 7
```

### 基础设施 (Docker)

```
MySQL 8.0 + Redis 7 + RabbitMQ 3.13 + Chroma + Ollama
```

---

## 核心功能

### 1. 流式对话 (SSE)

支持实时流式输出，通过 Server-Sent Events (SSE) 逐字返回 AI 回答，提供流畅的对话体验。

### 2. 文档 RAG

- 支持 PDF、Word、TXT、Markdown 等格式文档上传
- 异步解析：通过 RabbitMQ 队列处理，避免阻塞
- 向量化存储：基于 Chroma 实现语义检索
- 可指定文档提问，或在全部文档中检索

### 3. 多级缓存

- L1 Caffeine（本地）：3分钟 TTL
- L2 Redis（分布式）：20分钟 TTL
- Cache-Aside 模式 + 定时校准

### 4. 分布式锁

基于 Redis SET NX + Lua 脚本实现，支持 WatchDog 自动续期。

### 5. 文档解析重试机制

- 三级重试 + 死信队列 (DLX)
- 5 分钟解析超时控制
- 10MB 内容上限

---

## 快速开始

### 环境要求

- JDK 21+
- Node.js 18+
- Docker Desktop

### 启动基础设施 (Docker)

```bash
cd /Users/nineloong/codes/ChatRAG\ Engine
docker-compose up -d
```

### 启动后端

```bash
cd /Users/nineloong/codes/ChatRAG\ Engine
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 启动前端

```bash
cd /Users/nineloong/codes/ChatRAG\ Engine/frontend
npm install
npm run dev
```

### 访问地址

| 服务 | 地址 |
|------|------|
| 前端页面 | http://localhost:3000 |
| 后端 API | http://localhost:8080/api/v1 |
| 健康检查 | http://localhost:8080/api/v1/actuator/health |
| RabbitMQ 管理 | http://localhost:15672 |

---

## API 文档

### 对话接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/rag/ask` | 同步对话（阻塞式响应） |
| POST | `/rag/ask/stream` | 流式对话（SSE） |

**请求示例：**
```json
POST /rag/ask
{
  "question": "你好，请介绍一下自己",
  "documentId": null,
  "topK": 5
}
```

**响应示例：**
```json
{
  "code": 200,
  "data": {
    "answer": "您好！我是一个AI助手...",
    "source": "RAG",
    "relevantChunkCount": 3,
    "contextPreview": "..."
  }
}
```

### 聊天历史

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/chat/history` | 创建对话记录 |
| GET | `/chat/history/{id}` | 查询单条对话 |
| GET | `/chat/history/session/{sessionId}` | 查询会话下所有对话 |

### 文档管理

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/document/upload` | 上传文档 |
| GET | `/document/list` | 文档列表 |
| GET | `/document/{id}` | 文档详情 |
| GET | `/document/{id}/status` | 解析状态 |

---

## 项目结构

```
ChatRAG Engine/
├── frontend/                      # React 前端
│   ├── src/
│   │   ├── api/                   # API 调用
│   │   ├── components/            # UI 组件
│   │   │   ├── chat/              # 聊天相关组件
│   │   │   ├── document/          # 文档相关组件
│   │   │   └── layout/            # 布局组件
│   │   ├── pages/                 # 页面
│   │   ├── stores/                # Zustand 状态管理
│   │   ├── types/                 # TypeScript 类型
│   │   └── utils/                 # 工具函数
│   └── package.json
│
├── src/main/java/com/sa/assistant/
│   ├── config/                    # 配置层
│   │   ├── CacheConfig.java       # 多级缓存配置
│   │   ├── RabbitMQConfig.java    # RabbitMQ 配置
│   │   └── RedisConfig.java       # Redis 配置
│   │
│   ├── controller/                # 控制器层
│   │   ├── RagController.java     # RAG 对话接口
│   │   ├── ChatHistoryController.java
│   │   └── DocumentController.java
│   │
│   ├── service/                   # 业务逻辑层
│   │   ├── RagService.java        # RAG 核心服务
│   │   ├── ChatHistoryService.java
│   │   ├── DocumentService.java
│   │   ├── DocumentParseService.java
│   │   └── TextChunkService.java
│   │
│   ├── infra/                     # 基础设施层
│   │   ├── ChromaVectorStore.java # Chroma 向量库封装
│   │   └── OllamaEmbeddingClient.java
│   │
│   ├── cache/                     # 缓存组件
│   ├── consumer/                  # RabbitMQ 消费者
│   ├── common/                    # 公共组件
│   └── model/                     # 数据模型
│
├── docker/                       # Docker 配置
│   └── mysql/init/                # MySQL 初始化脚本
├── docker-compose.yml             # 基础设施编排
└── pom.xml                        # Maven 依赖
```

---

## 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.4.4 |
| AI 集成 | Spring AI | 1.0.0 |
| 前端框架 | React | 18.3 |
| 构建工具 | Vite | 6.0 |
| LLM | Ollama (qwen2.5:3b) / OpenAI | - |
| Embedding | Ollama (nomic-embed-text) | - |
| 向量数据库 | Chroma | 1.0.0 |
| 本地缓存 | Caffeine | 3.1.8 |
| 分布式缓存 | Redis | 7 |
| 消息队列 | RabbitMQ | 3.13 |
| 文档解析 | Apache Tika | 2.9.3 |
| 数据库 | MySQL | 8.0 |
| UI 组件 | Ant Design | 5.24 |
| 状态管理 | Zustand | 5.0 |

---

## License

MIT
