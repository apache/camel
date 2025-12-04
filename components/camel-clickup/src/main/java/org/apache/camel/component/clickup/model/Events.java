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

package org.apache.camel.component.clickup.model;

import java.util.Set;

import com.google.common.collect.Sets;

public final class Events {

    public static final String TASK_CREATED = "taskCreated";
    public static final String TASK_UPDATED = "taskUpdated";
    public static final String TASK_DELETED = "taskDeleted";
    public static final String TASK_PRIORITY_UPDATED = "taskPriorityUpdated";
    public static final String TASK_STATUS_UPDATED = "taskStatusUpdated";
    public static final String TASK_ASSIGNEE_UPDATED = "taskAssigneeUpdated";
    public static final String TASK_DUE_DATE_UPDATED = "taskDueDateUpdated";
    public static final String TASK_TAG_UPDATED = "taskTagUpdated";
    public static final String TASK_MOVED = "taskMoved";
    public static final String TASK_COMMENT_POSTED = "taskCommentPosted";
    public static final String TASK_COMMENT_UPDATED = "taskCommentUpdated";
    public static final String TASK_TIME_ESTIMATE_UPDATED = "taskTimeEstimateUpdated";
    public static final String TASK_TIME_TRACKED_UPDATED = "taskTimeTrackedUpdated";
    public static final String LIST_CREATED = "listCreated";
    public static final String LIST_UPDATED = "listUpdated";
    public static final String LIST_DELETED = "listDeleted";
    public static final String FOLDER_CREATED = "folderCreated";
    public static final String FOLDER_UPDATED = "folderUpdated";
    public static final String FOLDER_DELETED = "folderDeleted";
    public static final String SPACE_CREATED = "spaceCreated";
    public static final String SPACE_UPDATED = "spaceUpdated";
    public static final String SPACE_DELETED = "spaceDeleted";
    public static final String GOAL_CREATED = "goalCreated";
    public static final String GOAL_UPDATED = "goalUpdated";
    public static final String GOAL_DELETED = "goalDeleted";
    public static final String KEY_RESULT_CREATED = "keyResultCreated";
    public static final String KEY_RESULT_UPDATED = "keyResultUpdated";
    public static final String KEY_RESULT_DELETED = "keyResultDeleted";

    public static Set<String> supportedEvents() {
        return Set.of(TASK_TIME_TRACKED_UPDATED);
    }

    public static boolean areAllEventsSupported(Set<String> events) {
        return supportedEvents().containsAll(events);
    }

    public static Set<String> computeUnsupportedEvents(Set<String> events) {
        return Sets.difference(events, Events.supportedEvents());
    }
}
