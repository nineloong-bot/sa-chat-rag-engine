# SA Intelligent Assistant

## Prerequisites
- Java 21+
- Docker & Docker Compose
- Maven 3.9+

## Quick Start

### 1. Start Infrastructure
```bash
docker compose up -d
```

### 2. Build & Run
```bash
./mvnw spring-boot:run
```

### 3. Verify
```bash
curl http://localhost:8080/api/api/health
```

## Infrastructure Ports
| Service   | Port  |
|-----------|-------|
| App       | 8080  |
| MySQL     | 3306  |
| Redis     | 6379  |
| RabbitMQ  | 5672  |
| RabbitMQ Management | 15672 |
| ChromaDB  | 8000  |
