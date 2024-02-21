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

package org.apache.camel.component.box.api;

import com.box.sdk.BoxAPIException;

public final class BoxHelper {
    public static final String BASE_ERROR_MESSAGE = "Box API returned the error code %d%n%n%s";
    public static final String MISSING_LISTENER = "Parameter 'listener' is null: will not listen for events";
    public static final String COLLABORATION_ID = "collaborationId";
    public static final String FOLDER_ID = "folderId";
    public static final String MESSAGE = "message";
    public static final String FILE_ID = "fileId";
    public static final String COMMENT_ID = "commentId";
    public static final String INFO = "info";
    public static final String PARENT_FOLDER_ID = "parentFolderId";
    public static final String CONTENT = "content";
    public static final String FILE_NAME = "fileName";
    public static final String FILE_CONTENT = "fileContent";
    public static final String VERSION = "version";
    public static final String METADATA = "metadata";
    public static final String SIZE = "size";
    public static final String DESTINATION_FOLDER_ID = "destinationFolderId";
    public static final String NEW_FOLDER_NAME = "newFolderName";
    public static final String ACCESS = "access";
    public static final String FOLDER_NAME = "folderName";
    public static final String PATH = "path";
    public static final String GROUP_ID = "groupId";
    public static final String GROUP_INFO = "groupInfo";
    public static final String USER_ID = "userId";
    public static final String GROUP_MEMBERSHIP_ID = "groupMembershipId";
    public static final String QUERY = "query";
    public static final String ACTION = "action";
    public static final String DUE_AT = "dueAt";
    public static final String TASK_ID = "taskId";
    public static final String ASSIGN_TO = "assignTo";
    public static final String TASK_ASSIGNMENT_ID = "taskAssignmentId";
    public static final String LOGIN = "login";
    public static final String NAME = "name";
    public static final String EMAIL = "email";
    public static final String EMAIL_ALIAS_ID = "emailAliasId";
    public static final String SOURCE_USER_ID = "sourceUserId";
    public static final String COLLABORATOR = "collaborator";
    public static final String ROLE = "role";

    private BoxHelper() {

    }

    static String buildBoxApiErrorMessage(BoxAPIException e) {
        return String.format(BASE_ERROR_MESSAGE, e.getResponseCode(), e.getResponse());
    }

    static <T> void notNull(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException("Parameter '" + name + "' cannot be null");
        }
    }
}
