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

import org.apache.camel.component.a2a.A2AConstants;

/**
 * A2A supported interface.
 */
public class SupportedInterface {
    private String url;
    private String protocolBinding;
    private String protocolVersion;

    public SupportedInterface() {
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getProtocolBinding() {
        return protocolBinding;
    }

    public void setProtocolBinding(String protocolBinding) {
        this.protocolBinding = normalizeProtocolBinding(protocolBinding);
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    private static String normalizeProtocolBinding(String protocolBinding) {
        if (A2AConstants.PROTOCOL_REST_ALIAS.equalsIgnoreCase(protocolBinding)
                || A2AConstants.PROTOCOL_REST.equalsIgnoreCase(protocolBinding)) {
            return A2AConstants.PROTOCOL_REST;
        }
        if (A2AConstants.PROTOCOL_JSONRPC_ALIAS.equalsIgnoreCase(protocolBinding)
                || A2AConstants.PROTOCOL_JSONRPC.equalsIgnoreCase(protocolBinding)) {
            return A2AConstants.PROTOCOL_JSONRPC;
        }
        return protocolBinding;
    }
}
