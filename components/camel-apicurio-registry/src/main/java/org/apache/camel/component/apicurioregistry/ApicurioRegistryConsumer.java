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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.SortOrder;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.apicurio.registry.rest.client.models.VersionSortBy;
import io.apicurio.registry.rest.client.models.VersionState;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ApicurioRegistryConsumer extends ScheduledPollConsumer {

    static final int PAGE_SIZE = 100;

    private static final Logger LOG = LoggerFactory.getLogger(ApicurioRegistryConsumer.class);

    private final ApicurioRegistryEndpoint endpoint;
    private final ApicurioRegistryConfiguration configuration;
    private volatile Long lastSeenGlobalId;

    public ApicurioRegistryConsumer(ApicurioRegistryEndpoint endpoint, Processor processor,
                                    ApicurioRegistryConfiguration configuration) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    @Override
    protected int poll() throws Exception {
        String groupId = endpoint.getGroupId();
        String artifactId = endpoint.getArtifactId();
        RegistryClient client = endpoint.getRegistryClient();

        int count = 0;
        for (SearchedVersion version : fetchNewVersions(client, groupId, artifactId)) {
            Long globalId = version.getGlobalId();
            if (configuration.isFetchContent() && version.getState() == VersionState.DISABLED) {
                // the registry answers 404 for the content of a disabled version, so it can never be delivered
                LOG.debug("Skipping disabled artifact version {} (globalId {})", version.getVersion(), globalId);
                lastSeenGlobalId = globalId;
                continue;
            }
            Exchange exchange = createExchange(false);
            try {
                Message message = exchange.getIn();
                message.setHeader(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
                message.setHeader(ApicurioRegistryConstants.HEADER_ARTIFACT_ID, artifactId);
                message.setHeader(ApicurioRegistryConstants.HEADER_VERSION, version.getVersion());
                message.setHeader(ApicurioRegistryConstants.HEADER_GLOBAL_ID, globalId);
                message.setHeader(ApicurioRegistryConstants.HEADER_CONTENT_ID, version.getContentId());
                message.setHeader(ApicurioRegistryConstants.HEADER_ARTIFACT_TYPE, version.getArtifactType());
                if (version.getState() != null) {
                    message.setHeader(ApicurioRegistryConstants.HEADER_VERSION_STATE, version.getState().getValue());
                }

                if (configuration.isFetchContent()) {
                    byte[] content = fetchContent(client, groupId, artifactId, version);
                    if (content == null) {
                        lastSeenGlobalId = globalId;
                        continue;
                    }
                    message.setBody(content);
                } else {
                    message.setBody(version);
                }

                getProcessor().process(exchange);
                if (exchange.getException() != null) {
                    // do not advance the watermark so the version is retried on the next poll
                    if (exchange.getExchangeExtension().isErrorHandlerHandledSet()) {
                        // the error handler has already logged the failure
                        LOG.debug("Artifact version with globalId {} failed and will be retried on the next poll", globalId);
                    } else {
                        getExceptionHandler().handleException(
                                "Error processing artifact version with globalId " + globalId, exchange,
                                exchange.getException());
                    }
                    break;
                }
                lastSeenGlobalId = globalId;
                count++;
            } finally {
                releaseExchange(exchange, false);
            }
        }
        return count;
    }

    /**
     * Fetches the content of a version, or returns null if the version no longer has retrievable content (it was
     * disabled or deleted after the version list was read).
     */
    private static byte[] fetchContent(
            RegistryClient client, String groupId, String artifactId, SearchedVersion version)
            throws Exception {
        try (InputStream content = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(version.getVersion()).content().get()) {
            return content.readAllBytes();
        } catch (ProblemDetails e) {
            if (e.getStatus() != null && e.getStatus() == 404) {
                LOG.debug("Skipping artifact version {} (globalId {}) whose content is no longer available: {}",
                        version.getVersion(), version.getGlobalId(), e.getDetail());
                return null;
            }
            throw e;
        }
    }

    /**
     * Fetches the versions newer than the watermark, in ascending globalId order. Pages are requested newest first so
     * that polling stops as soon as an already seen version is reached.
     */
    private List<SearchedVersion> fetchNewVersions(RegistryClient client, String groupId, String artifactId) {
        // keyed by globalId: a version created between page requests shifts the pages and may repeat one entry
        Map<Long, SearchedVersion> answer = new TreeMap<>();
        if (lastSeenGlobalId != null) {
            // cheap probe so that an idle poll only transfers the newest version
            VersionSearchResults newest = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                    .versions().get(config -> {
                        config.queryParameters.orderby = VersionSortBy.GlobalId;
                        config.queryParameters.order = SortOrder.Desc;
                        config.queryParameters.offset = 0;
                        config.queryParameters.limit = 1;
                    });
            if (newest == null || newest.getVersions() == null || newest.getVersions().isEmpty()
                    || newest.getVersions().get(0).getGlobalId() <= lastSeenGlobalId) {
                return List.of();
            }
        }
        int offset = 0;
        while (true) {
            final int pageOffset = offset;
            VersionSearchResults page = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                    .versions().get(config -> {
                        config.queryParameters.orderby = VersionSortBy.GlobalId;
                        config.queryParameters.order = SortOrder.Desc;
                        config.queryParameters.offset = pageOffset;
                        config.queryParameters.limit = PAGE_SIZE;
                    });
            List<SearchedVersion> versions = page != null ? page.getVersions() : null;
            if (versions == null || versions.isEmpty()) {
                break;
            }
            boolean reachedWatermark = false;
            for (SearchedVersion version : versions) {
                if (lastSeenGlobalId != null && version.getGlobalId() <= lastSeenGlobalId) {
                    reachedWatermark = true;
                    break;
                }
                answer.put(version.getGlobalId(), version);
            }
            offset += versions.size();
            if (reachedWatermark || versions.size() < PAGE_SIZE
                    || page.getCount() != null && offset >= page.getCount()) {
                break;
            }
        }
        return new ArrayList<>(answer.values());
    }
}
