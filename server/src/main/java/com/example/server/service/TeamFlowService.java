package com.example.server.service;

import com.example.server.common.ApiException;
import com.example.server.entity.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class TeamFlowService {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final Map<String, Long> tokens = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;

    private final RowMapper<UserEntity> userMapper = (rs, rowNum) -> new UserEntity(rs.getLong("id"), rs.getString("username"), rs.getString("email"),
            rs.getString("password_hash"), rs.getString("avatar_url"), time(rs.getTimestamp("created_at")), time(rs.getTimestamp("updated_at")));
    private final RowMapper<ProjectEntity> projectMapper = (rs, rowNum) -> new ProjectEntity(rs.getLong("id"), rs.getString("name"), rs.getString("description"),
            rs.getString("status"), rs.getLong("created_by"), time(rs.getTimestamp("created_at")), time(rs.getTimestamp("updated_at")));
    private final RowMapper<ProjectMemberEntity> memberMapper = (rs, rowNum) -> new ProjectMemberEntity(rs.getLong("id"), rs.getLong("project_id"),
            rs.getLong("user_id"), rs.getString("role"), time(rs.getTimestamp("joined_at")), time(rs.getTimestamp("deleted_at")));
    private final RowMapper<InviteEntity> inviteMapper = (rs, rowNum) -> new InviteEntity(rs.getLong("id"), rs.getLong("project_id"), rs.getString("email"),
            rs.getString("role"), rs.getString("status"), rs.getLong("invited_by"), time(rs.getTimestamp("created_at")), time(rs.getTimestamp("handled_at")));
    private final RowMapper<TaskEntity> taskMapper = (rs, rowNum) -> new TaskEntity(rs.getLong("id"), rs.getLong("project_id"), rs.getString("title"),
            rs.getString("description"), rs.getString("status"), rs.getString("priority"), nullableLong(rs.getObject("assignee_id")),
            rs.getLong("creator_id"), rs.getDate("due_date") == null ? null : rs.getDate("due_date").toLocalDate(), rs.getInt("sort_order"),
            time(rs.getTimestamp("deleted_at")), time(rs.getTimestamp("created_at")), time(rs.getTimestamp("updated_at")));
    private final RowMapper<CommentEntity> commentMapper = (rs, rowNum) -> new CommentEntity(rs.getLong("id"), rs.getLong("task_id"), rs.getLong("project_id"),
            rs.getLong("user_id"), rs.getString("content"), time(rs.getTimestamp("deleted_at")), time(rs.getTimestamp("created_at")), time(rs.getTimestamp("updated_at")));
    private final RowMapper<TaskActivityEntity> activityMapper = (rs, rowNum) -> new TaskActivityEntity(rs.getLong("id"), rs.getLong("project_id"),
            nullableLong(rs.getObject("task_id")), rs.getLong("user_id"), rs.getString("type"), rs.getString("content"),
            rs.getString("old_value"), rs.getString("new_value"), time(rs.getTimestamp("created_at")));

    public TeamFlowService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== Auth ====================

    public UserEntity register(String name, String email, String password) {
        if (name == null || name.isBlank() || email == null || email.isBlank() || password == null || password.length() < 6) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "姓名、邮箱和至少 6 位密码均必填");
        }
        var normalized = email.toLowerCase(Locale.ROOT);
        if (findUserByEmail(normalized).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "邮箱已注册");
        }
        try {
            var id = insert("INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)", name, normalized, encoder.encode(password));
            return userById(id);
        } catch (DuplicateKeyException error) {
            throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "邮箱已注册");
        }
    }

    public UserEntity login(String email, String password) {
        var user = userByEmail(email);
        if (!encoder.matches(password, user.passwordHash)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "邮箱或密码错误");
        }
        return user;
    }

    public Map<String, Object> authPayload(UserEntity user) {
        var token = UUID.randomUUID().toString();
        tokens.put(token, user.id);
        return Map.of("token", token, "user", userDto(user, false));
    }

    public UserEntity currentUser(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        var userId = tokens.get(authorization.substring(7));
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录状态无效");
        }
        return userById(userId);
    }

    // ==================== Project ====================

    @Transactional
    public ProjectEntity createProject(UserEntity user, String name, String description) {
        if (name == null || name.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "项目名称不能为空");
        }
        var projectId = insert("INSERT INTO projects (name, description, status, created_by) VALUES (?, ?, 'ACTIVE', ?)", name, description, user.id);
        insert("INSERT INTO project_members (project_id, user_id, role) VALUES (?, ?, 'OWNER')", projectId, user.id);
        return projectById(projectId).orElseThrow();
    }

    public Map<String, Object> projectList(UserEntity user, String status, String keyword, int page, int pageSize) {
        var filtered = jdbc.query("""
                SELECT p.*
                FROM project_members pm
                JOIN projects p ON p.id = pm.project_id
                WHERE pm.user_id = ? AND pm.deleted_at IS NULL AND p.deleted_at IS NULL
                ORDER BY p.updated_at DESC
                """, projectMapper, user.id).stream()
                .filter(project -> status == null || project.status.equalsIgnoreCase(status))
                .filter(project -> keyword == null || project.name.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))
                .map(project -> {
                    var dto = projectDto(project, user);
                    dto.put("ownerName", userById(project.ownerId).name);
                    dto.put("taskSummary", taskSummary(project.id));
                    return dto;
                }).toList();
        return page(filtered, page, pageSize);
    }

    public ProjectEntity projectForMember(UserEntity user, long projectId) {
        var project = projectById(projectId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在或无权限访问"));
        requireMember(user, projectId);
        return project;
    }

    @Transactional
    public ProjectEntity updateProject(UserEntity user, long projectId, String name, String description) {
        requireOwner(user, projectId);
        jdbc.update("UPDATE projects SET name = ?, description = ?, updated_at = NOW() WHERE id = ? AND deleted_at IS NULL", name, description, projectId);
        return projectForMember(user, projectId);
    }

    @Transactional
    public ProjectEntity archiveProject(UserEntity user, long projectId) {
        requireOwner(user, projectId);
        jdbc.update("UPDATE projects SET status = 'ARCHIVED', updated_at = NOW() WHERE id = ? AND deleted_at IS NULL", projectId);
        return projectForMember(user, projectId);
    }

    // ==================== Member ====================

    public List<Map<String, Object>> members(UserEntity user, long projectId, String keyword) {
        requireMember(user, projectId);
        return members(projectId).stream().map(this::memberDto)
                .filter(member -> keyword == null || member.toString().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))).toList();
    }

    @Transactional
    public Map<String, Object> invite(UserEntity user, long projectId, String email, String role) {
        requireOwner(user, projectId);
        var invitee = userByEmail(email);
        if (activeMember(projectId, invitee.id).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "MEMBER_ALREADY_EXISTS", "用户已是项目成员");
        }
        var inviteRole = role == null ? "MEMBER" : role;
        var memberId = insert("INSERT INTO project_members (project_id, user_id, role) VALUES (?, ?, ?)", projectId, invitee.id, inviteRole);
        var inviteId = insert("""
                INSERT INTO project_invites (project_id, email, role, status, invited_by, handled_at)
                VALUES (?, ?, ?, 'ACCEPTED', ?, NOW())
                """, projectId, invitee.email, inviteRole, user.id);
        return Map.of("invite", inviteDto(inviteById(inviteId)), "member", memberDto(memberById(memberId)));
    }

    public Map<String, Object> invites(UserEntity user, long projectId, String status, int page, int pageSize) {
        requireOwner(user, projectId);
        return page(jdbc.query("SELECT * FROM project_invites WHERE project_id = ? ORDER BY created_at DESC", inviteMapper, projectId).stream()
                .filter(invite -> status == null || invite.status.equalsIgnoreCase(status)).map(this::inviteDto).toList(), page, pageSize);
    }

    @Transactional
    public void removeMember(UserEntity user, long projectId, long memberId) {
        requireOwner(user, projectId);
        var member = findMemberById(memberId).filter(value -> value.projectId == projectId && value.deletedAt == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "成员不存在"));
        if ("OWNER".equals(member.role)) {
            throw new ApiException(HttpStatus.CONFLICT, "OWNER_CANNOT_BE_REMOVED", "不能移除项目所有者");
        }
        jdbc.update("UPDATE project_members SET deleted_at = NOW(), updated_at = NOW() WHERE id = ?", memberId);
    }

    // ==================== Task ====================

    public Map<String, Object> tasks(UserEntity user, long projectId, String status, Long assigneeId, String priority, String keyword, int page, int pageSize) {
        requireMember(user, projectId);
        var filtered = activeTasks(projectId).stream()
                .filter(task -> status == null || task.status.equalsIgnoreCase(status))
                .filter(task -> assigneeId == null || Objects.equals(task.assigneeId, assigneeId))
                .filter(task -> priority == null || task.priority.equalsIgnoreCase(priority))
                .filter(task -> keyword == null || task.title.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))
                .map(task -> taskDto(task, user, false)).toList();
        return page(filtered, page, pageSize);
    }

    public Map<String, Object> board(UserEntity user, long projectId, Long assigneeId, String keyword) {
        requireMember(user, projectId);
        var statuses = List.of(List.of("TODO", "待处理"), List.of("IN_PROGRESS", "进行中"), List.of("DONE", "已完成"));
        var columns = statuses.stream().map(status -> Map.of("status", status.get(0), "title", status.get(1), "tasks", activeTasks(projectId).stream()
                .filter(task -> task.status.equals(status.get(0)))
                .filter(task -> assigneeId == null || Objects.equals(task.assigneeId, assigneeId))
                .filter(task -> keyword == null || task.title.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))
                .map(task -> taskDto(task, user, false)).toList())).toList();
        return Map.of("columns", columns);
    }

    @Transactional
    public TaskEntity createTask(UserEntity user, long projectId, Map<String, Object> body) {
        requireMember(user, projectId);
        var assigneeId = longValue(body.get("assigneeId"));
        if (assigneeId != null) {
            requireProjectMember(projectId, assigneeId);
        }
        var sortOrder = (activeTasks(projectId).stream().filter(task -> "TODO".equals(task.status)).toList().size() + 1) * 1000;
        var taskId = insert("""
                INSERT INTO tasks (project_id, title, description, status, priority, assignee_id, creator_id, due_date, sort_order)
                VALUES (?, ?, ?, 'TODO', ?, ?, ?, ?, ?)
                """, projectId, text(body, "title"), optionalText(body, "description"),
                optionalText(body, "priority") == null ? "MEDIUM" : optionalText(body, "priority"), assigneeId, user.id,
                dateValue(optionalText(body, "dueDate")), sortOrder);
        touchProject(projectId);
        var created = activeTask(taskId);
        recordActivity(projectId, created.id, user.id, "TASK_CREATED", created.title, null, created.title);
        return created;
    }

    public TaskEntity taskForMember(UserEntity user, long taskId) {
        var task = activeTask(taskId);
        requireMember(user, task.projectId);
        return task;
    }

    @Transactional
    public TaskEntity updateTask(UserEntity user, long taskId, Map<String, Object> body) {
        var task = taskForMember(user, taskId);
        if (!canManageTask(user, task)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无任务编辑权限");
        }
        var assigneeId = longValue(body.get("assigneeId"));
        if (assigneeId != null) {
            requireProjectMember(task.projectId, assigneeId);
        }
        var newTitle = text(body, "title");
        var newDescription = optionalText(body, "description");
        var newPriority = optionalText(body, "priority") == null ? "MEDIUM" : optionalText(body, "priority");
        var newDueDate = dateValue(optionalText(body, "dueDate"));

        // Record assignee change separately for better UX
        if (!Objects.equals(task.assigneeId, assigneeId)) {
            var oldAssignee = task.assigneeId == null ? "未分配" : userById(task.assigneeId).name;
            var newAssignee = assigneeId == null ? "未分配" : userById(assigneeId).name;
            recordActivity(task.projectId, task.id, user.id, "TASK_ASSIGNED", task.title, oldAssignee, newAssignee);
        }

        jdbc.update("""
                UPDATE tasks
                SET title = ?, description = ?, priority = ?, assignee_id = ?, due_date = ?, updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, newTitle, newDescription, newPriority, assigneeId, newDueDate, taskId);

        var updated = activeTask(taskId);
        var changes = new ArrayList<String>();
        if (!Objects.equals(task.title, newTitle)) changes.add("标题");
        if (!Objects.equals(task.description, newDescription)) changes.add("描述");
        if (!Objects.equals(task.priority, newPriority)) changes.add("优先级");
        if (!Objects.equals(task.dueDate, newDueDate)) changes.add("截止日期");
        if (!changes.isEmpty()) {
            recordActivity(task.projectId, task.id, user.id, "TASK_UPDATED", task.title, null, String.join("、", changes));
        }
        return updated;
    }

    @Transactional
    public TaskEntity moveTask(UserEntity user, long taskId, String status) {
        var task = taskForMember(user, taskId);
        var normalized = status.toUpperCase(Locale.ROOT);
        if (!List.of("TODO", "IN_PROGRESS", "DONE").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_STATUS", "任务状态非法");
        }
        if (!task.status.equals(normalized)) {
            recordActivity(task.projectId, task.id, user.id, "TASK_STATUS_CHANGED", task.title, task.status, normalized);
        }
        var sortOrder = (int) activeTasks(task.projectId).stream().filter(item -> item.status.equals(normalized) && item.id != taskId).count() * 1000 + 1000;
        jdbc.update("UPDATE tasks SET status = ?, sort_order = ?, updated_at = NOW() WHERE id = ? AND deleted_at IS NULL", normalized, sortOrder, taskId);
        touchProject(task.projectId);
        return activeTask(taskId);
    }

    @Transactional
    public void deleteTask(UserEntity user, long taskId) {
        var task = taskForMember(user, taskId);
        if (!canManageTask(user, task)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无任务删除权限");
        }
        jdbc.update("UPDATE tasks SET deleted_at = NOW(), updated_at = NOW() WHERE id = ?", taskId);
        recordActivity(task.projectId, task.id, user.id, "TASK_DELETED", task.title, null, null);
        touchProject(task.projectId);
    }

    @Transactional
    public void reorderTasks(UserEntity user, long projectId, String status, List<Long> orderedTaskIds) {
        requireMember(user, projectId);
        var normalized = status.toUpperCase(Locale.ROOT);
        if (!List.of("TODO", "IN_PROGRESS", "DONE").contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_STATUS", "任务状态非法");
        }
        for (int i = 0; i < orderedTaskIds.size(); i++) {
            jdbc.update("UPDATE tasks SET sort_order = ?, status = ?, updated_at = NOW() WHERE id = ? AND project_id = ? AND deleted_at IS NULL",
                    (i + 1) * 1000, normalized, orderedTaskIds.get(i), projectId);
        }
        touchProject(projectId);
    }

    // ==================== Comment ====================

    public Map<String, Object> comments(UserEntity user, long taskId, int page, int pageSize) {
        var task = taskForMember(user, taskId);
        return page(jdbc.query("SELECT * FROM task_comments WHERE task_id = ? AND deleted_at IS NULL ORDER BY created_at ASC", commentMapper, task.id).stream()
                .map(comment -> commentDto(comment, user)).toList(), page, pageSize);
    }

    @Transactional
    public CommentEntity addComment(UserEntity user, long taskId, String content) {
        var task = taskForMember(user, taskId);
        var commentId = insert("INSERT INTO task_comments (task_id, project_id, user_id, content) VALUES (?, ?, ?, ?)", task.id, task.projectId, user.id, content);
        jdbc.update("UPDATE tasks SET updated_at = NOW() WHERE id = ?", task.id);
        var summary = content.length() > 50 ? content.substring(0, 50) + "..." : content;
        recordActivity(task.projectId, task.id, user.id, "COMMENT_ADDED", task.title, null, summary);
        return commentById(commentId);
    }

    @Transactional
    public void deleteComment(UserEntity user, long commentId) {
        var comment = findCommentById(commentId).filter(value -> value.deletedAt == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "评论不存在或无权限访问"));
        requireMember(user, comment.projectId);
        if (comment.userId != user.id && !isOwner(user, comment.projectId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无评论删除权限");
        }
        var task = activeTask(comment.taskId);
        var summary = comment.content.length() > 50 ? comment.content.substring(0, 50) + "..." : comment.content;
        recordActivity(comment.projectId, comment.taskId, user.id, "COMMENT_DELETED", task.title, summary, null);
        jdbc.update("UPDATE task_comments SET deleted_at = NOW(), updated_at = NOW() WHERE id = ?", commentId);
    }

    // ==================== Stats ====================

    public Map<String, Object> stats(UserEntity user, long projectId) {
        requireMember(user, projectId);
        var list = activeTasks(projectId);
        var total = list.size();
        var done = countStatus(list, "DONE");
        var overdue = list.stream().filter(task -> task.dueDate != null && task.dueDate.isBefore(LocalDate.now()) && !"DONE".equals(task.status)).count();
        var byStatus = List.of("TODO", "IN_PROGRESS", "DONE").stream().map(status -> Map.of("status", status, "count", countStatus(list, status))).toList();
        var byPriority = list.stream().collect(Collectors.groupingBy(task -> task.priority, Collectors.counting())).entrySet().stream()
                .map(entry -> Map.of("priority", entry.getKey(), "count", entry.getValue())).toList();
        var byAssignee = list.stream().collect(Collectors.groupingBy(task -> task.assigneeId == null ? 0L : task.assigneeId)).entrySet().stream().map(entry -> {
            var assignee = entry.getKey() == 0 ? null : userById(entry.getKey());
            return Map.of("userId", assignee == null ? "" : assignee.id, "userName", assignee == null ? "未分配" : assignee.name,
                    "total", entry.getValue().size(), "done", countStatus(entry.getValue(), "DONE"));
        }).toList();
        return mapOf("totalTasks", total, "todoTasks", countStatus(list, "TODO"), "inProgressTasks", countStatus(list, "IN_PROGRESS"),
                "doneTasks", done, "overdueTasks", overdue, "completionRate", total == 0 ? 0 : Math.round(done * 10000.0 / total) / 100.0,
                "byStatus", byStatus, "byPriority", byPriority, "byAssignee", byAssignee);
    }

    // ==================== DTO Helpers ====================

    public Map<String, Object> userDto(UserEntity user, boolean includeCreatedAt) {
        var dto = mapOf("id", user.id, "name", user.name, "email", user.email, "avatarUrl", user.avatarUrl);
        if (includeCreatedAt) {
            dto.put("createdAt", user.createdAt);
        }
        return dto;
    }

    public Map<String, Object> projectDto(ProjectEntity project, UserEntity user) {
        return mapOf("id", project.id, "name", project.name, "description", project.description, "status", project.status,
                "owner", smallUser(project.ownerId), "currentUserRole", activeMember(project.id, user.id).map(member -> member.role).orElse(null),
                "memberCount", members(project.id).size(), "taskSummary", taskSummary(project.id), "createdAt", project.createdAt, "updatedAt", project.updatedAt);
    }

    public Map<String, Object> memberDto(ProjectMemberEntity member) {
        return mapOf("id", member.id, "user", userDto(userById(member.userId), false), "role", member.role, "joinedAt", member.joinedAt);
    }

    public Map<String, Object> inviteDto(InviteEntity invite) {
        return mapOf("id", invite.id, "email", invite.email, "status", invite.status, "role", invite.role,
                "inviter", smallUser(invite.invitedBy), "createdAt", invite.createdAt, "acceptedAt", invite.handledAt);
    }

    public Map<String, Object> taskDto(TaskEntity task, UserEntity user, boolean detail) {
        var dto = mapOf("id", task.id, "projectId", task.projectId, "title", task.title, "description", task.description, "status", task.status,
                "priority", task.priority, "sortOrder", task.sortOrder, "assignee", task.assigneeId == null ? null : smallUser(task.assigneeId),
                "creator", smallUser(task.creatorId), "dueDate", task.dueDate,
                "commentCount", jdbc.queryForObject("SELECT COUNT(*) FROM task_comments WHERE task_id = ? AND deleted_at IS NULL", Long.class, task.id),
                "createdAt", task.createdAt, "updatedAt", task.updatedAt);
        if (detail) {
            dto.put("projectName", projectById(task.projectId).map(project -> project.name).orElse(""));
            dto.put("canEdit", canManageTask(user, task));
            dto.put("canDelete", canManageTask(user, task));
        }
        return dto;
    }

    public Map<String, Object> commentDto(CommentEntity comment, UserEntity user) {
        return mapOf("id", comment.id, "content", comment.content, "author", userDto(userById(comment.userId), false),
                "createdAt", comment.createdAt, "updatedAt", comment.updatedAt, "canDelete", comment.userId == user.id || isOwner(user, comment.projectId));
    }

    // ==================== Activity ====================

    public Map<String, Object> activities(UserEntity user, long projectId, int page, int pageSize) {
        requireMember(user, projectId);
        var list = jdbc.query("""
                SELECT * FROM task_activities
                WHERE project_id = ?
                ORDER BY created_at DESC
                """, activityMapper, projectId).stream().map(a -> activityDto(a)).toList();
        return page(list, page, pageSize);
    }

    public Map<String, Object> taskActivities(UserEntity user, long taskId, int page, int pageSize) {
        var task = taskForMember(user, taskId);
        var list = jdbc.query("""
                SELECT * FROM task_activities
                WHERE task_id = ?
                ORDER BY created_at DESC
                """, activityMapper, task.id).stream().map(a -> activityDto(a)).toList();
        return page(list, page, pageSize);
    }

    public Map<String, Object> activityDto(TaskActivityEntity activity) {
        return mapOf("id", activity.id, "projectId", activity.projectId, "taskId", activity.taskId,
                "user", smallUser(activity.userId), "type", activity.type, "content", activity.content,
                "oldValue", activity.oldValue, "newValue", activity.newValue, "createdAt", activity.createdAt);
    }

    // ==================== Private Helpers ====================

    private Map<String, Object> taskSummary(long projectId) {
        var list = activeTasks(projectId);
        var total = list.size();
        var done = countStatus(list, "DONE");
        return mapOf("total", total, "todo", countStatus(list, "TODO"), "inProgress", countStatus(list, "IN_PROGRESS"), "done", done,
                "completionRate", total == 0 ? 0 : Math.round(done * 10000.0 / total) / 100.0);
    }

    private long countStatus(List<TaskEntity> list, String status) {
        return list.stream().filter(task -> task.status.equals(status)).count();
    }

    private List<TaskEntity> activeTasks(long projectId) {
        return jdbc.query("SELECT * FROM tasks WHERE project_id = ? AND deleted_at IS NULL ORDER BY status ASC, sort_order ASC, created_at ASC", taskMapper, projectId);
    }

    private TaskEntity activeTask(long taskId) {
        return findTaskById(taskId).filter(task -> task.deletedAt == null)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在或无权限访问"));
    }

    private List<ProjectMemberEntity> members(long projectId) {
        return jdbc.query("SELECT * FROM project_members WHERE project_id = ? AND deleted_at IS NULL ORDER BY joined_at ASC", memberMapper, projectId);
    }

    private UserEntity userByEmail(String email) {
        return findUserByEmail(email).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
    }

    private void requireMember(UserEntity user, long projectId) {
        if (activeMember(projectId, user.id).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在或无权限访问");
        }
    }

    private void requireProjectMember(long projectId, long userId) {
        if (activeMember(projectId, userId).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "负责人必须是项目成员");
        }
    }

    private void requireOwner(UserEntity user, long projectId) {
        if (!isOwner(user, projectId)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无项目管理权限");
        }
    }

    private boolean isOwner(UserEntity user, long projectId) {
        return activeMember(projectId, user.id).map(member -> "OWNER".equals(member.role)).orElse(false);
    }

    private boolean canManageTask(UserEntity user, TaskEntity task) {
        return isOwner(user, task.projectId) || task.creatorId == user.id || Objects.equals(task.assigneeId, user.id);
    }

    private Optional<ProjectMemberEntity> activeMember(long projectId, long userId) {
        return queryOne("SELECT * FROM project_members WHERE project_id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1", memberMapper, projectId, userId);
    }

    private Map<String, Object> smallUser(long userId) {
        var user = userById(userId);
        return mapOf("id", user.id, "name", user.name, "email", user.email, "avatarUrl", user.avatarUrl);
    }

    private static String text(Map<String, Object> body, String key) {
        var value = optionalText(body, key);
        if (value == null || value.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", key + " 不能为空");
        }
        return value;
    }

    private static String optionalText(Map<String, Object> body, String key) {
        var value = body.get(key);
        return value == null || value.toString().isBlank() ? null : value.toString().trim();
    }

    private static Long longValue(Object value) {
        if (value == null || value.toString().isBlank()) {
            return null;
        }
        return ((Number) value).longValue();
    }

    private static LocalDate dateValue(String value) {
        return value == null ? null : LocalDate.parse(value);
    }

    private UserEntity userById(long userId) {
        return queryOne("SELECT * FROM users WHERE id = ? AND deleted_at IS NULL", userMapper, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在"));
    }

    private Optional<UserEntity> findUserByEmail(String email) {
        return queryOne("SELECT * FROM users WHERE email = ? AND deleted_at IS NULL", userMapper, email == null ? "" : email.toLowerCase(Locale.ROOT));
    }

    private Optional<ProjectEntity> projectById(long projectId) {
        return queryOne("SELECT * FROM projects WHERE id = ? AND deleted_at IS NULL", projectMapper, projectId);
    }

    private Optional<ProjectMemberEntity> findMemberById(long memberId) {
        return queryOne("SELECT * FROM project_members WHERE id = ?", memberMapper, memberId);
    }

    private ProjectMemberEntity memberById(long memberId) {
        return findMemberById(memberId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "成员不存在"));
    }

    private InviteEntity inviteById(long inviteId) {
        return queryOne("SELECT * FROM project_invites WHERE id = ?", inviteMapper, inviteId).orElseThrow();
    }

    private Optional<TaskEntity> findTaskById(long taskId) {
        return queryOne("SELECT * FROM tasks WHERE id = ?", taskMapper, taskId);
    }

    private Optional<CommentEntity> findCommentById(long commentId) {
        return queryOne("SELECT * FROM task_comments WHERE id = ?", commentMapper, commentId);
    }

    private CommentEntity commentById(long commentId) {
        return findCommentById(commentId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "评论不存在或无权限访问"));
    }

    private void touchProject(long projectId) {
        jdbc.update("UPDATE projects SET updated_at = NOW() WHERE id = ?", projectId);
    }

    private void recordActivity(long projectId, Long taskId, long userId, String type, String content, String oldValue, String newValue) {
        jdbc.update("INSERT INTO task_activities (project_id, task_id, user_id, type, content, old_value, new_value) VALUES (?, ?, ?, ?, ?, ?, ?)",
                projectId, taskId, userId, type, content, oldValue, newValue);
    }

    private long insert(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS);
            for (var index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            return statement;
        }, keyHolder);
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }

    private <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... args) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(sql, mapper, args));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    private static LocalDateTime time(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static Map<String, Object> page(List<Map<String, Object>> items, int page, int pageSize) {
        var safePage = Math.max(1, page);
        var safeSize = Math.max(1, Math.min(100, pageSize));
        var from = Math.min(items.size(), (safePage - 1) * safeSize);
        var to = Math.min(items.size(), from + safeSize);
        return mapOf("items", items.subList(from, to), "page", safePage, "pageSize", safeSize, "total", items.size(), "totalPages", (int) Math.ceil(items.size() / (double) safeSize));
    }

    private static Map<String, Object> mapOf(Object... values) {
        var map = new LinkedHashMap<String, Object>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(values[index].toString(), values[index + 1]);
        }
        return map;
    }
}
