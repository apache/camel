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

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.apache.camel.dsl.jbang.run.common.CatalogLoader;
import org.apache.camel.dsl.jbang.run.common.RuntimeType;
import org.apache.camel.main.download.DownloadException;
import org.apache.camel.main.download.MavenDependencyDownloader;
import org.apache.camel.tooling.maven.MavenResolutionException;

/**
 * Tells a Camel version that does not exist from a Maven repository that cannot be reached, so a wrong
 * {@code --camel-version} says so and exits with {@link CliExitCode#NOT_FOUND} (CAMEL-26931).
 */
public final class CamelVersionCheck {

    private CamelVersionCheck() {
    }

    /**
     * The error for a {@code --camel-version} that the Maven repositories say does not exist, or null when the
     * exception is something else. With the Quarkus runtime the version picks a platform, which reports its own errors.
     *
     * @param e       the exception of loading the catalog of the version
     * @param runtime the runtime, or null for Camel Main
     * @param version the version, or null for the version of the CLI
     */
    public static CliException notFound(Throwable e, RuntimeType runtime, String version) {
        if (RuntimeType.quarkus == runtime) {
            return null;
        }
        return notFound(e, RuntimeType.springBoot == runtime ? "Camel Spring Boot" : "Camel", version);
    }

    /**
     * The error for a version that the Maven repositories say does not exist, or null when the exception is something
     * else, such as a repository that cannot be reached.
     *
     * @param e       the exception of loading the artifacts of the version
     * @param what    what the version is of, such as {@code Camel} or {@code Camel Spring Boot}
     * @param version the version
     */
    private static CliException notFound(Throwable e, String what, String version) {
        if (version == null || !CatalogLoader.isArtifactNotFound(e)) {
            return null;
        }
        String repos = null;
        for (Throwable c = e; c != null && repos == null; c = c.getCause()) {
            if (c instanceof MavenResolutionException mre && mre.getRepositories() != null
                    && !mre.getRepositories().isEmpty()) {
                repos = String.join(", ", mre.getRepositories());
            }
        }
        return new CliException(
                CliExitCode.NOT_FOUND,
                what + " version " + version + " not found" + (repos != null ? " in " + repos : "")
                                       + " (camel version list shows the released versions)",
                e);
    }

    /**
     * Whether the Camel version exists: {@code true} or {@code false} as the Maven repositories answer, or null when
     * they cannot be reached.
     *
     * @param version the Camel version
     * @param repos   extra Maven repositories (comma separated), or null
     */
    public static Boolean exists(String version, String repos) {
        try (MavenDependencyDownloader downloader = new MavenDependencyDownloader()) {
            downloader.setRepositories(repos);
            downloader.setDownload(true);
            downloader.start();
            downloader.resolveDependenciesViaAether(List.of("org.apache.camel:camel-api:pom:" + version), Set.of(),
                    false, version.endsWith("-SNAPSHOT"));
            return true;
        } catch (DownloadException e) {
            return CatalogLoader.isArtifactNotFound(e) ? false : null;
        } catch (IOException e) {
            // closing the downloader; the answer is known already
            return true;
        }
    }
}
