# Focus Alpha Matrix Assistant — 校园助手

> 通过反向代理技术，将学校教务系统嵌入自有平台，实现课表自动抓取、解析和展示的校园教育辅助应用。

## 功能特性

- **课表自动导入** — 嵌入教务系统 iframe，用户登录后自动抓取课程数据，支持卡片式和表格式两种教务系统布局
- **教务系统反向代理** — 完整的 HTTP 代理，包括 Cookie 管理、HTML 注入 JS 劫持网络请求、URL 重写、SSL 绕过
- **课表展示** — 12 节次 × 7 天的网格课表，支持多周次切换、自动定位当前周
- **用户认证** — BCrypt 密码哈希 + JWT Token + 角色权限控制（USER / ADMIN）
- **头像上传** — 支持腾讯云 COS 对象存储和本地存储双模式
- **小工具 / 用户推荐** — 管理员发布小工具（支持跳转链接），用户发布图文推荐，支持点赞、收藏、评论
- **电费查询** — 绑定宿舍后自动采集余额，ECharts 折线图展示每日用电趋势，与楼栋平均对比
- **消息通知** — 文章收藏、评论回复、评论点赞等事件自动推送通知
- **管理后台** — 用户管理、系统配置

## 技术栈

### 前端

| 技术 | 用途 |
|------|------|
| Vue 3 + TypeScript | 框架，Composition API + `<script setup>` |
| Pinia | 状态管理 |
| Element Plus | UI 组件库 |
| ECharts | 电费趋势图表 |
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
| Jsoup | HTML 解析（教务系统页面 + 电费查询页面） |

### 部署

| 技术 | 用途 |
|------|------|
| Nginx | 反向代理、静态资源托管 |
| 腾讯云 COS | 头像、推荐封面、文章内容文件存储（自定义域名 + HTTPS） |
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
                    ┌──────┴──────┐
                    ▼             ▼
             ┌─────────────┐ ┌─────────────┐
             │ 学校教务系统 │ │ 校园卡电费API│
             │ (HTTPS)     │ │ (HTTPS)     │
             └─────────────┘ └─────────────┘
```

**请求流程**：浏览器 → Nginx（静态资源直接返回，`/api/*` 转发到后端）→ Spring Boot → MySQL / Redis / 教务系统 / 电费 API

## 项目结构

```
Focus Alpha Matrix Assistant/
├── frontend/                        # 前端 Vue 3 项目
│   ├── src/
│   │   ├── components/              # 组件
│   │   │   ├── StatusBar.vue        #   顶部导航栏
│   │   │   ├── ScheduleGrid.vue     #   课表网格
│   │   │   ├── EduSystemFrame.vue   #   教务系统 iframe
│   │   │   ├── ElectricityCard.vue  #   首页电费卡片
│   │   │   └── tools/               #   小工具组件
│   │   ├── views/                   # 页面
│   │   │   ├── Home.vue             #   首页（课表 + 电费卡片）
│   │   │   ├── Tools.vue            #   小工具 / 用户推荐
│   │   │   ├── ElectricityDetail.vue#   电费详情（ECharts 图表）
│   │   │   ├── RecommendDetail.vue  #   推荐详情 + 评论
│   │   │   ├── RecommendEditor.vue  #   推荐编辑器（Markdown）
│   │   │   ├── Notifications.vue    #   消息通知
│   │   │   ├── Favorites.vue        #   我的收藏
│   │   │   ├── MyComments.vue       #   我的评论
│   │   │   ├── Profile.vue          #   个人中心（含宿舍绑定）
│   │   │   ├── ScheduleImport.vue   #   课表导入
│   │   │   ├── ShareSchedule.vue    #   课表分享
│   │   │   ├── Login.vue / Register.vue
│   │   │   └── admin/               #   管理后台
│   │   ├── stores/                  # Pinia 状态（user.ts、schedule.ts）
│   │   ├── router/                  # 路由配置
│   │   ├── utils/                   # 工具（request.ts — Axios 封装）
│   │   └── types/                   # TypeScript 类型定义
│   ├── package.json
│   └── vite.config.ts
│
├── backend/assistant/               # 后端 Spring Boot 项目
│   ├── src/main/java/com/educate/assistant/
│   │   ├── controller/              # 控制器
│   │   │   ├── AuthController.java  #   登录注册（公开）
│   │   │   ├── UserController.java  #   用户信息（认证）
│   │   │   ├── AdminController.java #   管理后台（ADMIN）
│   │   │   ├── ScheduleController.java # 课表 CRUD
│   │   │   ├── EduProxyController.java # 教务系统反向代理
│   │   │   ├── RecommendController.java # 推荐 / 工具 / 评论
│   │   │   ├── ElectricityController.java # 电费查询
│   │   │   └── NotificationController.java # 消息通知
│   │   ├── service/                 # 业务逻辑
│   │   ├── task/                    # 定时任务（ElectricityTask — 每日电费采集）
│   │   ├── entity/                  # 实体类
│   │   ├── config/                  # 配置（SecurityConfig、JwtFilter、WebConfig）
│   │   └── util/                    # 工具类（JwtUtil）
│   ├── src/main/resources/
│   │   ├── application.yml          # 开发环境配置
│   │   ├── application-prod.yml     # 生产环境配置
│   │   ├── schema-schedule.sql      # 课表 DDL
│   │   └── schema-recommend.sql     # 推荐 / 电费 DDL
│   └── pom.xml
│
└── 操作记录.md                       # 部署操作文档
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

建表 SQL 参见 `backend/assistant/src/main/resources/schema-schedule.sql` 和 `schema-recommend.sql`

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

### 电费查询

1. 用户在个人中心绑定宿舍（选择楼栋 → 房间）
2. 后端定时任务每日凌晨 4:00 采集所有已绑定楼栋的房间余额（校园卡 API）
3. 首页电费卡片显示当前余额和昨日耗电
4. 详情页用 ECharts 折线图展示每日耗电趋势 + 楼栋平均对比 + 余额变化
5. 自动检测充值（余额上升），用户可补充充值金额

### 小工具与用户推荐

- **小工具**：管理员发布，支持普通内容和跳转链接两种类型，可拖拽排序
- **用户推荐**：登录用户发布图文内容，支持 Markdown 编辑器、封面上传、草稿保存
- **互动**：点赞、收藏、评论（支持回复嵌套），事件触发消息通知

### 认证授权

- BCrypt 密码哈希 → JWT Token（3.5 天过期）→ `Authorization: Bearer` Header → `JwtAuthenticationFilter` 验证
- 角色区分：用户名 "admin" 为管理员，其余为普通用户
- 前端 401/403 自动跳转登录页

### 数据库设计

| 表 | 用途 |
|------|------|
| `user` | 用户信息（BCrypt 密码哈希、头像 URL、宿舍绑定） |
| `schedule` | 课表主表（用户、学期、当前周次、起始日期） |
| `course_entry` | 课程条目（名称、教师、地点、星期、节次、周次） |
| `school_config` | 学校配置（教务系统 URL，支持多学校扩展） |
| `user_recommendation` | 推荐 / 工具内容（标题、摘要、封面、内容 URL、类型、状态） |
| `user_favorite` | 用户收藏关系 |
| `user_comment` | 评论（支持嵌套回复） |
| `user_comment_like` | 评论点赞 |
| `user_notification` | 消息通知 |
| `electricity_balance` | 电费余额每日快照（余额、耗电量） |
| `electricity_recharge` | 充值记录 |

## 项目亮点

- **反向代理技术** — 完整实现 HTTP 代理，包括 Cookie 管理、HTML 注入、URL 重写
- **前端 JS 劫持** — 注入脚本劫持 `jQuery.ajax`、`fetch`、`XMLHttpRequest`，实现请求拦截
- **Session 安全管理** — 区分登录/非登录响应，防止 Cookie 轮转破坏会话
- **多种解析策略** — HTML 解析 + JSON API 两种方式，兼容不同教务系统
- **多学校架构** — 配置化设计，`school_config` 表存储各学校配置
- **电费自动采集** — 定时任务 + 并发采集 + 充值检测，无需用户手动操作
- **COS 代理访问** — 推荐内容文件通过后端代理返回，不暴露 COS 地址
- **COS 自定义域名** — 腾讯云对象存储自定义域名 + SSL 证书

## 量化成果

- 课表导入时间从手动 10 分钟降低到 30 秒
- 支持 12 节次 × 7 天 × 多周次的完整课表展示
- Redis Session TTL 2 小时，平衡性能和安全
- 前端 Bundle 约 500KB（Gzip 后约 150KB）
