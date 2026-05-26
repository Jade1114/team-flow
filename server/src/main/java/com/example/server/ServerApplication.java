package com.example.server;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServerApplication.class, args);
	}

}

@RestController
@CrossOrigin
class TeamFlowController {
	private final TeamFlowStore store = new TeamFlowStore();

	@PostMapping("/api/auth/register")
	ResponseEntity<ApiResponse<Map<String, Object>>> register(@RequestBody Map<String, Object> body) {
		var user = store.register(text(body, "name"), text(body, "email"), text(body, "password"));
		return created(store.authPayload(user));
	}

	@PostMapping("/api/auth/login")
	ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
		return ApiResponse.ok(store.authPayload(store.login(text(body, "email"), text(body, "password"))));
	}

	@GetMapping("/api/auth/me")
	ApiResponse<Map<String, Object>> me(@RequestHeader(name = "Authorization", required = false) String authorization) {
		return ApiResponse.ok(store.userDto(store.currentUser(authorization), true));
	}

	@GetMapping("/api/projects")
	ApiResponse<Map<String, Object>> projects(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(store.projectList(store.currentUser(authorization), status, keyword, page, pageSize));
	}

	@PostMapping("/api/projects")
	ResponseEntity<ApiResponse<Map<String, Object>>> createProject(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@RequestBody Map<String, Object> body) {
		var project = store.createProject(store.currentUser(authorization), text(body, "name"), optionalText(body, "description"));
		return created(store.projectDto(project, store.currentUser(authorization)));
	}

	@GetMapping("/api/projects/{projectId}")
	ApiResponse<Map<String, Object>> project(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long projectId) {
		return ApiResponse.ok(store.projectDto(store.projectForMember(store.currentUser(authorization), projectId), store.currentUser(authorization)));
	}

	@PutMapping("/api/projects/{projectId}")
	ApiResponse<Map<String, Object>> updateProject(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long projectId,
			@RequestBody Map<String, Object> body) {
		return ApiResponse.ok(store.projectDto(store.updateProject(store.currentUser(authorization), projectId, text(body, "name"), optionalText(body, "description")), store.currentUser(authorization)));
	}

	@PatchMapping("/api/projects/{projectId}/archive")
	ApiResponse<Map<String, Object>> archiveProject(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long projectId) {
		var project = store.archiveProject(store.currentUser(authorization), projectId);
		return ApiResponse.ok(Map.of("id", project.id, "status", project.status, "updatedAt", project.updatedAt));
	}

	@GetMapping("/api/projects/{projectId}/members")
	ApiResponse<Map<String, Object>> members(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long projectId,
			@RequestParam(required = false) String keyword) {
		return ApiResponse.ok(Map.of("items", store.members(store.currentUser(authorization), projectId, keyword)));
	}

	@PostMapping("/api/projects/{projectId}/invites")
	ResponseEntity<ApiResponse<Map<String, Object>>> invite(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long projectId,
			@RequestBody Map<String, Object> body) {
		return created(store.invite(store.currentUser(authorization), projectId, text(body, "email"), optionalText(body, "role")));
	}

	@GetMapping("/api/projects/{projectId}/invites")
	ApiResponse<Map<String, Object>> invites(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long projectId,
			@RequestParam(required = false) String status,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(store.invites(store.currentUser(authorization), projectId, status, page, pageSize));
	}

	@DeleteMapping("/api/projects/{projectId}/members/{memberId}")
	ApiResponse<Object> removeMember(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long projectId,
			@PathVariable long memberId) {
		store.removeMember(store.currentUser(authorization), projectId, memberId);
		return ApiResponse.ok(null);
	}

	@GetMapping("/api/projects/{projectId}/tasks")
	ApiResponse<Map<String, Object>> tasks(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long projectId,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) Long assigneeId,
			@RequestParam(required = false) String priority,
			@RequestParam(required = false) String keyword,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "20") int pageSize) {
		return ApiResponse.ok(store.tasks(store.currentUser(authorization), projectId, status, assigneeId, priority, keyword, page, pageSize));
	}

	@GetMapping("/api/projects/{projectId}/board")
	ApiResponse<Map<String, Object>> board(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long projectId,
			@RequestParam(required = false) Long assigneeId,
			@RequestParam(required = false) String keyword) {
		return ApiResponse.ok(store.board(store.currentUser(authorization), projectId, assigneeId, keyword));
	}

	@PostMapping("/api/projects/{projectId}/tasks")
	ResponseEntity<ApiResponse<Map<String, Object>>> createTask(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long projectId,
			@RequestBody Map<String, Object> body) {
		return created(store.taskDto(store.createTask(store.currentUser(authorization), projectId, body), store.currentUser(authorization), true));
	}

	@GetMapping("/api/tasks/{taskId}")
	ApiResponse<Map<String, Object>> task(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long taskId) {
		return ApiResponse.ok(store.taskDto(store.taskForMember(store.currentUser(authorization), taskId), store.currentUser(authorization), true));
	}

	@PutMapping("/api/tasks/{taskId}")
	ApiResponse<Map<String, Object>> updateTask(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long taskId,
			@RequestBody Map<String, Object> body) {
		return ApiResponse.ok(store.taskDto(store.updateTask(store.currentUser(authorization), taskId, body), store.currentUser(authorization), true));
	}

	@PatchMapping("/api/tasks/{taskId}/move")
	ApiResponse<Map<String, Object>> moveTask(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long taskId,
			@RequestBody Map<String, Object> body) {
		var task = store.moveTask(store.currentUser(authorization), taskId, text(body, "targetStatus"));
		return ApiResponse.ok(Map.of("id", task.id, "status", task.status, "sortOrder", task.sortOrder, "updatedAt", task.updatedAt));
	}

	@PatchMapping("/api/tasks/{taskId}/status")
	ApiResponse<Map<String, Object>> updateTaskStatus(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long taskId,
			@RequestBody Map<String, Object> body) {
		var task = store.moveTask(store.currentUser(authorization), taskId, text(body, "status"));
		return ApiResponse.ok(Map.of("id", task.id, "status", task.status, "updatedAt", task.updatedAt));
	}

	@DeleteMapping("/api/tasks/{taskId}")
	ApiResponse<Object> deleteTask(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long taskId) {
		store.deleteTask(store.currentUser(authorization), taskId);
		return ApiResponse.ok(null);
	}

	@GetMapping("/api/tasks/{taskId}/comments")
	ApiResponse<Map<String, Object>> comments(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long taskId,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "50") int pageSize) {
		return ApiResponse.ok(store.comments(store.currentUser(authorization), taskId, page, pageSize));
	}

	@PostMapping("/api/tasks/{taskId}/comments")
	ResponseEntity<ApiResponse<Map<String, Object>>> addComment(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long taskId,
			@RequestBody Map<String, Object> body) {
		return created(store.commentDto(store.addComment(store.currentUser(authorization), taskId, text(body, "content")), store.currentUser(authorization)));
	}

	@DeleteMapping("/api/comments/{commentId}")
	ApiResponse<Object> deleteComment(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long commentId) {
		store.deleteComment(store.currentUser(authorization), commentId);
		return ApiResponse.ok(null);
	}

	@GetMapping("/api/projects/{projectId}/stats")
	ApiResponse<Map<String, Object>> stats(
			@RequestHeader(name = "Authorization", required = false) String authorization,
			@PathVariable long projectId) {
		return ApiResponse.ok(store.stats(store.currentUser(authorization), projectId));
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ApiResponse<Object>> handleApiException(ApiException error) {
		return ResponseEntity.status(error.status).body(ApiResponse.fail(error.code, error.getMessage()));
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ApiResponse<Object>> handleException(Exception error) {
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.fail("INTERNAL_ERROR", error.getMessage()));
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
		return value == null ? null : value.toString().trim();
	}

	private static <T> ResponseEntity<ApiResponse<T>> created(T data) {
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(data));
	}
}

class TeamFlowStore {
	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
	private final AtomicLong userIds = new AtomicLong(1);
	private final AtomicLong projectIds = new AtomicLong(101);
	private final AtomicLong memberIds = new AtomicLong(1001);
	private final AtomicLong inviteIds = new AtomicLong(501);
	private final AtomicLong taskIds = new AtomicLong(2001);
	private final AtomicLong commentIds = new AtomicLong(3001);
	private final Map<Long, UserEntity> users = new ConcurrentHashMap<>();
	private final Map<String, Long> userIdsByEmail = new ConcurrentHashMap<>();
	private final Map<String, Long> tokens = new ConcurrentHashMap<>();
	private final Map<Long, ProjectEntity> projects = new ConcurrentHashMap<>();
	private final Map<Long, ProjectMemberEntity> members = new ConcurrentHashMap<>();
	private final Map<Long, InviteEntity> invites = new ConcurrentHashMap<>();
	private final Map<Long, TaskEntity> tasks = new ConcurrentHashMap<>();
	private final Map<Long, CommentEntity> comments = new ConcurrentHashMap<>();

	TeamFlowStore() {
		var owner = register("张三", "zhangsan@example.com", "123456");
		var member = register("李四", "lisi@example.com", "123456");
		var project = createProject(owner, "官网改版", "完成首页、项目页和联系页改版，沉淀轻量协作流程。");
		invite(owner, project.id, member.email, "MEMBER");
		createTask(owner, project.id, new HashMap<>(Map.of(
				"title", "设计任务看板交互",
				"description", "完成三列看板、任务卡片摘要和状态拖拽。",
				"priority", "HIGH",
				"assigneeId", member.id,
				"dueDate", LocalDate.now().plusDays(5).toString())));
		var task = createTask(member, project.id, new HashMap<>(Map.of(
				"title", "梳理项目进度统计",
				"description", "统计总数、完成率、逾期任务和成员任务分布。",
				"priority", "MEDIUM",
				"assigneeId", owner.id,
				"dueDate", LocalDate.now().plusDays(2).toString())));
		moveTask(member, task.id, "IN_PROGRESS");
		addComment(owner, task.id, "统计面板先覆盖 MVP 指标，后续再扩展趋势图。");
	}

	UserEntity register(String name, String email, String password) {
		if (name == null || name.isBlank() || email == null || email.isBlank() || password == null || password.length() < 6) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "姓名、邮箱和至少 6 位密码均必填");
		}
		var normalized = email.toLowerCase(Locale.ROOT);
		if (userIdsByEmail.containsKey(normalized)) {
			throw new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "邮箱已注册");
		}
		var now = LocalDateTime.now();
		var user = new UserEntity(userIds.getAndIncrement(), name, normalized, encoder.encode(password), null, now, now);
		users.put(user.id, user);
		userIdsByEmail.put(normalized, user.id);
		return user;
	}

	UserEntity login(String email, String password) {
		var user = userByEmail(email);
		if (!encoder.matches(password, user.passwordHash)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "邮箱或密码错误");
		}
		return user;
	}

	Map<String, Object> authPayload(UserEntity user) {
		var token = UUID.randomUUID().toString();
		tokens.put(token, user.id);
		return Map.of("token", token, "user", userDto(user, false));
	}

	UserEntity currentUser(String authorization) {
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
		}
		var userId = tokens.get(authorization.substring(7));
		if (userId == null || !users.containsKey(userId)) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录状态无效");
		}
		return users.get(userId);
	}

	ProjectEntity createProject(UserEntity user, String name, String description) {
		if (name == null || name.isBlank()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "项目名称不能为空");
		}
		var now = LocalDateTime.now();
		var project = new ProjectEntity(projectIds.getAndIncrement(), name, description, "ACTIVE", user.id, now, now);
		projects.put(project.id, project);
		members.put(memberIds.get(), new ProjectMemberEntity(memberIds.getAndIncrement(), project.id, user.id, "OWNER", now, null));
		return project;
	}

	Map<String, Object> projectList(UserEntity user, String status, String keyword, int page, int pageSize) {
		var ownedProjectIds = members.values().stream()
				.filter(member -> member.userId == user.id && member.deletedAt == null)
				.map(member -> member.projectId)
				.collect(Collectors.toSet());
		var filtered = projects.values().stream()
				.filter(project -> ownedProjectIds.contains(project.id))
				.filter(project -> status == null || project.status.equalsIgnoreCase(status))
				.filter(project -> keyword == null || project.name.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))
				.sorted(Comparator.comparing((ProjectEntity project) -> project.updatedAt).reversed())
				.map(project -> {
					var dto = projectDto(project, user);
					dto.put("ownerName", users.get(project.ownerId).name);
					dto.put("taskSummary", taskSummary(project.id));
					return dto;
				})
				.toList();
		return page(filtered, page, pageSize);
	}

	ProjectEntity projectForMember(UserEntity user, long projectId) {
		var project = projects.get(projectId);
		if (project == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND", "项目不存在或无权限访问");
		}
		requireMember(user, projectId);
		return project;
	}

	ProjectEntity updateProject(UserEntity user, long projectId, String name, String description) {
		requireOwner(user, projectId);
		var project = projectForMember(user, projectId);
		project.name = name;
		project.description = description;
		project.updatedAt = LocalDateTime.now();
		return project;
	}

	ProjectEntity archiveProject(UserEntity user, long projectId) {
		requireOwner(user, projectId);
		var project = projectForMember(user, projectId);
		project.status = "ARCHIVED";
		project.updatedAt = LocalDateTime.now();
		return project;
	}

	List<Map<String, Object>> members(UserEntity user, long projectId, String keyword) {
		requireMember(user, projectId);
		return members.values().stream()
				.filter(member -> member.projectId == projectId && member.deletedAt == null)
				.map(this::memberDto)
				.filter(member -> keyword == null || member.toString().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))
				.toList();
	}

	Map<String, Object> invite(UserEntity user, long projectId, String email, String role) {
		requireOwner(user, projectId);
		var invitee = userByEmail(email);
		if (activeMember(projectId, invitee.id).isPresent()) {
			throw new ApiException(HttpStatus.CONFLICT, "MEMBER_ALREADY_EXISTS", "用户已是项目成员");
		}
		var now = LocalDateTime.now();
		var member = new ProjectMemberEntity(memberIds.getAndIncrement(), projectId, invitee.id, "MEMBER", now, null);
		var invite = new InviteEntity(inviteIds.getAndIncrement(), projectId, invitee.email, role == null ? "MEMBER" : role, "ACCEPTED", user.id, now, now);
		members.put(member.id, member);
		invites.put(invite.id, invite);
		return Map.of("invite", inviteDto(invite), "member", memberDto(member));
	}

	Map<String, Object> invites(UserEntity user, long projectId, String status, int page, int pageSize) {
		requireOwner(user, projectId);
		return page(invites.values().stream()
				.filter(invite -> invite.projectId == projectId)
				.filter(invite -> status == null || invite.status.equalsIgnoreCase(status))
				.sorted(Comparator.comparing((InviteEntity invite) -> invite.createdAt).reversed())
				.map(this::inviteDto)
				.toList(), page, pageSize);
	}

	void removeMember(UserEntity user, long projectId, long memberId) {
		requireOwner(user, projectId);
		var member = Optional.ofNullable(members.get(memberId)).filter(value -> value.projectId == projectId && value.deletedAt == null)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "成员不存在"));
		if ("OWNER".equals(member.role)) {
			throw new ApiException(HttpStatus.CONFLICT, "OWNER_CANNOT_BE_REMOVED", "不能移除项目所有者");
		}
		member.deletedAt = LocalDateTime.now();
	}

	Map<String, Object> tasks(UserEntity user, long projectId, String status, Long assigneeId, String priority, String keyword, int page, int pageSize) {
		requireMember(user, projectId);
		var filtered = activeTasks(projectId).stream()
				.filter(task -> status == null || task.status.equalsIgnoreCase(status))
				.filter(task -> assigneeId == null || Objects.equals(task.assigneeId, assigneeId))
				.filter(task -> priority == null || task.priority.equalsIgnoreCase(priority))
				.filter(task -> keyword == null || task.title.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))
				.map(task -> taskDto(task, user, false))
				.toList();
		return page(filtered, page, pageSize);
	}

	Map<String, Object> board(UserEntity user, long projectId, Long assigneeId, String keyword) {
		requireMember(user, projectId);
		var statuses = List.of(List.of("TODO", "待处理"), List.of("IN_PROGRESS", "进行中"), List.of("DONE", "已完成"));
		var columns = statuses.stream().map(status -> Map.of(
				"status", status.get(0),
				"title", status.get(1),
				"tasks", activeTasks(projectId).stream()
						.filter(task -> task.status.equals(status.get(0)))
						.filter(task -> assigneeId == null || Objects.equals(task.assigneeId, assigneeId))
						.filter(task -> keyword == null || task.title.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)))
						.map(task -> taskDto(task, user, false))
						.toList())).toList();
		return Map.of("columns", columns);
	}

	TaskEntity createTask(UserEntity user, long projectId, Map<String, Object> body) {
		requireMember(user, projectId);
		var assigneeId = longValue(body.get("assigneeId"));
		if (assigneeId != null) {
			requireProjectMember(projectId, assigneeId);
		}
		var now = LocalDateTime.now();
		var statusTasks = activeTasks(projectId).stream().filter(task -> "TODO".equals(task.status)).toList();
		var task = new TaskEntity(taskIds.getAndIncrement(), projectId, text(body, "title"), optionalText(body, "description"), "TODO",
				optionalText(body, "priority") == null ? "MEDIUM" : optionalText(body, "priority"), assigneeId, user.id,
				dateValue(optionalText(body, "dueDate")), (statusTasks.size() + 1) * 1000, null, now, now);
		tasks.put(task.id, task);
		projects.get(projectId).updatedAt = now;
		return task;
	}

	TaskEntity taskForMember(UserEntity user, long taskId) {
		var task = activeTask(taskId);
		requireMember(user, task.projectId);
		return task;
	}

	TaskEntity updateTask(UserEntity user, long taskId, Map<String, Object> body) {
		var task = taskForMember(user, taskId);
		if (!canManageTask(user, task)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无任务编辑权限");
		}
		task.title = text(body, "title");
		task.description = optionalText(body, "description");
		task.priority = optionalText(body, "priority") == null ? "MEDIUM" : optionalText(body, "priority");
		task.assigneeId = longValue(body.get("assigneeId"));
		if (task.assigneeId != null) {
			requireProjectMember(task.projectId, task.assigneeId);
		}
		task.dueDate = dateValue(optionalText(body, "dueDate"));
		task.updatedAt = LocalDateTime.now();
		return task;
	}

	TaskEntity moveTask(UserEntity user, long taskId, String status) {
		var task = taskForMember(user, taskId);
		var normalized = status.toUpperCase(Locale.ROOT);
		if (!List.of("TODO", "IN_PROGRESS", "DONE").contains(normalized)) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TASK_STATUS", "任务状态非法");
		}
		task.status = normalized;
		task.sortOrder = (int) activeTasks(task.projectId).stream().filter(item -> item.status.equals(normalized)).count() * 1000 + 1000;
		task.updatedAt = LocalDateTime.now();
		projects.get(task.projectId).updatedAt = task.updatedAt;
		return task;
	}

	void deleteTask(UserEntity user, long taskId) {
		var task = taskForMember(user, taskId);
		if (!canManageTask(user, task)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无任务删除权限");
		}
		task.deletedAt = LocalDateTime.now();
	}

	Map<String, Object> comments(UserEntity user, long taskId, int page, int pageSize) {
		var task = taskForMember(user, taskId);
		return page(comments.values().stream()
				.filter(comment -> comment.taskId == task.id && comment.deletedAt == null)
				.sorted(Comparator.comparing(comment -> comment.createdAt))
				.map(comment -> commentDto(comment, user))
				.toList(), page, pageSize);
	}

	CommentEntity addComment(UserEntity user, long taskId, String content) {
		var task = taskForMember(user, taskId);
		var now = LocalDateTime.now();
		var comment = new CommentEntity(commentIds.getAndIncrement(), task.id, task.projectId, user.id, content, null, now, now);
		comments.put(comment.id, comment);
		task.updatedAt = now;
		return comment;
	}

	void deleteComment(UserEntity user, long commentId) {
		var comment = Optional.ofNullable(comments.get(commentId)).filter(value -> value.deletedAt == null)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "COMMENT_NOT_FOUND", "评论不存在或无权限访问"));
		requireMember(user, comment.projectId);
		if (comment.userId != user.id && !isOwner(user, comment.projectId)) {
			throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无评论删除权限");
		}
		comment.deletedAt = LocalDateTime.now();
	}

	Map<String, Object> stats(UserEntity user, long projectId) {
		requireMember(user, projectId);
		var list = activeTasks(projectId);
		var total = list.size();
		var done = countStatus(list, "DONE");
		var todo = countStatus(list, "TODO");
		var progress = countStatus(list, "IN_PROGRESS");
		var overdue = list.stream().filter(task -> task.dueDate != null && task.dueDate.isBefore(LocalDate.now()) && !"DONE".equals(task.status)).count();
		var byStatus = List.of("TODO", "IN_PROGRESS", "DONE").stream().map(status -> Map.of("status", status, "count", countStatus(list, status))).toList();
		var byPriority = list.stream().collect(Collectors.groupingBy(task -> task.priority, Collectors.counting())).entrySet().stream()
				.map(entry -> Map.of("priority", entry.getKey(), "count", entry.getValue())).toList();
		var byAssignee = list.stream().collect(Collectors.groupingBy(task -> task.assigneeId == null ? 0L : task.assigneeId)).entrySet().stream()
				.map(entry -> {
					var assignee = entry.getKey() == 0 ? null : users.get(entry.getKey());
					return Map.of("userId", assignee == null ? "" : assignee.id, "userName", assignee == null ? "未分配" : assignee.name,
							"total", entry.getValue().size(), "done", countStatus(entry.getValue(), "DONE"));
				}).toList();
		return mapOf("totalTasks", total, "todoTasks", todo, "inProgressTasks", progress, "doneTasks", done, "overdueTasks", overdue,
				"completionRate", total == 0 ? 0 : Math.round(done * 10000.0 / total) / 100.0, "byStatus", byStatus, "byPriority", byPriority, "byAssignee", byAssignee);
	}

	Map<String, Object> userDto(UserEntity user, boolean includeCreatedAt) {
		var dto = mapOf("id", user.id, "name", user.name, "email", user.email, "avatarUrl", user.avatarUrl);
		if (includeCreatedAt) {
			dto.put("createdAt", user.createdAt);
		}
		return dto;
	}

	Map<String, Object> projectDto(ProjectEntity project, UserEntity user) {
		return mapOf("id", project.id, "name", project.name, "description", project.description, "status", project.status,
				"owner", smallUser(project.ownerId), "currentUserRole", activeMember(project.id, user.id).map(member -> member.role).orElse(null),
				"memberCount", members(project.id).size(), "taskSummary", taskSummary(project.id), "createdAt", project.createdAt, "updatedAt", project.updatedAt);
	}

	Map<String, Object> memberDto(ProjectMemberEntity member) {
		return mapOf("id", member.id, "user", userDto(users.get(member.userId), false), "role", member.role, "joinedAt", member.joinedAt);
	}

	Map<String, Object> inviteDto(InviteEntity invite) {
		return mapOf("id", invite.id, "email", invite.email, "status", invite.status, "role", invite.role,
				"inviter", smallUser(invite.invitedBy), "createdAt", invite.createdAt, "acceptedAt", invite.handledAt);
	}

	Map<String, Object> taskDto(TaskEntity task, UserEntity user, boolean detail) {
		var dto = mapOf("id", task.id, "projectId", task.projectId, "title", task.title, "description", task.description, "status", task.status,
				"priority", task.priority, "sortOrder", task.sortOrder, "assignee", task.assigneeId == null ? null : smallUser(task.assigneeId),
				"creator", smallUser(task.creatorId), "dueDate", task.dueDate, "commentCount", comments.values().stream().filter(comment -> comment.taskId == task.id && comment.deletedAt == null).count(),
				"createdAt", task.createdAt, "updatedAt", task.updatedAt);
		if (detail) {
			dto.put("projectName", projects.get(task.projectId).name);
			dto.put("canEdit", canManageTask(user, task));
			dto.put("canDelete", canManageTask(user, task));
		}
		return dto;
	}

	Map<String, Object> commentDto(CommentEntity comment, UserEntity user) {
		return mapOf("id", comment.id, "content", comment.content, "author", userDto(users.get(comment.userId), false),
				"createdAt", comment.createdAt, "updatedAt", comment.updatedAt, "canDelete", comment.userId == user.id || isOwner(user, comment.projectId));
	}

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
		return tasks.values().stream()
				.filter(task -> task.projectId == projectId && task.deletedAt == null)
				.sorted(Comparator.comparing((TaskEntity task) -> task.status).thenComparing(task -> task.sortOrder).thenComparing(task -> task.createdAt))
				.toList();
	}

	private TaskEntity activeTask(long taskId) {
		return Optional.ofNullable(tasks.get(taskId)).filter(task -> task.deletedAt == null)
				.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "任务不存在或无权限访问"));
	}

	private List<ProjectMemberEntity> members(long projectId) {
		return members.values().stream().filter(member -> member.projectId == projectId && member.deletedAt == null).toList();
	}

	private UserEntity userByEmail(String email) {
		var id = userIdsByEmail.get(email == null ? "" : email.toLowerCase(Locale.ROOT));
		if (id == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "用户不存在");
		}
		return users.get(id);
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
		return members.values().stream().filter(member -> member.projectId == projectId && member.userId == userId && member.deletedAt == null).findFirst();
	}

	private Map<String, Object> smallUser(long userId) {
		var user = users.get(userId);
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

record ApiResponse<T>(boolean success, T data, String message, String code, Object details) {
	static <T> ApiResponse<T> ok(T data) {
		return new ApiResponse<>(true, data, "OK", null, null);
	}

	static ApiResponse<Object> fail(String code, String message) {
		return new ApiResponse<>(false, null, message, code, null);
	}
}

class ApiException extends RuntimeException {
	final HttpStatus status;
	final String code;

	ApiException(HttpStatus status, String code, String message) {
		super(message);
		this.status = status;
		this.code = code;
	}
}

class UserEntity {
	long id;
	String name;
	String email;
	String passwordHash;
	String avatarUrl;
	LocalDateTime createdAt;
	LocalDateTime updatedAt;

	UserEntity(long id, String name, String email, String passwordHash, String avatarUrl, LocalDateTime createdAt, LocalDateTime updatedAt) {
		this.id = id;
		this.name = name;
		this.email = email;
		this.passwordHash = passwordHash;
		this.avatarUrl = avatarUrl;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}
}

class ProjectEntity {
	long id;
	String name;
	String description;
	String status;
	long ownerId;
	LocalDateTime createdAt;
	LocalDateTime updatedAt;

	ProjectEntity(long id, String name, String description, String status, long ownerId, LocalDateTime createdAt, LocalDateTime updatedAt) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.status = status;
		this.ownerId = ownerId;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}
}

class ProjectMemberEntity {
	long id;
	long projectId;
	long userId;
	String role;
	LocalDateTime joinedAt;
	LocalDateTime deletedAt;

	ProjectMemberEntity(long id, long projectId, long userId, String role, LocalDateTime joinedAt, LocalDateTime deletedAt) {
		this.id = id;
		this.projectId = projectId;
		this.userId = userId;
		this.role = role;
		this.joinedAt = joinedAt;
		this.deletedAt = deletedAt;
	}
}

class InviteEntity {
	long id;
	long projectId;
	String email;
	String role;
	String status;
	long invitedBy;
	LocalDateTime createdAt;
	LocalDateTime handledAt;

	InviteEntity(long id, long projectId, String email, String role, String status, long invitedBy, LocalDateTime createdAt, LocalDateTime handledAt) {
		this.id = id;
		this.projectId = projectId;
		this.email = email;
		this.role = role;
		this.status = status;
		this.invitedBy = invitedBy;
		this.createdAt = createdAt;
		this.handledAt = handledAt;
	}
}

class TaskEntity {
	long id;
	long projectId;
	String title;
	String description;
	String status;
	String priority;
	Long assigneeId;
	long creatorId;
	LocalDate dueDate;
	int sortOrder;
	LocalDateTime deletedAt;
	LocalDateTime createdAt;
	LocalDateTime updatedAt;

	TaskEntity(long id, long projectId, String title, String description, String status, String priority, Long assigneeId, long creatorId, LocalDate dueDate, int sortOrder, LocalDateTime deletedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
		this.id = id;
		this.projectId = projectId;
		this.title = title;
		this.description = description;
		this.status = status;
		this.priority = priority;
		this.assigneeId = assigneeId;
		this.creatorId = creatorId;
		this.dueDate = dueDate;
		this.sortOrder = sortOrder;
		this.deletedAt = deletedAt;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}
}

class CommentEntity {
	long id;
	long taskId;
	long projectId;
	long userId;
	String content;
	LocalDateTime deletedAt;
	LocalDateTime createdAt;
	LocalDateTime updatedAt;

	CommentEntity(long id, long taskId, long projectId, long userId, String content, LocalDateTime deletedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
		this.id = id;
		this.taskId = taskId;
		this.projectId = projectId;
		this.userId = userId;
		this.content = content;
		this.deletedAt = deletedAt;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}
}
