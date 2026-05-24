# 工具页与用户推荐功能设计

## 概述

在状态栏左侧添加汉堡菜单用于页面切换，新增工具页包含「小工具」和「用户推荐」两个模块。用户推荐支持 Markdown 编辑与实时预览，内容存储于 COS，元数据存储于 MySQL。

## 1. 状态栏导航

在 `StatusBar.vue` 左侧添加汉堡菜单图标（☰），点击弹出下拉菜单：
- **主页** → `/home`
- **工具** → `/tools`

样式与右侧用户菜单保持一致。

## 2. 工具页（`/tools`）

页面顶部 Tab 切换两个模块：
- **小工具**（默认）：公开访问，管理员维护，当前为占位
- **用户推荐**：需要登录，用户可发布和浏览推荐内容

## 3. 用户推荐功能

### 3.1 卡片列表

网格布局展示推荐卡片，每张卡片显示：
- 封面图（如有）或默认占位图
- 标题
- 作者昵称
- 发布时间
- 浏览量、点赞量

### 3.2 发布流程

1. 点击「发布推荐」进入编辑页
2. 编辑页布局：左侧 MD 编辑器，右侧实时预览
3. 填写元信息：标题、简介、封面图（可选）
4. 支持上传 `.md` 文件自动填充编辑器内容
5. 发布后 MD 文件存 COS，元数据存 MySQL

### 3.3 详情页

点击卡片进入详情页，渲染 Markdown 内容，显示浏览量和点赞按钮。

### 3.4 权限

- 浏览推荐列表和详情：需要登录
- 发布推荐：需要登录

## 4. 数据库设计

```sql
CREATE TABLE user_recommendation (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  summary VARCHAR(500),
  cover_url VARCHAR(500),
  content_url VARCHAR(500) NOT NULL,
  views INT DEFAULT 0,
  likes INT DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES user(id)
);
```

## 5. 后端接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/recommendations` | 获取推荐列表 | 登录 |
| GET | `/api/recommendations/{id}` | 获取推荐详情 | 登录 |
| POST | `/api/recommendations` | 发布推荐 | 登录 |
| PUT | `/api/recommendations/{id}` | 编辑推荐 | 作者 |
| DELETE | `/api/recommendations/{id}` | 删除推荐 | 作者 |
| POST | `/api/recommendations/{id}/like` | 点赞 | 登录 |
| POST | `/api/cos/upload` | 上传文件到 COS | 登录 |

## 6. 文件清单

### 新建文件
- `frontend/src/views/Tools.vue` — 工具页（Tab 切换）
- `frontend/src/views/RecommendEditor.vue` — MD 编辑页
- `frontend/src/views/RecommendDetail.vue` — 推荐详情页
- `backend/.../controller/RecommendController.java` — 推荐接口
- `backend/.../service/RecommendService.java` — 推荐服务
- `backend/.../entity/Recommendation.java` — 实体类
- `schema-recommend.sql` — 建表 DDL

### 修改文件
- `frontend/src/components/StatusBar.vue` — 添加左侧菜单
- `frontend/src/router/index.ts` — 添加路由

## 7. 验证方式

1. 点击状态栏左侧汉堡菜单，能切换主页和工具页
2. 工具页默认显示「小工具」Tab
3. 切换到「用户推荐」Tab，未登录时提示登录
4. 登录后可发布推荐：上传 MD 文件或直接编辑，实时预览正常
5. 发布后卡片列表正确显示标题、作者、时间等信息
6. 点击卡片进入详情页，MD 内容正确渲染
