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

import java.io.FileNotFoundException;
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
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link BoxTasksManager}
 * APIs.
 */
public class BoxTasksManagerIntegrationTest extends AbstractBoxTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(BoxTasksManagerIntegrationTest.class);
    private static final String PATH_PREFIX = BoxApiCollection.getCollection()
            .getApiName(BoxTasksManagerApiMethod.class).getName();
    private static final String CAMEL_TEST_FILE = "/CamelTestFile.txt";
    private static final String CAMEL_TEST_FILE_NAME = "CamelTestFile.txt";
    private static final String CAMEL_TEST_MESSAGE = "Camel Test Message";
    private static final long TEN_MINUTES_IN_MILLIS = 600000;

    private BoxTask testTask;

    @Ignore //needs https://community.box.com/t5/custom/page/page-id/BoxViewTicketDetail?ticket_id=1895413 to be solved
    @Test
    public void testAddAssignmentToTask() throws Exception {
        com.box.sdk.BoxTask result = null;

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.taskId", testTask.getID());
        // parameter type is com.box.sdk.BoxUser
        headers.put("CamelBox.assignTo", getCurrentUser());

        result = requestBodyAndHeaders("direct://ADDASSIGNMENTTOTASK", null, headers);

        assertNotNull("addAssignmentToTask result", result);
        LOG.debug("addAssignmentToTask: " + result);
    }

    @Test
    public void testAddFileTask() throws Exception {
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

            assertNotNull("addFileTask result", result);
            LOG.debug("addFileTask: " + result);
        } finally {
            if (result != null) {
                try {
                    result.delete();
                } catch (Throwable t) {
                }
            }
        }
    }

    @Test
    public void testDeleteTask() throws Exception {
        // using String message body for single parameter "taskId"
        requestBody("direct://DELETETASK", testTask.getID());

        List<BoxTask.Info> tasks = testFile.getTasks();
        boolean exists = tasks.size() != 0;
        assertEquals("deleteTask task still exists.", false, exists);
    }

    @Ignore // Receiving "not found" exception from Box API
    @Test
    public void testDeleteTaskAssignment() throws Exception {
        BoxTaskAssignment.Info info = testTask.addAssignment(getCurrentUser());

        // using String message body for single parameter "taskAssignmentId"
        requestBody("direct://DELETETASKASSIGNMENT", info.getID());

        List<BoxTaskAssignment.Info> assignments = testTask.getAssignments();
        boolean exists = assignments.size() != 0;
        assertEquals("deleteTaskAssignment assignment still exists.", false, exists);
    }

    @Test
    public void testGetFileTasks() throws Exception {
        // using String message body for single parameter "fileId"
        @SuppressWarnings("rawtypes")
        final java.util.List result = requestBody("direct://GETFILETASKS", testFile.getID());

        assertNotNull("getFileTasks result", result);
        LOG.debug("getFileTasks: " + result);
    }

    @Ignore
    @Test
    public void testGetTaskAssignmentInfo() throws Exception {
        BoxTaskAssignment.Info info = testTask.addAssignment(getCurrentUser());
        com.box.sdk.BoxTaskAssignment.Info result = null;

        try {
            // using String message body for single parameter "taskAssignmentId"
            result = requestBody("direct://GETTASKASSIGNMENTINFO", info.getID());

            assertNotNull("getTaskAssignmentInfo result", result);
            LOG.debug("getTaskAssignmentInfo: " + result);
        } finally {
            if (result != null) {
                try {
                    ((BoxTaskAssignment) result.getResource()).delete();
                } catch (Throwable t) {
                }
            }
        }
    }

    @Ignore //needs https://community.box.com/t5/custom/page/page-id/BoxViewTicketDetail?ticket_id=1895413 to be solved
    @Test
    public void testGetTaskAssignments() throws Exception {
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

        assertNotNull("getTaskAssignments result", result);
        LOG.debug("getTaskAssignments: " + result);
    }

    @Test
    public void testGetTaskInfo() throws Exception {
        // using String message body for single parameter "taskId"
        final com.box.sdk.BoxTask.Info result = requestBody("direct://GETTASKINFO", testTask.getID());

        assertNotNull("getTaskInfo result", result);
        LOG.debug("getTaskInfo: " + result);
    }

    @Ignore // No way to change BoxTask.Info parameters
    @Test
    public void testUpdateTaskInfo() throws Exception {
        BoxTask.Info info = testTask.getInfo();

        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelBox.taskId", testTask.getID());
        // parameter type is com.box.sdk.BoxTask.Info
        headers.put("CamelBox.info", info);

        final com.box.sdk.BoxTask result = requestBodyAndHeaders("direct://UPDATETASKINFO", null, headers);

        assertNotNull("updateTaskInfo result", result);
        LOG.debug("updateTaskInfo: " + result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
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

    @Before
    public void setupTest() throws Exception {
        createTestFile();
        createTestTask();
    }

    @After
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
        } catch (Throwable t) {
        }
        testTask = null;
    }

    private void createTestFile() throws FileNotFoundException {
        BoxFolder rootFolder = BoxFolder.getRootFolder(getConnection());
        InputStream stream = getClass().getResourceAsStream(CAMEL_TEST_FILE);
        testFile = rootFolder.uploadFile(stream, CAMEL_TEST_FILE_NAME).getResource();
    }

    private BoxUser getCurrentUser() {
        return BoxUser.getCurrentUser(getConnection());
    }
}
