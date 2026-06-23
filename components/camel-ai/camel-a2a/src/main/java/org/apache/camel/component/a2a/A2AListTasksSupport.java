/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.a2a;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.model.ListTasksResponse;
import org.apache.camel.component.a2a.model.Task;
import org.apache.camel.component.a2a.model.TaskListRequest;
import org.apache.camel.component.a2a.model.TaskState;
import org.apache.camel.component.a2a.state.A2ATaskStore;
import org.apache.camel.component.a2a.util.A2AJsonMapper;

final class A2AListTasksSupport {

    private static final String LIST_PAGE_TOKEN_VERSION = "a2a-list-v1";

    private A2AListTasksSupport() {
    }

    static TaskListRequest fromQuery(Exchange exchange) {
        TaskListRequest request = new TaskListRequest();
        request.setContextId(parseQueryParam(exchange, "contextId", String.class));
        request.setPageSize(parseQueryParam(exchange, "pageSize", Integer.class));
        request.setPageToken(parseQueryParam(exchange, "pageToken", String.class));
        request.setIncludeArtifacts(parseQueryParam(exchange, "includeArtifacts", Boolean.class));
        request.setHistoryLength(parseQueryParam(exchange, "historyLength", Integer.class));
        request.setStatusTimestampAfter(parseQueryParam(exchange, "statusTimestampAfter", String.class));

        String status = parseQueryParam(exchange, "status", String.class);
        if (status != null && !status.isBlank()) {
            request.setStatus(splitStatusValues(status));
        }
        return request;
    }

    static ListTasksResponse process(
            TaskListRequest request, A2ATaskStore store, String owner, Predicate<String> canAccessTask) {
        int pageSize = resolvePageSize(request.getPageSize());
        List<TaskState> states = resolveTaskStates(request.getStatus());
        OffsetDateTime statusTimestampAfter = resolveStatusTimestampAfter(request.getStatusTimestampAfter());
        validateHistoryLength(request.getHistoryLength());

        String fingerprint = listPageFingerprint(request, states, statusTimestampAfter, owner);
        ListPageCursor cursor = resolveListPageCursor(request, states, statusTimestampAfter, store, canAccessTask, fingerprint);

        int totalSize = cursor.taskIds.size();
        int fromIndex = Math.min(cursor.offset, totalSize);
        int toIndex = Math.min(fromIndex + pageSize, totalSize);
        List<Task> page = cursor.taskIds.subList(fromIndex, toIndex).stream()
                .map(store::get)
                .filter(task -> task != null)
                .filter(task -> canAccessTask.test(task.id()))
                .filter(task -> matchesListFilters(task, request, states, statusTimestampAfter))
                .map(task -> applyListResponseShape(task, request))
                .toList();
        String nextPageToken = toIndex < totalSize
                ? encodePageToken(cursor.taskIds, toIndex, fingerprint)
                : null;
        return new ListTasksResponse(page, nextPageToken, pageSize, totalSize);
    }

    private static int resolvePageSize(Integer pageSize) {
        if (pageSize == null) {
            return 50;
        }
        if (pageSize <= 0) {
            throw new IllegalArgumentException("pageSize must be greater than zero");
        }
        return Math.min(pageSize, 100);
    }

    private static List<TaskState> resolveTaskStates(List<String> statusValues) {
        if (statusValues == null || statusValues.isEmpty()) {
            return null;
        }
        return statusValues.stream()
                .map(A2AListTasksSupport::resolveTaskState)
                .toList();
    }

    private static TaskState resolveTaskState(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("status must not contain blank values");
        }
        for (TaskState state : TaskState.values()) {
            if (state.name().equalsIgnoreCase(value) || state.getProtoName().equalsIgnoreCase(value)) {
                return state;
            }
        }
        throw new IllegalArgumentException("unknown task status: " + value);
    }

    private static OffsetDateTime resolveStatusTimestampAfter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("statusTimestampAfter must be an ISO-8601 offset date-time", e);
        }
    }

    private static void validateHistoryLength(Integer historyLength) {
        if (historyLength != null && historyLength < 0) {
            throw new IllegalArgumentException("historyLength must be greater than or equal to zero");
        }
    }

    private static ListPageCursor resolveListPageCursor(
            TaskListRequest request, List<TaskState> states, OffsetDateTime statusTimestampAfter, A2ATaskStore store,
            Predicate<String> canAccessTask, String fingerprint) {
        String pageToken = request.getPageToken();
        if (pageToken == null || pageToken.isBlank()) {
            return new ListPageCursor(
                    0,
                    buildListTaskSnapshot(request, states, statusTimestampAfter, store, canAccessTask));
        }

        try {
            byte[] json = Base64.getUrlDecoder().decode(pageToken);
            ListPageToken token = A2AJsonMapper.instance().readValue(json, ListPageToken.class);
            if (!LIST_PAGE_TOKEN_VERSION.equals(token.version)
                    || token.offset < 0
                    || token.taskIds == null
                    || !fingerprint.equals(token.fingerprint)) {
                throw new IllegalArgumentException("pageToken is invalid or does not match the ListTasks filters");
            }
            return new ListPageCursor(token.offset, List.copyOf(token.taskIds));
        } catch (Exception e) {
            throw new IllegalArgumentException("pageToken is invalid or does not match the ListTasks filters", e);
        }
    }

    private static List<String> buildListTaskSnapshot(
            TaskListRequest request, List<TaskState> states, OffsetDateTime statusTimestampAfter,
            A2ATaskStore store, Predicate<String> canAccessTask) {
        int candidateLimit = Math.max(store.keys().size(), resolvePageSize(request.getPageSize()));
        return store.list(request.getContextId(), states, candidateLimit).stream()
                .filter(task -> canAccessTask.test(task.id()))
                .filter(task -> matchesStatusTimestamp(task, statusTimestampAfter))
                .map(Task::id)
                .toList();
    }

    private static String encodePageToken(List<String> taskIds, int offset, String fingerprint) {
        try {
            ListPageToken token = new ListPageToken();
            token.version = LIST_PAGE_TOKEN_VERSION;
            token.offset = offset;
            token.taskIds = List.copyOf(taskIds);
            token.fingerprint = fingerprint;
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(A2AJsonMapper.instance().writeValueAsBytes(token));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encode ListTasks pageToken", e);
        }
    }

    private static String listPageFingerprint(
            TaskListRequest request, List<TaskState> states, OffsetDateTime statusTimestampAfter, String owner) {
        try {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("contextId", request.getContextId());
            values.put("status", states != null ? states.stream().map(TaskState::getProtoName).toList() : null);
            values.put("statusTimestampAfter", statusTimestampAfter != null ? statusTimestampAfter.toString() : null);
            values.put("owner", owner);
            return A2AJsonMapper.instance().writeValueAsString(values);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build ListTasks pageToken fingerprint", e);
        }
    }

    private static boolean matchesListFilters(
            Task task, TaskListRequest request, List<TaskState> states, OffsetDateTime statusTimestampAfter) {
        return (request.getContextId() == null || request.getContextId().equals(task.contextId()))
                && (states == null || states.isEmpty()
                        || task.status() != null && states.contains(task.status().state()))
                && matchesStatusTimestamp(task, statusTimestampAfter);
    }

    private static boolean matchesStatusTimestamp(Task task, OffsetDateTime statusTimestampAfter) {
        return statusTimestampAfter == null
                || task.status() != null
                        && task.status().timestamp() != null
                        && task.status().timestamp().isAfter(statusTimestampAfter);
    }

    private static Task applyListResponseShape(Task task, TaskListRequest request) {
        Task.Builder builder = Task.builder(task);
        if (Boolean.FALSE.equals(request.getIncludeArtifacts())) {
            builder.artifacts(null);
        }
        Integer historyLength = request.getHistoryLength();
        if (historyLength != null && task.history() != null) {
            int fromIndex = Math.max(0, task.history().size() - historyLength);
            builder.history(task.history().subList(fromIndex, task.history().size()));
        }
        return builder.build();
    }

    private static List<String> splitStatusValues(String status) {
        List<String> answer = new ArrayList<>();
        for (String value : status.split(",")) {
            String trimmed = value.trim();
            if (!trimmed.isEmpty()) {
                answer.add(trimmed);
            }
        }
        return answer;
    }

    @SuppressWarnings("unchecked")
    private static <T> T parseQueryParam(Exchange exchange, String param, Class<T> type) {
        String value = A2AHttpPathSupport.parseQueryParameter(exchange, param);
        if (value == null) {
            return null;
        }
        if (type == Integer.class) {
            try {
                return (T) Integer.valueOf(value);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(param + " must be an integer", e);
            }
        }
        if (type == Boolean.class) {
            return (T) Boolean.valueOf(value);
        }
        return (T) value;
    }

    private static final class ListPageCursor {
        private final int offset;
        private final List<String> taskIds;

        private ListPageCursor(int offset, List<String> taskIds) {
            this.offset = offset;
            this.taskIds = taskIds;
        }
    }

    private static final class ListPageToken {
        public String version;
        public int offset;
        public List<String> taskIds;
        public String fingerprint;
    }
}
