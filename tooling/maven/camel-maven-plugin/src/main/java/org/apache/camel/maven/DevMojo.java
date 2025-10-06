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
package org.apache.camel.maven;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.camel.util.CastUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.filter.ScopeArtifactFilter;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.eclipse.aether.RepositorySystem;

@Mojo(name = "dev", defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
      requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class DevMojo extends RunMojo {

    @Parameter(defaultValue = "${mojoExecution}", readonly = true)
    private MojoExecution mojo;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    /**
     * To watch the directory for file changes which triggers a live reload of the Camel routes on-the-fly.
     */
    @Parameter(property = "camel.routesDirectory")
    private String routesDirectory;

    @Inject
    public DevMojo(RepositorySystem repositorySystem) {
        super(repositorySystem);
    }

    /**
     * Enable routes reloading
     */
    @Override
    protected void beforeBootstrapCamel() throws Exception {
        String dir;
        if (routesDirectory != null) {
            dir = routesDirectory;
        } else if (project.getResources().size() == 1) {
            dir = project.getResources().get(0).getDirectory();
        } else {
            dir = "src/main/resources";
        }

        // use the absolute path for dir
        dir = new File(dir).getAbsolutePath();

        // use dev profile by default
        System.setProperty("camel.main.profile", profile == null ? "dev" : profile);
        System.setProperty("camel.main.routesReloadEnabled", "true");
        System.setProperty("camel.main.routesReloadDirectory", dir);
        System.setProperty("camel.main.routesReloadDirectoryRecursive", "true");
        System.setProperty("camel.main.sourceLocationEnabled", "true");
        System.setProperty("camel.main.durationMaxAction", "stop");
        System.setProperty("camel.main.routesReloadPattern",
                "*.xml,*.yaml,*.java");
    }

    @Override
    protected List<Artifact> getClasspath() throws MojoExecutionException, MojoFailureException {
        List<Artifact> classpath = super.getClasspath();
        if (classpath.stream().anyMatch(artifact -> "org.apache.camel".equals(artifact.getGroupId())
                && "camel-debug".equals(artifact.getArtifactId()))) {
            getLog().debug("The component camel-debug has been detected in the classpath so no need to add it");
            return classpath;
        }
        getLog().info("The component camel-debug is not available in the classpath, it will be added automatically");
        Optional<String> camelCoreVersion = classpath.stream()
                .filter(artifact -> "org.apache.camel".equals(artifact.getGroupId())
                        && Objects.nonNull(artifact.getArtifactId()) && artifact.getArtifactId().startsWith("camel-core"))
                .map(Artifact::getBaseVersion)
                .filter(Objects::nonNull)
                .findAny();
        if (camelCoreVersion.isEmpty()) {
            getLog().info("The version of Camel could not be detected, the version of the plugin will be used instead");
            if ("camel:debug".equals(goal())) {
                addCamelDependency(classpath, "camel-debug", mojo.getVersion());
            }
            addCamelDependency(classpath, "camel-dsl-modeline", mojo.getVersion());
            return classpath;
        }
        if ("camel:debug".equals(goal())) {
            addCamelDependency(classpath, "camel-debug", mojo.getVersion());
        }
        addCamelDependency(classpath, "camel-dsl-modeline", mojo.getVersion());
        return classpath;
    }

    /**
     * Automatically retrieve the given version of camel-debug and add it to the classpath if it can be found.
     *
     * @param classpath the classpath to which camel-debug and its dependencies are added.
     * @param version   the version of camel-debug to retrieve.
     */
    private void addCamelDependency(List<Artifact> classpath, String artifactId, String version) {
        getLog().debug(String.format("Trying to retrieve the version %s of %s", artifactId, version));
        ArtifactResolutionRequest request = new ArtifactResolutionRequest();
        request.setResolveRoot(true);
        request.setResolveTransitively(true);
        request.setLocalRepository(session.getLocalRepository());
        request.setRemoteRepositories(session.getCurrentProject().getRemoteArtifactRepositories());
        request.setOffline(session.isOffline());
        request.setForceUpdate(session.getRequest().isUpdateSnapshots());
        request.setServers(session.getRequest().getServers());
        request.setMirrors(session.getRequest().getMirrors());
        request.setProxies(session.getRequest().getProxies());
        request.setManagedVersionMap(Collections.emptyMap());
        request.setArtifact(
                new DefaultArtifact(
                        "org.apache.camel", artifactId, version, Artifact.SCOPE_RUNTIME, "jar", null,
                        new DefaultArtifactHandler("jar")));
        request.setResolutionFilter(new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME));
        ArtifactResolutionResult result = artifactResolver.resolve(request);
        if (result.isSuccess()) {
            getLog().info(String.format("Adding the version %s of %s", version, artifactId));
            classpath.addAll(CastUtils.cast(result.getArtifacts()));
            return;
        }

        if (result.hasMissingArtifacts()) {
            getLog().warn(
                    String.format(
                            "Could not find the artifacts: %s",
                            result.getMissingArtifacts().stream().map(Objects::toString).collect(Collectors.joining(", "))));
        }
        if (result.hasExceptions()) {
            result.getExceptions().forEach(
                    ex -> getLog()
                            .warn(String.format("An error occurred while retrieving %s:%s", artifactId, ex.getMessage())));
        }
    }

    @Override
    protected String goal() {
        return "camel:dev";
    }

}
