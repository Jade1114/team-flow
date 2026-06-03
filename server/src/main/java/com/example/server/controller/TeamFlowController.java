package com.example.server.controller;

import com.example.server.common.ApiException;
import com.example.server.common.ApiResponse;
import com.example.server.service.TeamFlowService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@CrossOrigin
public class TeamFlowController {
    private final TeamFlowService service;

    public TeamFlowController(TeamFlowService service) {
        this.service = service;
    }

    @PostMapping("/api/auth/register")
    ResponseEntity<ApiResponse<Map<String, Object>>> register(@RequestBody Map<String, Object> body) {
        var user = service.register(text(body, "name"), text(body, "email"), text(body, "password"));
        return created(service.authPayload(user));
    }

    @PostMapping("/api/auth/login")
    ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.authPayload(service.login(text(body, "email"), text(body, "password"))));
    }

    @GetMapping("/api/auth/me")
    ApiResponse<Map<String, Object>> me(@RequestHeader(name = "Authorization", required = false) String authorization) {
        return ApiResponse.ok(service.userDto(service.currentUser(authorization), true));
    }

    @GetMapping("/api/projects")
    ApiResponse<Map<String, Object>> projects(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.projectList(service.currentUser(authorization), status, keyword, page, pageSize));
    }

    @PostMapping("/api/projects")
    ResponseEntity<ApiResponse<Map<String, Object>>> createProject(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody Map<String, Object> body) {
        var project = service.createProject(service.currentUser(authorization), text(body, "name"), optionalText(body, "description"));
        return created(service.projectDto(project, service.currentUser(authorization)));
    }

    @GetMapping("/api/projects/{projectId}")
    ApiResponse<Map<String, Object>> project(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long projectId) {
        return ApiResponse.ok(service.projectDto(service.projectForMember(service.currentUser(authorization), projectId), service.currentUser(authorization)));
    }

    @PutMapping("/api/projects/{projectId}")
    ApiResponse<Map<String, Object>> updateProject(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long projectId,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.projectDto(service.updateProject(service.currentUser(authorization), projectId, text(body, "name"), optionalText(body, "description")), service.currentUser(authorization)));
    }

    @PatchMapping("/api/projects/{projectId}/archive")
    ApiResponse<Map<String, Object>> archiveProject(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long projectId) {
        var project = service.archiveProject(service.currentUser(authorization), projectId);
        return ApiResponse.ok(Map.of("id", project.id, "status", project.status, "updatedAt", project.updatedAt));
    }

    @GetMapping("/api/projects/{projectId}/members")
    ApiResponse<Map<String, Object>> members(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long projectId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(Map.of("items", service.members(service.currentUser(authorization), projectId, keyword)));
    }

    @PostMapping("/api/projects/{projectId}/invites")
    ResponseEntity<ApiResponse<Map<String, Object>>> invite(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long projectId,
            @RequestBody Map<String, Object> body) {
        return created(service.invite(service.currentUser(authorization), projectId, text(body, "email"), optionalText(body, "role")));
    }

    @GetMapping("/api/projects/{projectId}/invites")
    ApiResponse<Map<String, Object>> invites(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long projectId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.invites(service.currentUser(authorization), projectId, status, page, pageSize));
    }

    @DeleteMapping("/api/projects/{projectId}/members/{memberId}")
    ApiResponse<Object> removeMember(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long projectId,
            @PathVariable long memberId) {
        service.removeMember(service.currentUser(authorization), projectId, memberId);
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
        return ApiResponse.ok(service.tasks(service.currentUser(authorization), projectId, status, assigneeId, priority, keyword, page, pageSize));
    }

    @GetMapping("/api/projects/{projectId}/board")
    ApiResponse<Map<String, Object>> board(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long projectId,
            @RequestParam(required = false) Long assigneeId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(service.board(service.currentUser(authorization), projectId, assigneeId, keyword));
    }

    @PostMapping("/api/projects/{projectId}/tasks")
    ResponseEntity<ApiResponse<Map<String, Object>>> createTask(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long projectId,
            @RequestBody Map<String, Object> body) {
        return created(service.taskDto(service.createTask(service.currentUser(authorization), projectId, body), service.currentUser(authorization), true));
    }

    @GetMapping("/api/tasks/{taskId}")
    ApiResponse<Map<String, Object>> task(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long taskId) {
        return ApiResponse.ok(service.taskDto(service.taskForMember(service.currentUser(authorization), taskId), service.currentUser(authorization), true));
    }

    @PutMapping("/api/tasks/{taskId}")
    ApiResponse<Map<String, Object>> updateTask(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long taskId,
            @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(service.taskDto(service.updateTask(service.currentUser(authorization), taskId, body), service.currentUser(authorization), true));
    }

    @PatchMapping("/api/tasks/{taskId}/move")
    ApiResponse<Map<String, Object>> moveTask(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long taskId,
            @RequestBody Map<String, Object> body) {
        var task = service.moveTask(service.currentUser(authorization), taskId, text(body, "targetStatus"));
        return ApiResponse.ok(Map.of("id", task.id, "status", task.status, "sortOrder", task.sortOrder, "updatedAt", task.updatedAt));
    }

    @PatchMapping("/api/tasks/{taskId}/status")
    ApiResponse<Map<String, Object>> updateTaskStatus(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long taskId,
            @RequestBody Map<String, Object> body) {
        var task = service.moveTask(service.currentUser(authorization), taskId, text(body, "status"));
        return ApiResponse.ok(Map.of("id", task.id, "status", task.status, "updatedAt", task.updatedAt));
    }

    @DeleteMapping("/api/tasks/{taskId}")
    ApiResponse<Object> deleteTask(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long taskId) {
        service.deleteTask(service.currentUser(authorization), taskId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/tasks/{taskId}/comments")
    ApiResponse<Map<String, Object>> comments(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long taskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return ApiResponse.ok(service.comments(service.currentUser(authorization), taskId, page, pageSize));
    }

    @PostMapping("/api/tasks/{taskId}/comments")
    ResponseEntity<ApiResponse<Map<String, Object>>> addComment(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long taskId,
            @RequestBody Map<String, Object> body) {
        return created(service.commentDto(service.addComment(service.currentUser(authorization), taskId, text(body, "content")), service.currentUser(authorization)));
    }

    @DeleteMapping("/api/comments/{commentId}")
    ApiResponse<Object> deleteComment(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long commentId) {
        service.deleteComment(service.currentUser(authorization), commentId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/projects/{projectId}/tasks/reorder")
    ApiResponse<Object> reorderTasks(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long projectId,
            @RequestBody Map<String, Object> body) {
        var rawList = body.get("orderedTaskIds");
        var orderedTaskIds = rawList == null ? List.<Long>of() : ((List<?>) rawList).stream().map(v -> ((Number) v).longValue()).toList();
        service.reorderTasks(service.currentUser(authorization), projectId, text(body, "status"), orderedTaskIds);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/projects/{projectId}/stats")
    ApiResponse<Map<String, Object>> stats(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long projectId) {
        return ApiResponse.ok(service.stats(service.currentUser(authorization), projectId));
    }

    @GetMapping("/api/projects/{projectId}/activities")
    ApiResponse<Map<String, Object>> projectActivities(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long projectId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.activities(service.currentUser(authorization), projectId, page, pageSize));
    }

    @GetMapping("/api/tasks/{taskId}/activities")
    ApiResponse<Map<String, Object>> taskActivities(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @PathVariable long taskId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(service.taskActivities(service.currentUser(authorization), taskId, page, pageSize));
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
