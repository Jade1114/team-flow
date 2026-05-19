# Team Flow REST API 设计文档

## 1. 文档说明

本文档面向 Team Flow MVP 阶段的前后端开发，描述 Spring Boot 后端需要提供的 REST API，以及 React 前端调用接口时应遵守的请求、响应、权限与错误处理约定。

本文档只覆盖 MVP 必需能力，不设计刷新 Token、OAuth、复杂 RBAC、WebSocket、邮件发送、附件上传、通知中心等非 MVP 能力。

## 2. 统一约定

### 2.1 Base URL

| 环境 | Base URL | 说明 |
| --- | --- | --- |
| 本地开发 | `http://localhost:8080/api` | Spring Boot 默认本地服务地址 |
| 前端代理 | `/api` | React 开发环境可通过 Vite proxy 转发 |

所有接口路径均以 `/api` 为前缀。本文后续路径省略域名，但保留 `/api` 前缀。

### 2.2 请求与响应格式

- 请求体格式：`application/json; charset=utf-8`
- 响应体格式：`application/json; charset=utf-8`
- 日期格式：`YYYY-MM-DD`，例如 `2026-05-19`
- 时间格式：ISO 8601 本地时间字符串，例如 `2026-05-19T10:30:00`
- ID 类型：后端使用 `Long`，前端以 `number` 接收；如后续出现精度问题，可统一改为字符串返回
- 字段命名：JSON 使用 `camelCase`
- 枚举值：统一使用大写英文字符串，例如 `TODO`、`OWNER`

### 2.3 认证方式

MVP 使用 JWT Bearer Token。

登录或注册成功后，后端返回 `token`。前端在后续受保护接口中携带：

```http
Authorization: Bearer <token>
```

未携带 Token、Token 无效或过期时，后端返回 `401 UNAUTHORIZED`。

### 2.4 通用 Header

| Header | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `Content-Type` | POST/PUT/PATCH 必填 | `application/json` | 请求体格式 |
| `Accept` | 建议 | `application/json` | 期望响应格式 |
| `Authorization` | 受保护接口必填 | `Bearer eyJ...` | 登录凭证 |

### 2.5 统一响应结构

所有成功响应统一包裹在 `data` 字段中，便于前端统一处理。

成功响应：

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "官网改版"
  },
  "message": "OK"
}
```

删除成功且无业务数据时：

```json
{
  "success": true,
  "data": null,
  "message": "OK"
}
```

错误响应：

```json
{
  "success": false,
  "code": "PROJECT_NOT_FOUND",
  "message": "项目不存在或无权限访问",
  "details": null
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | Boolean | 请求是否成功 |
| `data` | Object / Array / null | 成功时的业务数据 |
| `message` | String | 成功或失败提示 |
| `code` | String | 失败时的业务错误码 |
| `details` | Object / Array / null | 参数校验等详细错误信息 |

### 2.6 分页参数

列表接口默认支持分页；如果 MVP 前端暂不分页，也建议后端保留参数。

请求参数：

| 参数 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `page` | Integer | `1` | 页码，从 1 开始 |
| `pageSize` | Integer | `20` | 每页数量，建议最大 100 |

分页响应结构：

```json
{
  "success": true,
  "data": {
    "items": [],
    "page": 1,
    "pageSize": 20,
    "total": 0,
    "totalPages": 0
  },
  "message": "OK"
}
```

### 2.7 HTTP 状态码

| 状态码 | 场景 |
| --- | --- |
| `200` | 查询、更新、删除成功 |
| `201` | 创建成功 |
| `400` | 请求参数错误、字段校验失败 |
| `401` | 未登录、Token 无效或过期 |
| `403` | 已登录但无权限 |
| `404` | 资源不存在或不可见 |
| `409` | 资源冲突，如邮箱重复、成员已存在 |
| `500` | 服务端异常 |

### 2.8 通用错误码

| 错误码 | HTTP 状态码 | 说明 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | 请求字段校验失败 |
| `INVALID_CREDENTIALS` | 401 | 邮箱或密码错误 |
| `UNAUTHORIZED` | 401 | 未登录或登录状态无效 |
| `FORBIDDEN` | 403 | 无操作权限 |
| `USER_NOT_FOUND` | 404 | 用户不存在 |
| `PROJECT_NOT_FOUND` | 404 | 项目不存在或无权限访问 |
| `TASK_NOT_FOUND` | 404 | 任务不存在或无权限访问 |
| `COMMENT_NOT_FOUND` | 404 | 评论不存在或无权限访问 |
| `EMAIL_ALREADY_EXISTS` | 409 | 邮箱已注册 |
| `MEMBER_ALREADY_EXISTS` | 409 | 用户已是项目成员 |
| `OWNER_CANNOT_BE_REMOVED` | 409 | 不能移除项目所有者 |
| `INVALID_TASK_STATUS` | 400 | 任务状态非法 |

### 2.9 权限模型

MVP 只包含以下角色：

| 角色 | 枚举值 | 说明 |
| --- | --- | --- |
| 项目所有者 | `OWNER` | 创建项目的用户，拥有项目和成员管理权限 |
| 项目成员 | `MEMBER` | 被加入项目的用户，可参与任务和评论 |

权限原则：

- 未登录用户只能访问注册、登录接口。
- 登录用户可以创建项目、查看自己参与的项目。
- 项目成员可以查看项目、任务、评论、统计。
- 项目成员可以创建任务、更新任务状态、新增评论。
- 项目所有者可以更新项目、归档项目、邀请成员、移除成员、删除任务、删除任意评论。
- 评论作者可以删除自己的评论。

## 3. 枚举定义

### 3.1 项目状态 `projectStatus`

| 值 | 说明 |
| --- | --- |
| `ACTIVE` | 正常进行中 |
| `ARCHIVED` | 已归档 |

### 3.2 项目角色 `projectRole`

| 值 | 说明 |
| --- | --- |
| `OWNER` | 项目所有者 |
| `MEMBER` | 项目成员 |

### 3.3 邀请状态 `inviteStatus`

| 值 | 说明 |
| --- | --- |
| `PENDING` | 待处理 |
| `ACCEPTED` | 已接受或已加入 |
| `CANCELED` | 已取消 |

MVP 可简化为“按邮箱直接邀请已注册用户并自动加入”，此时邀请记录可直接落为 `ACCEPTED`。

### 3.4 任务状态 `taskStatus`

| 值 | 看板列 | 说明 |
| --- | --- | --- |
| `TODO` | 待处理 | 尚未开始 |
| `IN_PROGRESS` | 进行中 | 正在处理 |
| `DONE` | 已完成 | 已完成 |

### 3.5 任务优先级 `taskPriority`

| 值 | 说明 |
| --- | --- |
| `LOW` | 低优先级 |
| `MEDIUM` | 中优先级，默认值 |
| `HIGH` | 高优先级 |

## 4. 认证与用户接口

### 4.1 注册

- 方法：`POST`
- 路径：`/api/auth/register`
- 说明：创建新用户账号，并返回登录 Token。
- 权限：未登录可访问。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | String | 是 | 用户名称，1-50 字符 |
| `email` | String | 是 | 邮箱，唯一 |
| `password` | String | 是 | 密码，建议 6-64 字符 |

请求示例：

```json
{
  "name": "张三",
  "email": "zhangsan@example.com",
  "password": "123456"
}
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `token` | String | JWT Token |
| `user.id` | Long | 用户 ID |
| `user.name` | String | 用户名称 |
| `user.email` | String | 邮箱 |
| `user.avatarUrl` | String / null | 头像地址 |

响应示例：

```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.xxx",
    "user": {
      "id": 1,
      "name": "张三",
      "email": "zhangsan@example.com",
      "avatarUrl": null
    }
  },
  "message": "OK"
}
```

### 4.2 登录

- 方法：`POST`
- 路径：`/api/auth/login`
- 说明：使用邮箱和密码登录。
- 权限：未登录可访问。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `email` | String | 是 | 邮箱 |
| `password` | String | 是 | 密码 |

请求示例：

```json
{
  "email": "zhangsan@example.com",
  "password": "123456"
}
```

响应字段同注册接口。

响应示例：

```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9.xxx",
    "user": {
      "id": 1,
      "name": "张三",
      "email": "zhangsan@example.com",
      "avatarUrl": null
    }
  },
  "message": "OK"
}
```

### 4.3 获取当前用户

- 方法：`GET`
- 路径：`/api/auth/me`
- 说明：获取当前登录用户信息。
- 权限：登录用户。

请求参数：无。

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 用户 ID |
| `name` | String | 用户名称 |
| `email` | String | 邮箱 |
| `avatarUrl` | String / null | 头像地址 |
| `createdAt` | String | 注册时间 |

响应示例：

```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "张三",
    "email": "zhangsan@example.com",
    "avatarUrl": null,
    "createdAt": "2026-05-19T10:30:00"
  },
  "message": "OK"
}
```

## 5. 项目接口

### 5.1 创建项目

- 方法：`POST`
- 路径：`/api/projects`
- 说明：创建项目，当前用户自动成为项目所有者。
- 权限：登录用户。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | String | 是 | 项目名称，1-100 字符 |
| `description` | String | 否 | 项目描述，最多 1000 字符 |

请求示例：

```json
{
  "name": "官网改版",
  "description": "完成公司官网首页、项目页和联系页改版"
}
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 项目 ID |
| `name` | String | 项目名称 |
| `description` | String / null | 项目描述 |
| `status` | String | 项目状态 |
| `owner.id` | Long | 所有者 ID |
| `owner.name` | String | 所有者名称 |
| `currentUserRole` | String | 当前用户角色 |
| `createdAt` | String | 创建时间 |
| `updatedAt` | String | 更新时间 |

响应示例：

```json
{
  "success": true,
  "data": {
    "id": 101,
    "name": "官网改版",
    "description": "完成公司官网首页、项目页和联系页改版",
    "status": "ACTIVE",
    "owner": {
      "id": 1,
      "name": "张三"
    },
    "currentUserRole": "OWNER",
    "createdAt": "2026-05-19T10:30:00",
    "updatedAt": "2026-05-19T10:30:00"
  },
  "message": "OK"
}
```

### 5.2 查询我的项目列表

- 方法：`GET`
- 路径：`/api/projects`
- 说明：查询当前用户创建或参与的项目。
- 权限：登录用户。

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `status` | String | 否 | 项目状态：`ACTIVE`、`ARCHIVED` |
| `keyword` | String | 否 | 项目名称关键词 |
| `page` | Integer | 否 | 页码，默认 1 |
| `pageSize` | Integer | 否 | 每页数量，默认 20 |

请求示例：

```http
GET /api/projects?status=ACTIVE&keyword=官网&page=1&pageSize=20
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `items[].id` | Long | 项目 ID |
| `items[].name` | String | 项目名称 |
| `items[].description` | String / null | 项目描述 |
| `items[].status` | String | 项目状态 |
| `items[].currentUserRole` | String | 当前用户角色 |
| `items[].ownerName` | String | 项目所有者名称 |
| `items[].taskSummary.total` | Integer | 任务总数 |
| `items[].taskSummary.done` | Integer | 已完成任务数 |
| `items[].taskSummary.completionRate` | Number | 完成率，0-100 |
| `items[].updatedAt` | String | 更新时间 |

响应示例：

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 101,
        "name": "官网改版",
        "description": "完成公司官网首页、项目页和联系页改版",
        "status": "ACTIVE",
        "currentUserRole": "OWNER",
        "ownerName": "张三",
        "taskSummary": {
          "total": 12,
          "done": 4,
          "completionRate": 33.33
        },
        "updatedAt": "2026-05-19T10:30:00"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "totalPages": 1
  },
  "message": "OK"
}
```

### 5.3 查看项目详情

- 方法：`GET`
- 路径：`/api/projects/{projectId}`
- 说明：查看项目基础信息。
- 权限：项目成员。

路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `projectId` | Long | 项目 ID |

响应字段同创建项目接口，可额外返回成员和任务概要。

响应示例：

```json
{
  "success": true,
  "data": {
    "id": 101,
    "name": "官网改版",
    "description": "完成公司官网首页、项目页和联系页改版",
    "status": "ACTIVE",
    "owner": {
      "id": 1,
      "name": "张三"
    },
    "currentUserRole": "OWNER",
    "memberCount": 5,
    "taskSummary": {
      "total": 12,
      "todo": 5,
      "inProgress": 3,
      "done": 4,
      "completionRate": 33.33
    },
    "createdAt": "2026-05-19T10:30:00",
    "updatedAt": "2026-05-19T10:30:00"
  },
  "message": "OK"
}
```

### 5.4 更新项目

- 方法：`PUT`
- 路径：`/api/projects/{projectId}`
- 说明：更新项目名称和描述。
- 权限：项目所有者。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `name` | String | 是 | 项目名称 |
| `description` | String | 否 | 项目描述 |

请求示例：

```json
{
  "name": "官网改版一期",
  "description": "优先完成首页、项目页和联系页"
}
```

响应示例：

```json
{
  "success": true,
  "data": {
    "id": 101,
    "name": "官网改版一期",
    "description": "优先完成首页、项目页和联系页",
    "status": "ACTIVE",
    "owner": {
      "id": 1,
      "name": "张三"
    },
    "currentUserRole": "OWNER",
    "createdAt": "2026-05-19T10:30:00",
    "updatedAt": "2026-05-20T09:00:00"
  },
  "message": "OK"
}
```

### 5.5 归档项目

- 方法：`PATCH`
- 路径：`/api/projects/{projectId}/archive`
- 说明：将项目状态改为 `ARCHIVED`，MVP 不做物理删除。
- 权限：项目所有者。

请求体：无。

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 项目 ID |
| `status` | String | 项目状态 |
| `updatedAt` | String | 更新时间 |

响应示例：

```json
{
  "success": true,
  "data": {
    "id": 101,
    "status": "ARCHIVED",
    "updatedAt": "2026-05-20T10:00:00"
  },
  "message": "OK"
}
```

## 6. 项目成员与邀请接口

### 6.1 查询项目成员列表

- 方法：`GET`
- 路径：`/api/projects/{projectId}/members`
- 说明：查询项目成员。
- 权限：项目成员。

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | String | 否 | 按名称或邮箱搜索 |

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `items[].id` | Long | 成员关系 ID |
| `items[].user.id` | Long | 用户 ID |
| `items[].user.name` | String | 用户名称 |
| `items[].user.email` | String | 用户邮箱 |
| `items[].user.avatarUrl` | String / null | 头像地址 |
| `items[].role` | String | 项目角色 |
| `items[].joinedAt` | String | 加入时间 |

响应示例：

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 1001,
        "user": {
          "id": 1,
          "name": "张三",
          "email": "zhangsan@example.com",
          "avatarUrl": null
        },
        "role": "OWNER",
        "joinedAt": "2026-05-19T10:30:00"
      }
    ]
  },
  "message": "OK"
}
```

### 6.2 邀请成员

- 方法：`POST`
- 路径：`/api/projects/{projectId}/invites`
- 说明：通过邮箱邀请成员。MVP 可直接添加已注册用户为成员，同时生成一条邀请记录。
- 权限：项目所有者。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `email` | String | 是 | 被邀请用户邮箱 |
| `role` | String | 否 | 项目角色，默认 `MEMBER`，MVP 不建议邀请 `OWNER` |

请求示例：

```json
{
  "email": "lisi@example.com",
  "role": "MEMBER"
}
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `invite.id` | Long | 邀请记录 ID |
| `invite.email` | String | 被邀请邮箱 |
| `invite.status` | String | 邀请状态 |
| `member.id` | Long | 成员关系 ID |
| `member.user.id` | Long | 用户 ID |
| `member.role` | String | 项目角色 |

响应示例：

```json
{
  "success": true,
  "data": {
    "invite": {
      "id": 501,
      "email": "lisi@example.com",
      "status": "ACCEPTED",
      "createdAt": "2026-05-19T11:00:00"
    },
    "member": {
      "id": 1002,
      "user": {
        "id": 2,
        "name": "李四",
        "email": "lisi@example.com",
        "avatarUrl": null
      },
      "role": "MEMBER",
      "joinedAt": "2026-05-19T11:00:00"
    }
  },
  "message": "OK"
}
```

### 6.3 查询邀请记录

- 方法：`GET`
- 路径：`/api/projects/{projectId}/invites`
- 说明：查询项目邀请记录，用于项目设置页展示操作历史。
- 权限：项目所有者。

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `status` | String | 否 | 邀请状态 |
| `page` | Integer | 否 | 页码 |
| `pageSize` | Integer | 否 | 每页数量 |

响应示例：

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 501,
        "email": "lisi@example.com",
        "status": "ACCEPTED",
        "inviter": {
          "id": 1,
          "name": "张三"
        },
        "createdAt": "2026-05-19T11:00:00",
        "acceptedAt": "2026-05-19T11:00:00"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "totalPages": 1
  },
  "message": "OK"
}
```

### 6.4 移除成员

- 方法：`DELETE`
- 路径：`/api/projects/{projectId}/members/{memberId}`
- 说明：移除项目成员，不能移除项目所有者。
- 权限：项目所有者。

路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `projectId` | Long | 项目 ID |
| `memberId` | Long | 成员关系 ID |

响应示例：

```json
{
  "success": true,
  "data": null,
  "message": "OK"
}
```

## 7. 任务看板与任务接口

### 7.1 查询项目任务列表

- 方法：`GET`
- 路径：`/api/projects/{projectId}/tasks`
- 说明：查询项目任务，用于看板列和列表展示。
- 权限：项目成员。

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `status` | String | 否 | 任务状态 |
| `assigneeId` | Long | 否 | 负责人 ID |
| `priority` | String | 否 | 优先级 |
| `keyword` | String | 否 | 标题关键词 |
| `page` | Integer | 否 | 页码 |
| `pageSize` | Integer | 否 | 每页数量 |

请求示例：

```http
GET /api/projects/101/tasks?status=TODO&assigneeId=2&page=1&pageSize=20
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `items[].id` | Long | 任务 ID |
| `items[].title` | String | 任务标题 |
| `items[].description` | String / null | 任务描述 |
| `items[].status` | String | 任务状态 |
| `items[].priority` | String | 优先级 |
| `items[].sortOrder` | Integer | 同状态列内排序值 |
| `items[].assignee.id` | Long / null | 负责人 ID |
| `items[].assignee.name` | String / null | 负责人名称 |
| `items[].creator.id` | Long | 创建人 ID |
| `items[].dueDate` | String / null | 截止日期 |
| `items[].commentCount` | Integer | 评论数量 |
| `items[].createdAt` | String | 创建时间 |
| `items[].updatedAt` | String | 更新时间 |

响应示例：

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 2001,
        "title": "设计首页任务看板",
        "description": "完成任务卡片和状态列交互",
        "status": "TODO",
        "priority": "HIGH",
        "sortOrder": 1000,
        "assignee": {
          "id": 2,
          "name": "李四"
        },
        "creator": {
          "id": 1,
          "name": "张三"
        },
        "dueDate": "2026-05-31",
        "commentCount": 3,
        "createdAt": "2026-05-19T11:30:00",
        "updatedAt": "2026-05-19T11:30:00"
      }
    ],
    "page": 1,
    "pageSize": 20,
    "total": 1,
    "totalPages": 1
  },
  "message": "OK"
}
```

### 7.2 查询任务看板

- 方法：`GET`
- 路径：`/api/projects/{projectId}/board`
- 说明：按状态分组返回任务，适合看板页面一次性加载。
- 权限：项目成员。

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `assigneeId` | Long | 否 | 负责人 ID |
| `keyword` | String | 否 | 标题关键词 |

响应示例：

```json
{
  "success": true,
  "data": {
    "columns": [
      {
        "status": "TODO",
        "title": "待处理",
        "tasks": [
          {
            "id": 2001,
            "title": "设计首页任务看板",
            "priority": "HIGH",
            "sortOrder": 1000,
            "assignee": {
              "id": 2,
              "name": "李四"
            },
            "dueDate": "2026-05-31"
          }
        ]
      },
      {
        "status": "IN_PROGRESS",
        "title": "进行中",
        "tasks": []
      },
      {
        "status": "DONE",
        "title": "已完成",
        "tasks": []
      }
    ]
  },
  "message": "OK"
}
```

### 7.3 创建任务

- 方法：`POST`
- 路径：`/api/projects/{projectId}/tasks`
- 说明：在项目中创建任务。
- 权限：项目成员。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `title` | String | 是 | 任务标题，1-200 字符 |
| `description` | String | 否 | 任务描述 |
| `priority` | String | 否 | 优先级，默认 `MEDIUM` |
| `assigneeId` | Long | 否 | 负责人 ID，必须是项目成员 |
| `dueDate` | String | 否 | 截止日期，格式 `YYYY-MM-DD` |

请求示例：

```json
{
  "title": "设计首页任务看板",
  "description": "完成任务卡片和状态列交互",
  "priority": "HIGH",
  "assigneeId": 2,
  "dueDate": "2026-05-31"
}
```

响应示例：

```json
{
  "success": true,
  "data": {
    "id": 2001,
    "projectId": 101,
    "title": "设计首页任务看板",
    "description": "完成任务卡片和状态列交互",
    "status": "TODO",
    "priority": "HIGH",
    "sortOrder": 1000,
    "assignee": {
      "id": 2,
      "name": "李四"
    },
    "creator": {
      "id": 1,
      "name": "张三"
    },
    "dueDate": "2026-05-31",
    "createdAt": "2026-05-19T11:30:00",
    "updatedAt": "2026-05-19T11:30:00"
  },
  "message": "OK"
}
```

### 7.4 查看任务详情

- 方法：`GET`
- 路径：`/api/tasks/{taskId}`
- 说明：查看单个任务详情。
- 权限：任务所属项目成员。

路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `taskId` | Long | 任务 ID |

响应示例：

```json
{
  "success": true,
  "data": {
    "id": 2001,
    "projectId": 101,
    "projectName": "官网改版",
    "title": "设计首页任务看板",
    "description": "完成任务卡片和状态列交互",
    "status": "TODO",
    "priority": "HIGH",
    "sortOrder": 1000,
    "assignee": {
      "id": 2,
      "name": "李四",
      "email": "lisi@example.com"
    },
    "creator": {
      "id": 1,
      "name": "张三",
      "email": "zhangsan@example.com"
    },
    "dueDate": "2026-05-31",
    "commentCount": 3,
    "createdAt": "2026-05-19T11:30:00",
    "updatedAt": "2026-05-19T11:30:00"
  },
  "message": "OK"
}
```

### 7.5 更新任务

- 方法：`PUT`
- 路径：`/api/tasks/{taskId}`
- 说明：更新任务基础信息，不用于拖拽排序。
- 权限：项目所有者、任务创建人或任务负责人。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `title` | String | 是 | 任务标题 |
| `description` | String | 否 | 任务描述 |
| `priority` | String | 否 | 优先级 |
| `assigneeId` | Long | 否 | 负责人 ID；传 `null` 表示取消负责人 |
| `dueDate` | String | 否 | 截止日期；传 `null` 表示清空 |

请求示例：

```json
{
  "title": "设计首页任务看板交互",
  "description": "完成拖拽、空状态和卡片摘要展示",
  "priority": "HIGH",
  "assigneeId": 2,
  "dueDate": "2026-06-01"
}
```

响应示例：

```json
{
  "success": true,
  "data": {
    "id": 2001,
    "projectId": 101,
    "title": "设计首页任务看板交互",
    "description": "完成拖拽、空状态和卡片摘要展示",
    "status": "TODO",
    "priority": "HIGH",
    "sortOrder": 1000,
    "assignee": {
      "id": 2,
      "name": "李四"
    },
    "dueDate": "2026-06-01",
    "updatedAt": "2026-05-20T09:30:00"
  },
  "message": "OK"
}
```

### 7.6 拖拽更新任务状态与排序

- 方法：`PATCH`
- 路径：`/api/tasks/{taskId}/move`
- 说明：看板拖拽任务卡片后，同时更新状态列和列内排序。
- 权限：项目成员。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `targetStatus` | String | 是 | 目标状态：`TODO`、`IN_PROGRESS`、`DONE` |
| `beforeTaskId` | Long | 否 | 移动后位于当前任务之前的任务 ID |
| `afterTaskId` | Long | 否 | 移动后位于当前任务之后的任务 ID |

排序规则建议：

- 如果只切换状态不关心位置，可不传 `beforeTaskId` 和 `afterTaskId`，后端放到目标列末尾。
- 如果拖到两个任务之间，传相邻任务 ID，后端计算新的 `sortOrder`。
- MVP 可使用间隔排序值，例如 1000、2000、3000；必要时后台重排同列任务。

请求示例：

```json
{
  "targetStatus": "IN_PROGRESS",
  "beforeTaskId": null,
  "afterTaskId": 2005
}
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | Long | 任务 ID |
| `status` | String | 更新后的状态 |
| `sortOrder` | Integer | 更新后的排序值 |
| `updatedAt` | String | 更新时间 |

响应示例：

```json
{
  "success": true,
  "data": {
    "id": 2001,
    "status": "IN_PROGRESS",
    "sortOrder": 500,
    "updatedAt": "2026-05-20T10:00:00"
  },
  "message": "OK"
}
```

### 7.7 快速更新任务状态

- 方法：`PATCH`
- 路径：`/api/tasks/{taskId}/status`
- 说明：非拖拽场景下只更新任务状态，如详情页状态下拉框。
- 权限：项目成员。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `status` | String | 是 | 目标状态 |

请求示例：

```json
{
  "status": "DONE"
}
```

响应示例：

```json
{
  "success": true,
  "data": {
    "id": 2001,
    "status": "DONE",
    "updatedAt": "2026-05-20T10:30:00"
  },
  "message": "OK"
}
```

### 7.8 删除任务

- 方法：`DELETE`
- 路径：`/api/tasks/{taskId}`
- 说明：删除任务。MVP 推荐软删除，保留评论数据但默认不再展示。
- 权限：项目所有者，或任务创建人。

响应示例：

```json
{
  "success": true,
  "data": null,
  "message": "OK"
}
```

## 8. 评论接口

### 8.1 查询任务评论列表

- 方法：`GET`
- 路径：`/api/tasks/{taskId}/comments`
- 说明：查看任务评论，按创建时间正序返回。
- 权限：任务所属项目成员。

Query 参数：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `page` | Integer | 否 | 页码，默认 1 |
| `pageSize` | Integer | 否 | 每页数量，默认 50 |

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `items[].id` | Long | 评论 ID |
| `items[].content` | String | 评论内容 |
| `items[].author.id` | Long | 作者 ID |
| `items[].author.name` | String | 作者名称 |
| `items[].author.avatarUrl` | String / null | 作者头像 |
| `items[].createdAt` | String | 创建时间 |
| `items[].updatedAt` | String | 更新时间 |
| `items[].canDelete` | Boolean | 当前用户是否可删除 |

响应示例：

```json
{
  "success": true,
  "data": {
    "items": [
      {
        "id": 3001,
        "content": "这个任务我今天先做交互草图。",
        "author": {
          "id": 2,
          "name": "李四",
          "avatarUrl": null
        },
        "createdAt": "2026-05-20T11:00:00",
        "updatedAt": "2026-05-20T11:00:00",
        "canDelete": true
      }
    ],
    "page": 1,
    "pageSize": 50,
    "total": 1,
    "totalPages": 1
  },
  "message": "OK"
}
```

### 8.2 新增任务评论

- 方法：`POST`
- 路径：`/api/tasks/{taskId}/comments`
- 说明：在任务下发表评论。
- 权限：任务所属项目成员。

请求体：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `content` | String | 是 | 评论内容，1-2000 字符 |

请求示例：

```json
{
  "content": "这个任务我今天先做交互草图。"
}
```

响应示例：

```json
{
  "success": true,
  "data": {
    "id": 3001,
    "content": "这个任务我今天先做交互草图。",
    "author": {
      "id": 2,
      "name": "李四",
      "avatarUrl": null
    },
    "createdAt": "2026-05-20T11:00:00",
    "updatedAt": "2026-05-20T11:00:00",
    "canDelete": true
  },
  "message": "OK"
}
```

### 8.3 删除任务评论

- 方法：`DELETE`
- 路径：`/api/comments/{commentId}`
- 说明：删除评论。MVP 推荐软删除。
- 权限：评论作者或项目所有者。

路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `commentId` | Long | 评论 ID |

响应示例：

```json
{
  "success": true,
  "data": null,
  "message": "OK"
}
```

## 9. 项目统计与进度接口

### 9.1 查看项目统计

- 方法：`GET`
- 路径：`/api/projects/{projectId}/stats`
- 说明：获取项目任务统计数据，用于项目概览和进度面板。
- 权限：项目成员。

路径参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `projectId` | Long | 项目 ID |

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `totalTasks` | Integer | 任务总数 |
| `todoTasks` | Integer | 待处理任务数 |
| `inProgressTasks` | Integer | 进行中任务数 |
| `doneTasks` | Integer | 已完成任务数 |
| `overdueTasks` | Integer | 逾期且未完成任务数 |
| `completionRate` | Number | 完成率，0-100 |
| `byStatus[].status` | String | 任务状态 |
| `byStatus[].count` | Integer | 状态数量 |
| `byPriority[].priority` | String | 优先级 |
| `byPriority[].count` | Integer | 优先级数量 |
| `byAssignee[].userId` | Long / null | 负责人 ID，未分配为 null |
| `byAssignee[].userName` | String | 负责人名称，未分配为“未分配” |
| `byAssignee[].total` | Integer | 任务总数 |
| `byAssignee[].done` | Integer | 已完成任务数 |

响应示例：

```json
{
  "success": true,
  "data": {
    "totalTasks": 12,
    "todoTasks": 5,
    "inProgressTasks": 3,
    "doneTasks": 4,
    "overdueTasks": 2,
    "completionRate": 33.33,
    "byStatus": [
      {
        "status": "TODO",
        "count": 5
      },
      {
        "status": "IN_PROGRESS",
        "count": 3
      },
      {
        "status": "DONE",
        "count": 4
      }
    ],
    "byPriority": [
      {
        "priority": "HIGH",
        "count": 3
      },
      {
        "priority": "MEDIUM",
        "count": 7
      },
      {
        "priority": "LOW",
        "count": 2
      }
    ],
    "byAssignee": [
      {
        "userId": 2,
        "userName": "李四",
        "total": 6,
        "done": 2
      },
      {
        "userId": null,
        "userName": "未分配",
        "total": 2,
        "done": 0
      }
    ]
  },
  "message": "OK"
}
```

## 10. 前后端开发注意事项

- 前端应统一封装 API Client，自动添加 `Authorization` Header，并统一处理 `401` 跳转登录页。
- 后端 Controller 只负责参数接收和响应转换，权限校验建议放在 Service 层统一处理。
- 所有涉及项目资源的接口，都应先校验当前用户是否为项目成员。
- `assigneeId` 必须属于当前项目成员，否则返回 `400 VALIDATION_ERROR` 或 `403 FORBIDDEN`。
- 删除任务和评论建议使用软删除，避免破坏统计和历史数据。
- 项目归档后，MVP 可限制新增任务、邀请成员等写操作，但允许查看历史数据。
