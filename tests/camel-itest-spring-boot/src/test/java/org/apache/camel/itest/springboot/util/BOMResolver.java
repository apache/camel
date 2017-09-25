/**
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
package org.apache.camel.itest.springboot.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.itest.springboot.ITestConfig;
import org.apache.commons.io.FileUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.deployment.DeployRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactDescriptorRequest;
import org.eclipse.aether.resolution.ArtifactDescriptorResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import static org.apache.camel.itest.springboot.util.LocationUtils.camelRoot;

public final class BOMResolver {

    private static final String LOCAL_REPO = "target/maven-aether-repo";

    private static final File CACHE_FILE = LocationUtils.camelRoot("tests/camel-itest-spring-boot/target/bom-versions-cache");

    private static BOMResolver INSTANCE;

    private ITestConfig config;

    private Map<String, String> versions;

    @SuppressWarnings("unchecked")
    private BOMResolver(ITestConfig config) {
        try {
            this.config = config;
            cleanupLocalRepo();

            if (canUseCache()) {
                try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(CACHE_FILE))) {
                    this.versions = (Map<String, String>) in.readObject();
                }
            } else {
                retrieveUpstreamBOMVersions();

                CACHE_FILE.delete();
                try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(CACHE_FILE))) {
                    out.writeObject(versions);
                }
            }

        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialize the version resolver", e);
        }

    }

    private boolean canUseCache() throws IOException {
        if (CACHE_FILE.exists()) {
            BasicFileAttributes attr = Files.readAttributes(CACHE_FILE.toPath(), BasicFileAttributes.class);
            FileTime fileTime = attr != null ? attr.creationTime() : null;
            Long time = fileTime != null ? fileTime.toMillis() : null;
            // Update the cache every day
            return time != null && time.compareTo(System.currentTimeMillis() - 1000 * 60 * 60 * 24) > 0;
        }

        return false;
    }

    private void retrieveUpstreamBOMVersions() throws Exception {
        RepositorySystem system = newRepositorySystem();
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(LOCAL_REPO);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));

        String camelVersion = DependencyResolver.resolveCamelParentProperty("${project.version}");

        List<Artifact> neededArtifacts = new LinkedList<>();
        Artifact camelRoot = new DefaultArtifact("org.apache.camel:camel:pom:"
                + camelVersion).setFile(camelRoot("pom.xml"));
        neededArtifacts.add(camelRoot);
        Artifact camelParent = new DefaultArtifact("org.apache.camel:camel-parent:pom:"
                + camelVersion).setFile(camelRoot("parent/pom.xml"));
        neededArtifacts.add(camelParent);
        neededArtifacts.add(new DefaultArtifact("org.apache.camel:spring-boot:pom:"
            + camelVersion).setFile(camelRoot("platforms/spring-boot/pom.xml")));
        neededArtifacts.add(new DefaultArtifact("org.apache.camel:camel-spring-boot-dm:pom:"
            + camelVersion).setFile(camelRoot("platforms/spring-boot/spring-boot-dm/pom.xml")));
        neededArtifacts.add(new DefaultArtifact("org.apache.camel:camel-spring-boot-dependencies:pom:"
            + camelVersion).setFile(camelRoot("platforms/spring-boot/spring-boot-dm/camel-spring-boot-dependencies/pom.xml")));
        Artifact camelStarterParent = new DefaultArtifact("org.apache.camel:camel-starter-parent:pom:"
            + camelVersion).setFile(camelRoot("platforms/spring-boot/spring-boot-dm/camel-starter-parent/pom.xml"));
        neededArtifacts.add(camelStarterParent);

        RemoteRepository localRepoDist = new RemoteRepository.Builder("org.apache.camel.itest.springboot", "default", new File(LOCAL_REPO).toURI().toString()).build();

        for (Artifact artifact : neededArtifacts) {
            DeployRequest deployRequest = new DeployRequest();
            deployRequest.addArtifact(artifact);
            deployRequest.setRepository(localRepoDist);

            system.deploy(session, deployRequest);
        }

        RemoteRepository mavenCentral = new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/").build();
        RemoteRepository apacheSnapshots = new RemoteRepository.Builder("apache-snapshots", "default", "http://repository.apache.org/snapshots/").build();
        RemoteRepository springMilestones = new RemoteRepository.Builder("spring-milestones", "default", "https://repo.spring.io/libs-milestone/").build();

        this.versions = new TreeMap<>();

        ArtifactDescriptorRequest springBootParentReq = new ArtifactDescriptorRequest(camelStarterParent, Arrays.asList(localRepoDist, mavenCentral, apacheSnapshots, springMilestones), null);
        ArtifactDescriptorResult springBootParentRes = system.readArtifactDescriptor(session, springBootParentReq);
        for (Dependency dependency : springBootParentRes.getManagedDependencies()) {
            Artifact a = dependency.getArtifact();
            String key = a.getGroupId() + ":" + a.getArtifactId();
            versions.put(key, dependency.getArtifact().getVersion());
        }

        Artifact springBootDependencies = new DefaultArtifact("org.springframework.boot:spring-boot-dependencies:pom:" + config.getSpringBootVersion());
        ArtifactDescriptorRequest springBootDependenciesReq = new ArtifactDescriptorRequest(springBootDependencies, Arrays.asList(localRepoDist, mavenCentral, apacheSnapshots, springMilestones), null);
        ArtifactDescriptorResult springBootDependenciesRes = system.readArtifactDescriptor(session, springBootDependenciesReq);
        for (Dependency dependency : springBootDependenciesRes.getManagedDependencies()) {
            Artifact a = dependency.getArtifact();
            String key = a.getGroupId() + ":" + a.getArtifactId();
            versions.put(key, dependency.getArtifact().getVersion());
        }
    }

    public static BOMResolver getInstance(ITestConfig config) {
        if (INSTANCE == null) {
            INSTANCE = new BOMResolver(config);
        }
        return INSTANCE;
    }

    public String getBOMVersion(String groupId, String artifactId) {
        return versions.get(groupId + ":" + artifactId);
    }

    private void cleanupLocalRepo() throws IOException {
        File f = new File(LOCAL_REPO);
        if (f.exists()) {
            FileUtils.deleteDirectory(f);
        }
    }

    private RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        RepositorySystem system = locator.getService(RepositorySystem.class);
        return system;
    }


}
