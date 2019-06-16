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
import java.io.FileOutputStream;
import java.io.IOException;
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
import java.util.stream.Collectors;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.catalog.JSonSchemaHelper;
import org.apache.camel.catalog.maven.MavenVersionManager;
import org.apache.camel.util.IOHelper;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExcludesArtifactFilter;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.mojo.exec.AbstractExecMojo;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

/**
 * Pre scans your project and prepare autowiring by classpath scanning
 */
@Mojo(name = "autowire", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class AutowireMojo extends AbstractExecMojo {

    /**
     * Whether to allow downloading Camel catalog version from the internet. This is needed if the project
     * uses a different Camel version than this plugin is using by default.
     */
    @Parameter(property = "camel.downloadVersion", defaultValue = "true")
    private boolean downloadVersion;

    /**
     * Whether to log the classpath when starting
     */
    @Parameter(property = "camel.logClasspath", defaultValue = "false")
    protected boolean logClasspath;

    /**
     * The output directory for generated autowire file
     */
    @Parameter(readonly = true, defaultValue = "${project.build.directory}/classes/META-INF/services/org/apache/camel/")
    protected File outFolder;

    @Component
    private ArtifactFactory artifactFactory;

    @Component
    private ArtifactResolver artifactResolver;

    @Parameter(property = "localRepository")
    private ArtifactRepository localRepository;

    @Parameter(property = "project.remoteArtifactRepositories")
    private List remoteRepositories;

    private transient ClassLoader classLoader;

    // TODO: Allow to configure known types in xml config, or refer to external file
    // TODO: Allow to configure include/exclude names on components,names
    // TODO: Skip some known types

    // CHECKSTYLE:OFF
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        CamelCatalog catalog = new DefaultCamelCatalog();
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
        Set<String> components = resolveCamelComponentsFromClasspath();
        if (components.isEmpty()) {
            getLog().warn("No Camel components discovered in classpath");
            return;
        } else {
            getLog().info("Discovered " + components.size() + " Camel components from classpath: " + components);
        }

        // build index of classes on classpath
        getLog().debug("Indexing classes on classpath");
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .addUrls(ClasspathHelper.forClassLoader(classLoader))
                .addClassLoader(classLoader)
                .setScanners(new SubTypesScanner()));

        // load known types
        Properties knownTypes = loadKnownTypes();
        getLog().debug("Loaded known-types: " + knownTypes);

        // find the autowire via classpath scanning
        List<String> autowires = findAutowireComponentOptionsByClasspath(catalog, components, reflections, knownTypes);

        if (!autowires.isEmpty()) {
            outFolder.mkdirs();
            File file = new File(outFolder, "autowire.properties");
            try {
                FileOutputStream fos = new FileOutputStream(file, false);
                fos.write("# Generated by camel build tools\n".getBytes());
                for (String line : autowires) {
                    fos.write(line.getBytes());
                    fos.write("\n".getBytes());
                }
                IOHelper.close(fos);
                getLog().info("Created file: " + file + " (autowire by classpath: " + autowires.size() + ")");
            } catch (Throwable e) {
                throw new MojoFailureException("Cannot write to file " + file + " due " + e.getMessage(), e);
            }
        }
    }

    protected Properties loadKnownTypes() throws MojoFailureException {
        Properties knownTypes = new Properties();
        try {
            InputStream is = AutowireMojo.class.getResourceAsStream("/known-types.properties");
            if (is != null) {
                knownTypes.load(is);
            }
        } catch (IOException e) {
            throw new MojoFailureException("Cannot load known-types.properties from classpath");
        }
        return knownTypes;
    }

    protected List<String> findAutowireComponentOptionsByClasspath(CamelCatalog catalog, Set<String> components,
                                                                   Reflections reflections, Properties knownTypes) {
        List<String> autowires = new ArrayList<>();

        for (String componentName : components) {
            getLog().debug("Autowiring Camel component: " + componentName);

            String json = catalog.componentJSonSchema(componentName);
            if (json == null) {
                getLog().debug("Cannot find component JSon metadata for component: " + componentName);
                continue;
            }

            List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);
            Set<String> names = JSonSchemaHelper.getNames(rows);
            for (String name : names) {
                Map<String, String> row = JSonSchemaHelper.getRow(rows, name);
                String type = row.get("type");
                String javaType = safeJavaType(row.get("javaType"));
                if ("object".equals(type)) {
                    try {
                        Class clazz = classLoader.loadClass(javaType);
                        if (clazz.isInterface() && isComplexUserType(clazz)) {
                            Set<Class<?>> classes = reflections.getSubTypesOf(clazz);
                            // filter classes to not be interfaces or not a top level class
                            classes = classes.stream().filter(c -> !c.isInterface() && c.getEnclosingClass() == null).collect(Collectors.toSet());
                            Class best = chooseBestKnownType(clazz, classes, knownTypes);
                            if (isValidAutowireClass(best)) {
                                String line = "camel.component." + componentName + "." + name + "=#class:" + best.getName();
                                getLog().debug(line);
                                autowires.add(line);
                            }
                        }

                    } catch (Exception e) {
                        // ignore
                        getLog().debug("Cannot load class: " + name, e);
                    }
                }
            }
        }

        return autowires;
    }

    protected Class chooseBestKnownType(Class type, Set<Class<?>> candidates, Properties knownTypes) {
        String known = knownTypes.getProperty(type.getName());
        if (known != null) {
            for (String k : known.split(",")) {
                // special as we should skip this option
                if ("#skip#".equals(k)) {
                    return null;
                }
                Class found = candidates.stream().filter(c -> c.getName().equals(k)).findFirst().orElse(null);
                if (found != null) {
                    return found;
                }
            }
        }

        if (candidates.size() == 1) {
            return candidates.iterator().next();
        } else if (candidates.size() > 1) {
            getLog().debug("Cannot chose best type: " + type.getName() + " among " + candidates.size() + " implementations: " + candidates);
        }
        return null;
    }

    private static boolean isComplexUserType(Class type) {
        // lets consider all non java, as complex types
        return type != null && !type.isPrimitive() && !type.getName().startsWith("java.");
    }

    protected boolean isValidAutowireClass(Class clazz) {
        // skip all from Apache Camel and regular JDK as they would be default anyway
        return !clazz.getName().startsWith("org.apache.camel");
    }

    protected String safeJavaType(String javaType) {
        int pos = javaType.indexOf('<');
        if (pos > 0) {
            return javaType.substring(0, pos);
        }
        return javaType;
    }

    protected Set<String> resolveCamelComponentsFromClasspath() throws MojoFailureException {
        Set<String> components = new TreeSet<>();
        try {
            classLoader = getClassLoader();
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

    private static String findCamelVersion(MavenProject project) {
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

    /**
     * Set up a classloader for scanning
     */
    private ClassLoader getClassLoader() throws MalformedURLException, MojoExecutionException {
        Set<URL> classpathURLs = new LinkedHashSet<>();

        // add project classpath
        URL mainClasses = new File(project.getBuild().getOutputDirectory()).toURI().toURL();
        classpathURLs.add(mainClasses);

        // add maven dependencies
        Set<Artifact> deps = project.getArtifacts();
        deps.addAll(getAllNonTestScopedDependencies());
        for (Artifact dep : deps) {
            File file = dep.getFile();
            if (file != null) {
                classpathURLs.add(file.toURI().toURL());
            }
        }

        if (logClasspath) {
            getLog().info("Classpath:");
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
            if (!artifact.getScope().equals(Artifact.SCOPE_TEST)) {

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
    private Collection<Artifact> getAllDependencies() throws MojoExecutionException {
        List<Artifact> artifacts = new ArrayList<>();

        for (Iterator<?> dependencies = project.getDependencies().iterator(); dependencies.hasNext();) {
            Dependency dependency = (Dependency)dependencies.next();

            String groupId = dependency.getGroupId();
            String artifactId = dependency.getArtifactId();

            VersionRange versionRange;
            try {
                versionRange = VersionRange.createFromVersionSpec(dependency.getVersion());
            } catch (InvalidVersionSpecificationException e) {
                throw new MojoExecutionException("unable to parse version", e);
            }

            String type = dependency.getType();
            if (type == null) {
                type = "jar";
            }
            String classifier = dependency.getClassifier();
            boolean optional = dependency.isOptional();
            String scope = dependency.getScope();
            if (scope == null) {
                scope = Artifact.SCOPE_COMPILE;
            }

            Artifact art = this.artifactFactory.createDependencyArtifact(groupId, artifactId, versionRange,
                    type, classifier, scope, null, optional);

            if (scope.equalsIgnoreCase(Artifact.SCOPE_SYSTEM)) {
                art.setFile(new File(dependency.getSystemPath()));
            }

            List<String> exclusions = new ArrayList<>();
            for (Exclusion exclusion : dependency.getExclusions()) {
                exclusions.add(exclusion.getGroupId() + ":" + exclusion.getArtifactId());
            }

            ArtifactFilter newFilter = new ExcludesArtifactFilter(exclusions);

            art.setDependencyFilter(newFilter);

            artifacts.add(art);
        }

        return artifacts;
    }

}
