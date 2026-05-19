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
package org.apache.camel.component.a2a.model;

import java.util.List;

/**
 * Request to list tasks with filtering and pagination.
 */
public class TaskListRequest {
    private String contextId;
    private List<String> status;
    private String statusTimestampAfter;
    private Boolean includeArtifacts;
    private Integer historyLength;
    private Integer pageSize;
    private String pageToken;

    public String getContextId() {
        return contextId;
    }

    public void setContextId(String contextId) {
        this.contextId = contextId;
    }

    public List<String> getStatus() {
        return status;
    }

    public void setStatus(List<String> status) {
        this.status = status;
    }

    public String getStatusTimestampAfter() {
        return statusTimestampAfter;
    }

    public void setStatusTimestampAfter(String statusTimestampAfter) {
        this.statusTimestampAfter = statusTimestampAfter;
    }

    public Boolean getIncludeArtifacts() {
        return includeArtifacts;
    }

    public void setIncludeArtifacts(Boolean includeArtifacts) {
        this.includeArtifacts = includeArtifacts;
    }

    public Integer getHistoryLength() {
        return historyLength;
    }

    public void setHistoryLength(Integer historyLength) {
        this.historyLength = historyLength;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public String getPageToken() {
        return pageToken;
    }

    public void setPageToken(String pageToken) {
        this.pageToken = pageToken;
    }
}
