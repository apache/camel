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

import java.io.InputStream;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import org.apache.camel.Message;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.support.HeaderSelectorProducer;

public class ApicurioRegistryProducer extends HeaderSelectorProducer {

    private final ApicurioRegistryEndpoint endpoint;
    private final ApicurioRegistryConfiguration configuration;

    public ApicurioRegistryProducer(ApicurioRegistryEndpoint endpoint,
                                    ApicurioRegistryConfiguration configuration) {
        super(endpoint, ApicurioRegistryConstants.HEADER_OPERATION, configuration::getOperation);
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    private RegistryClient getClient() {
        return endpoint.getRegistryClient();
    }

    private String resolveGroupId(Message message) {
        String gid = message.getHeader(ApicurioRegistryConstants.HEADER_GROUP_ID, String.class);
        return gid != null ? gid : endpoint.getGroupId();
    }

    private String resolveArtifactId(Message message) {
        String aid = message.getHeader(ApicurioRegistryConstants.HEADER_ARTIFACT_ID, String.class);
        return aid != null ? aid : endpoint.getArtifactId();
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_CREATE_ARTIFACT)
    public void createArtifact(Message message) {
        String groupId = resolveGroupId(message);
        String artifactId = resolveArtifactId(message);
        String artifactType = message.getHeader(
                ApicurioRegistryConstants.HEADER_ARTIFACT_TYPE, configuration.getArtifactType(), String.class);
        String name = message.getHeader(ApicurioRegistryConstants.HEADER_ARTIFACT_NAME, String.class);
        String description = message.getHeader(ApicurioRegistryConstants.HEADER_ARTIFACT_DESCRIPTION, String.class);
        String content = message.getBody(String.class);
        String contentType = message.getHeader(
                ApicurioRegistryConstants.HEADER_CONTENT_TYPE, "application/json", String.class);
        String ifExistsVal = message.getHeader(
                ApicurioRegistryConstants.HEADER_IF_EXISTS, configuration.getIfExists(), String.class);

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(artifactType);
        createArtifact.setName(name);
        createArtifact.setDescription(description);

        if (content != null) {
            CreateVersion firstVersion = new CreateVersion();
            VersionContent vc = new VersionContent();
            vc.setContent(content);
            vc.setContentType(contentType);
            firstVersion.setContent(vc);
            createArtifact.setFirstVersion(firstVersion);
        }

        CreateArtifactResponse result = getClient().groups().byGroupId(groupId).artifacts()
                .post(createArtifact, config -> {
                    if (ifExistsVal != null) {
                        config.queryParameters.ifExists = IfArtifactExists.forValue(ifExistsVal);
                    }
                });
        message.setBody(result);
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_UPDATE_ARTIFACT)
    public void updateArtifact(Message message) {
        String groupId = resolveGroupId(message);
        String artifactId = resolveArtifactId(message);
        String content = message.getBody(String.class);
        String version = message.getHeader(ApicurioRegistryConstants.HEADER_VERSION, String.class);
        String contentType = message.getHeader(
                ApicurioRegistryConstants.HEADER_CONTENT_TYPE, "application/json", String.class);

        CreateVersion createVersion = new CreateVersion();
        createVersion.setVersion(version);
        VersionContent vc = new VersionContent();
        vc.setContent(content);
        vc.setContentType(contentType);
        createVersion.setContent(vc);

        VersionMetaData result = getClient().groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().post(createVersion);
        message.setBody(result);
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_DELETE_ARTIFACT)
    public void deleteArtifact(Message message) {
        String groupId = resolveGroupId(message);
        String artifactId = resolveArtifactId(message);
        getClient().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_GET_ARTIFACT_CONTENT)
    public void getArtifactContent(Message message) {
        String groupId = resolveGroupId(message);
        String artifactId = resolveArtifactId(message);
        String version = message.getHeader(
                ApicurioRegistryConstants.HEADER_VERSION, "branch=latest", String.class);

        InputStream content = getClient().groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression(version).content().get();
        message.setBody(content);
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_GET_ARTIFACT_METADATA)
    public void getArtifactMetadata(Message message) {
        String groupId = resolveGroupId(message);
        String artifactId = resolveArtifactId(message);

        ArtifactMetaData metadata = getClient().groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).get();
        message.setBody(metadata);
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_SEARCH_ARTIFACTS)
    public void searchArtifacts(Message message) {
        var results = getClient().search().artifacts().get(config -> {
            String name = message.getHeader(ApicurioRegistryConstants.HEADER_ARTIFACT_NAME, String.class);
            String groupId = resolveGroupId(message);
            String description = message.getHeader(
                    ApicurioRegistryConstants.HEADER_ARTIFACT_DESCRIPTION, String.class);
            if (name != null) {
                config.queryParameters.name = name;
            }
            if (groupId != null) {
                config.queryParameters.groupId = groupId;
            }
            if (description != null) {
                config.queryParameters.description = description;
            }
        });
        message.setBody(results);
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_LIST_VERSIONS)
    public void listVersions(Message message) {
        String groupId = resolveGroupId(message);
        String artifactId = resolveArtifactId(message);

        VersionSearchResults results = getClient().groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().get();
        message.setBody(results);
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_CREATE_GROUP)
    public void createGroup(Message message) {
        String groupId = resolveGroupId(message);
        String description = message.getHeader(
                ApicurioRegistryConstants.HEADER_ARTIFACT_DESCRIPTION, String.class);

        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        createGroup.setDescription(description);

        GroupMetaData result = getClient().groups().post(createGroup);
        message.setBody(result);
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_TEST_COMPATIBILITY)
    public void testCompatibility(Message message) {
        String groupId = resolveGroupId(message);
        String artifactId = resolveArtifactId(message);
        String content = message.getBody(String.class);
        String contentType = message.getHeader(
                ApicurioRegistryConstants.HEADER_CONTENT_TYPE, "application/json", String.class);

        CreateVersion createVersion = new CreateVersion();
        VersionContent vc = new VersionContent();
        vc.setContent(content);
        vc.setContentType(contentType);
        createVersion.setContent(vc);

        try {
            getClient().groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactId).versions()
                    .post(createVersion, config -> config.queryParameters.dryRun = true);
            message.setBody(true);
        } catch (Exception e) {
            message.setBody(false);
            message.setHeader(ApicurioRegistryConstants.HEADER_VALIDATION_ERRORS, e.getMessage());
        }
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_VALIDATE)
    public void validate(Message message) throws Exception {
        String groupId = resolveGroupId(message);
        String artifactId = resolveArtifactId(message);
        String content = message.getBody(String.class);
        String contentType = message.getHeader(
                ApicurioRegistryConstants.HEADER_CONTENT_TYPE, "application/json", String.class);

        CreateVersion createVersion = new CreateVersion();
        VersionContent vc = new VersionContent();
        vc.setContent(content);
        vc.setContentType(contentType);
        createVersion.setContent(vc);

        try {
            getClient().groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactId).versions()
                    .post(createVersion, config -> config.queryParameters.dryRun = true);
            message.setHeader(ApicurioRegistryConstants.HEADER_VALIDATION_RESULT, true);
        } catch (Exception e) {
            message.setHeader(ApicurioRegistryConstants.HEADER_VALIDATION_RESULT, false);
            message.setHeader(ApicurioRegistryConstants.HEADER_VALIDATION_ERRORS, e.getMessage());
            if (configuration.isFailOnValidation()) {
                throw new ApicurioRegistryValidationException(
                        "Validation failed for artifact " + groupId + "/" + artifactId, e.getMessage());
            }
        }
    }
}
