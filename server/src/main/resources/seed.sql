USE team_flow;

INSERT INTO users (username, email, password_hash)
VALUES
  ('张三', 'zhangsan@example.com', '$2a$10$/uR3HA2whBITPj2Fglpi8eX1RUlOHyFH5eLa1qyIoK7Dvqjrww0Y.'),
  ('李四', 'lisi@example.com', '$2a$10$VE/cqM636OJGYPrhVHaRiei0FKVTh2.YpikOueqY8ehEmcLY1Pmme')
ON DUPLICATE KEY UPDATE
  username = VALUES(username),
  password_hash = VALUES(password_hash),
  deleted_at = NULL,
  updated_at = NOW();

SET @owner_id = (SELECT id FROM users WHERE email = 'zhangsan@example.com' AND deleted_at IS NULL LIMIT 1);
SET @member_id = (SELECT id FROM users WHERE email = 'lisi@example.com' AND deleted_at IS NULL LIMIT 1);

INSERT INTO projects (name, description, status, created_by)
SELECT
  '官网改版',
  '完成首页、项目页和联系页改版，沉淀轻量协作流程。',
  'ACTIVE',
  @owner_id
WHERE NOT EXISTS (
  SELECT 1
  FROM projects
  WHERE name = '官网改版'
    AND created_by = @owner_id
    AND deleted_at IS NULL
);

SET @project_id = (
  SELECT id
  FROM projects
  WHERE name = '官网改版'
    AND created_by = @owner_id
    AND deleted_at IS NULL
  ORDER BY id ASC
  LIMIT 1
);

INSERT INTO project_members (project_id, user_id, role)
SELECT @project_id, @owner_id, 'OWNER'
WHERE NOT EXISTS (
  SELECT 1
  FROM project_members
  WHERE project_id = @project_id
    AND user_id = @owner_id
    AND deleted_at IS NULL
);

INSERT INTO project_members (project_id, user_id, role)
SELECT @project_id, @member_id, 'MEMBER'
WHERE NOT EXISTS (
  SELECT 1
  FROM project_members
  WHERE project_id = @project_id
    AND user_id = @member_id
    AND deleted_at IS NULL
);

INSERT INTO project_invites (project_id, email, role, status, invited_by, handled_at)
SELECT @project_id, 'lisi@example.com', 'MEMBER', 'ACCEPTED', @owner_id, NOW()
WHERE NOT EXISTS (
  SELECT 1
  FROM project_invites
  WHERE project_id = @project_id
    AND email = 'lisi@example.com'
    AND status = 'ACCEPTED'
);

INSERT INTO tasks (project_id, title, description, status, priority, assignee_id, creator_id, due_date, sort_order)
SELECT
  @project_id,
  '设计任务看板交互',
  '完成三列看板、任务卡片摘要和状态拖拽。',
  'TODO',
  'HIGH',
  @member_id,
  @owner_id,
  DATE_ADD(CURDATE(), INTERVAL 5 DAY),
  1000
WHERE NOT EXISTS (
  SELECT 1
  FROM tasks
  WHERE project_id = @project_id
    AND title = '设计任务看板交互'
    AND deleted_at IS NULL
);

INSERT INTO tasks (project_id, title, description, status, priority, assignee_id, creator_id, due_date, sort_order)
SELECT
  @project_id,
  '梳理项目进度统计',
  '统计总数、完成率、逾期任务和成员任务分布。',
  'IN_PROGRESS',
  'MEDIUM',
  @owner_id,
  @member_id,
  DATE_ADD(CURDATE(), INTERVAL 2 DAY),
  1000
WHERE NOT EXISTS (
  SELECT 1
  FROM tasks
  WHERE project_id = @project_id
    AND title = '梳理项目进度统计'
    AND deleted_at IS NULL
);

SET @stats_task_id = (
  SELECT id
  FROM tasks
  WHERE project_id = @project_id
    AND title = '梳理项目进度统计'
    AND deleted_at IS NULL
  ORDER BY id ASC
  LIMIT 1
);

INSERT INTO task_comments (task_id, project_id, user_id, content)
SELECT
  @stats_task_id,
  @project_id,
  @owner_id,
  '统计面板先覆盖 MVP 指标，后续再扩展趋势图。'
WHERE NOT EXISTS (
  SELECT 1
  FROM task_comments
  WHERE task_id = @stats_task_id
    AND user_id = @owner_id
    AND content = '统计面板先覆盖 MVP 指标，后续再扩展趋势图。'
    AND deleted_at IS NULL
);
