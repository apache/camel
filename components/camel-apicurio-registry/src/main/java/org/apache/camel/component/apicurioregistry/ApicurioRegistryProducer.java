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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ArtifactMetaData;
import io.apicurio.registry.rest.client.models.ArtifactSearchResults;
import io.apicurio.registry.rest.client.models.ArtifactSortBy;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateArtifactResponse;
import io.apicurio.registry.rest.client.models.CreateGroup;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.GroupMetaData;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.RuleViolationProblemDetails;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rest.client.models.VersionSortBy;
import org.apache.camel.Message;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.support.HeaderSelectorProducer;
import org.apache.camel.util.ObjectHelper;

public class ApicurioRegistryProducer extends HeaderSelectorProducer {

    private static final int PAGE_SIZE = 100;

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

    private String requireGroupId(Message message, String operation) {
        return require(resolveGroupId(message), "groupId", ApicurioRegistryConstants.HEADER_GROUP_ID, operation);
    }

    private String requireArtifactId(Message message, String operation) {
        return require(resolveArtifactId(message), "artifactId", ApicurioRegistryConstants.HEADER_ARTIFACT_ID, operation);
    }

    private static String require(String value, String name, String header, String operation) {
        if (ObjectHelper.isEmpty(value)) {
            throw new IllegalArgumentException(
                    "The " + operation + " operation requires the " + name + ": set it in the endpoint path or the "
                                               + header + " header");
        }
        return value;
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_CREATE_ARTIFACT)
    public void createArtifact(Message message) {
        String groupId = requireGroupId(message, "createArtifact");
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
        String groupId = requireGroupId(message, "updateArtifact");
        String artifactId = requireArtifactId(message, "updateArtifact");
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
        String groupId = requireGroupId(message, "deleteArtifact");
        String artifactId = requireArtifactId(message, "deleteArtifact");
        getClient().groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_GET_ARTIFACT_CONTENT)
    public void getArtifactContent(Message message) throws Exception {
        String groupId = requireGroupId(message, "getArtifactContent");
        String artifactId = requireArtifactId(message, "getArtifactContent");
        String version = message.getHeader(
                ApicurioRegistryConstants.HEADER_VERSION, "branch=latest", String.class);

        try (InputStream content = getClient().groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).versions().byVersionExpression(version).content().get()) {
            message.setBody(content.readAllBytes());
        }
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_GET_ARTIFACT_METADATA)
    public void getArtifactMetadata(Message message) {
        String groupId = requireGroupId(message, "getArtifactMetadata");
        String artifactId = requireArtifactId(message, "getArtifactMetadata");

        ArtifactMetaData metadata = getClient().groups().byGroupId(groupId).artifacts()
                .byArtifactId(artifactId).get();
        message.setBody(metadata);
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_SEARCH_ARTIFACTS)
    public void searchArtifacts(Message message) {
        String name = message.getHeader(ApicurioRegistryConstants.HEADER_ARTIFACT_NAME, String.class);
        String groupId = resolveGroupId(message);
        String artifactId = resolveArtifactId(message);
        String description = message.getHeader(ApicurioRegistryConstants.HEADER_ARTIFACT_DESCRIPTION, String.class);
        // only an explicit header: the artifactType endpoint option has a default that would filter every search
        String artifactType = message.getHeader(ApicurioRegistryConstants.HEADER_ARTIFACT_TYPE, String.class);
        String[] labels = toLabels(message.getHeader(ApicurioRegistryConstants.HEADER_LABELS));
        Integer offset = message.getHeader(ApicurioRegistryConstants.HEADER_SEARCH_OFFSET, Integer.class);
        Integer limit = message.getHeader(ApicurioRegistryConstants.HEADER_SEARCH_LIMIT, Integer.class);
        String order = message.getHeader(ApicurioRegistryConstants.HEADER_SEARCH_ORDER, String.class);
        String orderBy = message.getHeader(ApicurioRegistryConstants.HEADER_SEARCH_ORDER_BY, String.class);

        ArtifactSearchResults results = getClient().search().artifacts().get(config -> {
            var query = config.queryParameters;
            query.name = name;
            query.groupId = groupId;
            query.artifactId = artifactId;
            query.description = description;
            query.artifactType = artifactType;
            query.labels = labels;
            query.offset = offset;
            query.limit = limit;
            if (order != null) {
                query.order = parseEnum(SortOrder.forValue(order), order, ApicurioRegistryConstants.HEADER_SEARCH_ORDER);
            }
            if (orderBy != null) {
                query.orderby = parseEnum(ArtifactSortBy.forValue(orderBy), orderBy,
                        ApicurioRegistryConstants.HEADER_SEARCH_ORDER_BY);
            }
        });
        message.setBody(results);
    }

    private static <T> T parseEnum(T value, String text, String header) {
        if (value == null) {
            throw new IllegalArgumentException("Unsupported value '" + text + "' for header " + header);
        }
        return value;
    }

    private static String[] toLabels(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String[] array) {
            return array;
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(String::valueOf).toArray(String[]::new);
        }
        return Arrays.stream(value.toString().split(",")).map(String::trim).filter(l -> !l.isEmpty())
                .toArray(String[]::new);
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_LIST_VERSIONS)
    public void listVersions(Message message) {
        String groupId = requireGroupId(message, "listVersions");
        String artifactId = requireArtifactId(message, "listVersions");

        List<SearchedVersion> all = new ArrayList<>();
        Integer total = null;
        int offset = 0;
        while (true) {
            final int pageOffset = offset;
            VersionSearchResults page = getClient().groups().byGroupId(groupId).artifacts()
                    .byArtifactId(artifactId).versions().get(config -> {
                        config.queryParameters.orderby = VersionSortBy.GlobalId;
                        config.queryParameters.order = SortOrder.Asc;
                        config.queryParameters.offset = pageOffset;
                        config.queryParameters.limit = PAGE_SIZE;
                    });
            List<SearchedVersion> versions = page != null ? page.getVersions() : null;
            if (page != null && page.getCount() != null) {
                total = page.getCount();
            }
            if (versions == null || versions.isEmpty()) {
                break;
            }
            all.addAll(versions);
            offset += versions.size();
            if (versions.size() < PAGE_SIZE || total != null && offset >= total) {
                break;
            }
        }
        VersionSearchResults results = new VersionSearchResults();
        results.setVersions(all);
        results.setCount(total != null ? total : all.size());
        message.setBody(results);
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_CREATE_GROUP)
    public void createGroup(Message message) {
        String groupId = requireGroupId(message, "createGroup");
        String description = message.getHeader(
                ApicurioRegistryConstants.HEADER_ARTIFACT_DESCRIPTION, String.class);

        CreateGroup createGroup = new CreateGroup();
        createGroup.setGroupId(groupId);
        createGroup.setDescription(description);

        GroupMetaData result = getClient().groups().post(createGroup);
        message.setBody(result);
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_TEST_COMPATIBILITY)
    public void testCompatibility(Message message) throws Exception {
        boolean compatible = doDryRun(message, ApicurioRegistryConstants.OPERATION_TEST_COMPATIBILITY);
        message.setHeader(ApicurioRegistryConstants.HEADER_VALIDATION_RESULT, compatible);
        message.setBody(compatible);
    }

    @InvokeOnHeader(ApicurioRegistryConstants.OPERATION_VALIDATE)
    public void validate(Message message) throws Exception {
        boolean valid = doDryRun(message, ApicurioRegistryConstants.OPERATION_VALIDATE);
        message.setHeader(ApicurioRegistryConstants.HEADER_VALIDATION_RESULT, valid);
        if (!valid && configuration.isFailOnValidation()) {
            String errors = message.getHeader(
                    ApicurioRegistryConstants.HEADER_VALIDATION_ERRORS, String.class);
            String groupId = resolveGroupId(message);
            String artifactId = resolveArtifactId(message);
            throw new ApicurioRegistryValidationException(
                    "Validation failed for artifact " + groupId + "/" + artifactId, errors);
        }
    }

    private boolean doDryRun(Message message, String operation) throws Exception {
        message.removeHeader(ApicurioRegistryConstants.HEADER_VALIDATION_ERRORS);
        String groupId = requireGroupId(message, operation);
        String artifactId = requireArtifactId(message, operation);
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
            return true;
        } catch (RuleViolationProblemDetails e) {
            message.setHeader(ApicurioRegistryConstants.HEADER_VALIDATION_ERRORS,
                    e.getDetail() != null ? e.getDetail() : e.getTitle());
            return false;
        }
    }
}
