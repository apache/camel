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

/**
 * <a href="https://clickup.com/api/developer-portal/webhooks/">Webhooks</a>
 *
 * <p>
 * Task webhooks - taskCreated: Triggered when a new task is created. - taskUpdated: Triggered when a task is updated.
 * Adding an attachment to a task does not trigger the taskUpdated webhook. To trigger this webhook using an attachment,
 * you can upload an attachment to a task comment. - taskDeleted: Triggered when a task is deleted. -
 * taskPriorityUpdated: Triggered when the priority of a task is updated - taskStatusUpdated: Triggered when the status
 * of a task is updated. - taskAssigneeUpdated: Triggered when an assignee is added or removed from a task. -
 * taskDueDateUpdated: Triggered when the due date of a task is updated. - taskTagUpdated: Triggered when a tag is added
 * or removed from a task. - taskMoved: Triggered when a task is moved to a new List. - taskCommentPosted: Triggered
 * when a comment is added to a task. - taskCommentUpdated: Triggered when an existing comment on a task is updated. -
 * taskTimeEstimateUpdated: Triggered when the time estimate on a task is added or updated. - taskTimeTrackedUpdated:
 * Triggered when the time tracked on a task is added, updated, or deleted.
 * <p>
 * List webhooks - listCreated: Triggered when a new List is created. - listUpdated: Triggered when an existing List is
 * updated. - listDeleted: Triggered when a List is deleted.
 * <p>
 * Folder webhooks - folderCreated: Triggered when a new Folder is created. - folderUpdated: Triggered when an existing
 * Folder is updated. - folderDeleted: Triggered when a Folder is deleted.
 * <p>
 * Space webhooks - spaceCreated: Triggered when a new Space is created. - spaceUpdated: Triggered when an existing
 * Space is updated. - spaceDeleted: Triggered when a Space is deleted.
 * <p>
 * Goal and Target (key result) webhooks - goalCreated: Triggered when a new Goal is created. - goalUpdated: Triggered
 * when an existing Goal is updated. - goalDeleted: Triggered when a Goal is deleted. - keyResultCreated: Triggered when
 * a new Target is created. - keyResultUpdated: Triggered when an existing Target is updated. - keyResultDeleted:
 * Triggered when a Target is deleted.
 */
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
        return Set.of(
                TASK_TIME_TRACKED_UPDATED);
    }

    public static boolean areAllEventsSupported(Set<String> events) {
        return supportedEvents().containsAll(events);
    }

    public static Set<String> computeUnsupportedEvents(Set<String> events) {
        return Sets.difference(events, Events.supportedEvents());
    }

}
