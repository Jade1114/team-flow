# Team Flow 数据库设计文档

## 1. 文档说明

本文档面向 Team Flow MVP 阶段的后端开发，描述团队协作管理系统的核心数据库表结构、字段规范、枚举约定、索引设计与建表 SQL 草案。

项目技术栈预期为：

- 后端：Spring Boot
- 数据库：MySQL 8.x
- ORM/持久层：MyBatis、MyBatis-Plus 或 JPA 均可
- 前端：React

本文档只覆盖 MVP 必需表结构，不设计复杂 RBAC、组织架构、多租户、附件、通知、审计日志、全文搜索等非 MVP 能力。

## 2. 设计目标

### 2.1 MVP 目标

数据库需要支持以下核心业务：

1. 用户注册、登录、查看当前用户信息。
2. 用户创建项目，并成为项目负责人。
3. 项目负责人邀请成员加入项目。
4. 项目成员在项目中创建、分配、编辑任务。
5. 任务支持看板状态流转和同列排序。
6. 成员可以围绕任务发表评论。
7. 项目页面可以统计任务进度、任务状态分布和成员任务量。

### 2.2 设计原则

- 表结构先满足 MVP，不做过度抽象。
- 所有业务表使用自增 `BIGINT` 主键。
- 外键关系在业务层保证，数据库层可不强制创建物理外键，降低开发阶段迁移成本。
- 所有核心表保留 `created_at`、`updated_at` 字段。
- 需要删除但可能被历史数据引用的表，优先使用软删除。
- 枚举字段使用 `VARCHAR` 存储大写英文值，便于接口直接返回。
- 密码只存储哈希值，禁止存储明文密码。

## 3. 命名与字段规范

### 3.1 表命名

| 类型 | 规范 | 示例 |
| --- | --- | --- |
| 表名 | 小写蛇形命名，复数名词 | `users`、`projects`、`tasks` |
| 字段名 | 小写蛇形命名 | `created_at`、`project_id` |
| 索引名 | `idx_表名_字段名` | `idx_tasks_project_status` |
| 唯一索引名 | `uk_表名_字段名` | `uk_users_email` |

### 3.2 通用字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 自增主键 |
| `created_at` | `DATETIME NOT NULL` | 创建时间 |
| `updated_at` | `DATETIME NOT NULL` | 更新时间 |
| `deleted_at` | `DATETIME NULL` | 软删除时间，部分表使用 |

### 3.3 时间约定

- 数据库统一使用 `DATETIME`。
- 后端统一按 `UTC+8` 或应用配置时区写入。
- API 返回 ISO 8601 风格字符串，例如 `2026-05-19T10:30:00`。
- `created_at` 和 `updated_at` 可由数据库默认值维护，也可由后端统一填充。

### 3.4 软删除策略

MVP 建议：

| 表 | 删除策略 | 说明 |
| --- | --- | --- |
| `users` | 软删除 | 避免历史任务、评论找不到用户 |
| `projects` | 软删除 | 删除项目时保留历史数据 |
| `project_members` | 软删除或状态化 | 成员移除后保留历史关系 |
| `project_invites` | 不软删 | 邀请记录可直接保留状态 |
| `tasks` | 软删除 | 删除任务后评论和统计可追溯 |
| `task_comments` | 软删除 | 评论删除后保留审计空间 |

### 3.5 金额/排序/计数字段

本项目 MVP 暂无金额字段。

任务看板排序使用 `sort_order INT`：

- 数值越小越靠前。
- 拖拽任务时更新目标任务的 `status` 和 `sort_order`。
- MVP 可简单重排同一列所有任务的 `sort_order`，不必一开始做稀疏排序或小数排序。

## 4. 枚举定义

### 4.1 项目成员角色 `project_members.role`

| 值 | 中文 | 说明 |
| --- | --- | --- |
| `OWNER` | 项目负责人 | 创建项目的人，拥有项目管理权限 |
| `ADMIN` | 项目管理员 | 可管理成员和任务，MVP 可选 |
| `MEMBER` | 普通成员 | 可参与任务协作 |

MVP 可先只实现 `OWNER` 和 `MEMBER`，保留 `ADMIN` 枚举方便后续扩展。

### 4.2 邀请状态 `project_invites.status`

| 值 | 中文 | 说明 |
| --- | --- | --- |
| `PENDING` | 待处理 | 邀请已创建，等待被邀请人接受 |
| `ACCEPTED` | 已接受 | 被邀请人已加入项目 |
| `REJECTED` | 已拒绝 | 被邀请人拒绝邀请 |
| `EXPIRED` | 已过期 | 邀请超过有效期 |
| `CANCELED` | 已取消 | 邀请人取消邀请 |

MVP 可以只实现 `PENDING`、`ACCEPTED`、`REJECTED`，过期与取消可后续补。

### 4.3 任务状态 `tasks.status`

| 值 | 中文 | 看板列 | 说明 |
| --- | --- | --- | --- |
| `TODO` | 待处理 | 是 | 新建任务默认状态 |
| `IN_PROGRESS` | 进行中 | 是 | 成员正在处理 |
| `REVIEW` | 待验收 | 是 | 等待负责人或协作者确认 |
| `DONE` | 已完成 | 是 | 任务完成 |

### 4.4 任务优先级 `tasks.priority`

| 值 | 中文 | 说明 |
| --- | --- | --- |
| `LOW` | 低 | 不紧急 |
| `MEDIUM` | 中 | 默认优先级 |
| `HIGH` | 高 | 重要任务 |
| `URGENT` | 紧急 | 需要优先处理 |

## 5. 实体关系概览

```text
users 1 ── N projects.created_by
users 1 ── N project_members.user_id
projects 1 ── N project_members.project_id
projects 1 ── N project_invites.project_id
projects 1 ── N tasks.project_id
users 1 ── N tasks.creator_id
users 1 ── N tasks.assignee_id
users 1 ── N task_comments.user_id
tasks 1 ── N task_comments.task_id
```

核心关系说明：

- 一个用户可以创建多个项目。
- 一个项目可以有多个成员。
- 一个用户可以加入多个项目。
- 一个项目下有多个任务。
- 一个任务最多分配给一个负责人，MVP 暂不支持多人同时负责人。
- 一个任务可以有多条评论。
- 邀请通过邮箱或用户 ID 关联用户，MVP 推荐先用邮箱邀请。

## 6. 核心表设计

## 6.1 `users` 用户表

### 用途

存储系统用户信息，用于注册、登录、成员展示、任务分配和评论展示。

### 字段设计

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 是 | 自增 | 用户 ID |
| `username` | `VARCHAR(50)` | 是 | 无 | 用户名，展示用 |
| `email` | `VARCHAR(100)` | 是 | 无 | 登录邮箱，唯一 |
| `password_hash` | `VARCHAR(255)` | 是 | 无 | BCrypt 等算法生成的密码哈希 |
| `avatar_url` | `VARCHAR(500)` | 否 | `NULL` | 用户头像地址 |
| `deleted_at` | `DATETIME` | 否 | `NULL` | 软删除时间 |
| `created_at` | `DATETIME` | 是 | 当前时间 | 创建时间 |
| `updated_at` | `DATETIME` | 是 | 当前时间 | 更新时间 |

### 约束与索引

| 类型 | 名称 | 字段 | 说明 |
| --- | --- | --- | --- |
| 主键 | `pk_users` | `id` | 用户主键 |
| 唯一索引 | `uk_users_email` | `email` | 登录邮箱唯一 |
| 普通索引 | `idx_users_deleted_at` | `deleted_at` | 过滤已删除用户 |

### 业务说明

- 注册时必须校验邮箱唯一。
- 登录时通过 `email` 查询用户，再校验密码哈希。
- 返回前端时禁止返回 `password_hash`。
- 删除用户时不物理删除，避免项目成员、任务、评论历史断链。

## 6.2 `projects` 项目表

### 用途

存储用户创建的项目基础信息。

### 字段设计

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 是 | 自增 | 项目 ID |
| `name` | `VARCHAR(100)` | 是 | 无 | 项目名称 |
| `description` | `VARCHAR(1000)` | 否 | `NULL` | 项目描述 |
| `created_by` | `BIGINT UNSIGNED` | 是 | 无 | 创建者用户 ID |
| `deleted_at` | `DATETIME` | 否 | `NULL` | 软删除时间 |
| `created_at` | `DATETIME` | 是 | 当前时间 | 创建时间 |
| `updated_at` | `DATETIME` | 是 | 当前时间 | 更新时间 |

### 约束与索引

| 类型 | 名称 | 字段 | 说明 |
| --- | --- | --- | --- |
| 主键 | `pk_projects` | `id` | 项目主键 |
| 普通索引 | `idx_projects_created_by` | `created_by` | 查询用户创建的项目 |
| 普通索引 | `idx_projects_deleted_at` | `deleted_at` | 过滤已删除项目 |

### 业务说明

- 创建项目时，同步在 `project_members` 插入一条 `OWNER` 成员记录。
- 项目列表接口应查询当前用户参与的项目，而不是只查 `created_by`。
- 删除项目时建议软删除项目，并在业务层禁止访问其任务和评论。

## 6.3 `project_members` 项目成员表

### 用途

存储用户和项目之间的成员关系，以及用户在项目中的角色。

### 字段设计

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 是 | 自增 | 成员关系 ID |
| `project_id` | `BIGINT UNSIGNED` | 是 | 无 | 项目 ID |
| `user_id` | `BIGINT UNSIGNED` | 是 | 无 | 用户 ID |
| `role` | `VARCHAR(20)` | 是 | `MEMBER` | 项目角色：`OWNER`、`ADMIN`、`MEMBER` |
| `joined_at` | `DATETIME` | 是 | 当前时间 | 加入时间 |
| `deleted_at` | `DATETIME` | 否 | `NULL` | 被移出项目的时间 |
| `created_at` | `DATETIME` | 是 | 当前时间 | 创建时间 |
| `updated_at` | `DATETIME` | 是 | 当前时间 | 更新时间 |

### 约束与索引

| 类型 | 名称 | 字段 | 说明 |
| --- | --- | --- | --- |
| 主键 | `pk_project_members` | `id` | 成员关系主键 |
| 唯一索引 | `uk_project_members_project_user_active` | `project_id, user_id, deleted_at` | 避免同一用户重复作为有效成员，注意 MySQL NULL 唯一语义 |
| 普通索引 | `idx_project_members_user` | `user_id, deleted_at` | 查询用户参与的项目 |
| 普通索引 | `idx_project_members_project` | `project_id, deleted_at` | 查询项目成员列表 |

### 业务说明

- MVP 可在业务层保证同一项目同一用户只有一条有效成员关系。
- MySQL 唯一索引中 `NULL` 不互斥，如需严格保证“有效成员唯一”，可增加 `active_flag TINYINT`，或用业务代码校验。
- 移除成员时设置 `deleted_at`，不直接删除记录。
- 项目创建者默认角色为 `OWNER`。
- `OWNER` 不允许被普通移除接口删除。

## 6.4 `project_invites` 项目邀请表

### 用途

存储项目成员邀请记录。MVP 建议使用邮箱邀请，暂不接入真实邮件服务也可以，只在系统内展示邀请状态。

### 字段设计

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 是 | 自增 | 邀请 ID |
| `project_id` | `BIGINT UNSIGNED` | 是 | 无 | 项目 ID |
| `email` | `VARCHAR(100)` | 是 | 无 | 被邀请邮箱 |
| `role` | `VARCHAR(20)` | 是 | `MEMBER` | 邀请加入后的角色 |
| `status` | `VARCHAR(20)` | 是 | `PENDING` | 邀请状态 |
| `invited_by` | `BIGINT UNSIGNED` | 是 | 无 | 邀请人用户 ID |
| `invite_token` | `VARCHAR(100)` | 否 | `NULL` | 邀请令牌，MVP 可选 |
| `expires_at` | `DATETIME` | 否 | `NULL` | 过期时间，MVP 可选 |
| `handled_at` | `DATETIME` | 否 | `NULL` | 接受/拒绝时间 |
| `created_at` | `DATETIME` | 是 | 当前时间 | 创建时间 |
| `updated_at` | `DATETIME` | 是 | 当前时间 | 更新时间 |

### 约束与索引

| 类型 | 名称 | 字段 | 说明 |
| --- | --- | --- | --- |
| 主键 | `pk_project_invites` | `id` | 邀请主键 |
| 唯一索引 | `uk_project_invites_token` | `invite_token` | 邀请令牌唯一，可为空 |
| 普通索引 | `idx_project_invites_project_status` | `project_id, status` | 查询项目邀请列表 |
| 普通索引 | `idx_project_invites_email_status` | `email, status` | 查询用户收到的邀请 |

### 业务说明

- 同一项目同一邮箱存在 `PENDING` 邀请时，不应重复创建。
- 接受邀请时：
  1. 校验邀请状态为 `PENDING`。
  2. 校验当前登录用户邮箱与邀请邮箱一致。
  3. 插入或恢复 `project_members` 成员关系。
  4. 更新邀请状态为 `ACCEPTED`。
- 拒绝邀请时更新状态为 `REJECTED`。
- 若 MVP 暂不做邀请链接，`invite_token` 可以先保留为空。

## 6.5 `tasks` 任务表

### 用途

存储项目下的任务信息，是看板、分配、拖拽状态流转和进度统计的核心表。

### 字段设计

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 是 | 自增 | 任务 ID |
| `project_id` | `BIGINT UNSIGNED` | 是 | 无 | 所属项目 ID |
| `title` | `VARCHAR(200)` | 是 | 无 | 任务标题 |
| `description` | `TEXT` | 否 | `NULL` | 任务描述 |
| `status` | `VARCHAR(30)` | 是 | `TODO` | 任务状态 |
| `priority` | `VARCHAR(20)` | 是 | `MEDIUM` | 任务优先级 |
| `assignee_id` | `BIGINT UNSIGNED` | 否 | `NULL` | 负责人用户 ID |
| `creator_id` | `BIGINT UNSIGNED` | 是 | 无 | 创建者用户 ID |
| `due_date` | `DATE` | 否 | `NULL` | 截止日期 |
| `sort_order` | `INT` | 是 | `0` | 看板同状态列内排序 |
| `deleted_at` | `DATETIME` | 否 | `NULL` | 软删除时间 |
| `created_at` | `DATETIME` | 是 | 当前时间 | 创建时间 |
| `updated_at` | `DATETIME` | 是 | 当前时间 | 更新时间 |

### 约束与索引

| 类型 | 名称 | 字段 | 说明 |
| --- | --- | --- | --- |
| 主键 | `pk_tasks` | `id` | 任务主键 |
| 普通索引 | `idx_tasks_project_status_sort` | `project_id, status, sort_order` | 看板按列查询与排序 |
| 普通索引 | `idx_tasks_project_assignee` | `project_id, assignee_id` | 查询成员任务 |
| 普通索引 | `idx_tasks_project_due_date` | `project_id, due_date` | 查询项目截止日期任务 |
| 普通索引 | `idx_tasks_deleted_at` | `deleted_at` | 过滤已删除任务 |

### 业务说明

- 创建任务时默认 `status = TODO`，`priority = MEDIUM`。
- `assignee_id` 可以为空，表示未分配。
- 分配任务时必须校验负责人是项目成员。
- 编辑任务时必须校验当前用户是项目成员。
- 拖拽任务时更新 `status` 与 `sort_order`。
- 删除任务使用软删除，评论不立即物理删除。
- 看板查询默认过滤 `deleted_at IS NULL`。

## 6.6 `task_comments` 任务评论表

### 用途

存储任务下的沟通评论。

### 字段设计

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 是 | 自增 | 评论 ID |
| `task_id` | `BIGINT UNSIGNED` | 是 | 无 | 任务 ID |
| `project_id` | `BIGINT UNSIGNED` | 是 | 无 | 项目 ID，冗余便于权限校验和查询 |
| `user_id` | `BIGINT UNSIGNED` | 是 | 无 | 评论用户 ID |
| `content` | `TEXT` | 是 | 无 | 评论内容 |
| `deleted_at` | `DATETIME` | 否 | `NULL` | 软删除时间 |
| `created_at` | `DATETIME` | 是 | 当前时间 | 创建时间 |
| `updated_at` | `DATETIME` | 是 | 当前时间 | 更新时间 |

### 约束与索引

| 类型 | 名称 | 字段 | 说明 |
| --- | --- | --- | --- |
| 主键 | `pk_task_comments` | `id` | 评论主键 |
| 普通索引 | `idx_task_comments_task_created` | `task_id, created_at` | 查询任务评论列表 |
| 普通索引 | `idx_task_comments_project` | `project_id, deleted_at` | 项目级统计或清理 |
| 普通索引 | `idx_task_comments_user` | `user_id` | 查询用户评论历史，MVP 可选 |

### 业务说明

- 新增评论时必须校验当前用户是任务所属项目成员。
- 删除评论时建议只允许评论作者或项目 `OWNER` 删除。
- 评论列表按 `created_at ASC` 返回，形成自然对话顺序。
- `project_id` 虽然可由 `task_id` 推导，但冗余后能减少权限校验时的 join。

## 6.7 可选表：`task_status_history` 任务状态变更历史

### MVP 是否需要

MVP 可不做。若后续需要展示“任务动态”或追踪拖拽历史，可以增加该表。

### 用途

记录任务状态变化、负责人变化等事件。

### 字段设计

| 字段 | 类型 | 必填 | 默认值 | 说明 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT UNSIGNED` | 是 | 自增 | 历史记录 ID |
| `task_id` | `BIGINT UNSIGNED` | 是 | 无 | 任务 ID |
| `project_id` | `BIGINT UNSIGNED` | 是 | 无 | 项目 ID |
| `changed_by` | `BIGINT UNSIGNED` | 是 | 无 | 操作人用户 ID |
| `field_name` | `VARCHAR(50)` | 是 | 无 | 变更字段，例如 `status` |
| `old_value` | `VARCHAR(255)` | 否 | `NULL` | 旧值 |
| `new_value` | `VARCHAR(255)` | 否 | `NULL` | 新值 |
| `created_at` | `DATETIME` | 是 | 当前时间 | 创建时间 |

## 7. 推荐建表 SQL 草案

> 说明：以下 SQL 适用于 MySQL 8.x。物理外键先不强制创建，由业务层保证数据一致性。后续若项目稳定，可再补充外键约束。

```sql
CREATE TABLE users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  username VARCHAR(50) NOT NULL COMMENT '用户名',
  email VARCHAR(100) NOT NULL COMMENT '邮箱',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希',
  avatar_url VARCHAR(500) NULL COMMENT '头像地址',
  deleted_at DATETIME NULL COMMENT '软删除时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_email (email),
  KEY idx_users_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

CREATE TABLE projects (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '项目ID',
  name VARCHAR(100) NOT NULL COMMENT '项目名称',
  description VARCHAR(1000) NULL COMMENT '项目描述',
  created_by BIGINT UNSIGNED NOT NULL COMMENT '创建者用户ID',
  deleted_at DATETIME NULL COMMENT '软删除时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_projects_created_by (created_by),
  KEY idx_projects_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目表';

CREATE TABLE project_members (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '项目成员关系ID',
  project_id BIGINT UNSIGNED NOT NULL COMMENT '项目ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
  role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT '项目角色：OWNER/ADMIN/MEMBER',
  joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  deleted_at DATETIME NULL COMMENT '移出项目时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_project_members_user (user_id, deleted_at),
  KEY idx_project_members_project (project_id, deleted_at),
  KEY idx_project_members_project_user (project_id, user_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目成员表';

CREATE TABLE project_invites (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '邀请ID',
  project_id BIGINT UNSIGNED NOT NULL COMMENT '项目ID',
  email VARCHAR(100) NOT NULL COMMENT '被邀请邮箱',
  role VARCHAR(20) NOT NULL DEFAULT 'MEMBER' COMMENT '邀请角色',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '邀请状态：PENDING/ACCEPTED/REJECTED/EXPIRED/CANCELED',
  invited_by BIGINT UNSIGNED NOT NULL COMMENT '邀请人用户ID',
  invite_token VARCHAR(100) NULL COMMENT '邀请令牌',
  expires_at DATETIME NULL COMMENT '过期时间',
  handled_at DATETIME NULL COMMENT '处理时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_project_invites_token (invite_token),
  KEY idx_project_invites_project_status (project_id, status),
  KEY idx_project_invites_email_status (email, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='项目邀请表';

CREATE TABLE tasks (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  project_id BIGINT UNSIGNED NOT NULL COMMENT '项目ID',
  title VARCHAR(200) NOT NULL COMMENT '任务标题',
  description TEXT NULL COMMENT '任务描述',
  status VARCHAR(30) NOT NULL DEFAULT 'TODO' COMMENT '任务状态：TODO/IN_PROGRESS/REVIEW/DONE',
  priority VARCHAR(20) NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级：LOW/MEDIUM/HIGH/URGENT',
  assignee_id BIGINT UNSIGNED NULL COMMENT '负责人用户ID',
  creator_id BIGINT UNSIGNED NOT NULL COMMENT '创建者用户ID',
  due_date DATE NULL COMMENT '截止日期',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '看板排序值',
  deleted_at DATETIME NULL COMMENT '软删除时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_tasks_project_status_sort (project_id, status, sort_order),
  KEY idx_tasks_project_assignee (project_id, assignee_id),
  KEY idx_tasks_project_due_date (project_id, due_date),
  KEY idx_tasks_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务表';

CREATE TABLE task_comments (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '评论ID',
  task_id BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
  project_id BIGINT UNSIGNED NOT NULL COMMENT '项目ID',
  user_id BIGINT UNSIGNED NOT NULL COMMENT '评论用户ID',
  content TEXT NOT NULL COMMENT '评论内容',
  deleted_at DATETIME NULL COMMENT '软删除时间',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_task_comments_task_created (task_id, created_at),
  KEY idx_task_comments_project (project_id, deleted_at),
  KEY idx_task_comments_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务评论表';
```

### 7.1 可选历史表 SQL

```sql
CREATE TABLE task_status_history (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '历史记录ID',
  task_id BIGINT UNSIGNED NOT NULL COMMENT '任务ID',
  project_id BIGINT UNSIGNED NOT NULL COMMENT '项目ID',
  changed_by BIGINT UNSIGNED NOT NULL COMMENT '操作人用户ID',
  field_name VARCHAR(50) NOT NULL COMMENT '变更字段',
  old_value VARCHAR(255) NULL COMMENT '旧值',
  new_value VARCHAR(255) NULL COMMENT '新值',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_task_status_history_task_created (task_id, created_at),
  KEY idx_task_status_history_project_created (project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='任务状态变更历史表';
```

## 8. 常见查询场景与索引说明

### 8.1 登录

```sql
SELECT id, username, email, password_hash
FROM users
WHERE email = ? AND deleted_at IS NULL;
```

命中索引：`uk_users_email`。

### 8.2 查询当前用户参与的项目

```sql
SELECT p.*
FROM project_members pm
JOIN projects p ON p.id = pm.project_id
WHERE pm.user_id = ?
  AND pm.deleted_at IS NULL
  AND p.deleted_at IS NULL
ORDER BY p.updated_at DESC;
```

命中索引：

- `idx_project_members_user`
- `PRIMARY` on `projects.id`

### 8.3 查询项目成员列表

```sql
SELECT pm.id, pm.role, pm.joined_at, u.id AS user_id, u.username, u.email, u.avatar_url
FROM project_members pm
JOIN users u ON u.id = pm.user_id
WHERE pm.project_id = ?
  AND pm.deleted_at IS NULL
  AND u.deleted_at IS NULL
ORDER BY pm.joined_at ASC;
```

命中索引：`idx_project_members_project`。

### 8.4 查询项目看板任务

```sql
SELECT *
FROM tasks
WHERE project_id = ?
  AND deleted_at IS NULL
ORDER BY status ASC, sort_order ASC, created_at ASC;
```

推荐按状态分组查询时使用：

```sql
SELECT *
FROM tasks
WHERE project_id = ?
  AND status = ?
  AND deleted_at IS NULL
ORDER BY sort_order ASC, created_at ASC;
```

命中索引：`idx_tasks_project_status_sort`。

### 8.5 查询某成员负责的任务

```sql
SELECT *
FROM tasks
WHERE project_id = ?
  AND assignee_id = ?
  AND deleted_at IS NULL
ORDER BY due_date ASC, priority DESC;
```

命中索引：`idx_tasks_project_assignee`。

### 8.6 查询任务评论

```sql
SELECT tc.*, u.username, u.avatar_url
FROM task_comments tc
JOIN users u ON u.id = tc.user_id
WHERE tc.task_id = ?
  AND tc.deleted_at IS NULL
ORDER BY tc.created_at ASC;
```

命中索引：`idx_task_comments_task_created`。

### 8.7 统计项目任务进度

```sql
SELECT status, COUNT(*) AS count
FROM tasks
WHERE project_id = ?
  AND deleted_at IS NULL
GROUP BY status;
```

可复用 `idx_tasks_project_status_sort` 的前缀字段 `project_id, status`。

## 9. 权限校验相关数据规则

### 9.1 是否项目成员

大多数项目内接口需要先校验当前用户是否为项目成员：

```sql
SELECT id, role
FROM project_members
WHERE project_id = ?
  AND user_id = ?
  AND deleted_at IS NULL;
```

如果查不到，返回 `403 FORBIDDEN` 或 `404 NOT_FOUND`。MVP 建议返回 `403`，前端提示“无权访问该项目”。

### 9.2 是否项目负责人

成员邀请、移除成员、删除项目等操作需要校验：

```sql
SELECT id
FROM project_members
WHERE project_id = ?
  AND user_id = ?
  AND role IN ('OWNER', 'ADMIN')
  AND deleted_at IS NULL;
```

MVP 如果暂不实现 `ADMIN`，则只判断 `OWNER`。

### 9.3 任务负责人合法性

创建或更新任务负责人时：

```sql
SELECT id
FROM project_members
WHERE project_id = ?
  AND user_id = ?
  AND deleted_at IS NULL;
```

负责人不是项目成员时，返回 `400 BAD_REQUEST`，错误码可为 `ASSIGNEE_NOT_PROJECT_MEMBER`。

## 10. 数据写入流程建议

### 10.1 创建项目

事务内执行：

1. 插入 `projects`。
2. 插入 `project_members`，`role = OWNER`。
3. 返回项目详情。

### 10.2 邀请成员

事务内执行：

1. 校验当前用户是项目 `OWNER` 或 `ADMIN`。
2. 校验目标邮箱没有有效成员关系。
3. 校验没有重复的 `PENDING` 邀请。
4. 插入 `project_invites`。

### 10.3 接受邀请

事务内执行：

1. 查询邀请并加锁。
2. 校验状态为 `PENDING`。
3. 校验当前用户邮箱与邀请邮箱一致。
4. 插入或恢复 `project_members`。
5. 更新邀请为 `ACCEPTED`，写入 `handled_at`。

### 10.4 拖拽任务

事务内执行：

1. 校验当前用户是项目成员。
2. 查询任务并确认属于当前项目。
3. 更新任务 `status`。
4. 重排目标状态列任务的 `sort_order`。
5. 如实现历史表，插入 `task_status_history`。

MVP 可以让前端提交完整目标列任务顺序，后端批量更新：

```json
{
  "status": "IN_PROGRESS",
  "orderedTaskIds": [8, 3, 12, 20]
}
```

## 11. 与 API 文档的字段对应

| API 字段 | DB 字段 | 说明 |
| --- | --- | --- |
| `user.id` | `users.id` | 用户 ID |
| `user.name` 或 `username` | `users.username` | 建议 API 统一叫 `username` |
| `project.id` | `projects.id` | 项目 ID |
| `project.createdBy.id` | `projects.created_by` | 创建人 |
| `member.role` | `project_members.role` | 项目角色 |
| `invite.status` | `project_invites.status` | 邀请状态 |
| `task.status` | `tasks.status` | 看板状态 |
| `task.priority` | `tasks.priority` | 任务优先级 |
| `task.assignee.id` | `tasks.assignee_id` | 任务负责人 |
| `comment.content` | `task_comments.content` | 评论内容 |

## 12. 后续扩展预留

MVP 后可以逐步扩展：

| 能力 | 可能新增表/字段 | 说明 |
| --- | --- | --- |
| 项目动态 | `task_status_history` / `project_activities` | 展示操作流水 |
| 多负责人任务 | `task_assignees` | 一个任务分配多人 |
| 附件上传 | `task_attachments` | 评论或任务附件 |
| 通知中心 | `notifications` | 邀请、评论、任务分配通知 |
| 标签 | `task_labels`、`labels` | 任务分类 |
| 工时 | `task_time_logs` | 记录投入时间 |
| 更复杂权限 | `roles`、`permissions` | 暂不建议 MVP 做 |

## 13. MVP 表优先级

### 第一阶段必须实现

1. `users`
2. `projects`
3. `project_members`
4. `tasks`
5. `task_comments`

### 第二阶段实现

1. `project_invites`

如果开发时间紧，邀请可以先用“项目负责人直接按用户邮箱添加成员”替代，后续再补完整邀请流程。

### 暂缓实现

1. `task_status_history`
2. 附件、通知、标签、工时相关表

## 14. 验收标准

数据库设计满足以下条件即可进入后端编码：

- 可以支持用户注册和登录。
- 可以支持一个用户创建多个项目。
- 可以支持项目成员关系和项目权限判断。
- 可以支持项目任务 CRUD、状态流转、拖拽排序。
- 可以支持任务评论增删查。
- 可以通过 SQL 统计项目任务完成进度。
- 核心查询有明确索引支撑。
- 表结构与 API 文档字段能对应起来。
