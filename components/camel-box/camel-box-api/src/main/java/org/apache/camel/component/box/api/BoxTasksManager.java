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

import java.util.Date;
import java.util.List;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxFile;
import com.box.sdk.BoxTask;
import com.box.sdk.BoxTaskAssignment;
import com.box.sdk.BoxUser;
import org.apache.camel.RuntimeCamelException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.box.api.BoxHelper.buildBoxApiErrorMessage;

/**
 * Provides operations to manage Box tasks.
 */
public class BoxTasksManager {

    private static final Logger LOG = LoggerFactory.getLogger(BoxTasksManager.class);

    /**
     * Box connection to authenticated user account.
     */
    private BoxAPIConnection boxConnection;

    /**
     * Create tasks manager to manage the tasks of Box connection's authenticated user.
     *
     * @param boxConnection - Box connection to authenticated user account.
     */
    public BoxTasksManager(BoxAPIConnection boxConnection) {
        this.boxConnection = boxConnection;
    }

    /**
     * Get a list of any tasks on file.
     *
     * @param  fileId - the id of file.
     * @return        The list of tasks on file.
     */
    public List<BoxTask.Info> getFileTasks(String fileId) {
        try {
            LOG.debug("Getting tasks of file(id={})", fileId);
            BoxHelper.notNull(fileId, BoxHelper.FILE_ID);

            BoxFile file = new BoxFile(boxConnection, fileId);

            return file.getTasks();

        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Add task to file.
     *
     * @param  fileId  - the id of file to add task to.
     * @param  action  - the action the task assignee will be prompted to do.
     * @param  dueAt   - - the day at which this task is due.
     * @param  message - an optional message to include with the task.
     * @return         The new task.
     */
    public BoxTask addFileTask(String fileId, BoxTask.Action action, Date dueAt, String message) {
        try {
            LOG.debug("Adding task to file(id={}) to '{}'", fileId, message);
            BoxHelper.notNull(fileId, BoxHelper.FILE_ID);
            BoxHelper.notNull(action, BoxHelper.ACTION);
            BoxHelper.notNull(dueAt, BoxHelper.DUE_AT);

            BoxFile fileToAddTaskOn = new BoxFile(boxConnection, fileId);
            return fileToAddTaskOn.addTask(action, message, dueAt).getResource();
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Delete task.
     *
     * @param taskId - the id of task to delete.
     */
    public void deleteTask(String taskId) {
        try {
            LOG.debug("Deleting task(id={})", taskId);
            BoxHelper.notNull(taskId, BoxHelper.TASK_ID);
            BoxTask task = new BoxTask(boxConnection, taskId);
            task.delete();
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Get task information.
     *
     * @param  taskId - the id of task.
     * @return        The task information.
     */
    public BoxTask.Info getTaskInfo(String taskId) {
        try {
            LOG.debug("Getting info for task(id={})", taskId);
            BoxHelper.notNull(taskId, BoxHelper.TASK_ID);

            BoxTask task = new BoxTask(boxConnection, taskId);

            return task.getInfo();
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Update task information.
     *
     * @param  taskId - the id of task.
     * @param  info   - the updated information
     * @return        The updated task.
     */
    public BoxTask updateTaskInfo(String taskId, BoxTask.Info info) {
        try {
            LOG.debug("Updating info for task(id={})", taskId);
            BoxHelper.notNull(taskId, BoxHelper.TASK_ID);
            BoxHelper.notNull(info, BoxHelper.INFO);

            BoxTask task = new BoxTask(boxConnection, taskId);
            task.updateInfo(info);

            return task;
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Get a list of any assignments for task.
     *
     * @param  taskId - the id of task.
     * @return        The list of assignments for task.
     */
    public List<BoxTaskAssignment.Info> getTaskAssignments(String taskId) {
        try {
            LOG.debug("Getting assignments for task(id={})", taskId);
            BoxHelper.notNull(taskId, BoxHelper.TASK_ID);

            BoxTask file = new BoxTask(boxConnection, taskId);

            return file.getAssignments();

        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Add assignment for task.
     *
     * @param  taskId   - the id of task to add assignment for.
     * @param  assignTo - the user to assign to task.
     * @return          The assigned task.
     */
    @SuppressWarnings("unused") // compiler for some reason thinks 'if (assignTo
                               // == null)' clause is dead code.
    public BoxTask addAssignmentToTask(String taskId, BoxUser assignTo) {
        try {
            BoxHelper.notNull(taskId, BoxHelper.TASK_ID);
            BoxHelper.notNull(assignTo, BoxHelper.ASSIGN_TO);

            LOG.debug("Assigning task(id={}) to user(id={})", taskId, assignTo.getID());

            BoxTask task = new BoxTask(boxConnection, taskId);
            task.addAssignment(assignTo);

            return task;
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    /**
     * Get task assignment information.
     *
     * @param  taskAssignmentId - the id of task assignment.
     * @return                  The task assignment information.
     */
    public BoxTaskAssignment.Info getTaskAssignmentInfo(String taskAssignmentId) {
        try {
            LOG.debug("Getting info for task(id={})", taskAssignmentId);
            BoxHelper.notNull(taskAssignmentId, BoxHelper.TASK_ASSIGNMENT_ID);

            BoxTaskAssignment taskAssignment = new BoxTaskAssignment(boxConnection, taskAssignmentId);

            return taskAssignment.getInfo();
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }

    // TODO Add this method when BoxTaskAssignment API fixed:
    // BoxTaskAssignment.update method currently
    // takes BoxTask.Info instead of BoxTaskAssignment.Info
    // /**
    // * Update task assignment information.
    // *
    // * @param taskAssignmentId
    // * - the id of task assignment.
    // * @param info
    // * - the updated information
    // * @return The updated task assignment.
    // */
    // public BoxTaskAssignment updateTaskAssignmentInfo(String
    // taskAssignmentId, BoxTaskAssignment.Info info) {
    // try {
    // LOG.debug("Updating info for task(id={})", taskAssignmentId);
    // if (taskAssignmentId == null) {
    // throw new IllegalArgumentException("Parameter 'taskAssignmentId' can not
    // be null");
    // }
    // if (info == null) {
    // throw new IllegalArgumentException("Parameter 'info' can not be null");
    // }
    //
    // BoxTaskAssignment taskAssignment = new BoxTaskAssignment(boxConnection,
    // taskAssignmentId);
    // taskAssignment.updateInfo(info);
    //
    // return taskAssignment;
    // } catch (BoxAPIException e) {
    // throw new RuntimeException(
    // String.format("Box API returned the error code %d\n\n%s",
    // e.getResponseCode(), e.getResponse()), e);
    // }
    // }

    /**
     * Delete task assignment.
     *
     * @param taskAssignmentId - the id of task assignment to delete.
     */
    public void deleteTaskAssignment(String taskAssignmentId) {
        try {
            LOG.debug("Deleting task(id={})", taskAssignmentId);
            BoxHelper.notNull(taskAssignmentId, BoxHelper.TASK_ASSIGNMENT_ID);

            BoxTaskAssignment taskAssignment = new BoxTaskAssignment(boxConnection, taskAssignmentId);
            taskAssignment.delete();
        } catch (BoxAPIException e) {
            throw new RuntimeCamelException(
                    buildBoxApiErrorMessage(e), e);
        }
    }
}
