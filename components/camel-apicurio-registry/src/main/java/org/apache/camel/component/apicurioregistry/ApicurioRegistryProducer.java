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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class ApicurioRegistryProducer extends DefaultProducer {

    private final ApicurioRegistryEndpoint endpoint;
    private ApicurioRegistryClient client;

    public ApicurioRegistryProducer(ApicurioRegistryEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    ApicurioRegistryProducer(ApicurioRegistryEndpoint endpoint, ApicurioRegistryClient client) {
        super(endpoint);
        this.endpoint = endpoint;
        this.client = client;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Message in = exchange.getIn();
        ApicurioRegistryOperations operation = resolveOperation(in);

        switch (operation) {
            case createArtifact:
                createArtifact(in);
                break;
            case updateArtifact:
                updateArtifact(in);
                break;
            case getArtifact:
                getArtifact(in);
                break;
            case getArtifactVersion:
                getArtifactVersion(in);
                break;
            case listArtifactVersions:
                listArtifactVersions(in);
                break;
            case deleteArtifact:
                deleteArtifact(in);
                break;
            case deleteArtifactVersion:
                deleteArtifactVersion(in);
                break;
            default:
                throw new IllegalArgumentException("Unsupported operation: " + operation);
        }
    }

    private void createArtifact(Message in) throws Exception {
        String groupId = groupId(in);
        String artifactId = requireArtifactId(in);
        String artifactType = header(in, ApicurioRegistryConstants.ARTIFACT_TYPE, endpoint.getArtifactType());
        String contentType = header(in, ApicurioRegistryConstants.CONTENT_TYPE, endpoint.getContentType());
        String content = in.getMandatoryBody(String.class);

        Object result = client.createArtifact(groupId, artifactId, artifactType, content, contentType);
        in.setBody(result);
    }

    private void updateArtifact(Message in) throws Exception {
        String groupId = groupId(in);
        String artifactId = requireArtifactId(in);
        String contentType = header(in, ApicurioRegistryConstants.CONTENT_TYPE, endpoint.getContentType());
        String content = in.getMandatoryBody(String.class);

        Object result = client.addArtifactVersion(groupId, artifactId, content, contentType);
        in.setBody(result);
    }

    private void getArtifact(Message in) {
        String groupId = groupId(in);
        String artifactId = requireArtifactId(in);

        String content = client.getLatestArtifactContent(groupId, artifactId);
        in.setBody(content, String.class);
    }

    private void getArtifactVersion(Message in) {
        String groupId = groupId(in);
        String artifactId = requireArtifactId(in);
        String version = requireVersion(in);

        String content = client.getArtifactVersionContent(groupId, artifactId, version);
        in.setBody(content, String.class);
    }

    private void listArtifactVersions(Message in) {
        String groupId = groupId(in);
        String artifactId = requireArtifactId(in);

        in.setBody(client.listArtifactVersions(groupId, artifactId));
    }

    private void deleteArtifact(Message in) {
        String groupId = groupId(in);
        String artifactId = requireArtifactId(in);

        client.deleteArtifact(groupId, artifactId);
    }

    private void deleteArtifactVersion(Message in) {
        String groupId = groupId(in);
        String artifactId = requireArtifactId(in);
        String version = requireVersion(in);

        client.deleteArtifactVersion(groupId, artifactId, version);
    }

    private ApicurioRegistryOperations resolveOperation(Message in) {
        ApicurioRegistryOperations operation
                = in.getHeader(ApicurioRegistryConstants.OPERATION, endpoint.getOperation(),
                        ApicurioRegistryOperations.class);
        if (operation == null) {
            throw new IllegalArgumentException(
                    "No operation specified. Set the operation on the endpoint or the "
                                               + ApicurioRegistryConstants.OPERATION + " header");
        }
        return operation;
    }

    private String groupId(Message in) {
        return header(in, ApicurioRegistryConstants.GROUP_ID, endpoint.getGroupId());
    }

    private String requireArtifactId(Message in) {
        String artifactId = header(in, ApicurioRegistryConstants.ARTIFACT_ID, endpoint.getArtifactId());
        if (ObjectHelper.isEmpty(artifactId)) {
            throw new IllegalArgumentException(
                    "No artifactId specified. Set the artifactId on the endpoint or the "
                                               + ApicurioRegistryConstants.ARTIFACT_ID + " header");
        }
        return artifactId;
    }

    private String requireVersion(Message in) {
        String version = header(in, ApicurioRegistryConstants.VERSION, endpoint.getVersion());
        if (ObjectHelper.isEmpty(version)) {
            throw new IllegalArgumentException(
                    "No version specified. Set the version on the endpoint or the "
                                               + ApicurioRegistryConstants.VERSION + " header");
        }
        return version;
    }

    private static String header(Message in, String header, String defaultValue) {
        return in.getHeader(header, defaultValue, String.class);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (client == null) {
            client = endpoint.createClient();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client.close();
            client = null;
        }
        super.doStop();
    }
}
