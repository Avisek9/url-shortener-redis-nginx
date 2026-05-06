# URL Shortener — Spring Boot + Redis + Nginx

A production-ready URL shortening service built with Spring Boot, featuring Redis caching for high-speed lookups and Nginx load balancing across multiple app instances.

---

## Architecture

```
Client → Nginx :9090 → App Instance 1 :8081 ─┐
                        App Instance 2 :8082 ─┤→ Redis :6379
                        App Instance 3 :8083 ─┘
                                ↓
                           H2 Database
```

---

## Features

- Generate unique short URLs from long URLs
- Fast redirection via short link lookup
- Murmur3 hashing algorithm for collision-resistant URL generation
- Redis caching — repeat lookups skip the database entirely
- Cache TTL automatically matches URL expiration time
- Nginx load balancing across 3 Spring Boot instances using least-connection strategy
- Graceful Redis fallback — if Redis is down, requests fall through to H2
- URL expiration support with configurable expiry date
- RESTful API design tested with Postman

---

## Technology Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.2.5 |
| Caching | Redis, Spring Cache |
| Load Balancer | Nginx (least_conn) |
| Hashing | Murmur3 via Google Guava |
| Database | H2 (in-memory) |
| Build Tool | Maven |
| Testing | Postman |

---

## Prerequisites

- Java 21
- Maven
- Redis
- Nginx

### Install on macOS

```bash
brew install redis nginx
brew services start redis
```

---

## Running Locally

### 1. Clone the repository

```bash
git clone https://github.com/YOUR_USERNAME/url-shortener-redis-nginx.git
cd url-shortener-redis-nginx
```

### 2. Start Redis

```bash
brew services start redis
redis-cli ping   # should return PONG
```

### 3. Configure Nginx

```bash
mkdir -p /opt/homebrew/etc/nginx/servers
cp nginx/nginx.conf /opt/homebrew/etc/nginx/servers/url-shortener.conf
brew services start nginx
curl http://127.0.0.1:9090/nginx-health   # should return: healthy
```

### 4. Run 3 app instances

Open 3 terminals and run one in each:

```bash
# Terminal 1
SERVER_PORT=8081 ./mvnw spring-boot:run

# Terminal 2
SERVER_PORT=8082 ./mvnw spring-boot:run

# Terminal 3
SERVER_PORT=8083 ./mvnw spring-boot:run
```

Or configure 3 run configurations in IntelliJ IDEA with `SERVER_PORT=8081/8082/8083` as environment variables.

---

## API Reference

### Generate a short URL

```
POST http://127.0.0.1:9090/generate
Content-Type: application/json
```

**Request body:**
```json
{
    "url": "https://example.com",
    "expirationDate": "2026-12-31T23:59:59"
}
```

**Response:**
```json
{
    "originalUrl": "https://example.com",
    "shortLink": "1314c06c",
    "expirationDate": "2026-12-31T23:59:59"
}
```

> `expirationDate` is optional. Defaults to 60 seconds from creation if not provided.

---

### Redirect via short link

```
GET http://127.0.0.1:9090/{shortLink}
```

Redirects to the original URL if the link exists and has not expired.

---

### Nginx health check

```
GET http://127.0.0.1:9090/nginx-health
```

Returns `healthy` if Nginx is running correctly.

---

## How Redis Caching Works

1. On first lookup, the URL is fetched from H2 and stored in Redis with a TTL matching its expiration time
2. On subsequent lookups, Redis serves the response instantly without touching the database
3. When a URL is deleted or expires, it is evicted from the Redis cache immediately
4. If Redis is unavailable, all requests fall through to H2 transparently

---

## H2 Console

Access the in-memory database console at:

```
http://127.0.0.1:8081/h2-console
JDBC URL: jdbc:h2:mem:urlshortenerdb
Username: sa
Password: (leave blank)
```

---

## Future Enhancements

- Custom short URL aliases
- Click analytics dashboard
- JWT-based authentication
- Rate limiting and abuse protection
- PostgreSQL/MySQL support for production
- Docker Compose setup
