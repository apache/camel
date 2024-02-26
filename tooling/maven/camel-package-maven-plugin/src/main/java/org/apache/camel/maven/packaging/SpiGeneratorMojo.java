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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.maven.packaging.generics.PackagePluginUtils;
import org.apache.camel.spi.annotations.ConstantProvider;
import org.apache.camel.spi.annotations.ServiceFactory;
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

@Mojo(name = "generate-spi", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
      defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class SpiGeneratorMojo extends AbstractGeneratorMojo {

    public static final DotName SERVICE_FACTORY = DotName.createSimple(ServiceFactory.class.getName());
    public static final DotName CONSTANT_PROVIDER = DotName.createSimple(ConstantProvider.class.getName());

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    protected File classesDirectory;
    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File sourcesOutputDir;
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

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
        // @ServiceFactory and children
        //
        for (AnnotationInstance sfa : index.getAnnotations(SERVICE_FACTORY)) {
            if (sfa.target().kind() != Kind.CLASS || sfa.target().asClass().nestingType() != NestingType.TOP_LEVEL) {
                continue;
            }
            DotName sfaName = sfa.target().asClass().name();
            for (AnnotationInstance annotation : index.getAnnotations(sfaName)) {
                if (annotation.target().kind() != Kind.CLASS) {
                    continue;
                }
                if (annotation.target().asClass().nestingType() != NestingType.TOP_LEVEL
                        && annotation.target().asClass().nestingType() != NestingType.INNER) {
                    continue;
                }
                String className = annotation.target().asClass().name().toString();
                if (!isLocal(className)) {
                    continue;
                }
                String pvals;
                // @DataTypeTransformer uses name instead of value
                if (annotation.value() == null) {
                    pvals = annotation.values().stream()
                            .filter(annotationValue -> "name".equals(annotationValue.name()))
                            .map(name -> name.value().toString())
                            .findFirst().get();
                } else {
                    pvals = annotation.value().asString();
                }
                for (String pval : pvals.split(",")) {
                    pval = sanitizeFileName(pval);
                    StringBuilder sb = new StringBuilder();
                    sb.append("# ").append(GENERATED_MSG).append(NL).append("class=").append(className).append(NL);
                    if (ServiceFactory.JDK_SERVICE.equals(sfa.value().asString())) {
                        updateResource(resourcesOutputDir.toPath(),
                                "META-INF/services/org/apache/camel/" + pval,
                                sb.toString());
                    } else {
                        updateResource(resourcesOutputDir.toPath(),
                                "META-INF/services/org/apache/camel/" + sfa.value().asString() + "/" + pval,
                                sb.toString());
                    }
                }
            }
        }
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9-/+]", "-");
    }

    private boolean isLocal(String className) {
        Path output = Paths.get(project.getBuild().getOutputDirectory());
        Path file = output.resolve(className.replace('.', '/') + ".class");
        return Files.isRegularFile(file);
    }

    private IndexView getIndex() throws MojoExecutionException {
        Pattern cpePattern = Pattern.compile(".*/camel-[^/]+.jar");
        try {
            List<IndexView> indices = new ArrayList<>();
            indices.add(PackagePluginUtils.readJandexIndex(project));

            for (String cpe : project.getCompileClasspathElements()) {
                Matcher matcher = cpePattern.matcher(cpe);
                if (matcher.matches()) {
                    addIndex(indices, cpe);
                }
            }
            return CompositeIndex.create(indices);
        } catch (Exception e) {
            throw new MojoExecutionException("IOException: " + e.getMessage(), e);
        }
    }

    private void addIndex(List<IndexView> indices, String cpe) throws IOException {
        try (JarFile jf = new JarFile(cpe)) {
            JarEntry indexEntry = jf.getJarEntry("META-INF/jandex.idx");
            if (indexEntry != null) {
                readIndexFromJandex(indices, jf, indexEntry);
            } else {
                createIndexFromClass(indices, jf);
            }
        }
    }

    private void createIndexFromClass(List<IndexView> indices, JarFile jf) throws IOException {
        final Indexer indexer = new Indexer();

        List<JarEntry> classes = jf.stream()
                .filter(je -> je.getName().endsWith(".class"))
                .toList();

        for (JarEntry je : classes) {
            try (InputStream is = jf.getInputStream(je)) {
                indexer.index(is);
            }
        }

        indices.add(indexer.complete());
    }

    private void readIndexFromJandex(List<IndexView> indices, JarFile jf, JarEntry indexEntry) throws IOException {
        try (InputStream is = jf.getInputStream(indexEntry)) {
            indices.add(new IndexReader(is).read());
        }
    }

    private String generateConstantProviderClass(String fqn, Map<String, String> fields) {
        String pn = fqn.substring(0, fqn.lastIndexOf('.'));
        String cn = fqn.substring(fqn.lastIndexOf('.') + 1);

        StringBuilder w = new StringBuilder();
        w.append("/* ").append(GENERATED_MSG).append(" */\n");
        w.append("package ").append(pn).append(";\n");
        w.append("\n");
        w.append("import java.util.HashMap;\n");
        w.append("import java.util.Map;\n");
        w.append("\n");
        w.append("/**\n");
        w.append(" * ").append(GENERATED_MSG).append("\n");
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
