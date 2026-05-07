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
package org.apache.camel.component.box;

import java.io.InputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxTask;
import com.box.sdk.BoxTask.Action;
import com.box.sdk.BoxTaskAssignment;
import com.box.sdk.BoxUser;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.box.api.BoxTasksManager;
import org.apache.camel.component.box.internal.BoxApiCollection;
import org.apache.camel.component.box.internal.BoxTasksManagerApiMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for {@link BoxTasksManager} APIs.
 */
@EnabledIf(value = "org.apache.camel.component.box.AbstractBoxITSupport#hasCredentials",
           disabledReason = "Box credentials were not provided")
public class BoxTasksManagerIT extends AbstractBoxITSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BoxTasksManagerIT.class);
    private static final String PATH_PREFIX = BoxApiCollection.getCollection()
            .getApiName(BoxTasksManagerApiMethod.class).getName();
    private static final String CAMEL_TEST_FILE = "/CamelTestFile.txt";
    private static final String CAMEL_TEST_FILE_NAME = "CamelTestFile.txt";
    private static final String CAMEL_TEST_MESSAGE = "Camel Test Message";
    private static final long TEN_MINUTES_IN_MILLIS = 600000;

    private BoxTask testTask;

    @Disabled
    //needs https://community.box.com/t5/custom/page/page-id/BoxViewTicketDetail?ticket_id=1895413 to be solved
    @Test
    public void testAddAssignmentToTask() {
        com.box.sdk.BoxTask result = null;

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.taskId", testTask.getID());
        // parameter type is com.box.sdk.BoxUser
        headers.put("CamelBox.assignTo", getCurrentUser());

        result = requestBodyAndHeaders("direct://ADDASSIGNMENTTOTASK", null, headers);

        assertNotNull(result, "addAssignmentToTask result");
        LOG.debug("addAssignmentToTask: {}", result);
    }

    @Test
    public void testAddFileTask() {
        com.box.sdk.BoxTask result = null;

        try {
            final Map<String, Object> headers = new HashMap<>();
            // parameter type is String
            headers.put("CamelBox.fileId", testFile.getID());
            // parameter type is com.box.sdk.BoxTask.Action
            headers.put("CamelBox.action", BoxTask.Action.REVIEW);
            // parameter type is java.util.Date
            Date now = new Date();
            Date dueAt = new Date(now.getTime() + TEN_MINUTES_IN_MILLIS);
            headers.put("CamelBox.dueAt", dueAt);
            // parameter type is String
            headers.put("CamelBox.message", CAMEL_TEST_MESSAGE);

            result = requestBodyAndHeaders("direct://ADDFILETASK", null, headers);

            assertNotNull(result, "addFileTask result");
            LOG.debug("addFileTask: {}", result);
        } finally {
            if (result != null) {
                try {
                    result.delete();
                } catch (Exception t) {
                }
            }
        }
    }

    @Test
    public void testDeleteTask() {
        // using String message body for single parameter "taskId"
        requestBody("direct://DELETETASK", testTask.getID());

        List<BoxTask.Info> tasks = testFile.getTasks();
        assertNotEquals(0, tasks.size(), "deleteTask task still exists.");
    }

    @Disabled // Receiving "not found" exception from Box API
    @Test
    public void testDeleteTaskAssignment() {
        BoxTaskAssignment.Info info = testTask.addAssignment(getCurrentUser());

        // using String message body for single parameter "taskAssignmentId"
        requestBody("direct://DELETETASKASSIGNMENT", info.getID());

        List<BoxTaskAssignment.Info> assignments = testTask.getAssignments();
        assertNotEquals(0, assignments.size(), "deleteTaskAssignment assignment still exists.");
    }

    @Test
    public void testGetFileTasks() {
        // using String message body for single parameter "fileId"
        @SuppressWarnings("rawtypes")
        final java.util.List result = requestBody("direct://GETFILETASKS", testFile.getID());

        assertNotNull(result, "getFileTasks result");
        LOG.debug("getFileTasks: {}", result);
    }

    @Disabled
    @Test
    public void testGetTaskAssignmentInfo() {
        BoxTaskAssignment.Info info = testTask.addAssignment(getCurrentUser());
        com.box.sdk.BoxTaskAssignment.Info result = null;

        try {
            // using String message body for single parameter "taskAssignmentId"
            result = requestBody("direct://GETTASKASSIGNMENTINFO", info.getID());

            assertNotNull(result, "getTaskAssignmentInfo result");
            LOG.debug("getTaskAssignmentInfo: {}", result);
        } finally {
            if (result != null) {
                try {
                    ((BoxTaskAssignment) result.getResource()).delete();
                } catch (Exception t) {
                }
            }
        }
    }

    @Disabled
    //needs https://community.box.com/t5/custom/page/page-id/BoxViewTicketDetail?ticket_id=1895413 to be solved
    @Test
    public void testGetTaskAssignments() {
        // using String message body for single parameter "taskId"

        //add assignment to task -> to be able to search for assignments
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.taskId", testTask.getID());
        // parameter type is com.box.sdk.BoxUser
        headers.put("CamelBox.assignTo", getCurrentUser());

        requestBodyAndHeaders("direct://ADDASSIGNMENTTOTASK", null, headers);

        @SuppressWarnings("rawtypes")
        final java.util.List result = requestBody("direct://GETTASKASSIGNMENTS", testTask.getID());

        assertNotNull(result, "getTaskAssignments result");
        LOG.debug("getTaskAssignments: {}", result);
    }

    @Test
    public void testGetTaskInfo() {
        // using String message body for single parameter "taskId"
        final com.box.sdk.BoxTask.Info result = requestBody("direct://GETTASKINFO", testTask.getID());

        assertNotNull(result, "getTaskInfo result");
        LOG.debug("getTaskInfo: {}", result);
    }

    @Disabled // No way to change BoxTask.Info parameters
    @Test
    public void testUpdateTaskInfo() {
        BoxTask.Info info = testTask.getInfo();

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.taskId", testTask.getID());
        // parameter type is com.box.sdk.BoxTask.Info
        headers.put("CamelBox.info", info);

        final com.box.sdk.BoxTask result = requestBodyAndHeaders("direct://UPDATETASKINFO", null, headers);

        assertNotNull(result, "updateTaskInfo result");
        LOG.debug("updateTaskInfo: {}", result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for addAssignmentToTask
                from("direct://ADDASSIGNMENTTOTASK").to("box://" + PATH_PREFIX + "/addAssignmentToTask");

                // test route for addFileTask
                from("direct://ADDFILETASK").to("box://" + PATH_PREFIX + "/addFileTask");

                // test route for deleteTask
                from("direct://DELETETASK").to("box://" + PATH_PREFIX + "/deleteTask?inBody=taskId");

                // test route for deleteTaskAssignment
                from("direct://DELETETASKASSIGNMENT")
                        .to("box://" + PATH_PREFIX + "/deleteTaskAssignment?inBody=taskAssignmentId");

                // test route for getFileTasks
                from("direct://GETFILETASKS").to("box://" + PATH_PREFIX + "/getFileTasks?inBody=fileId");

                // test route for getTaskAssignmentInfo
                from("direct://GETTASKASSIGNMENTINFO")
                        .to("box://" + PATH_PREFIX + "/getTaskAssignmentInfo?inBody=taskAssignmentId");

                // test route for getTaskAssignments
                from("direct://GETTASKASSIGNMENTS").to("box://" + PATH_PREFIX + "/getTaskAssignments?inBody=taskId");

                // test route for getTaskInfo
                from("direct://GETTASKINFO").to("box://" + PATH_PREFIX + "/getTaskInfo?inBody=taskId");

                // test route for updateTaskInfo
                from("direct://UPDATETASKINFO").to("box://" + PATH_PREFIX + "/updateTaskInfo");

            }
        };
    }

    @BeforeEach
    public void setupTest() throws Exception {
        createTestFile();
        createTestTask();
    }

    @AfterEach
    public void teardownTest() {
        deleteTestTask();
        deleteTestFile();
    }

    public BoxAPIConnection getConnection() {
        BoxEndpoint endpoint = (BoxEndpoint) context().getEndpoint("box://" + PATH_PREFIX + "/addAssignmentToTask");
        return endpoint.getBoxConnection();
    }

    private void createTestTask() {
        Date now = new Date();
        Date dueAt = new Date(now.getTime() + TEN_MINUTES_IN_MILLIS);
        testTask = testFile.addTask(Action.REVIEW, CAMEL_TEST_MESSAGE, dueAt).getResource();
    }

    private void deleteTestTask() {
        try {
            testTask.delete();
        } catch (Exception t) {
        }
        testTask = null;
    }

    private void createTestFile() {
        BoxFolder rootFolder = BoxFolder.getRootFolder(getConnection());
        InputStream stream = getClass().getResourceAsStream(CAMEL_TEST_FILE);
        testFile = rootFolder.uploadFile(stream, CAMEL_TEST_FILE_NAME).getResource();
    }

    private BoxUser getCurrentUser() {
        return BoxUser.getCurrentUser(getConnection());
    }
}
