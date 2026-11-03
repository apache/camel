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

/**
 * The document and framing shape of an agent family, as captured from real agents: a cppagent 2.8 agent serving the 2.5
 * schema, and Mazak 1.4 and 1.6 agents serving the 1.3 and 1.5 schemas with the vendor namespace.
 */
enum StubDialect {

    /** cppagent 2.8.0.4: multipart/mixed, Content-Type and Content-Length part headers, OUT_OF_RANGE with 400. */
    V2_5("agents/v25-probe.xml", "agents/v28-assets.xml", "2.5", "2.8.0.4", "multipart/mixed", "application/xml",
         "Content-Type",
         "Content-Length", 400, null),
    /** Mazak agent 1.4.0.12: multipart/x-mixed-replace, lower-case part header names, OUT_OF_RANGE with 200. */
    V1_3("agents/v13-probe.xml", "agents/v13-assets.xml", "1.3", "1.4.0.12", "multipart/x-mixed-replace", "text/xml",
         "Content-type",
         "Content-length", 200, "urn:mazakusa.com:MazakStreams:1.3"),
    /**
     * Mazak agent 1.6.0.6, which holds no assets. Its vendor namespace and the status of its OUT_OF_RANGE answer were
     * not captured; they are assumed to follow the 1.4 agent.
     */
    V1_5("agents/v15-probe.xml", null, "1.5", "1.6.0.6", "multipart/x-mixed-replace", "text/xml", "Content-type",
         "Content-length", 200, "urn:mazakusa.com:MazakStreams:1.5");

    final String probeResource;
    /** The captured MTConnectAssets document that seeds the asset store, or null for none. */
    final String assetsResource;
    final String schema;
    final String version;
    final String multipartType;
    final String contentType;
    final String partContentType;
    final String partContentLength;
    final int outOfRangeStatus;
    final String vendorNamespace;

    StubDialect(String probeResource, String assetsResource, String schema, String version, String multipartType,
                String contentType,
                String partContentType, String partContentLength, int outOfRangeStatus, String vendorNamespace) {
        this.probeResource = probeResource;
        this.assetsResource = assetsResource;
        this.schema = schema;
        this.version = version;
        this.multipartType = multipartType;
        this.contentType = contentType;
        this.partContentType = partContentType;
        this.partContentLength = partContentLength;
        this.outOfRangeStatus = outOfRangeStatus;
        this.vendorNamespace = vendorNamespace;
    }

    boolean modern() {
        return this == V2_5;
    }
}
