# Team Flow MVP 产品与接口设计

## 1. 项目概述

Team Flow 是一个面向小团队的项目协作管理系统，采用 Spring Boot + React 的前后端分离 B/S 架构。系统帮助团队围绕项目组织成员、任务、状态流转、评论沟通和进度查看，目标是在较短周期内交付一个可运行、可演示、可继续扩展的 MVP。

### 1.1 一句话介绍

面向小团队的项目协作平台，支持用户创建项目、邀请成员、分配任务、拖拽任务状态、评论沟通和查看项目进度。

### 1.2 MVP 设计原则

- 聚焦单体应用：后端使用单个 Spring Boot 服务，前端使用单个 React 应用。
- 聚焦核心协作：项目、成员、任务、评论、进度为核心闭环。
- 权限保持简单：仅区分系统登录用户、项目所有者、项目成员，不引入复杂 RBAC。
- 接口保持清晰：REST API 面向页面和业务流程设计，便于前后端并行开发。
- 暂不引入复杂 DevOps：本地开发以 Maven、pnpm、数据库配置为主。

## 2. 目标用户

### 2.1 主要用户

- 小型研发团队：3-10 人，管理需求、开发任务、测试任务和缺陷跟踪。
- 学生项目小组：课程设计、毕业设计、比赛项目的任务分工和进度同步。
- 轻量创业团队：希望使用低成本工具管理短周期项目。

### 2.2 用户痛点

- 项目任务分散在聊天工具中，难以追踪责任人和状态。
- 团队成员不知道当前任务整体进度和阻塞点。
- 缺少简单统一的任务评论与沟通记录。
- 复杂项目管理工具学习成本高，不适合小团队快速使用。

## 3. 核心业务流程

### 3.1 注册与登录

1. 用户注册账号。
2. 用户使用邮箱和密码登录。
3. 登录成功后进入项目列表页。
4. 前端保存登录凭证，并在后续请求中携带认证信息。

### 3.2 创建项目与邀请成员

1. 登录用户创建项目，自动成为项目所有者。
2. 项目所有者通过成员邮箱邀请用户加入项目。
3. 被邀请用户登录后可在项目列表中看到该项目。
4. 项目成员可查看项目详情和参与任务协作。

MVP 中可简化为“直接按邮箱添加已注册用户”，不实现邮件发送和邀请链接。

### 3.3 任务创建与分配

1. 项目成员进入项目详情页。
2. 有权限的成员创建任务，填写标题、描述、优先级、截止日期。
3. 创建人可将任务分配给项目成员。
4. 被分配成员在任务看板和我的任务中看到任务。

### 3.4 拖拽任务状态

1. 项目详情页以看板方式展示任务。
2. 默认状态列：待处理、进行中、已完成。
3. 用户拖拽任务卡片到目标状态列。
4. 前端调用接口更新任务状态。
5. 页面刷新后仍保持最新状态。

### 3.5 评论沟通

1. 用户打开任务详情抽屉或详情页。
2. 查看任务基础信息和评论列表。
3. 项目成员可以发布评论。
4. 评论按创建时间正序展示。

### 3.6 查看项目进度

1. 用户进入项目进度页或项目详情页概览区域。
2. 系统展示任务总数、各状态数量、完成率、逾期任务数量。
3. 用户根据统计信息判断项目进展。

## 4. MVP 功能模块

### 4.1 用户与认证

- 用户注册。
- 用户登录。
- 获取当前登录用户信息。
- 用户退出登录由前端清理凭证完成。

### 4.2 项目管理

- 创建项目。
- 查询我参与的项目列表。
- 查看项目详情。
- 更新项目基础信息。
- 归档项目，MVP 中可作为软删除或状态字段处理。

### 4.3 项目成员

- 查看项目成员列表。
- 通过邮箱添加已注册用户为项目成员。
- 移除项目成员。
- 设置成员在项目中的角色：项目所有者、项目成员。

### 4.4 任务管理

- 创建任务。
- 查看项目任务列表。
- 查看任务详情。
- 更新任务基础信息。
- 更新任务状态。
- 分配或变更负责人。
- 删除任务，MVP 中可使用硬删除或软删除，建议优先软删除。

### 4.5 任务评论

- 查看任务评论列表。
- 新增任务评论。
- 删除自己的评论或由项目所有者删除评论。

### 4.6 项目进度

- 查看项目任务统计。
- 查看按状态聚合的任务数量。
- 查看完成率、逾期任务数。

## 5. 角色与权限

### 5.1 角色定义

| 角色 | 说明 |
| --- | --- |
| 未登录用户 | 只能访问登录、注册页面 |
| 登录用户 | 可以创建项目，查看自己参与的项目 |
| 项目所有者 | 项目创建人，拥有项目管理和成员管理权限 |
| 项目成员 | 被加入项目的用户，可以查看项目、处理任务、发表评论 |

### 5.2 权限矩阵

| 功能 | 项目所有者 | 项目成员 |
| --- | --- | --- |
| 查看项目 | 是 | 是 |
| 更新项目信息 | 是 | 否 |
| 归档项目 | 是 | 否 |
| 添加成员 | 是 | 否 |
| 移除成员 | 是 | 否 |
| 查看任务 | 是 | 是 |
| 创建任务 | 是 | 是 |
| 编辑任务 | 是 | 是，建议仅限自己创建或负责的任务 |
| 更新任务状态 | 是 | 是 |
| 删除任务 | 是 | 否，或仅限删除自己创建的任务 |
| 查看评论 | 是 | 是 |
| 新增评论 | 是 | 是 |
| 删除评论 | 是 | 是，仅限自己的评论 |

MVP 中建议在后端统一校验用户是否属于项目成员，再根据少量项目角色判断管理操作权限。

## 6. 数据实体概览

### 6.1 User 用户

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 用户 ID |
| name | String | 用户名称 |
| email | String | 邮箱，唯一 |
| passwordHash | String | 密码哈希 |
| avatarUrl | String | 头像地址，可选 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

### 6.2 Project 项目

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 项目 ID |
| name | String | 项目名称 |
| description | String | 项目描述 |
| ownerId | Long | 项目所有者用户 ID |
| status | String | 项目状态：ACTIVE、ARCHIVED |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

### 6.3 ProjectMember 项目成员

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 成员关系 ID |
| projectId | Long | 项目 ID |
| userId | Long | 用户 ID |
| role | String | 项目角色：OWNER、MEMBER |
| joinedAt | LocalDateTime | 加入时间 |

### 6.4 Task 任务

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 任务 ID |
| projectId | Long | 所属项目 ID |
| title | String | 任务标题 |
| description | String | 任务描述 |
| status | String | 任务状态：TODO、IN_PROGRESS、DONE |
| priority | String | 优先级：LOW、MEDIUM、HIGH |
| assigneeId | Long | 负责人用户 ID，可为空 |
| creatorId | Long | 创建人用户 ID |
| dueDate | LocalDate | 截止日期，可为空 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

### 6.5 TaskComment 任务评论

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 评论 ID |
| taskId | Long | 任务 ID |
| authorId | Long | 评论作者用户 ID |
| content | String | 评论内容 |
| createdAt | LocalDateTime | 创建时间 |
| updatedAt | LocalDateTime | 更新时间 |

## 7. REST API 接口设计

### 7.1 通用约定

- API 前缀：`/api`
- 请求格式：`application/json`
- 响应格式：`application/json`
- 时间格式：ISO 8601，例如 `2026-05-19T10:30:00`
- 日期格式：`YYYY-MM-DD`
- 认证方式：MVP 可使用 JWT Bearer Token。
- 认证请求头：`Authorization: Bearer <token>`

### 7.2 通用响应结构

成功响应可直接返回业务对象。错误响应统一格式：

```json
{
  "code": "PROJECT_NOT_FOUND",
  "message": "项目不存在或无权限访问"
}
```

常见 HTTP 状态码：

| 状态码 | 场景 |
| --- | --- |
| 200 | 查询或更新成功 |
| 201 | 创建成功 |
| 204 | 删除成功且无响应体 |
| 400 | 请求字段错误 |
| 401 | 未登录或登录过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 邮箱重复、成员已存在等冲突 |

### 7.3 认证接口

#### 注册

- 方法：`POST`
- 路径：`/api/auth/register`
- 用途：创建新用户账号。
- 主要请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | String | 是 | 用户名称 |
| email | String | 是 | 邮箱 |
| password | String | 是 | 密码 |

- 主要响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 用户 ID |
| name | String | 用户名称 |
| email | String | 邮箱 |
| token | String | 登录令牌 |

#### 登录

- 方法：`POST`
- 路径：`/api/auth/login`
- 用途：用户登录并获取令牌。
- 主要请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| email | String | 是 | 邮箱 |
| password | String | 是 | 密码 |

- 主要响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| token | String | 登录令牌 |
| user.id | Long | 用户 ID |
| user.name | String | 用户名称 |
| user.email | String | 邮箱 |

#### 当前用户

- 方法：`GET`
- 路径：`/api/auth/me`
- 用途：获取当前登录用户信息。
- 主要请求字段：无。
- 主要响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 用户 ID |
| name | String | 用户名称 |
| email | String | 邮箱 |
| avatarUrl | String | 头像地址 |

### 7.4 项目接口

#### 创建项目

- 方法：`POST`
- 路径：`/api/projects`
- 用途：创建项目，当前用户成为项目所有者。
- 主要请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | String | 是 | 项目名称 |
| description | String | 否 | 项目描述 |

- 主要响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 项目 ID |
| name | String | 项目名称 |
| description | String | 项目描述 |
| ownerId | Long | 所有者 ID |
| status | String | 项目状态 |
| createdAt | String | 创建时间 |

#### 查询我的项目列表

- 方法：`GET`
- 路径：`/api/projects`
- 用途：查询当前用户创建或参与的项目。
- 主要请求字段：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| status | String | 否 | 项目状态：ACTIVE、ARCHIVED |
| keyword | String | 否 | 项目名称关键词 |

- 主要响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| items[].id | Long | 项目 ID |
| items[].name | String | 项目名称 |
| items[].description | String | 项目描述 |
| items[].role | String | 当前用户在项目中的角色 |
| items[].taskSummary.total | Number | 任务总数 |
| items[].taskSummary.done | Number | 已完成任务数 |

#### 查看项目详情

- 方法：`GET`
- 路径：`/api/projects/{projectId}`
- 用途：查看项目基础信息。
- 主要请求字段：路径参数 `projectId`。
- 主要响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 项目 ID |
| name | String | 项目名称 |
| description | String | 项目描述 |
| owner.id | Long | 所有者 ID |
| owner.name | String | 所有者名称 |
| status | String | 项目状态 |
| currentUserRole | String | 当前用户项目角色 |
| createdAt | String | 创建时间 |
| updatedAt | String | 更新时间 |

#### 更新项目

- 方法：`PUT`
- 路径：`/api/projects/{projectId}`
- 用途：更新项目名称和描述，仅项目所有者可操作。
- 主要请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| name | String | 是 | 项目名称 |
| description | String | 否 | 项目描述 |

- 主要响应字段：返回更新后的项目详情。

#### 归档项目

- 方法：`PATCH`
- 路径：`/api/projects/{projectId}/archive`
- 用途：将项目状态改为 `ARCHIVED`，仅项目所有者可操作。
- 主要请求字段：无。
- 主要响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 项目 ID |
| status | String | 项目状态 |

### 7.5 项目成员接口

#### 查询项目成员

- 方法：`GET`
- 路径：`/api/projects/{projectId}/members`
- 用途：查看项目成员列表。
- 主要请求字段：路径参数 `projectId`。
- 主要响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| items[].id | Long | 成员关系 ID |
| items[].user.id | Long | 用户 ID |
| items[].user.name | String | 用户名称 |
| items[].user.email | String | 用户邮箱 |
| items[].role | String | 项目角色 |
| items[].joinedAt | String | 加入时间 |

#### 添加项目成员

- 方法：`POST`
- 路径：`/api/projects/{projectId}/members`
- 用途：通过邮箱添加已注册用户为项目成员，仅项目所有者可操作。
- 主要请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| email | String | 是 | 被添加用户邮箱 |
| role | String | 否 | 项目角色，默认 `MEMBER` |

- 主要响应字段：返回新增的成员关系。

#### 移除项目成员

- 方法：`DELETE`
- 路径：`/api/projects/{projectId}/members/{memberId}`
- 用途：移除项目成员，仅项目所有者可操作。
- 主要请求字段：路径参数 `projectId`、`memberId`。
- 主要响应字段：无，成功返回 `204`。

### 7.6 任务接口

#### 查询项目任务列表

- 方法：`GET`
- 路径：`/api/projects/{projectId}/tasks`
- 用途：查询项目下任务，用于看板和列表展示。
- 主要请求字段：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| status | String | 否 | 任务状态 |
| assigneeId | Long | 否 | 负责人 ID |
| keyword | String | 否 | 标题关键词 |

- 主要响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| items[].id | Long | 任务 ID |
| items[].title | String | 任务标题 |
| items[].description | String | 任务描述摘要 |
| items[].status | String | 任务状态 |
| items[].priority | String | 优先级 |
| items[].assignee.id | Long | 负责人 ID |
| items[].assignee.name | String | 负责人名称 |
| items[].dueDate | String | 截止日期 |
| items[].updatedAt | String | 更新时间 |

#### 创建任务

- 方法：`POST`
- 路径：`/api/projects/{projectId}/tasks`
- 用途：在项目中创建任务。
- 主要请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| title | String | 是 | 任务标题 |
| description | String | 否 | 任务描述 |
| priority | String | 否 | 优先级，默认 `MEDIUM` |
| assigneeId | Long | 否 | 负责人 ID |
| dueDate | String | 否 | 截止日期 |

- 主要响应字段：返回创建后的任务详情。

#### 查看任务详情

- 方法：`GET`
- 路径：`/api/tasks/{taskId}`
- 用途：查看单个任务详情。
- 主要请求字段：路径参数 `taskId`。
- 主要响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 任务 ID |
| projectId | Long | 项目 ID |
| title | String | 任务标题 |
| description | String | 任务描述 |
| status | String | 任务状态 |
| priority | String | 优先级 |
| assignee.id | Long | 负责人 ID |
| assignee.name | String | 负责人名称 |
| creator.id | Long | 创建人 ID |
| creator.name | String | 创建人名称 |
| dueDate | String | 截止日期 |
| createdAt | String | 创建时间 |
| updatedAt | String | 更新时间 |

#### 更新任务

- 方法：`PUT`
- 路径：`/api/tasks/{taskId}`
- 用途：更新任务基础信息。
- 主要请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| title | String | 是 | 任务标题 |
| description | String | 否 | 任务描述 |
| priority | String | 否 | 优先级 |
| assigneeId | Long | 否 | 负责人 ID |
| dueDate | String | 否 | 截止日期 |

- 主要响应字段：返回更新后的任务详情。

#### 更新任务状态

- 方法：`PATCH`
- 路径：`/api/tasks/{taskId}/status`
- 用途：拖拽任务卡片后更新任务状态。
- 主要请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| status | String | 是 | 目标状态：TODO、IN_PROGRESS、DONE |

- 主要响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | Long | 任务 ID |
| status | String | 更新后的状态 |
| updatedAt | String | 更新时间 |

#### 删除任务

- 方法：`DELETE`
- 路径：`/api/tasks/{taskId}`
- 用途：删除任务，建议 MVP 阶段仅项目所有者可操作。
- 主要请求字段：路径参数 `taskId`。
- 主要响应字段：无，成功返回 `204`。

### 7.7 任务评论接口

#### 查询任务评论

- 方法：`GET`
- 路径：`/api/tasks/{taskId}/comments`
- 用途：查看任务评论列表。
- 主要请求字段：路径参数 `taskId`。
- 主要响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| items[].id | Long | 评论 ID |
| items[].content | String | 评论内容 |
| items[].author.id | Long | 作者 ID |
| items[].author.name | String | 作者名称 |
| items[].createdAt | String | 创建时间 |

#### 新增任务评论

- 方法：`POST`
- 路径：`/api/tasks/{taskId}/comments`
- 用途：在任务下发表评论。
- 主要请求字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| content | String | 是 | 评论内容 |

- 主要响应字段：返回新增的评论详情。

#### 删除任务评论

- 方法：`DELETE`
- 路径：`/api/comments/{commentId}`
- 用途：删除评论，评论作者或项目所有者可操作。
- 主要请求字段：路径参数 `commentId`。
- 主要响应字段：无，成功返回 `204`。

### 7.8 项目进度接口

#### 查看项目进度统计

- 方法：`GET`
- 路径：`/api/projects/{projectId}/stats`
- 用途：获取项目任务统计数据，用于进度概览。
- 主要请求字段：路径参数 `projectId`。
- 主要响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| totalTasks | Number | 任务总数 |
| todoTasks | Number | 待处理任务数 |
| inProgressTasks | Number | 进行中任务数 |
| doneTasks | Number | 已完成任务数 |
| overdueTasks | Number | 逾期未完成任务数 |
| completionRate | Number | 完成率，范围 0-100 |
| byAssignee[].userId | Long | 负责人 ID |
| byAssignee[].userName | String | 负责人名称 |
| byAssignee[].total | Number | 该负责人任务总数 |
| byAssignee[].done | Number | 该负责人完成任务数 |

## 8. 前端页面规划

### 8.1 页面路由建议

| 路由 | 页面 | 说明 |
| --- | --- | --- |
| `/login` | 登录页 | 邮箱密码登录 |
| `/register` | 注册页 | 用户注册 |
| `/projects` | 项目列表页 | 展示我参与的项目 |
| `/projects/new` | 创建项目页 | 创建新项目 |
| `/projects/:projectId` | 项目详情页 | 项目概览与任务看板 |
| `/projects/:projectId/settings` | 项目设置页 | 更新项目信息、管理成员 |
| `/tasks/:taskId` | 任务详情页 | 可作为独立页，也可在 MVP 中用抽屉替代 |

### 8.2 页面组件建议

- `AuthLayout`：登录、注册页面布局。
- `AppLayout`：登录后页面框架，包含顶部导航和用户信息。
- `ProjectList`：项目卡片列表。
- `ProjectForm`：项目创建和编辑表单。
- `ProjectBoard`：任务看板容器。
- `TaskColumn`：任务状态列。
- `TaskCard`：任务卡片。
- `TaskForm`：任务创建和编辑表单。
- `TaskDetailDrawer`：任务详情与评论抽屉。
- `MemberList`：项目成员列表。
- `ProjectStats`：项目进度统计组件。

### 8.3 前端状态与交互建议

- MVP 可先使用 React 内置状态和自定义 API 工具函数，不强制引入复杂状态管理库。
- 登录令牌可先存储在 `localStorage`，后续再考虑更安全的 Cookie 方案。
- 看板拖拽可在 MVP 中使用轻量拖拽库，也可先用状态切换按钮替代，后续迭代为拖拽体验。
- 表单校验优先覆盖必填、长度、邮箱格式和日期合法性。

## 9. 后端模块建议

### 9.1 包结构建议

```text
com.example.server
├── auth
├── user
├── project
├── member
├── task
├── comment
├── stats
├── common
└── config
```

### 9.2 分层建议

每个业务模块可按以下职责拆分：

- `controller`：接收 REST 请求，处理参数和响应。
- `service`：承载业务规则和权限校验。
- `repository`：数据访问层。
- `entity`：数据库实体。
- `dto`：请求和响应对象。

### 9.3 核心后端能力

- 认证与当前用户解析。
- 统一异常处理。
- 请求参数校验。
- 项目成员权限校验。
- 任务状态变更校验。
- 统计数据聚合查询。

### 9.4 数据库建议

MVP 开发阶段可选择：

- 本地开发：H2 或 MySQL。
- 更贴近真实部署：MySQL。

建议优先保持实体关系清晰，不引入复杂审计表、动态工作流、通知中心等高级能力。

## 10. 迭代计划

### 10.1 第 1 迭代：项目骨架与认证

目标：完成可登录的前后端基础闭环。

- 后端添加用户注册、登录、当前用户接口。
- 前端添加登录页、注册页、登录态保存。
- 建立 API 请求工具和路由保护。
- 完成基础异常响应格式。

### 10.2 第 2 迭代：项目与成员

目标：用户可以创建项目并管理成员。

- 后端完成项目 CRUD 的 MVP 接口。
- 后端完成项目成员查询、添加、移除接口。
- 前端完成项目列表、创建项目、项目设置页。
- 完成项目所有者与成员权限校验。

### 10.3 第 3 迭代：任务看板

目标：项目成员可以创建、分配和流转任务。

- 后端完成任务查询、创建、详情、更新、状态更新、删除接口。
- 前端完成任务看板、任务卡片、任务表单。
- 完成任务按状态展示和状态更新。
- 优先实现稳定状态切换，再增强拖拽体验。

### 10.4 第 4 迭代：评论与进度

目标：补齐协作沟通和项目进度查看。

- 后端完成评论查询、新增、删除接口。
- 后端完成项目统计接口。
- 前端完成任务详情抽屉和评论区。
- 前端完成项目进度概览组件。

### 10.5 第 5 迭代：体验打磨与验收

目标：整理边界场景，形成可演示版本。

- 完善空状态、加载状态、错误提示。
- 完善表单校验和权限错误提示。
- 补充关键后端单元测试或接口测试。
- 梳理 README 启动说明和演示流程。

## 11. 非 MVP 范围

以下能力暂不纳入第一版 MVP，避免过度设计：

- 多租户与组织空间。
- 复杂 RBAC 权限模型。
- 自定义任务工作流。
- 邮件邀请和站内通知。
- 文件上传与附件管理。
- 实时协同和 WebSocket 推送。
- 微服务拆分、消息队列、分布式缓存。
- 复杂 CI/CD 和容器编排。

## 12. MVP 验收标准

- 用户可以注册、登录并查看自己的项目。
- 用户可以创建项目，并添加已注册用户为成员。
- 项目成员可以创建任务、分配负责人、更新状态。
- 项目成员可以围绕任务发表评论。
- 项目页面可以展示任务按状态分布和完成率。
- 未登录用户不能访问受保护接口。
- 非项目成员不能访问项目详情、任务和评论。
