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
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.maven.MavenVersionManager;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.util.IOHelper;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.mojo.exec.AbstractExecMojo;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

/**
 * Base class for maven goals.
 */
public abstract class AbstractMainMojo extends AbstractExecMojo {

    /**
     * Whether to log the classpath when starting
     */
    @Parameter(property = "camel.logClasspath", defaultValue = "false")
    protected boolean logClasspath;

    /**
     * Whether to allow downloading Camel catalog version from the internet. This is needed if the project
     * uses a different Camel version than this plugin is using by default.
     */
    @Parameter(property = "camel.downloadVersion", defaultValue = "true")
    protected boolean downloadVersion;

    protected transient ClassLoader classLoader;

    protected transient ClassLoader sourcesClassLoader;

    protected transient CamelCatalog catalog;

    protected transient Reflections reflections;

    protected transient Set<String> camelComponentsOnClasspath;

    @Component
    private RepositorySystem repositorySystem;

    @Component
    private ArtifactResolver artifactResolver;

    @Parameter(property = "localRepository")
    private ArtifactRepository localRepository;

    @Parameter(property = "project.remoteArtifactRepositories")
    private List remoteRepositories;

    @FunctionalInterface
    protected interface ComponentCallback {
        void onOption(String componentName, String componentJavaType, String componentDescription,
                      String name, String type, String javaType, String description, String defaultValue, boolean deprecated);
    }

    protected void doExecute(ComponentCallback callback) throws MojoExecutionException, MojoFailureException {
        catalog = new DefaultCamelCatalog();
        // add activemq as known component
        catalog.addComponent("activemq", "org.apache.activemq.camel.component.ActiveMQComponent");
        // enable loading other catalog versions dynamically
        catalog.setVersionManager(new MavenVersionManager());
        // enable caching
        catalog.enableCache();

        String detectedVersion = findCamelVersion(project);
        if (detectedVersion != null) {
            getLog().info("Detected Camel version used in project: " + detectedVersion);
        }

        if (downloadVersion) {
            String catalogVersion = catalog.getCatalogVersion();
            String version = findCamelVersion(project);
            if (version != null && !version.equals(catalogVersion)) {
                // the project uses a different Camel version so attempt to load it
                getLog().info("Downloading Camel version: " + version);
                boolean loaded = catalog.loadVersion(version);
                if (!loaded) {
                    getLog().warn("Error downloading Camel version: " + version);
                }
            }
        }

        if (catalog.getLoadedVersion() != null) {
            getLog().info("Pre-scanning using downloaded Camel version: " + catalog.getLoadedVersion());
        } else {
            getLog().info("Pre-scanning using Camel version: " + catalog.getCatalogVersion());
        }

        // find all Camel components on classpath and check in the camel-catalog for all component options
        // then check each option if its a complex type and an interface
        // and if so scan class-path and find the single class implementing this interface
        // write this to META-INF/services/org/apache/camel/autowire.properties

        // find all Camel components on classpath
        camelComponentsOnClasspath = resolveCamelComponentsFromClasspath();
        if (camelComponentsOnClasspath.isEmpty()) {
            getLog().warn("No Camel components discovered in classpath");
            return;
        } else {
            getLog().info("Discovered " + camelComponentsOnClasspath.size() + " Camel components from classpath: " + camelComponentsOnClasspath);
        }

        // build index of classes on classpath
        getLog().debug("Indexing classes on classpath");
        reflections = new Reflections(new ConfigurationBuilder()
                .addUrls(ClasspathHelper.forClassLoader(classLoader))
                .addClassLoader(classLoader)
                .setScanners(new SubTypesScanner()));

        for (String componentName : camelComponentsOnClasspath) {
            String json = catalog.componentJSonSchema(componentName);
            if (json == null) {
                getLog().debug("Cannot find component JSon metadata for component: " + componentName);
                continue;
            }

            ComponentModel model = JsonMapper.generateComponentModel(json);
            String componentJavaType = model.getJavaType();
            String componentDescription = model.getDescription();
            model.getComponentOptions().forEach(option ->
                    callback.onOption(componentName, componentJavaType, componentDescription,
                            option.getName(), option.getType(),
                            safeJavaType(option.getJavaType()),
                            safeValue(option.getDescription()),
                            option.getDefaultValue() != null ? option.getDefaultValue().toString() : null,
                            option.isDeprecated()));
        }
    }

    protected static String safeValue(String description) {
        return description != null ? description : "";
    }

    protected static String findCamelVersion(MavenProject project) {
        Dependency candidate = null;

        List list = project.getDependencies();
        for (Object obj : list) {
            Dependency dep = (Dependency) obj;
            if ("org.apache.camel".equals(dep.getGroupId())) {
                if ("camel-core".equals(dep.getArtifactId())) {
                    // favor camel-core
                    candidate = dep;
                    break;
                } else {
                    candidate = dep;
                }
            }
        }
        if (candidate != null) {
            return candidate.getVersion();
        }

        return null;
    }

    public static String getComponentJavaType(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            if (row.containsKey("javaType")) {
                return row.get("javaType");
            }
        }
        return null;
    }

    public static String getComponentDescription(List<Map<String, String>> rows) {
        for (Map<String, String> row : rows) {
            if (row.containsKey("description")) {
                return row.get("description");
            }
        }
        return null;
    }

    protected Set<String> resolveCamelComponentsFromClasspath() throws MojoFailureException {
        Set<String> components = new TreeSet<>();
        try {
            classLoader = getClassLoader(false);
            Enumeration<URL> en = classLoader.getResources("META-INF/services/org/apache/camel/component.properties");
            while (en.hasMoreElements()) {
                URL url = en.nextElement();
                InputStream is = (InputStream) url.getContent();
                Properties prop = new Properties();
                prop.load(is);
                String comps = prop.getProperty("components");
                if (comps != null) {
                    String[] parts = comps.split("\\s+");
                    components.addAll(Arrays.asList(parts));
                }
                IOHelper.close(is);
            }
        } catch (Throwable e) {
            throw new MojoFailureException("Error during discovering Camel components from classpath due " + e.getMessage(), e);
        }

        return components;
    }

    protected ClassLoader getSourcesClassLoader() throws Exception {
        if (sourcesClassLoader == null) {
            try {
                sourcesClassLoader = getClassLoader(true);
            } catch (Throwable e) {
                getLog().warn("Cannot download sources JAR to look for javadoc descriptions of the discovered options, due to: " + e.getMessage());
            }
        }
        return sourcesClassLoader;
    }

    /**
     * Set up a classloader for scanning
     */
    private ClassLoader getClassLoader(boolean sourcesOnly) throws MalformedURLException, MojoExecutionException {
        Set<URL> classpathURLs = new LinkedHashSet<>();

        // add project classpath
        if (!sourcesOnly) {
            URL mainClasses = new File(project.getBuild().getOutputDirectory()).toURI().toURL();
            classpathURLs.add(mainClasses);
        }

        // add maven dependencies
        Set<Artifact> deps;
        if (sourcesOnly) {
            deps = new LinkedHashSet<>();
            deps.addAll(getAllSourceOnlyDependencies(getAllNonTestScopedDependencies()));
        } else {
            deps = project.getArtifacts();
            deps.addAll(getAllNonTestScopedDependencies());
        }
        for (Artifact dep : deps) {
            File file = dep.getFile();
            if (file != null) {
                classpathURLs.add(file.toURI().toURL());
            }
        }

        if (logClasspath) {
            if (sourcesOnly) {
                getLog().info("Sources Classpath:");
            } else {
                getLog().info("Classpath:");
            }
            for (URL url : classpathURLs) {
                getLog().info("  " + url.getFile());
            }
        }
        return new URLClassLoader(classpathURLs.toArray(new URL[classpathURLs.size()]));
    }

    private Collection<Artifact> getAllNonTestScopedDependencies() throws MojoExecutionException {
        List<Artifact> answer = new ArrayList<>();

        for (Artifact artifact : getAllDependencies()) {

            // do not add test artifacts
            if (!Artifact.SCOPE_TEST.equals(artifact.getScope())) {

                if ("google-collections".equals(artifact.getArtifactId())) {
                    // skip this as we conflict with guava
                    continue;
                }

                if (!artifact.isResolved()) {
                    ArtifactResolutionRequest req = new ArtifactResolutionRequest();
                    req.setArtifact(artifact);
                    req.setResolveTransitively(true);
                    req.setLocalRepository(localRepository);
                    req.setRemoteRepositories(remoteRepositories);
                    artifactResolver.resolve(req);
                }

                answer.add(artifact);
            }
        }
        return answer;
    }

    // generic method to retrieve all the transitive dependencies
    private Collection<Artifact> getAllDependencies() {
        List<Artifact> answer = new ArrayList<>();

        for (Iterator<?> dependencies = project.getDependencies().iterator(); dependencies.hasNext();) {
            Dependency dep = (Dependency) dependencies.next();
            Artifact art = repositorySystem.createDependencyArtifact(dep);
            if (!art.isResolved()) {
                ArtifactResolutionRequest req = new ArtifactResolutionRequest();
                req.setArtifact(art);
                req.setResolveTransitively(true);
                req.setLocalRepository(localRepository);
                req.setRemoteRepositories(remoteRepositories);
                ArtifactResolutionResult res = artifactResolver.resolve(req);
                if (res.isSuccess()) {
                    answer.addAll(res.getArtifacts());
                }
            } else {
                answer.add(art);
            }
        }

        return answer;
    }

    private Collection<Artifact> getAllSourceOnlyDependencies(Collection<Artifact> artifacts) {
        List<Artifact> answer = new ArrayList<>();

        for (Artifact art : artifacts) {
            Artifact sourceArt = repositorySystem.createArtifactWithClassifier(art.getGroupId(), art.getArtifactId(), art.getVersion(), art.getType(), "sources");
            if (!sourceArt.isResolved()) {
                ArtifactResolutionRequest req = new ArtifactResolutionRequest();
                req.setArtifact(sourceArt);
                req.setResolveTransitively(false);
                req.setLocalRepository(localRepository);
                req.setRemoteRepositories(remoteRepositories);
                ArtifactResolutionResult res = artifactResolver.resolve(req);
                if (res.isSuccess()) {
                    for (Artifact a : res.getArtifacts()) {
                        answer.add(a);
                    }
                }
            } else {
                answer.add(sourceArt);
            }
        }
        return answer;
    }

    static String safeJavaType(String javaType) {
        int pos = javaType.indexOf('<');
        if (pos > 0) {
            return javaType.substring(0, pos);
        }
        return javaType;
    }

}
