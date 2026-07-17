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
import java.util.List;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.SearchedVersion;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.ScheduledPollConsumer;

public class ApicurioRegistryConsumer extends ScheduledPollConsumer {

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

        if (groupId == null || artifactId == null) {
            throw new IllegalArgumentException(
                    "Both groupId and artifactId are required for the consumer");
        }

        RegistryClient client = endpoint.getRegistryClient();
        VersionSearchResults results = client.groups().byGroupId(groupId)
                .artifacts().byArtifactId(artifactId).versions().get();

        if (results == null || results.getVersions() == null) {
            return 0;
        }

        List<SearchedVersion> versions = results.getVersions();
        int count = 0;
        for (SearchedVersion version : versions) {
            Long globalId = version.getGlobalId();
            if (lastSeenGlobalId == null || globalId > lastSeenGlobalId) {
                Exchange exchange = createExchange(true);
                Message message = exchange.getIn();

                message.setHeader(ApicurioRegistryConstants.HEADER_GROUP_ID, groupId);
                message.setHeader(ApicurioRegistryConstants.HEADER_ARTIFACT_ID, artifactId);
                message.setHeader(ApicurioRegistryConstants.HEADER_VERSION, version.getVersion());
                message.setHeader(ApicurioRegistryConstants.HEADER_GLOBAL_ID, globalId);
                message.setHeader(ApicurioRegistryConstants.HEADER_CONTENT_ID, version.getContentId());
                message.setHeader(ApicurioRegistryConstants.HEADER_ARTIFACT_TYPE, version.getArtifactType());
                if (version.getState() != null) {
                    message.setHeader(ApicurioRegistryConstants.HEADER_VERSION_STATE,
                            version.getState().getValue());
                }

                if (configuration.isFetchContent()) {
                    InputStream content = client.groups().byGroupId(groupId).artifacts()
                            .byArtifactId(artifactId).versions()
                            .byVersionExpression(version.getVersion()).content().get();
                    message.setBody(content);
                } else {
                    message.setBody(version);
                }

                getProcessor().process(exchange);

                if (lastSeenGlobalId == null || globalId > lastSeenGlobalId) {
                    lastSeenGlobalId = globalId;
                }
                count++;
            }
        }
        return count;
    }
}
