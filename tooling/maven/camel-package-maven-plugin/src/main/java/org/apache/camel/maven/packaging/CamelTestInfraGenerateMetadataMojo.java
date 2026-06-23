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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
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
 * The JSON can be used to retrieve the test-infra information and run the services (via Camel CLI for example)
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

        // Collect infra-service.properties from all dependency JARs once (for embedded service GA hints)
        Properties infraServiceProps = collectInfraServiceProperties();

        for (AnnotationInstance ai : PackagePluginUtils.readJandexIndexQuietly(project).getAnnotations(INFRA_SERVICE)) {

            InfrastructureServiceModel infrastructureServiceModel = new InfrastructureServiceModel();
            String targetClass = ai.target().toString();

            infrastructureServiceModel.setImplementation(targetClass);

            try {
                // Search for target class in the project transitive artifacts to retrieve maven coordinates
                for (Artifact artifact : project.getArtifacts()) {
                    if (classExistsInDependency(
                            targetClass.replace('.', '/') + ".class",
                            artifact.getFile())) {
                        infrastructureServiceModel.setVersion(artifact.getVersion());
                        infrastructureServiceModel.setGroupId(artifact.getGroupId());
                        infrastructureServiceModel.setArtifactId(artifact.getArtifactId());
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("Error reading jar file", e);
            }

            String annotationServiceVersion = null;
            List<String> serviceAliases = new ArrayList<>();
            List<String> implAliases = new ArrayList<>();

            for (AnnotationValue av : ai.values()) {
                if (av.name().equals("service")) {
                    infrastructureServiceModel.setService(av.asString());
                } else if (av.name().equals("serviceAlias")) {
                    List<String> aliases = Arrays.asList(av.asStringArray());
                    infrastructureServiceModel.setAlias(aliases);
                    serviceAliases.addAll(aliases);
                } else if (av.name().equals("serviceImplementationAlias")) {
                    List<String> aliases = Arrays.asList(av.asStringArray());
                    infrastructureServiceModel.getAliasImplementation().addAll(aliases);
                    implAliases.addAll(aliases);
                } else if (av.name().equals("description")) {
                    infrastructureServiceModel.setDescription(av.asString());
                } else if (av.name().equals("serviceVersion")) {
                    annotationServiceVersion = av.asString();
                }
            }

            // Resolve service version: annotation > container.properties > infra-service.properties GA hint
            if (annotationServiceVersion != null && !annotationServiceVersion.isEmpty()) {
                infrastructureServiceModel.setServiceVersion(annotationServiceVersion);
            } else {
                String detectedVersion = detectServiceVersionFromContainer(
                        targetClass, infrastructureServiceModel, implAliases, serviceAliases);
                if (detectedVersion != null) {
                    infrastructureServiceModel.setServiceVersion(detectedVersion);
                } else {
                    String infraVersion = resolveVersionFromGAHint(infraServiceProps, implAliases, serviceAliases);
                    if (infraVersion != null) {
                        infrastructureServiceModel.setServiceVersion(infraVersion);
                    }
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

    private String detectServiceVersionFromContainer(
            String targetClass, InfrastructureServiceModel model,
            List<String> implAliases, List<String> serviceAliases) {

        // Collect container.properties from all dependency JARs to handle
        // cross-module cases (e.g., Azure storage-queue using azure-common's properties)
        Properties allProps = new Properties();

        for (Artifact artifact : project.getArtifacts()) {
            File file = artifact.getFile();
            if (file == null || !file.exists()) {
                continue;
            }

            if (file.isDirectory()) {
                collectContainerPropertiesFromDirectory(file, file, allProps);
            } else {
                collectContainerPropertiesFromJar(file, allProps);
            }
        }

        if (allProps.isEmpty()) {
            return null;
        }

        return extractVersionFromProperties(allProps, implAliases, serviceAliases);
    }

    private void collectContainerPropertiesFromDirectory(File root, File dir, Properties target) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File f : files) {
            if (f.isDirectory()) {
                collectContainerPropertiesFromDirectory(root, f, target);
            } else if (f.getName().equals("container.properties")) {
                try (InputStream is = java.nio.file.Files.newInputStream(f.toPath())) {
                    target.load(is);
                } catch (IOException e) {
                    // skip
                }
            }
        }
    }

    private void collectContainerPropertiesFromJar(File jarPath, Properties target) {
        try (JarFile jarFile = new JarFile(jarPath)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith("/container.properties")) {
                    try (InputStream is = jarFile.getInputStream(entry)) {
                        target.load(is);
                    }
                }
            }
        } catch (IOException e) {
            // skip
        }
    }

    private String extractVersionFromProperties(
            Properties props, List<String> implAliases, List<String> serviceAliases) {

        // Determine which alias to match against property keys
        // Prefer implementation alias (e.g., "redpanda" for Kafka Redpanda)
        List<String> aliasesToMatch = new ArrayList<>();
        if (!implAliases.isEmpty()) {
            aliasesToMatch.addAll(implAliases);
        }
        aliasesToMatch.addAll(serviceAliases);

        for (String alias : aliasesToMatch) {
            String normalizedAlias = normalizeForMatching(alias);

            for (String key : props.stringPropertyNames()) {
                String lowerKey = key.toLowerCase();

                // Skip platform-specific keys
                if (lowerKey.endsWith(".ppc64le") || lowerKey.endsWith(".s390x")
                        || lowerKey.endsWith(".aarch64") || lowerKey.endsWith(".amd64")) {
                    continue;
                }
                // Skip version metadata keys
                if (lowerKey.contains(".version.exclude") || lowerKey.contains(".version.include")
                        || lowerKey.contains(".version.freeze")) {
                    continue;
                }
                // Must reference a container
                if (!lowerKey.contains("container")) {
                    continue;
                }
                // Skip keys ending with .version (handled separately for RocketMQ pattern)
                if (lowerKey.endsWith(".version")) {
                    continue;
                }

                // Extract prefix before first ".container" occurrence
                int containerIdx = lowerKey.indexOf(".container");
                if (containerIdx <= 0) {
                    continue;
                }
                String prefix = lowerKey.substring(0, containerIdx);
                String normalizedPrefix = normalizeForMatching(prefix);

                // Match: normalized prefix must equal the normalized alias,
                // or end with the normalized alias (for compound names like hivemq.sparkplug)
                if (!normalizedPrefix.equals(normalizedAlias)
                        && !normalizedPrefix.endsWith(normalizedAlias)) {
                    continue;
                }

                String imageRef = props.getProperty(key);
                if (imageRef == null || imageRef.isEmpty()) {
                    continue;
                }

                // Value must look like a container image reference (contains '/')
                if (!imageRef.contains("/")) {
                    continue;
                }

                // Extract version from image tag (after last ':')
                int colonIdx = imageRef.lastIndexOf(':');
                if (colonIdx > 0 && colonIdx < imageRef.length() - 1) {
                    return imageRef.substring(colonIdx + 1);
                }

                // No tag in image reference — check for a separate .version property (RocketMQ pattern)
                String versionKey = key + ".version";
                String separateVersion = props.getProperty(versionKey);
                if (separateVersion != null && !separateVersion.isEmpty()) {
                    return separateVersion;
                }
            }
        }
        return null;
    }

    private Properties collectInfraServiceProperties() {
        Properties allProps = new Properties();
        for (Artifact artifact : project.getArtifacts()) {
            File file = artifact.getFile();
            if (file == null || !file.exists()) {
                continue;
            }
            if (file.isDirectory()) {
                File propsFile = new File(file, "META-INF/infra-service.properties");
                if (propsFile.exists()) {
                    try (InputStream is = java.nio.file.Files.newInputStream(propsFile.toPath())) {
                        allProps.load(is);
                    } catch (IOException e) {
                        // skip
                    }
                }
            } else {
                try (JarFile jarFile = new JarFile(file)) {
                    JarEntry entry = jarFile.getJarEntry("META-INF/infra-service.properties");
                    if (entry != null) {
                        try (InputStream is = jarFile.getInputStream(entry)) {
                            allProps.load(is);
                        }
                    }
                } catch (IOException e) {
                    // skip
                }
            }
        }
        return allProps;
    }

    private String resolveVersionFromGAHint(
            Properties infraProps, List<String> implAliases, List<String> serviceAliases) {

        List<String> aliasesToTry = new ArrayList<>();
        aliasesToTry.addAll(implAliases);
        aliasesToTry.addAll(serviceAliases);

        for (String alias : aliasesToTry) {
            String ga = infraProps.getProperty(alias);
            if (ga == null || ga.isEmpty()) {
                continue;
            }
            String[] parts = ga.split(":");
            if (parts.length != 2) {
                continue;
            }
            for (Artifact artifact : project.getArtifacts()) {
                if (parts[0].equals(artifact.getGroupId()) && parts[1].equals(artifact.getArtifactId())) {
                    return artifact.getVersion();
                }
            }
        }
        return null;
    }

    private static String normalizeForMatching(String s) {
        return s.replace("-", "").replace(".", "").toLowerCase();
    }

    private boolean classExistsInDependency(String classPath, File dependency) throws IOException {
        if (dependency.isDirectory()) {
            return new File(dependency, classPath).exists();
        }
        try (JarFile jarFile = new JarFile(dependency)) {
            return jarFile.getEntry(classPath) != null;
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
        private String serviceVersion;

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

        public String getServiceVersion() {
            return serviceVersion;
        }

        public void setServiceVersion(String serviceVersion) {
            this.serviceVersion = serviceVersion;
        }
    }
}
