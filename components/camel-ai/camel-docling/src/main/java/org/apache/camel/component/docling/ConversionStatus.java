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

package org.apache.camel.component.docling;

/**
 * Represents the status of an async document conversion task.
 */
public class ConversionStatus {

    public enum Status {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        UNKNOWN
    }

    private final String taskId;
    private final Status status;
    private final String result;
    private final String errorMessage;
    private final Integer progress;

    public ConversionStatus(String taskId, Status status) {
        this(taskId, status, null, null, null);
    }

    public ConversionStatus(String taskId, Status status, String result, String errorMessage, Integer progress) {
        this.taskId = taskId;
        this.status = status;
        this.result = result;
        this.errorMessage = errorMessage;
        this.progress = progress;
    }

    public String getTaskId() {
        return taskId;
    }

    public Status getStatus() {
        return status;
    }

    public String getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Integer getProgress() {
        return progress;
    }

    public boolean isCompleted() {
        return status == Status.COMPLETED;
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public boolean isInProgress() {
        return status == Status.IN_PROGRESS || status == Status.PENDING;
    }

    @Override
    public String toString() {
        return "ConversionStatus{" + "taskId='"
                + taskId + '\'' + ", status="
                + status + ", progress="
                + progress + ", errorMessage='"
                + errorMessage + '\'' + '}';
    }
}
