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
import java.nio.charset.StandardCharsets;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionSearchResults;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.vertx.core.Vertx;

/**
 * Thin wrapper around the Apicurio Registry Java SDK ({@link RegistryClient}). It isolates the SDK API from the rest of
 * the component so the producer can be unit tested with a mocked client, and it owns the {@link Vertx} lifecycle used
 * by the underlying Kiota request adapter.
 */
public class ApicurioRegistryClient {

    /**
     * Version expression that resolves to the latest version of an artifact.
     */
    public static final String LATEST = "branch=latest";

    private final Vertx vertx;
    private final RegistryClient client;

    public ApicurioRegistryClient(String serverUrl) {
        this.vertx = Vertx.vertx();
        VertXRequestAdapter adapter = new VertXRequestAdapter(vertx);
        adapter.setBaseUrl(serverUrl);
        this.client = new RegistryClient(adapter);
    }

    ApicurioRegistryClient(Vertx vertx, RegistryClient client) {
        this.vertx = vertx;
        this.client = client;
    }

    public Object createArtifact(
            String groupId, String artifactId, String artifactType, String content,
            String contentType) {
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(contentType);

        CreateVersion firstVersion = new CreateVersion();
        firstVersion.setContent(versionContent);

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(artifactType);
        createArtifact.setFirstVersion(firstVersion);

        return client.groups().byGroupId(groupId).artifacts().post(createArtifact);
    }

    public Object addArtifactVersion(String groupId, String artifactId, String content, String contentType) {
        VersionContent versionContent = new VersionContent();
        versionContent.setContent(content);
        versionContent.setContentType(contentType);

        CreateVersion createVersion = new CreateVersion();
        createVersion.setContent(versionContent);

        return client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().post(createVersion);
    }

    public String getLatestArtifactContent(String groupId, String artifactId) {
        return getArtifactVersionContent(groupId, artifactId, LATEST);
    }

    public String getArtifactVersionContent(String groupId, String artifactId, String version) {
        try (InputStream is = client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(version).content().get()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new ApicurioRegistryException(
                    "Failed to read content for artifact " + groupId + "/" + artifactId + " version " + version, e);
        }
    }

    public VersionSearchResults listArtifactVersions(String groupId, String artifactId) {
        return client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get();
    }

    public void deleteArtifact(String groupId, String artifactId) {
        client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).delete();
    }

    public void deleteArtifactVersion(String groupId, String artifactId, String version) {
        client.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression(version).delete();
    }

    public void close() {
        if (vertx != null) {
            vertx.close();
        }
    }
}
