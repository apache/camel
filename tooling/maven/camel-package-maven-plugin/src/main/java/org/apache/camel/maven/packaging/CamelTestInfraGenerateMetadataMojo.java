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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import javax.inject.Inject;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.camel.maven.packaging.generics.PackagePluginUtils;
import org.apache.camel.spi.annotations.InfraService;
import org.apache.camel.tooling.util.FileUtil;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;

/**
 * Gather all classes annotated with @InfraService and create a JSON file containing all the metadata.
 *
 * The JSON can be used to retrieve the test-infra information and run the services (via Camel JBang for example)
 */
@Mojo(name = "test-infra-generate-metadata", threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class CamelTestInfraGenerateMetadataMojo extends AbstractGeneratorMojo {

    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File generatedResourcesOutputDir;

    public static final DotName INFRA_SERVICE = DotName.createSimple(InfraService.class.getName());

    @Inject
    protected CamelTestInfraGenerateMetadataMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Set<InfrastructureServiceModel> models = new LinkedHashSet<>();

        for (AnnotationInstance ai : PackagePluginUtils.readJandexIndexQuietly(project).getAnnotations(INFRA_SERVICE)) {

            InfrastructureServiceModel infrastructureServiceModel = new InfrastructureServiceModel();
            String targetClass = ai.target().toString();

            infrastructureServiceModel.setImplementation(targetClass);

            try {
                // Search for target class in the project transitive artifacts to retrieve maven coordinates
                for (Artifact artifact : project.getArtifacts()) {
                    if (classExistsInJarFile(
                            targetClass.substring(targetClass.lastIndexOf(".") + 1),
                            artifact.getFile())) {
                        infrastructureServiceModel.setVersion(artifact.getVersion());
                        infrastructureServiceModel.setGroupId(artifact.getGroupId());
                        infrastructureServiceModel.setArtifactId(artifact.getArtifactId());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Error reading jar file", e);
            }

            for (AnnotationValue av : ai.values()) {
                if (av.name().equals("service")) {
                    infrastructureServiceModel.setService(av.asString());
                } else if (av.name().equals("serviceAlias")) {
                    infrastructureServiceModel.setAlias(Arrays.asList(av.asStringArray()));
                } else if (av.name().equals("serviceImplementationAlias")) {
                    infrastructureServiceModel.getAliasImplementation().addAll(Arrays.asList(av.asStringArray()));
                } else if (av.name().equals("description")) {
                    infrastructureServiceModel.setDescription(av.asString());
                }
            }

            models.add(infrastructureServiceModel);
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            String modelsAsJson = mapper.writeValueAsString(models);

            if (generatedResourcesOutputDir == null) {
                generatedResourcesOutputDir = new File(project.getBasedir(), "src/generated/java");
            }

            FileUtil.updateFile(generatedResourcesOutputDir.toPath()
                    .resolve("META-INF")
                    .resolve("metadata.json"), modelsAsJson);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean classExistsInJarFile(String className, File dependency) throws IOException {
        try (JarFile jarFile = new JarFile(dependency)) {
            Enumeration<JarEntry> e = jarFile.entries();
            while (e.hasMoreElements()) {
                JarEntry jarEntry = e.nextElement();
                if (jarEntry.getName().contains(className)) {
                    return true;
                }
            }
            return false;
        }
    }

    private class InfrastructureServiceModel {
        private String service;
        private String description;
        private String implementation;
        private List<String> alias = new ArrayList<>();
        private List<String> aliasImplementation = new ArrayList<>();
        private String groupId;
        private String artifactId;
        private String version;

        public String getService() {
            return service;
        }

        public void setService(String service) {
            this.service = service;
        }

        public String getImplementation() {
            return implementation;
        }

        public void setImplementation(String implementation) {
            this.implementation = implementation;
        }

        public List<String> getAlias() {
            return alias;
        }

        public void setAlias(List<String> alias) {
            this.alias = alias;
        }

        public List<String> getAliasImplementation() {
            return aliasImplementation;
        }

        public void setAliasImplementation(List<String> aliasImplementation) {
            this.aliasImplementation = aliasImplementation;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }
}
