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
package org.apache.camel.tooling.maven;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.camel.tooling.maven.support.DIRegistry;
import org.apache.camel.util.FileUtil;
import org.apache.commons.codec.binary.Hex;
import org.apache.http.Header;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.apache.maven.settings.Mirror;
import org.apache.maven.settings.Repository;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MavenResolverTest {

    public static final Logger LOG = LoggerFactory.getLogger(MavenResolverTest.class);

    private static HttpServer localServer;

    @BeforeAll
    public static void startMavenMirror() throws Exception {
        localServer = ServerBootstrap.bootstrap()
                .setListenerPort(8888)
                .registerHandler("/maven/*", (req, res, context) -> {
                    Header authz = req.getFirstHeader("Authorization");
                    if (authz == null) {
                        res.addHeader("WWW-Authenticate", "Basic realm=Camel");
                        res.setStatusCode(401);
                        return;
                    }
                    String creds = new String(Base64.getDecoder().decode(authz.getValue().split(" ")[1]));
                    if (!"camel:passw0rd".equals(creds)) {
                        res.setStatusCode(403);
                        return;
                    }
                    LOG.info("Request: {}", req.getRequestLine());
                    String request = req.getRequestLine().getUri().substring("/maven".length());
                    if (request.endsWith(".jar") || request.endsWith(".pom")) {
                        res.setEntity(new StringEntity(request));
                    } else {
                        MessageDigest md = null;
                        try {
                            if (request.endsWith(".md5")) {
                                md = MessageDigest.getInstance("MD5");
                                request = request.substring(0, request.length() - 4);
                            } else if (request.endsWith(".sha1")) {
                                md = MessageDigest.getInstance("SHA");
                                request = request.substring(0, request.length() - 5);
                            }
                            if (md != null) {
                                byte[] digest = md.digest(request.getBytes(StandardCharsets.UTF_8));
                                res.setEntity(new StringEntity(Hex.encodeHexString(digest)));
                            }
                        } catch (NoSuchAlgorithmException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    res.setStatusCode(200);
                })
                .create();
        localServer.start();
    }

    @AfterAll
    public static void stopMavenMirror() {
        if (localServer != null) {
            localServer.shutdown(2, TimeUnit.SECONDS);
        }
    }

    @Test
    public void playingWithRepositorySystem() throws Exception {
        // MRESOLVER-157 is about removal of org.apache.maven.repository.internal.MavenRepositorySystemUtils,
        // so we can 1) do it manually, 2) use guice/sisu

        // org.eclipse.aether.RepositorySystem (2 levels down)
        //    org.eclipse.aether.impl.VersionResolver
        //       org.eclipse.aether.impl.MetadataResolver
        //       org.eclipse.aether.impl.SyncContextFactory
        //       org.eclipse.aether.impl.RepositoryEventDispatcher
        //    org.eclipse.aether.impl.VersionRangeResolver
        //       org.eclipse.aether.impl.MetadataResolver
        //       org.eclipse.aether.impl.SyncContextFactory
        //       org.eclipse.aether.impl.RepositoryEventDispatcher
        //    org.eclipse.aether.impl.ArtifactResolver
        //       org.eclipse.aether.spi.io.FileProcessor
        //       org.eclipse.aether.impl.RepositoryEventDispatcher
        //       org.eclipse.aether.impl.VersionResolver
        //       org.eclipse.aether.impl.UpdateCheckManager
        //       org.eclipse.aether.impl.RepositoryConnectorProvider
        //       org.eclipse.aether.impl.RemoteRepositoryManager
        //       org.eclipse.aether.spi.synccontext.SyncContextFactory
        //       org.eclipse.aether.impl.OfflineController
        //    org.eclipse.aether.impl.MetadataResolver
        //       org.eclipse.aether.impl.RepositoryEventDispatcher
        //       org.eclipse.aether.impl.UpdateCheckManager
        //       org.eclipse.aether.impl.RepositoryConnectorProvider
        //       org.eclipse.aether.impl.RemoteRepositoryManager
        //       org.eclipse.aether.spi.synccontext.SyncContextFactory
        //       org.eclipse.aether.impl.OfflineController
        //    org.eclipse.aether.impl.ArtifactDescriptorReader
        //       org.eclipse.aether.impl.RemoteRepositoryManager
        //       org.eclipse.aether.impl.VersionResolver
        //       org.eclipse.aether.impl.VersionRangeResolver
        //       org.eclipse.aether.impl.ArtifactResolver
        //       org.apache.maven.model.building.ModelBuilder
        //       org.eclipse.aether.impl.RepositoryEventDispatcher
        //    org.eclipse.aether.impl.DependencyCollector
        //       Map<String, org.eclipse.aether.internal.impl.collect.DependencyCollectorDelegate>
        //    org.eclipse.aether.impl.Installer
        //       org.eclipse.aether.spi.io.FileProcessor
        //       org.eclipse.aether.impl.RepositoryEventDispatcher
        //       Set<org.eclipse.aether.impl.MetadataGeneratorFactory>
        //       org.eclipse.aether.spi.synccontext.SyncContextFactory
        //    org.eclipse.aether.impl.Deployer
        //       org.eclipse.aether.spi.io.FileProcessor
        //       org.eclipse.aether.impl.RepositoryEventDispatcher
        //       org.eclipse.aether.impl.RepositoryConnectorProvider
        //       org.eclipse.aether.impl.RemoteRepositoryManager
        //       org.eclipse.aether.impl.UpdateCheckManager
        //       Set<org.eclipse.aether.impl.MetadataGeneratorFactory>
        //       org.eclipse.aether.spi.synccontext.SyncContextFactory
        //       org.eclipse.aether.impl.OfflineController
        //    org.eclipse.aether.impl.LocalRepositoryProvider
        //       Set<org.eclipse.aether.spi.localrepo.LocalRepositoryManagerFactory>
        //    org.eclipse.aether.spi.synccontext.SyncContextFactory
        //       org.eclipse.aether.internal.impl.synccontext.named.NamedLockFactorySelector
        //    org.eclipse.aether.impl.RemoteRepositoryManager
        //       org.eclipse.aether.impl.UpdatePolicyAnalyzer
        //       org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider

        try (DIRegistry registry = new DIRegistry();
             MavenDownloaderImpl downloader = new MavenDownloaderImpl()) {
            // here we don't call downloader.build() and will do the same stuff manually for demonstration purpose

            // see org.eclipse.aether.impl.DefaultServiceLocator.DefaultServiceLocator() - it registers
            // lots of default implementations to get started (but it's deprecated with MRESOLVER-157)

            // global settings file is by default ${m2.home}/.m2/settings.xml
            //  - no defaults in Maven, but shrinkwrap uses this order:
            //     - -Dmaven.home
            //     - $M2_HOME
            //     - $MAVEN_HOME
            // local settings file is by default ${user.home}/.m2/settings.xml
            // security settings file is by default ~/.m2/settings-security.xml
            // (see: org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher), but it may be altered
            // but it may be altered by (also from plexus-sec-dispatcher) -Dsettings.security
            // local repository is by default ${user.home}/.m2/repository, but may be altered by
            // -Dmaven.repo.local
            String localSettings = "target/test-classes/.m2/settings.xml";
            String localSettingsSecurity = "target/test-classes/.m2/settings-security.xml";

            downloader.setMavenSettingsLocation(localSettings);
            downloader.setMavenSettingsSecurityLocation(localSettingsSecurity);
            downloader.setFresh(true);
            downloader.setRepos(null);
            // don't build, as we'll do the maven-resolver initialization ourselves
            //downloader.build();

            Properties systemProperties = new Properties();

            // now, org.eclipse.aether.RepositorySystem can be obtained
            RepositorySystem repositorySystem
                    = downloader.configureRepositorySystem(registry, systemProperties, localSettingsSecurity);

            Settings settings = downloader.mavenConfiguration(registry, repositorySystem,
                    systemProperties, localSettings);

            // when using Maven without a project that may contain <repositories> in pom.xml, repositories
            // are taken from the active profiles defined in settings. If a repository is protected, the id
            // must match an id of one of the <server>s defined in the settings
            for (String ap : settings.getActiveProfiles()) {
                List<Repository> repositories = settings.getProfilesAsMap().get(ap).getRepositories();
                repositories.forEach(r -> {
                    if ("test-server".equals(r.getId())) {
                        r.setUrl("http://localhost:" + localServer.getLocalPort() + "/maven/repository");
                    }
                });
            }
            for (Mirror mirror : settings.getMirrors()) {
                if ("test-mirror".equals(mirror.getId())) {
                    mirror.setUrl("http://localhost:" + localServer.getLocalPort() + "/maven/mirror");
                }
            }

            // for test, we'll use hardcoded version
            String localRepository = "target/.m2/repository";
            // local repository manager is required and never set implicitly
            File m2Repo = new File(localRepository);
            FileUtil.removeDir(m2Repo);

            // we can finally create a session to resolve artifacts
            RepositorySystemSession session
                    = downloader.configureRepositorySystemSession(registry, systemProperties, settings, m2Repo);

            List<RemoteRepository> remoteRepositories = downloader.configureDefaultRepositories(settings);

            // finally we can resolve the artifact
            ArtifactRequest request = new ArtifactRequest();
            // repositories from the settings. auth, mirrors and proxies should be handled automatically if we use
            // one handy method: org.eclipse.aether.RepositorySystem#newResolutionRepositories()
            for (RemoteRepository r : repositorySystem.newResolutionRepositories(session, remoteRepositories)) {
                request.addRepository(r);
            }
            request.setArtifact(new DefaultArtifact("org.apache.camel", "camel-anything", "jar", "3.42.0"));

            ArtifactResult result = repositorySystem.resolveArtifact(session, request);
            assertTrue(result.getArtifact().getFile().isFile());
            assertEquals("/mirror/org/apache/camel/camel-anything/3.42.0/camel-anything-3.42.0.jar",
                    Files.readString(result.getArtifact().getFile().toPath()));
        }
    }

}
