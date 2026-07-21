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
package org.apache.camel.component.apicurioregistry;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Manage schemas and API artifacts (create, read, update, delete versions) stored in an Apicurio Registry.
 */
@UriEndpoint(firstVersion = "4.22.0", scheme = "apicurio-registry", title = "Apicurio Registry",
             syntax = "apicurio-registry:serverUrl", producerOnly = true,
             category = { Category.API }, headersClass = ApicurioRegistryConstants.class)
public class ApicurioRegistryEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    public static final String DEFAULT_GROUP_ID = "default";
    public static final String DEFAULT_CONTENT_TYPE = "application/json";

    @UriPath(description = "The base URL of the Apicurio Registry Core Registry v3 API,"
                           + " for example http://localhost:8080/apis/registry/v3")
    @Metadata(required = true)
    private final String serverUrl;

    @UriParam(defaultValue = DEFAULT_GROUP_ID)
    private String groupId = DEFAULT_GROUP_ID;
    @UriParam
    private String artifactId;
    @UriParam
    private ApicurioRegistryOperations operation;
    @UriParam
    private String artifactType;
    @UriParam
    private String version;
    @UriParam(defaultValue = DEFAULT_CONTENT_TYPE)
    private String contentType = DEFAULT_CONTENT_TYPE;

    public ApicurioRegistryEndpoint(String uri, String serverUrl, ApicurioRegistryComponent component) {
        super(uri, component);
        this.serverUrl = serverUrl;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ApicurioRegistryProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("The Apicurio Registry component does not support a consumer");
    }

    public ApicurioRegistryClient createClient() {
        return new ApicurioRegistryClient(serverUrl);
    }

    @Override
    public String getServiceUrl() {
        return serverUrl;
    }

    @Override
    public String getServiceProtocol() {
        return "http";
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getGroupId() {
        return groupId;
    }

    /**
     * The artifact group id to operate on. Defaults to <tt>default</tt>.
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    /**
     * The id of the artifact to operate on. Can be overridden per exchange with the
     * <tt>CamelApicurioRegistryArtifactId</tt> header.
     */
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public ApicurioRegistryOperations getOperation() {
        return operation;
    }

    /**
     * The producer operation to perform against the registry. Can be overridden per exchange with the
     * <tt>CamelApicurioRegistryOperation</tt> header.
     */
    public void setOperation(ApicurioRegistryOperations operation) {
        this.operation = operation;
    }

    public String getArtifactType() {
        return artifactType;
    }

    /**
     * The type of the artifact (for example AVRO, JSON, PROTOBUF, OPENAPI) used when creating an artifact. Can be
     * overridden per exchange with the <tt>CamelApicurioRegistryArtifactType</tt> header.
     */
    public void setArtifactType(String artifactType) {
        this.artifactType = artifactType;
    }

    public String getVersion() {
        return version;
    }

    /**
     * The version expression of the artifact to operate on (for example a version number or <tt>branch=latest</tt>).
     * Can be overridden per exchange with the <tt>CamelApicurioRegistryVersion</tt> header.
     */
    public void setVersion(String version) {
        this.version = version;
    }

    public String getContentType() {
        return contentType;
    }

    /**
     * The content type of the artifact content sent to the registry when creating or updating an artifact. Defaults to
     * <tt>application/json</tt>. Can be overridden per exchange with the <tt>CamelApicurioRegistryContentType</tt>
     * header.
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
