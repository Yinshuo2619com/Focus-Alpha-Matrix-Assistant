# Focus Alpha Matrix Assistant — 校园助手

> 通过反向代理技术，将学校教务系统嵌入自有平台，实现课表自动抓取、解析和展示的校园教育辅助应用。

## 功能特性

- **课表自动导入** — 嵌入教务系统 iframe，用户登录后自动抓取课程数据，支持卡片式和表格式两种教务系统布局
- **教务系统反向代理** — 完整的 HTTP 代理，包括 Cookie 管理、HTML 注入 JS 劫持网络请求、URL 重写、SSL 绕过
- **课表展示** — 12 节次 × 7 天的网格课表，支持多周次切换、自动定位当前周
- **用户认证** — BCrypt 密码哈希 + JWT Token + 角色权限控制（USER / ADMIN）
- **头像上传** — 支持腾讯云 COS 对象存储和本地存储双模式
- **管理后台** — 用户管理、系统配置

## 技术栈

### 前端

| 技术 | 用途 |
|------|------|
| Vue 3 + TypeScript | 框架，Composition API + `<script setup>` |
| Pinia | 状态管理 |
| Element Plus | UI 组件库 |
| Axios | HTTP 请求（JWT 拦截器） |
| Vite | 构建工具 |

### 后端

| 技术 | 用途 |
|------|------|
| Spring Boot 3 | 框架 |
| Spring Security + JWT | 认证授权（无状态） |
| MyBatis-Plus + JdbcTemplate | 数据访问 |
| Redis | 教务系统 Session 存储、Token 黑名单 |
| MySQL | 业务数据存储 |
| Jsoup | HTML 解析 |

### 部署

| 技术 | 用途 |
|------|------|
| Nginx | 反向代理、静态资源托管 |
| 腾讯云 COS | 头像文件存储（自定义域名 + HTTPS） |
| 云服务器 | 后端部署 |

## 系统架构

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   Browser   │────▶│   Nginx     │────▶│  Vue SPA    │
│             │     │  (port 80)  │     │ (静态资源)   │
└─────────────┘     └──────┬──────┘     └─────────────┘
                           │ /api/*
                           ▼
                    ┌─────────────┐     ┌─────────────┐
                    │ Spring Boot │────▶│    MySQL    │
                    │  (port 8080)│     └─────────────┘
                    │             │     ┌─────────────┐
                    │             │────▶│    Redis    │
                    └──────┬──────┘     └─────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │ 学校教务系统 │
                    │ (HTTPS)     │
                    └─────────────┘
```

**请求流程**：浏览器 → Nginx（静态资源直接返回，`/api/*` 转发到后端）→ Spring Boot → MySQL / Redis / 教务系统

## 项目结构

```
Focus Alpha Matrix Assistant/
├── frontend/                   # 前端 Vue 3 项目
│   ├── src/
│   │   ├── components/         # 组件（StatusBar、ScheduleGrid、EduSystemFrame）
│   │   ├── views/              # 页面（Home、Login、Register、Profile、ScheduleImport）
│   │   ├── stores/             # Pinia 状态（user.ts、schedule.ts）
│   │   ├── router/             # 路由配置
│   │   ├── utils/              # 工具（request.ts — Axios 封装）
│   │   └── types/              # TypeScript 类型定义
│   ├── package.json
│   └── vite.config.ts
│
├── backend/assistant/           # 后端 Spring Boot 项目
│   ├── src/main/java/com/educate/assistant/
│   │   ├── controller/         # 控制器（Auth、User、Admin、Schedule、EduProxy）
│   │   ├── service/            # 业务逻辑（UserService、ScheduleService、EduProxyService）
│   │   ├── entity/             # 实体类
│   │   ├── mapper/             # MyBatis-Plus Mapper
│   │   ├── config/             # 配置（SecurityConfig、JwtFilter、WebConfig）
│   │   └── util/               # 工具类（JwtUtil）
│   ├── src/main/resources/
│   │   ├── application.yml     # 开发环境配置
│   │   ├── application-prod.yml# 生产环境配置
│   │   ├── mapper/             # Mapper XML
│   │   └── schema-schedule.sql # 数据库 DDL
│   └── pom.xml
│
└── 操作记录.md                  # 部署操作文档
```

## 快速启动

### 环境要求

- Node.js 18+
- Java 17+
- MySQL 8.0+
- Redis

### 数据库准备

```sql
CREATE DATABASE edu_assistant DEFAULT CHARACTER SET utf8mb4;
```

建表 SQL 参见 `backend/assistant/src/main/resources/schema-schedule.sql`

### 后端启动

```bash
cd backend/assistant
# 修改 application.yml 中的数据库连接信息
./mvnw spring-boot:run
```

后端启动在 http://localhost:8080

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端启动在 http://localhost:5173，通过 Vite 代理将 `/api` 请求转发到后端。

## 部署

### 打包

```bash
# 前端
cd frontend
npm run build          # 产物：frontend/dist/

# 后端
cd backend/assistant
./mvnw package -DskipTests   # 产物：target/assistant-0.0.1-SNAPSHOT.jar
```

### 服务器启动

```bash
nohup java -jar assistant-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod > app.log 2>&1 &
```

### Nginx 配置

```nginx
server {
    listen 80;
    server_name www.yinshuo.top;

    # 前端静态资源
    location / {
        root /www/wwwroot/assistant/dist;
        index index.html;
        try_files $uri $uri/ /index.html;
    }

    # 后端 API 代理
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    }

    # 头像静态资源
    location /avatar/ {
        proxy_pass http://127.0.0.1:8080;
    }
}
```

## 核心设计

### 教务系统反向代理

这是项目的核心亮点。`EduProxyController` 实现了完整的 HTTP 反向代理，将学校教务系统嵌入自有平台：

1. **请求转发** — 前端请求 → 自有后端 → 教务系统 → 返回数据
2. **Cookie 管理** — Redis 存储每个用户的教务系统 Session，区分登录/非登录响应防止 Cookie 轮转覆盖
3. **HTML 注入** — 在返回的 HTML 中注入 JS，劫持 `jQuery.ajax`、`fetch`、`XMLHttpRequest`，将所有请求路由回代理
4. **URL 重写** — 教务系统 URL 改写为代理路径
5. **SSL 绕过** — 兼容教务系统自签名证书
6. **重定向处理** — 服务端跟随 302，防止浏览器直接访问教务系统

### 课表解析

支持三种解析方式：

- **前端 DOM 解析** — 直接读取 iframe DOM，正则匹配课程卡片
- **后端 HTML 解析** — Jsoup 解析 HTML，支持卡片式和表格式布局
- **JSON API 解析** — 调用教务系统 AJAX 接口，正则提取 `dateTimePlacePersonText` 字段

### 认证授权

- BCrypt 密码哈希 → JWT Token（3.5 天过期）→ `Authorization: Bearer` Header → `JwtAuthenticationFilter` 验证
- 角色区分：用户名 "admin" 为管理员，其余为普通用户
- 前端 401/403 自动跳转登录页

### 数据库设计

| 表 | 用途 |
|------|------|
| `user` | 用户信息（BCrypt 密码哈希、头像 URL、角色） |
| `schedule` | 课表主表（用户、学期、当前周次、起始日期） |
| `course_entry` | 课程条目（名称、教师、地点、星期、节次、周次） |
| `school_config` | 学校配置（教务系统 URL，支持多学校扩展） |

## 项目亮点

- **反向代理技术** — 完整实现 HTTP 代理，包括 Cookie 管理、HTML 注入、URL 重写
- **前端 JS 劫持** — 注入脚本劫持 `jQuery.ajax`、`fetch`、`XMLHttpRequest`，实现请求拦截
- **Session 安全管理** — 区分登录/非登录响应，防止 Cookie 轮转破坏会话
- **多种解析策略** — HTML 解析 + JSON API 两种方式，兼容不同教务系统
- **多学校架构** — 配置化设计，`school_config` 表存储各学校配置
- **COS 自定义域名** — 腾讯云对象存储自定义域名 + SSL 证书

## 量化成果

- 课表导入时间从手动 10 分钟降低到 30 秒
- 支持 12 节次 × 7 天 × 多周次的完整课表展示
- Redis Session TTL 2 小时，平衡性能和安全
- 前端 Bundle 约 500KB（Gzip 后约 150KB）
