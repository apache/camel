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
package org.apache.camel.component.mtconnect;

import java.util.List;

/**
 * The agent answered a producer request with an MTConnectError document, such as OUT_OF_RANGE for a sequence it no
 * longer buffers or NO_DEVICE for an unknown device, whatever the HTTP status. It carries the agent's error codes and
 * texts in document order.
 */
public class MTConnectErrorException extends Exception {

    private static final long serialVersionUID = 1L;

    private final String agentUrl;
    private final List<String> errorCodes;
    private final List<String> errorTexts;

    MTConnectErrorException(String agentUrl, AgentDocument.Error error) {
        super("The MTConnect agent " + agentUrl + " answered with an error: " + error.describe());
        this.agentUrl = agentUrl;
        this.errorCodes = error.errors().stream().map(AgentDocument.AgentError::code).toList();
        this.errorTexts = error.errors().stream().map(AgentDocument.AgentError::message).toList();
    }

    /** The agent address of the endpoint. */
    public String getAgentUrl() {
        return agentUrl;
    }

    /** The errorCode attribute of each Error element, such as OUT_OF_RANGE. */
    public List<String> getErrorCodes() {
        return errorCodes;
    }

    /** The text of each Error element, in the order of {@link #getErrorCodes()}. */
    public List<String> getErrorTexts() {
        return errorTexts;
    }

    /** Whether the agent reported the given error code. */
    public boolean hasErrorCode(String code) {
        return errorCodes.contains(code);
    }
}
