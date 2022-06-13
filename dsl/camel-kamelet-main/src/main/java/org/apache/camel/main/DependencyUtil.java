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
package org.apache.camel.main;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.jboss.shrinkwrap.resolver.api.ResolutionException;
import org.jboss.shrinkwrap.resolver.api.maven.ConfigurableMavenResolverSystem;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenFormatStage;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.MavenStrategyStage;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenRemoteRepositories;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenRemoteRepository;
import org.jboss.shrinkwrap.resolver.api.maven.repository.MavenUpdatePolicy;

final class DependencyUtil {

    private DependencyUtil() {
    }

    public static List<MavenArtifact> resolveDependenciesViaAether(
            List<String> depIds, List<String> customRepos,
            boolean offline, boolean updateCache, boolean transitively) {

        ConfigurableMavenResolverSystem resolver = Maven.configureResolver()
                .withMavenCentralRepo(true)
                .workOffline(offline);

        if (customRepos != null) {
            for (int i = 0; i < customRepos.size(); i++) {
                String repo = customRepos.get(i);
                MavenRemoteRepository repository
                        = MavenRemoteRepositories.createRemoteRepository("custom" + i + 1, repo, "default");
                if (updateCache) {
                    repository.setUpdatePolicy(MavenUpdatePolicy.UPDATE_POLICY_ALWAYS);
                }
                resolver.withRemoteRepo(repository);
            }
        }

        System.setProperty("maven.repo.local", getLocalMavenRepo().toAbsolutePath().toString());
        try {
            MavenStrategyStage resolve = resolver.resolve(depIds);

            MavenFormatStage stage = transitively ? resolve.withTransitivity() : resolve.withoutTransitivity();
            List<MavenResolvedArtifact> artifacts = stage.asList(MavenResolvedArtifact.class);

            return artifacts.stream()
                    .map(mra -> {
                        String gav = mra.getCoordinate().getGroupId() + ":" + mra.getCoordinate().getArtifactId() + ":"
                                     + mra.getCoordinate().getVersion();
                        return new MavenArtifact(MavenGav.parseGav(null, gav), mra.asFile());
                    })
                    .collect(Collectors.toList());
        } catch (ResolutionException e) {
            String msg = "Cannot resolve dependencies from maven central";
            if (customRepos != null) {
                msg = "Cannot resolve dependencies from " + String.join(", ", customRepos);
            }
            throw new DownloadException(msg, e);
        } catch (RuntimeException e) {
            throw new DownloadException("Unknown error occurred while trying to resolve dependencies", e);
        }
    }

    public static Path getLocalMavenRepo() {
        return Paths.get((String) System.getProperties()
                .getOrDefault("maven.repo.local",
                        System.getProperty("user.home")
                                                  + File.separator + ".m2" + File.separator
                                                  + "repository"))
                .toAbsolutePath();
    }

}
