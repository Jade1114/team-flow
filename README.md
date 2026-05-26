# Team Flow

Team Flow 是一个面向小团队的项目协作平台，支持用户创建项目、邀请成员、分配任务、拖拽任务状态、评论沟通和查看项目进度。

## 技术方向

- 后端：Spring Boot
- 前端：React
- 架构：前后端分离 B/S 架构
- 数据库：MySQL 8.x

## 项目文档

| 文档 | 说明 |
| --- | --- |
| [文档索引](./docs/README.md) | 项目文档入口 |
| [MVP 产品与接口设计](./docs/mvp-design.md) | 功能范围、业务流程、模块规划、迭代计划 |
| [REST API 设计文档](./docs/api-design.md) | 前后端接口契约、请求响应示例、错误码 |
| [数据库设计文档](./docs/database-design.md) | 表结构、字段、索引、枚举、建表 SQL 草案 |

## MVP 功能

- 用户注册、登录、获取当前用户信息
- 创建、查看、编辑、删除项目
- 邀请成员加入项目，维护项目成员列表
- 创建、分配、编辑、删除任务
- 通过看板拖拽任务状态和排序
- 在任务详情中发表评论
- 查看项目任务进度和成员任务统计

## 本地运行

后端默认监听 `http://localhost:8080`：

```bash
cd server
./mvnw spring-boot:run
```

前端默认监听 `http://localhost:5173`，并通过 Vite proxy 转发 `/api` 到后端：

```bash
cd web
npm install
npm run dev
```

如果本机 `8080` 已被占用，可以临时换端口启动后端，并通过 `VITE_API_TARGET` 指定前端代理目标：

```bash
cd server
./mvnw spring-boot:run -Dspring-boot.run.arguments=--server.port=8081

cd web
VITE_API_TARGET=http://localhost:8081 npm run dev
```

内存演示数据会在后端启动时初始化，默认账号：

| 用户 | 邮箱 | 密码 |
| --- | --- | --- |
| 张三 | `zhangsan@example.com` | `123456` |
| 李四 | `lisi@example.com` | `123456` |
