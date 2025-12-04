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

package org.apache.camel.component.dapr.operations;

import java.util.HashMap;
import java.util.Map;

import io.dapr.client.domain.ConfigurationItem;
import io.dapr.workflows.client.WorkflowInstanceStatus;
import org.apache.camel.component.dapr.DaprConstants;

public class DaprOperationResponse {
    private Object body;
    private Map<String, Object> headers = new HashMap<>();
    ;

    private DaprOperationResponse(final Object body) {
        this.body = body;
    }

    public DaprOperationResponse(final Object body, final Map<String, Object> headers) {
        this.body = body;
        this.headers = headers;
    }

    public static DaprOperationResponse create(final Object body) {
        return new DaprOperationResponse(body);
    }

    public static DaprOperationResponse create(final Object body, final Map<String, Object> headers) {
        return new DaprOperationResponse(body, headers);
    }

    public static DaprOperationResponse createFromConfig(final Map<String, ConfigurationItem> config) {
        Map<String, Object> responseHeaders = Map.of(DaprConstants.RAW_CONFIG_RESPONSE, config);
        Map<String, String> body = new HashMap<>();

        if (!config.isEmpty()) {
            config.forEach((k, v) -> {
                body.put(k, v.getValue());
            });
        }

        return create(body, responseHeaders);
    }

    public static DaprOperationResponse createFromWorkflowStatus(WorkflowInstanceStatus workflowStatus) {
        Map<String, Object> responseHeaders = new HashMap<>();

        responseHeaders.put(DaprConstants.WORKFLOW_NAME, workflowStatus.getName());
        responseHeaders.put(DaprConstants.WORKFLOW_CREATED_AT, workflowStatus.getCreatedAt());
        responseHeaders.put(DaprConstants.WORKFLOW_UPDATED_AT, workflowStatus.getLastUpdatedAt());
        responseHeaders.put(DaprConstants.WORKFLOW_SERIALIZED_INPUT, workflowStatus.getSerializedInput());
        responseHeaders.put(DaprConstants.WORKFLOW_SERIALIZED_OUTPUT, workflowStatus.getSerializedOutput());
        responseHeaders.put(DaprConstants.WORKFLOW_FAILURE_DETAILS, workflowStatus.getFailureDetails());
        responseHeaders.put(DaprConstants.IS_WORKFLOW_RUNNING, workflowStatus.isRunning());
        responseHeaders.put(DaprConstants.IS_WORKFLOW_COMPLETED, workflowStatus.isCompleted());

        return create(workflowStatus, responseHeaders);
    }

    public Object getBody() {
        return body;
    }

    private void setBody(Object body) {
        this.body = body;
    }

    public Map<String, Object> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }
}
