# Deployment & Infrastructure

This document details local configurations, multi-stage building, and production topography for the Chat Platform.

---

## 1. Local Development (Docker Compose)
We define PostgreSQL configuration in `docker-compose.yml`.

To spin up application databases:
```bash
docker compose up -d
```

---

## 2. Operating System & Web Server Optimization

To support high-concurrency WebSocket connections in production:

### 1. OS File Descriptors
Every WebSocket connection maintains an open file descriptor (socket). The default Linux limit is `1024` per process.
- **Action**: In `/etc/security/limits.conf`, add:
  ```text
  spring   soft   nofile   65536
  spring   hard   nofile   65536
  ```

### 2. Nginx Reverse Proxy Configuration
If Nginx acts as a reverse proxy/load balancer, configure connection upgrading:
```nginx
http {
    map $http_upgrade $connection_upgrade {
        default upgrade;
        ''      close;
    }

    server {
        listen 80;
        server_name chat.example.com;

        location /ws {
            proxy_pass http://backend_upstream/ws;
            proxy_http_version 1.1;
            proxy_set_header Upgrade $http_upgrade;
            proxy_set_header Connection $connection_upgrade;
            proxy_set_header Host $host;
            proxy_read_timeout 86400s; # Prevent proxy from dropping idle connections
        }

        location /api {
            proxy_pass http://backend_upstream/api;
        }
    }
}
```

### 3. Load Balancer Idle Timeouts
Ensure timeouts on load balancers (like AWS ALB) are set higher than the application's heartbeat interval (e.g., 60 seconds) to prevent connections from being dropped.
