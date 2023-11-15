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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.camel.spi.BacklogDebugger;
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

/**
 * The maven goal allowing to automatically set up the Camel application in order to debug the Camel routes thanks to
 * the Camel textual Route Debugger.
 */
@Mojo(name = "debug", defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
      requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class DebugMojo extends DevMojo {

    /**
     * Indicates whether the message processing done by Camel should be suspended as long as a debugger is not attached.
     */
    @Parameter(property = "camel.suspend", defaultValue = "true")
    private boolean suspend;

    @Parameter(defaultValue = "${mojoExecution}", readonly = true)
    private MojoExecution mojo;

    @Parameter(defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Override
    protected void beforeBootstrapCamel() throws Exception {
        super.beforeBootstrapCamel();

        // Enable JMX
        System.setProperty("org.apache.camel.jmx.disabled", "false");
        // Enable the suspend mode.
        System.setProperty(BacklogDebugger.SUSPEND_MODE_SYSTEM_PROP_NAME, Boolean.toString(suspend));
        String suspendMode = System.getenv(BacklogDebugger.SUSPEND_MODE_ENV_VAR_NAME);
        if (suspendMode != null && Boolean.parseBoolean(suspendMode) != suspend) {
            throw new MojoExecutionException(
                    String.format(
                            "The environment variable %s has been set and prevents to configure the suspend mode. Please remove it first.",
                            BacklogDebugger.SUSPEND_MODE_ENV_VAR_NAME));
        }
    }

    @Override
    protected String goal() {
        return "camel:debug";
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
            addCamelDebug(classpath, mojo.getVersion());
            return classpath;
        }
        addCamelDebug(classpath, camelCoreVersion.get());
        return classpath;
    }

    /**
     * Automatically retrieve the given version of camel-debug and add it to the classpath if it can be found.
     *
     * @param classpath the classpath to which camel-debug and its dependencies are added.
     * @param version   the version of camel-debug to retrieve.
     */
    private void addCamelDebug(List<Artifact> classpath, String version) {
        getLog().debug(String.format("Trying to retrieve the version %s of camel-debug", version));
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
                        "org.apache.camel", "camel-debug", version, Artifact.SCOPE_RUNTIME, "jar", null,
                        new DefaultArtifactHandler("jar")));
        request.setResolutionFilter(new ScopeArtifactFilter(Artifact.SCOPE_RUNTIME));
        ArtifactResolutionResult result = artifactResolver.resolve(request);
        if (result.isSuccess()) {
            getLog().info(String.format("Adding the version %s of camel-debug", version));
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
                    ex -> getLog().warn(String.format("An error occurred while retrieving camel-debug: %s", ex.getMessage())));
        }
    }
}
