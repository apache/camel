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
package org.apache.camel.dsl.jbang.core.common;

import java.net.ConnectException;
import java.util.List;

import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.main.download.DownloadException;
import org.apache.camel.tooling.maven.MavenResolutionException;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.transfer.ArtifactNotFoundException;
import org.eclipse.aether.transfer.ArtifactTransferException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A {@code --camel-version} that was never released says so and exits with 3, instead of "Cannot resolve dependencies"
 * with exit 1 (CAMEL-26931).
 */
class CamelVersionCheckTest {

    private static final DefaultArtifact CATALOG = new DefaultArtifact("org.apache.camel:camel-core-catalog:9.9.9");
    private static final RemoteRepository CENTRAL
            = new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2").build();

    @Test
    void aVersionThatWasNeverReleasedIsNotFound() {
        CliException e = CamelVersionCheck.notFound(download(new ArtifactNotFoundException(CATALOG, CENTRAL)), null, "9.9.9");
        assertEquals(CliExitCode.NOT_FOUND, e.getExitCode());
        assertEquals("Camel version 9.9.9 not found in https://repo1.maven.org/maven2"
                     + " (camel version list shows the released versions)",
                e.getMessage());

        CliException sb = CamelVersionCheck.notFound(
                download(new ArtifactNotFoundException(CATALOG, CENTRAL)), RuntimeType.springBoot, "9.9.9");
        assertEquals("Camel Spring Boot version 9.9.9 not found in https://repo1.maven.org/maven2"
                     + " (camel version list shows the released versions)",
                sb.getMessage());
    }

    @Test
    void aRepositoryThatCannotBeReachedKeepsItsError() {
        Exception refused = new ArtifactTransferException(CATALOG, CENTRAL, new ConnectException("Connection refused"));
        assertNull(CamelVersionCheck.notFound(download(refused), null, "9.9.9"));
    }

    @Test
    void theQuarkusPlatformReportsItsOwnErrors() {
        assertNull(CamelVersionCheck.notFound(
                download(new ArtifactNotFoundException(CATALOG, CENTRAL)), RuntimeType.quarkus, "9.9.9"));
    }

    /** As {@code MavenDependencyDownloader} throws it: the resolver error in a MavenResolutionException. */
    private static DownloadException download(Exception error) {
        ArtifactResult result = new ArtifactResult(new ArtifactRequest(CATALOG, List.of(CENTRAL), null));
        result.addException(error);
        ArtifactResolutionException are = new ArtifactResolutionException(List.of(result));
        MavenResolutionException mre = new MavenResolutionException(are.getMessage(), are);
        mre.getRepositories().add("https://repo1.maven.org/maven2");
        return new DownloadException("Cannot resolve dependencies in https://repo1.maven.org/maven2", mre);
    }
}
