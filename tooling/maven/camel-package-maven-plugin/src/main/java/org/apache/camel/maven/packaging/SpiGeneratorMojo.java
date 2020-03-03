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
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import org.apache.camel.spi.annotations.ConstantProvider;
import org.apache.camel.spi.annotations.ServiceFactory;
import org.apache.camel.spi.annotations.SubServiceFactory;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.ClassInfo.NestingType;
import org.jboss.jandex.CompositeIndex;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;

@Mojo(name = "generate-spi", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class SpiGeneratorMojo extends AbstractGeneratorMojo {

    public static final DotName SERVICE_FACTORY = DotName.createSimple(ServiceFactory.class.getName());
    public static final DotName SUB_SERVICE_FACTORY = DotName.createSimple(SubServiceFactory.class.getName());
    public static final DotName CONSTANT_PROVIDER = DotName.createSimple(ConstantProvider.class.getName());

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    protected File classesDirectory;
    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File sourcesOutputDir;
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    private ClassLoader projectClassLoader;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (classesDirectory == null) {
            classesDirectory = new File(project.getBuild().getOutputDirectory());
        }
        if (sourcesOutputDir == null) {
            sourcesOutputDir = new File(project.getBasedir(), "src/generated/java");
        }
        if (resourcesOutputDir == null) {
            resourcesOutputDir = new File(project.getBasedir(), "src/generated/resources");
        }
        if (!classesDirectory.isDirectory()) {
            return;
        }

        IndexView index = getIndex();

        //
        // @ConstantProvider
        //
        for (AnnotationInstance cpa : index.getAnnotations(CONSTANT_PROVIDER)) {
            if (cpa.target().kind() != Kind.CLASS || cpa.target().asClass().nestingType() != NestingType.TOP_LEVEL) {
                continue;
            }
            String providerClassName = cpa.value().asString();
            String className = cpa.target().asClass().name().toString();
            if (!isLocal(className)) {
                continue;
            }

            Map<String, String> fields = new TreeMap<>(String::compareToIgnoreCase);
            try {
                Class<?> cl = getProjectClassLoader().loadClass(className);
                for (Field field : cl.getDeclaredFields()) {
                    if (field.getType() == String.class && Modifier.isPublic(field.getModifiers())) {
                        fields.put(field.getName(), field.get(null).toString());
                    }
                }
            } catch (Exception e) {
                throw new MojoExecutionException("Unable to load class", e);
            }

            String source = generateConstantProviderClass(providerClassName, fields);
            updateResource(
                    sourcesOutputDir.toPath(),
                    providerClassName.replace('.', '/') + ".java",
                    source);
        }

        //
        // @ServiceFactory
        // @SubServiceFactory
        //
        // @CloudServiceFactory
        // @Component
        // @Dataformat
        // @Language
        // @SendDynamic
        //
        for (AnnotationInstance sfa : index.getAnnotations(SERVICE_FACTORY)) {
            if (sfa.target().kind() != Kind.CLASS || sfa.target().asClass().nestingType() != NestingType.TOP_LEVEL) {
                continue;
            }
            DotName sfaName = sfa.target().asClass().name();
            for  (AnnotationInstance annotation : index.getAnnotations(sfaName)) {
                if (annotation.target().kind() != Kind.CLASS || annotation.target().asClass().nestingType() != NestingType.TOP_LEVEL) {
                    continue;
                }
                String className = annotation.target().asClass().name().toString();
                if (!isLocal(className)) {
                    continue;
                }
                String pvals = annotation.value().asString();
                for (String pval : pvals.split(",")) {
                    if (ServiceFactory.JDK_SERVICE.equals(sfa.value().asString())) {
                        updateResource(resourcesOutputDir.toPath(),
                                "META-INF/services/org/apache/camel/" + pval,
                                "# " + GENERATED_MSG + NL + "class=" + className + NL);
                    } else {
                        StringBuilder sb = new StringBuilder();
                        sb.append("# " + GENERATED_MSG + NL + "class=").append(className).append(NL);
                        for (AnnotationInstance ai : annotation.target().asClass().classAnnotations()) {
                            AnnotationInstance ssf = index.getClassByName(ai.name()).classAnnotation(SUB_SERVICE_FACTORY);
                            if (ssf != null) {
                                sb.append(ssf.value().asString()).append(".class=").append(ai.value().asString()).append(NL);
                            }
                        }
                        updateResource(resourcesOutputDir.toPath(),
                                "META-INF/services/org/apache/camel/" + sfa.value().asString() + "/" + pval,
                                sb.toString());
                    }
                }
            }
        }
    }

    private boolean isLocal(String className) {
        Path output = Paths.get(project.getBuild().getOutputDirectory());
        Path file = output.resolve(className.replace('.', '/') + ".class");
        return Files.isRegularFile(file);
    }

    private ClassLoader getProjectClassLoader() throws MojoExecutionException {
        if (projectClassLoader == null) {
            projectClassLoader = createProjectClassLoader();
        }
        return projectClassLoader;
    }

    private DynamicClassLoader createProjectClassLoader() throws MojoExecutionException {
        try {
            return DynamicClassLoader.createDynamicClassLoader(project.getCompileClasspathElements());
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Unable to create project classloader", e);
        }
    }

    private IndexView getIndex() throws MojoExecutionException {
        try {
            List<IndexView> indices = new ArrayList<>();
            Path output = Paths.get(project.getBuild().getOutputDirectory());
            try (InputStream is = Files.newInputStream(output.resolve("META-INF/jandex.idx"))) {
                indices.add(new IndexReader(is).read());
            }
            for (String cpe : project.getCompileClasspathElements()) {
                if (cpe.matches(".*/camel-[^/]+.jar")) {
                    try (JarFile jf = new JarFile(cpe)) {
                        JarEntry indexEntry = jf.getJarEntry("META-INF/jandex.idx");
                        if (indexEntry != null) {
                            try (InputStream is = jf.getInputStream(indexEntry)) {
                                indices.add(new IndexReader(is).read());
                            }
                        } else {
                            final Indexer indexer = new Indexer();
                            List<JarEntry> classes = jf.stream()
                                    .filter(je -> je.getName().endsWith(".class"))
                                    .collect(Collectors.toList());
                            for (JarEntry je : classes) {
                                try (InputStream is = jf.getInputStream(je)) {
                                    indexer.index(is);
                                }
                            }
                            indices.add(indexer.complete());
                        }
                    }
                }
            }
            return CompositeIndex.create(indices);
        } catch (Exception e) {
            throw new MojoExecutionException("IOException: " + e.getMessage(), e);
        }
    }

    private String generateConstantProviderClass(String fqn, Map<String, String> fields) {
        String pn = fqn.substring(0, fqn.lastIndexOf('.'));
        String cn = fqn.substring(fqn.lastIndexOf('.') + 1);

        StringBuilder w = new StringBuilder(); 
        w.append("/* " + GENERATED_MSG + " */\n");
        w.append("package ").append(pn).append(";\n");
        w.append("\n");
        w.append("import java.util.HashMap;\n");
        w.append("import java.util.Map;\n");
        w.append("\n");
        w.append("/**\n");
        w.append(" * " + GENERATED_MSG + "\n");
        w.append(" */\n");
        w.append("public class ").append(cn).append(" {\n");
        w.append("\n");
        w.append("    private static final Map<String, String> MAP;\n");
        w.append("    static {\n");
        w.append("        Map<String, String> map = new HashMap<>(").append(fields.size()).append(");\n");
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            w.append("        map.put(\"").append(entry.getKey()).append("\", \"").append(entry.getValue()).append("\");\n");
        }
        w.append("        MAP = map;\n");
        w.append("    }\n");
        w.append("\n");
        w.append("    public static String lookup(String key) {\n");
        w.append("        return MAP.get(key);\n");
        w.append("    }\n");
        w.append("}\n");
        w.append("\n");

        return w.toString();
    }

}
