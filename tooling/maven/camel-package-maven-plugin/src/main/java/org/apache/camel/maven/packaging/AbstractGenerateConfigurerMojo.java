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
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.maven.packaging.generics.PackagePluginUtils;
import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.util.ReflectionHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExcludesArtifactFilter;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;

import static org.apache.camel.maven.packaging.generics.PackagePluginUtils.readJandexIndex;
import static org.apache.camel.tooling.util.ReflectionHelper.doWithMethods;
import static org.apache.camel.tooling.util.Strings.between;

/**
 * Abstract class for configurer generator.
 */
public abstract class AbstractGenerateConfigurerMojo extends AbstractGeneratorMojo {

    public static final DotName CONFIGURER = DotName.createSimple("org.apache.camel.spi.Configurer");

    /**
     * Whether to discover configurer classes from classpath by scanning for @Configurer annotations. This requires
     * using jandex-maven-plugin.
     */
    @Parameter(defaultValue = "true")
    protected boolean discoverClasses = true;

    /**
     * Whether to also allow using fluent builder style as configurer (getXXX and withXXX style).
     */
    @Parameter(defaultValue = "false")
    protected boolean allowBuilderPattern;

    /**
     * Whether to skip deprecated methods.
     */
    @Parameter(defaultValue = "false")
    protected boolean skipDeprecated;

    @Component
    private ArtifactFactory artifactFactory;

    private DynamicClassLoader projectClassLoader;

    public static class ConfigurerOption extends BaseOptionModel {

        private final boolean builderMethod;

        public ConfigurerOption(String name, Class<?> type, String getter, boolean builderMethod) {
            // we just use name, type
            setName(name);
            if (byte[].class == type) {
                // special for byte array
                setJavaType("byte[]");
            } else if (long[].class == type) {
                // special for long array
                setJavaType("long[]");
            } else if (type.isArray()) {
                // special for array
                String arrType = between(type.getName(), "[L", ";") + "[]";
                setJavaType(arrType);
            } else {
                setJavaType(type.getName());
            }
            setGetterMethod(getter);
            this.builderMethod = builderMethod;
        }

        public boolean isBuilderMethod() {
            return builderMethod;
        }
    }

    public AbstractGenerateConfigurerMojo() {
    }

    protected void doExecute(File sourcesOutputDir, File resourcesOutputDir, List<String> classes, boolean testClasspathOnly)
            throws MojoExecutionException {
        if ("pom".equals(project.getPackaging())) {
            return;
        }

        if (sourcesOutputDir == null) {
            sourcesOutputDir = new File(project.getBasedir(), "src/generated/java");
        }
        if (resourcesOutputDir == null) {
            resourcesOutputDir = new File(project.getBasedir(), "src/generated/resources");
        }

        List<URL> urls = new ArrayList<>();
        // need to include project compile dependencies (code similar to camel-maven-plugin)
        addRelevantProjectDependenciesToClasspath(urls, testClasspathOnly);
        projectClassLoader = DynamicClassLoader.createDynamicClassLoaderFromUrls(urls);

        Set<String> set = new LinkedHashSet<>();
        Set<String> extendedSet = new LinkedHashSet<>();
        Set<String> bootstrapSet = new LinkedHashSet<>();
        Set<String> bootstrapAndExtendedSet = new LinkedHashSet<>();

        if (discoverClasses) {
            Index index = PackagePluginUtils.readJandexIndexIgnoreMissing(project, getLog());

            if (index != null) {
                // discover all classes annotated with @Configurer
                List<AnnotationInstance> annotations = index.getAnnotations(CONFIGURER);
                annotations.stream()
                        .filter(annotation -> annotation.target().kind() == AnnotationTarget.Kind.CLASS)
                        .filter(annotation -> annotation.target().asClass().nestingType() == ClassInfo.NestingType.TOP_LEVEL)
                        .filter(annotation -> asBooleanDefaultTrue(annotation, "generateConfigurer"))
                        .forEach(annotation -> {
                            String currentClass = annotation.target().asClass().name().toString();
                            addToSets(annotation, bootstrapAndExtendedSet, currentClass, bootstrapSet, extendedSet, set);
                        });
            }
        }

        // additional classes
        if (classes != null && !classes.isEmpty()) {
            Index index = readJandexIndex(project);
            for (String clazz : classes) {
                ClassInfo ci = index.getClassByName(DotName.createSimple(clazz));
                AnnotationInstance ai = ci != null ? ci.declaredAnnotation(CONFIGURER) : null;
                if (ai != null) {
                    addToSets(ai, bootstrapAndExtendedSet, clazz, bootstrapSet, extendedSet, set);
                } else {
                    set.add(clazz);
                }
            }
        }

        for (String fqn : set) {
            processClass(fqn, sourcesOutputDir, false, false, resourcesOutputDir);
        }
        for (String fqn : bootstrapSet) {
            processClass(fqn, sourcesOutputDir, false, true, resourcesOutputDir);
        }
        for (String fqn : extendedSet) {
            processClass(fqn, sourcesOutputDir, true, false, resourcesOutputDir);
        }
        for (String fqn : bootstrapAndExtendedSet) {
            processClass(fqn, sourcesOutputDir, true, true, resourcesOutputDir);
        }
    }

    private void addToSets(
            AnnotationInstance annotation, Set<String> bootstrapAndExtendedSet, String currentClass, Set<String> bootstrapSet,
            Set<String> extendedSet, Set<String> set) {
        boolean bootstrap = asBooleanDefaultFalse(annotation, "bootstrap");
        boolean extended = asBooleanDefaultFalse(annotation, "extended");
        if (bootstrap && extended) {
            bootstrapAndExtendedSet.add(currentClass);
        } else if (bootstrap) {
            bootstrapSet.add(currentClass);
        } else if (extended) {
            extendedSet.add(currentClass);
        } else {
            set.add(currentClass);
        }
    }

    private void processClass(String fqn, File sourcesOutputDir, boolean extended, boolean bootstrap, File resourcesOutputDir)
            throws MojoExecutionException {
        try {
            String targetFqn = fqn;
            int pos = fqn.indexOf('=');
            if (pos != -1) {
                targetFqn = fqn.substring(pos + 1);
                fqn = fqn.substring(0, pos);
            }
            List<ConfigurerOption> options = processClass(fqn);
            generateConfigurer(fqn, targetFqn, options, sourcesOutputDir, extended, bootstrap);
            generateMetaInfConfigurer(fqn, targetFqn, resourcesOutputDir);
        } catch (Exception e) {
            throw new MojoExecutionException("Error processing class: " + fqn, e);
        }
    }

    /**
     * Add any relevant project dependencies to the classpath. Takes includeProjectDependencies into consideration.
     *
     * @param path classpath of {@link URL} objects
     */
    private void addRelevantProjectDependenciesToClasspath(List<URL> path, boolean testClasspathOnly)
            throws MojoExecutionException {
        try {
            getLog().debug("Project Dependencies will be included.");

            if (testClasspathOnly) {
                URL testClasses = new File(project.getBuild().getTestOutputDirectory()).toURI().toURL();

                if (getLog().isDebugEnabled()) {
                    getLog().debug("Adding to classpath : " + testClasses);
                }
                path.add(testClasses);
            } else {
                URL mainClasses = new File(project.getBuild().getOutputDirectory()).toURI().toURL();

                if (getLog().isDebugEnabled()) {
                    getLog().debug("Adding to classpath : " + mainClasses);
                }
                path.add(mainClasses);
            }

            Set<Artifact> dependencies = project.getArtifacts();

            // system scope dependencies are not returned by maven 2.0. See
            // MEXEC-17
            dependencies.addAll(getAllNonTestScopedDependencies());

            for (Artifact classPathElement : dependencies) {
                if (getLog().isDebugEnabled()) {
                    getLog().debug("Adding project dependency artifact: " + classPathElement.getArtifactId()
                                   + " to classpath");
                }

                File file = classPathElement.getFile();
                if (file != null) {
                    path.add(file.toURI().toURL());
                }
            }

        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Error during setting up classpath", e);
        }
    }

    private Collection<Artifact> getAllNonTestScopedDependencies() throws MojoExecutionException {
        List<Artifact> answer = new ArrayList<>();

        for (Artifact artifact : getAllDependencies()) {

            // do not add test artifacts
            if (!artifact.getScope().equals(Artifact.SCOPE_TEST)) {
                answer.add(artifact);
            }
        }
        return answer;
    }

    // generic method to retrieve all the transitive dependencies
    private Collection<Artifact> getAllDependencies() throws MojoExecutionException {
        List<Artifact> artifacts = new ArrayList<>();

        for (Dependency dependency : project.getDependencies()) {
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

            if (this.artifactFactory != null) {
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
        }

        return artifacts;
    }

    private List<ConfigurerOption> processClass(String fqn) throws ClassNotFoundException {
        List<ConfigurerOption> answer = new ArrayList<>();
        // filter out duplicates by using a names set that has already added
        Set<String> names = new HashSet<>();

        Class<?> clazz = projectClassLoader.loadClass(fqn);
        // find all public setters
        doWithMethods(clazz, m -> {
            boolean deprecated = m.isAnnotationPresent(Deprecated.class);
            if (skipDeprecated && deprecated) {
                return;
            }

            boolean setter = m.getName().length() >= 4 && m.getName().startsWith("set")
                    && Character.isUpperCase(m.getName().charAt(3));
            setter &= Modifier.isPublic(m.getModifiers()) && m.getParameterCount() == 1;
            setter &= filterSetter(m);
            boolean builder = allowBuilderPattern && m.getName().length() >= 5 && m.getName().startsWith("with")
                    && Character.isUpperCase(m.getName().charAt(4));
            builder &= Modifier.isPublic(m.getModifiers()) && m.getParameterCount() == 1;
            builder &= filterSetter(m);
            if (setter || builder) {
                String getter = "get" + (builder
                        ? Character.toUpperCase(m.getName().charAt(4)) + m.getName().substring(5)
                        : Character.toUpperCase(m.getName().charAt(3)) + m.getName().substring(4));
                Class<?> type = m.getParameterTypes()[0];
                if (boolean.class == type || Boolean.class == type) {
                    try {
                        String isGetter = "is" + getter.substring(3);
                        clazz.getMethod(isGetter, null);
                        getter = isGetter;
                    } catch (Exception e) {
                        // ignore as its then assumed to get
                    }
                }

                ConfigurerOption option = null;
                String t = builder
                        ? Character.toUpperCase(m.getName().charAt(4)) + m.getName().substring(4 + 1)
                        : Character.toUpperCase(m.getName().charAt(3)) + m.getName().substring(3 + 1);
                if (names.add(t)) {
                    option = new ConfigurerOption(t, type, getter, builder);
                    answer.add(option);
                } else {
                    boolean replace = false;
                    // try to find out what the real type is of the correspondent field so we chose among the clash
                    Field field = ReflectionHelper.findField(clazz, Character.toLowerCase(t.charAt(0)) + t.substring(1));
                    if (field != null && field.getType().equals(type)) {
                        // this is the correct type for the new option
                        replace = true;
                    }
                    if (replace) {
                        answer.removeIf(o -> o.getName().equals(t));
                        option = new ConfigurerOption(t, type, getter, builder);
                        answer.add(option);
                    }
                }

                if (option != null) {
                    String desc = type.isArray() ? type.getComponentType().getName() : m.toGenericString();
                    if (desc.contains("<") && desc.contains(">")) {
                        desc = Strings.between(desc, "<", ">");
                        // if it has additional nested types, then we only want the outer type
                        int pos = desc.indexOf('<');
                        if (pos != -1) {
                            desc = desc.substring(0, pos);
                        }
                        // if its a map then it has a key/value, so we only want the last part
                        pos = desc.indexOf(',');
                        if (pos != -1) {
                            desc = desc.substring(pos + 1);
                        }
                        desc = desc.replace('$', '.');
                        desc = desc.trim();
                        // skip if the type is generic, or a wildcard (a single letter is regarded as unknown)
                        if (desc.length() > 1 && desc.indexOf('?') == -1 && !desc.contains(" extends ")) {
                            option.setNestedType(desc);
                        }
                    }
                }
            }
        });

        return answer;
    }

    private boolean filterSetter(Method setter) {
        // special for some
        if ("setBindingMode".equals(setter.getName()) || "setHostNameResolver".equals(setter.getName())) {
            // we only want the string setter
            return setter.getParameterTypes()[0] == String.class;
        }

        Metadata meta = setter.getAnnotation(Metadata.class);
        if (meta != null && meta.skip()) {
            return false;
        }

        return true;
    }

    private void generateConfigurer(
            String fqn, String targetFqn, List<ConfigurerOption> options, File outputDir, boolean extended, boolean bootstrap) {
        int pos = targetFqn.lastIndexOf('.');
        String pn = targetFqn.substring(0, pos);
        String cn = targetFqn.substring(pos + 1) + "Configurer";
        String en = fqn;
        String pfqn = fqn;
        String psn = "org.apache.camel.support.component.PropertyConfigurerSupport";

        String source = PropertyConfigurerGenerator.generatePropertyConfigurer(pn, cn, en, pfqn, psn,
                false, false, extended, bootstrap, options, null);

        String fileName = pn.replace('.', '/') + "/" + cn + ".java";
        outputDir.mkdirs();
        boolean updated = updateResource(buildContext, outputDir.toPath().resolve(fileName), source);
        if (updated) {
            getLog().info("Updated " + fileName);
        }
    }

    private void generateMetaInfConfigurer(String fqn, String targetFqn, File resourcesOutputDir) {
        int pos = targetFqn.lastIndexOf('.');
        String pn = targetFqn.substring(0, pos);
        String en = targetFqn.substring(pos + 1);
        try (Writer w = new StringWriter()) {
            w.append("# ").append(GENERATED_MSG).append("\n");
            w.append("class=").append(pn).append(".").append(en).append("Configurer").append("\n");
            String fileName = "META-INF/services/org/apache/camel/configurer/" + fqn;
            boolean updated = updateResource(buildContext, resourcesOutputDir.toPath().resolve(fileName), w.toString());
            if (updated) {
                getLog().info("Updated " + fileName);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean asBooleanDefaultTrue(AnnotationInstance ai, String name) {
        AnnotationValue av = ai.value(name);
        return av == null || av.asBoolean();
    }

    private static boolean asBooleanDefaultFalse(AnnotationInstance ai, String name) {
        AnnotationValue av = ai.value(name);
        return av != null && av.asBoolean();
    }

}
