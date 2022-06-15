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
package org.apache.camel.main.download;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.camel.util.ReflectionHelper;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.jboss.shrinkwrap.resolver.api.ResolutionException;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.jboss.shrinkwrap.resolver.api.maven.MavenFormatStage;
import org.jboss.shrinkwrap.resolver.api.maven.MavenResolvedArtifact;
import org.jboss.shrinkwrap.resolver.api.maven.MavenStrategyStage;
import org.jboss.shrinkwrap.resolver.api.maven.MavenWorkingSession;
import org.jboss.shrinkwrap.resolver.impl.maven.ConfigurableMavenResolverSystemImpl;

public final class MavenDependencyResolver {

    private MavenDependencyResolver() {
    }

    public static List<MavenArtifact> resolveDependenciesViaAether(
            List<String> depIds, List<String> customRepos,
            boolean offline, boolean fresh, boolean transitively) {

        ConfigurableMavenResolverSystemImpl resolver = (ConfigurableMavenResolverSystemImpl) Maven.configureResolver()
                .withMavenCentralRepo(false) // do not use central
                .workOffline(offline);

        if (customRepos != null) {
            int custom = 1;
            for (String repo : customRepos) {
                // shrikwrap does not have public API for adding release vs snapshot repos
                // we need workaround using lower-level APIs and reflection
                boolean snapshot = repo.equals(MavenDependencyDownloader.APACHE_SNAPSHOT_REPO);
                boolean central = repo.equals(MavenDependencyDownloader.MAVEN_CENTRAL_REPO);
                String update = fresh ? RepositoryPolicy.UPDATE_POLICY_ALWAYS : RepositoryPolicy.UPDATE_POLICY_NEVER;
                RepositoryPolicy releasePolicy = new RepositoryPolicy(!snapshot, update, null);
                RepositoryPolicy snapshotPolicy = new RepositoryPolicy(snapshot, update, null);

                String id;
                if (snapshot) {
                    id = "apache-snapshot";
                } else if (central) {
                    id = "central";
                } else {
                    id = "custom" + custom++;
                }
                RemoteRepository rr = new RemoteRepository.Builder(id, "default", repo)
                        .setReleasePolicy(releasePolicy)
                        .setSnapshotPolicy(snapshotPolicy)
                        .build();

                MavenWorkingSession mws = resolver.getMavenWorkingSession();
                try {
                    Field f = mws.getClass().getDeclaredField("additionalRemoteRepositories");
                    Object obj = ReflectionHelper.getField(f, mws);
                    if (obj instanceof List) {
                        List list = (List) obj;
                        list.add(rr);
                    }
                } catch (Exception e) {
                    // ignore
                }
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
                        return new MavenArtifact(MavenGav.parseGav(gav), mra.asFile());
                    })
                    .collect(Collectors.toList());
        } catch (ResolutionException e) {
            String msg = "Cannot resolve dependencies in central (https://repo1.maven.org/maven2)";
            if (customRepos != null && customRepos.size() > 0) {
                msg = "Cannot resolve dependencies in " + String.join(", ", customRepos);
            }
            throw new DownloadException(msg, e);
        } catch (RuntimeException e) {
            throw new DownloadException("Unknown error occurred while trying to resolve dependencies", e);
        }
    }

    public static Path getLocalMavenRepo() {
        String m2 = System.getProperty("user.home") + File.separator + ".m2" + File.separator + "repository";
        String dir = System.getProperty("maven.repo.local", m2);
        return Paths.get(dir).toAbsolutePath();
    }

}
