# ChatRAG Engine

> An intelligent chat assistant based on Spring AI + React, supporting both casual chat and document RAG modes

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-brightgreen.svg)](https://spring.io/projects/spring-ai)
[![React](https://img.shields.io/badge/React-18.3-blue.svg)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-6.0-brightgreen.svg)](https://vitejs.dev/)

[中文](./README.md) | English

---

## Introduction

ChatRAG Engine is an intelligent chat system based on **Spring Boot + Spring AI + React**, supporting **dual-mode switching**:

- **Chat Mode**: Pure LLM conversation capability, suitable for daily Q&A, creative generation, and other scenarios
- **RAG Mode**: Retrieval-Augmented Generation based on uploaded documents. Documents are parsed by Apache Tika, vectorized, and stored in Chroma vector database. The LLM answers only based on retrieved relevant content, ensuring accurate and traceable answers.

The system is designed for production-grade deployment with complete high-availability design in caching, message queues, distributed locks, and other infrastructure layers.

---

## Technical Architecture

### Backend (Spring Boot)

```
Spring Boot 3.4.4 + Spring AI 1.0.0
├── Dual Model Support: OpenAI GPT-4o / Ollama (qwen2.5:3b)
├── Vector Database: Chroma (Local Deployment)
├── Multi-level Cache: Caffeine (L1) + Redis (L2)
├── Message Queue: RabbitMQ (Async Document Parsing)
├── Document Parsing: Apache Tika (PDF/Word/TXT)
└── Database: MySQL 8.0
```

### Frontend (React)

```
React 18.3 + TypeScript + Vite 6
├── UI Components: Ant Design 5
├── State Management: Zustand 5
├── HTTP Client: Axios
├── Routing: React Router DOM 7
├── Markdown: react-markdown + remark-gfm
└── Code Highlighting: react-syntax-highlighter
```

### Infrastructure (Docker)

```
MySQL 8.0 + Redis 7 + RabbitMQ 3.13 + Chroma + Ollama
```

---

## Core Features

### 1. Streaming Chat (SSE)

Supports real-time streaming output, returning AI responses character by character through Server-Sent Events (SSE), providing a smooth chat experience.

### 2. Document RAG

- Support document upload in PDF, Word, TXT, Markdown formats
- Asynchronous parsing: Processed through RabbitMQ queue to avoid blocking
- Vector storage: Semantic retrieval based on Chroma
- Can ask questions targeting specific documents or search across all documents

### 3. Multi-level Cache

- L1 Caffeine (Local): 3-minute TTL
- L2 Redis (Distributed): 20-minute TTL
- Cache-Aside pattern + periodic calibration

### 4. Distributed Lock

Implemented based on Redis SET NX + Lua scripts, supporting WatchDog automatic renewal.

### 5. Document Parsing Retry Mechanism

- Three-level retry + Dead Letter Queue (DLX)
- 5-minute parsing timeout control
- 10MB content limit

### 6. Frontend Accessibility & Internationalization

- **Accessibility**: All interactive elements support keyboard navigation and screen readers
- **Animation Adaptation**: Respects `prefers-reduced-motion` user preferences
- **Internationalization**: Date/time formatted using `Intl.DateTimeFormat`
- **Destructive Operation Protection**: Deletion operations require二次 confirmation

---

## Quick Start

### Prerequisites

- JDK 21+
- Node.js 18+
- Docker Desktop

### Start Infrastructure (Docker)

```bash
cd /Users/nineloong/codes/ChatRAG\ Engine
docker-compose up -d
```

### Start Backend

```bash
cd /Users/nineloong/codes/ChatRAG\ Engine
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Start Frontend

```bash
cd /Users/nineloong/codes/ChatRAG\ Engine/frontend
npm install
npm run dev
```

### Access URLs

| Service | URL |
|---------|-----|
| Frontend | http://localhost:3000 |
| Backend API | http://localhost:8080/api/v1 |
| Health Check | http://localhost:8080/api/v1/actuator/health |
| RabbitMQ Management | http://localhost:15672 |

---

## API Documentation

### Chat Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/rag/ask` | Synchronous chat (blocking response) |
| POST | `/rag/ask/stream` | Streaming chat (SSE) |

**Request Example:**
```json
POST /rag/ask
{
  "question": "Hello, please introduce yourself",
  "documentId": null,
  "topK": 5
}
```

**Response Example:**
```json
{
  "code": 200,
  "data": {
    "answer": "Hello! I am an AI assistant...",
    "source": "RAG",
    "relevantChunkCount": 3,
    "contextPreview": "..."
  }
}
```

### Chat History

| Method | Path | Description |
|--------|------|-------------|
| POST | `/chat/history` | Create chat record |
| GET | `/chat/history/{id}` | Query single chat |
| GET | `/chat/history/session/{sessionId}` | Query all chats in session |

### Document Management

| Method | Path | Description |
|--------|------|-------------|
| POST | `/document/upload` | Upload document |
| GET | `/document/list` | Document list |
| GET | `/document/{id}` | Document details |
| GET | `/document/{id}/status` | Parsing status |

---

## Project Structure

```
ChatRAG Engine/
├── frontend/                      # React Frontend
│   ├── src/
│   │   ├── api/                   # API calls
│   │   ├── components/            # UI components
│   │   │   ├── chat/              # Chat-related components
│   │   │   ├── document/          # Document-related components
│   │   │   └── layout/            # Layout components
│   │   ├── pages/                 # Pages
│   │   ├── stores/                # Zustand state management
│   │   ├── types/                 # TypeScript types
│   │   └── utils/                 # Utility functions
│   └── package.json
│
├── src/main/java/com/sa/assistant/
│   ├── config/                    # Configuration layer
│   │   ├── CacheConfig.java       # Multi-level cache config
│   │   ├── RabbitMQConfig.java    # RabbitMQ config
│   │   └── RedisConfig.java       # Redis config
│   │
│   ├── controller/                # Controller layer
│   │   ├── RagController.java     # RAG chat endpoints
│   │   ├── ChatHistoryController.java
│   │   └── DocumentController.java
│   │
│   ├── service/                   # Business logic layer
│   │   ├── RagService.java        # RAG core service
│   │   ├── ChatHistoryService.java
│   │   ├── DocumentService.java
│   │   ├── DocumentParseService.java
│   │   └── TextChunkService.java
│   │
│   ├── infra/                     # Infrastructure layer
│   │   ├── ChromaVectorStore.java # Chroma vector store wrapper
│   │   └── OllamaEmbeddingClient.java
│   │
│   ├── cache/                     # Cache components
│   ├── consumer/                  # RabbitMQ consumers
│   ├── common/                    # Common components
│   └── model/                     # Data models
│
├── docker/                       # Docker configuration
│   └── mysql/init/                # MySQL initialization scripts
├── docker-compose.yml             # Infrastructure orchestration
└── pom.xml                        # Maven dependencies
```

---

## Tech Stack

| Layer | Technology | Version |
|-------|------------|---------|
| Backend Framework | Spring Boot | 3.4.4 |
| AI Integration | Spring AI | 1.0.0 |
| Frontend Framework | React | 18.3 |
| Build Tool | Vite | 6.0 |
| LLM | Ollama (qwen2.5:3b) / OpenAI | - |
| Embedding | Ollama (nomic-embed-text) | - |
| Vector Database | Chroma | 1.0.0 |
| Local Cache | Caffeine | 3.1.8 |
| Distributed Cache | Redis | 7 |
| Message Queue | RabbitMQ | 3.13 |
| Document Parsing | Apache Tika | 2.9.3 |
| Database | MySQL | 8.0 |
| UI Components | Ant Design | 5.24 |
| State Management | Zustand | 5.0 |

---

## License

MIT
