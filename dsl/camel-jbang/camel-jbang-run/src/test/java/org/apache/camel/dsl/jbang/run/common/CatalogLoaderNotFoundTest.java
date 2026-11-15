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
package org.apache.camel.dsl.jbang.run.common;

import java.net.ConnectException;
import java.util.List;

import org.apache.camel.main.download.DownloadException;
import org.apache.camel.tooling.maven.MavenResolutionException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.eclipse.aether.transfer.RepositoryOfflineException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CatalogLoader#isArtifactNotFound(Throwable)} tells a version that does not exist from a repository that cannot
 * be reached (CAMEL-26931).
 */
class CatalogLoaderNotFoundTest {

    private static final DefaultArtifact CATALOG = new DefaultArtifact("org.apache.camel:camel-core-catalog:9.9.9");
    private static final RemoteRepository CENTRAL
            = new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2").build();
    private static final RemoteRepository CUSTOM
            = new RemoteRepository.Builder("custom1", "default", "http://127.0.0.1:9").build();

    @Test
    void aVersionNoRepositoryHasIsNotFound() {
        assertThat(CatalogLoader.isArtifactNotFound(download(new ArtifactNotFoundException(CATALOG, CENTRAL)))).isTrue();
    }

    @Test
    void aRepositoryThatCannotBeReachedIsNotANotFound() {
        Exception refused = new ArtifactTransferException(CATALOG, CUSTOM, new ConnectException("Connection refused"));
        assertThat(CatalogLoader.isArtifactNotFound(download(refused))).isFalse();
        // central has not got it, but the other repository was not asked
        assertThat(CatalogLoader.isArtifactNotFound(download(new ArtifactNotFoundException(CATALOG, CENTRAL), refused)))
                .isFalse();
    }

    @Test
    void anOfflineRepositoryIsNotANotFound() {
        Exception offline = new ArtifactNotFoundException(
                CATALOG, CENTRAL, "Cannot access central in offline mode", new RepositoryOfflineException(CENTRAL));
        assertThat(CatalogLoader.isArtifactNotFound(download(offline))).isFalse();
    }

    @Test
    void anotherErrorIsNotANotFound() {
        assertThat(CatalogLoader.isArtifactNotFound(new DownloadException("Unknown error"))).isFalse();
        assertThat(CatalogLoader.isArtifactNotFound(null)).isFalse();
    }

    /** As {@code MavenDependencyDownloader} throws it: the resolver error in a MavenResolutionException. */
    private static DownloadException download(Exception... errors) {
        ArtifactResult result = new ArtifactResult(new ArtifactRequest(CATALOG, List.of(CENTRAL, CUSTOM), null));
        for (Exception e : errors) {
            result.addException(e);
        }
        ArtifactResolutionException are = new ArtifactResolutionException(List.of(result));
        MavenResolutionException mre = new MavenResolutionException(are.getMessage(), are);
        mre.getRepositories().add("https://repo1.maven.org/maven2");
        return new DownloadException("Cannot resolve dependencies in https://repo1.maven.org/maven2", mre);
    }
}
