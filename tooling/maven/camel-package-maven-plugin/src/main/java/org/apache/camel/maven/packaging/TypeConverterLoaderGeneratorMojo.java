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
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo.NestingType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

@Mojo(name = "generate-type-converter-loader", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class TypeConverterLoaderGeneratorMojo extends AbstractGeneratorMojo {

    public static final DotName CONVERTER_ANNOTATION = DotName.createSimple("org.apache.camel.Converter");

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
        if ("pom".equals(project.getPackaging())) {
            return;
        }

        Path output = Paths.get(project.getBuild().getOutputDirectory());
        Index index;
        try (InputStream is = Files.newInputStream(output.resolve("META-INF/jandex.idx"))) {
            index = new IndexReader(is).read();
        } catch (IOException e) {
            throw new MojoExecutionException("IOException: " + e.getMessage(), e);
        }

        Map<String, ClassConverters> converters = new TreeMap<>();

        List<AnnotationInstance> annotations = index.getAnnotations(CONVERTER_ANNOTATION);
        annotations.stream()
                .filter(annotation -> annotation.target().kind() == Kind.CLASS)
                .filter(annotation -> annotation.target().asClass().nestingType() == NestingType.TOP_LEVEL)
                .filter(annotation -> asBoolean(annotation, "generateLoader"))
                .forEach(annotation -> {
                    String currentClass = annotation.target().asClass().name().toString();
                    ClassConverters classConverters = new ClassConverters();
                    converters.put(currentClass + "Loader", classConverters);
                    classConverters.setIgnoreOnLoadError(asBoolean(annotation, "ignoreOnLoadError"));
                    annotations.stream()
                            .filter(an -> an.target().kind() == Kind.METHOD)
                            .filter(an -> currentClass.equals(an.target().asMethod().declaringClass().name().toString()))
                            .forEach(an -> {
                                // is the method annotated with @Converter
                                MethodInfo ee = an.target().asMethod();
                                if (asBoolean(an, "fallback")) {
                                    classConverters.addFallbackTypeConverter(ee);
                                } else {
                                    Type to = ee.returnType();
                                    Type from = ee.parameters().get(0);
                                    //                                    String fromStr = ClassConverters.toString(from);
                                    //                                    if (!fromStr.endsWith("[]")) {
                                    //                                        TypeElement e = this.processingEnv.getElementUtils().getTypeElement(fromStr);
                                    //                                        if (e != null) {
                                    //                                            from = e.asType();
                                    //                                        } else {
                                    //                                            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Could not retrieve type element for " + fromStr);
                                    //                                        }
                                    //                                    }
                                    classConverters.addTypeConverter(to, from, ee);
                                }
                            });
                });
        if (!converters.isEmpty()) {
            converters.forEach((currentClass, classConverters) -> {
                String source = writeConverters(currentClass, classConverters);
                updateResource(sourcesOutputDir.toPath(), currentClass.replace('.', '/') + ".java", source);
            });
            updateResource(resourcesOutputDir.toPath(),
                    "META-INF/services/org/apache/camel/TypeConverterLoader",
                    "# " + GENERATED_MSG + NL + String.join(NL, converters.keySet()) + NL);
        }
    }

    private String writeConverters(String fqn, ClassConverters converters) {

        int pos = fqn.lastIndexOf('.');
        String p = fqn.substring(0, pos);
        String c = fqn.substring(pos + 1);

        Set<String> converterClasses = new LinkedHashSet<>();
        StringBuilder writer = new StringBuilder();

        writer.append("/* ").append(GENERATED_MSG).append(" */\n");
        writer.append("package ").append(p).append(";\n");
        writer.append("\n");
        writer.append("import org.apache.camel.Exchange;\n");
        writer.append("import org.apache.camel.TypeConversionException;\n");
        writer.append("import org.apache.camel.TypeConverterLoaderException;\n");
        writer.append("import org.apache.camel.spi.TypeConverterLoader;\n");
        writer.append("import org.apache.camel.spi.TypeConverterRegistry;\n");
        writer.append("import org.apache.camel.support.SimpleTypeConverter;\n");
        writer.append("import org.apache.camel.support.TypeConverterSupport;\n");
        writer.append("import org.apache.camel.util.DoubleMap;\n");
        writer.append("\n");
        writer.append("/**\n");
        writer.append(" * ").append(GENERATED_MSG).append("\n");
        writer.append(" */\n");
        writer.append("@SuppressWarnings(\"unchecked\")\n");
        writer.append("public final class ").append(c).append(" implements TypeConverterLoader {\n");
        writer.append("\n");
        writer.append("    ").append("public ").append(c).append("() {\n");
        writer.append("    }\n");
        writer.append("\n");
        writer.append("    @Override\n");
        writer.append("    public void load(TypeConverterRegistry registry) throws TypeConverterLoaderException {\n");
        if (converters.size() > 0) {
            if (converters.isIgnoreOnLoadError()) {
                writer.append("        try {\n");
                writer.append("            registerConverters(registry);\n");
                writer.append("        } catch (Throwable e) {\n");
                writer.append("            // ignore on load error\n");
                writer.append("        }\n");
            } else {
                writer.append("        registerConverters(registry);\n");
            }
        }
        if (converters.sizeFallback() > 0) {
            writer.append("        registerFallbackConverters(registry);\n");
        }
        writer.append("    }\n");
        writer.append("\n");

        if (converters.size() > 0) {
            writer.append("    private void registerConverters(TypeConverterRegistry registry) {\n");
            for (Map.Entry<String, Map<Type, MethodInfo>> to : converters.getConverters().entrySet()) {
                for (Map.Entry<Type, MethodInfo> from : to.getValue().entrySet()) {
                    boolean allowNull = isAllowNull(from.getValue());
                    writer.append("        addTypeConverter(registry, ").append(to.getKey()).append(".class").append(", ").append(toString(from.getKey())).append(".class, ")
                            .append(Boolean.toString(allowNull)).append(",\n");
                    writer.append("            (type, exchange, value) -> ").append(toJava(from.getValue(), converterClasses)).append(");\n");
                }
            }
            writer.append("    }\n");
            writer.append("\n");

            writer
                    .append("    private static void addTypeConverter(TypeConverterRegistry registry, Class<?> toType, Class<?> fromType, boolean allowNull, SimpleTypeConverter.ConversionMethod method)"
                            + " { \n");
            writer.append("        registry.addTypeConverter(toType, fromType, new SimpleTypeConverter(allowNull, method));\n");
            writer.append("    }\n");
            writer.append("\n");
        }

        if (converters.sizeFallback() > 0) {
            writer.append("    private void registerFallbackConverters(TypeConverterRegistry registry) {\n");
            for (MethodInfo ee : converters.getFallbackConverters()) {
                boolean allowNull = isAllowNull(ee);
                boolean canPromote = isFallbackCanPromote(ee);
                writer.append("        addFallbackTypeConverter(registry, ").append(Boolean.toString(allowNull)).append(", ").append(Boolean.toString(canPromote)).append(", ")
                        .append("(type, exchange, value) -> ").append(toJavaFallback(ee, converterClasses)).append(");\n");
            }
            writer.append("    }\n");
            writer.append("\n");

            writer
                    .append("    private static void addFallbackTypeConverter(TypeConverterRegistry registry, boolean allowNull, boolean canPromote, SimpleTypeConverter.ConversionMethod method) { \n");
            writer.append("        registry.addFallbackTypeConverter(new SimpleTypeConverter(allowNull, method), canPromote);\n");
            writer.append("    }\n");
            writer.append("\n");
        }

        for (String f : converterClasses) {
            String s = f.substring(f.lastIndexOf('.') + 1);
            String v = s.substring(0, 1).toLowerCase() + s.substring(1);
            writer.append("    private volatile ").append(f).append(" ").append(v).append(";\n");
            writer.append("    private ").append(f).append(" get").append(s).append("() {\n");
            writer.append("        if (").append(v).append(" == null) {\n");
            writer.append("            ").append(v).append(" = new ").append(f).append("();\n");
            writer.append("        }\n");
            writer.append("        return ").append(v).append(";\n");
            writer.append("    }\n");
        }

        writer.append("}\n");
        return writer.toString();
    }

    private String toString(Type type) {
        return type.toString()
                .replaceAll("<.*>", "")
                .replace('$', '.');
    }

    private String toJava(MethodInfo converter, Set<String> converterClasses) {
        String pfx;
        if (Modifier.isStatic(converter.flags())) {
            pfx = converter.declaringClass().toString() + "." + converter.name();
        } else {
            converterClasses.add(converter.declaringClass().toString());
            pfx = "get" + converter.declaringClass().simpleName() + "()." + converter.name();
        }
        String type = toString(converter.parameters().get(0));
        String cast = type.equals("java.lang.Object") ? "" : "(" + type + ") ";
        return pfx + "(" + cast + "value" + (converter.parameters().size() == 2 ? ", exchange" : "") + ")";
    }

    private String toJavaFallback(MethodInfo converter, Set<String> converterClasses) {
        String pfx;
        if (Modifier.isStatic(converter.flags())) {
            pfx = converter.declaringClass().toString() + "." + converter.name();
        } else {
            converterClasses.add(converter.declaringClass().toString());
            pfx = "get" + converter.declaringClass().simpleName() + "()." + converter.name();
        }
        String type = toString(converter.parameters().get(converter.parameters().size() - 2));
        String cast = type.equals("java.lang.Object") ? "" : "(" + type + ") ";
        return pfx + "(type, " + (converter.parameters().size() == 4 ? "exchange, " : "") + cast + "value" + ", registry)";
    }

    private static boolean isFallbackCanPromote(MethodInfo element) {
        return asBoolean(element.annotation(CONVERTER_ANNOTATION), "fallbackCanPromote");
    }

    private static boolean isAllowNull(MethodInfo element) {
        return asBoolean(element.annotation(CONVERTER_ANNOTATION), "allowNull");
    }

    private static boolean asBoolean(AnnotationInstance ai, String name) {
        AnnotationValue av = ai.value(name);
        return av != null && av.asBoolean();
    }

    public static final class ClassConverters {

        private final Comparator<Type> comparator;
        private final Map<String, Map<Type, MethodInfo>> converters = new TreeMap<>();
        private final List<MethodInfo> fallbackConverters = new ArrayList<>();
        private int size;
        private int sizeFallback;
        private boolean ignoreOnLoadError;

        ClassConverters() {
            this.comparator = (o1, o2) -> o1.toString().compareTo(o2.toString());
//                    processingEnv.getTypeUtils().isAssignable(o1, o2)
//                        ? -1
//                        : processingEnv.getTypeUtils().isAssignable(o2, o1)
//                            ? +1
//                            : o1.toString().compareTo(o2.toString());
        }

        public boolean isIgnoreOnLoadError() {
            return ignoreOnLoadError;
        }

        void setIgnoreOnLoadError(boolean ignoreOnLoadError) {
            this.ignoreOnLoadError = ignoreOnLoadError;
        }

        void addTypeConverter(Type to, Type from, MethodInfo ee) {
            converters.computeIfAbsent(toString(to), c -> new TreeMap<>(comparator)).put(from, ee);
            size++;
        }

        void addFallbackTypeConverter(MethodInfo ee) {
            fallbackConverters.add(ee);
            sizeFallback++;
        }

        public Map<String, Map<Type, MethodInfo>> getConverters() {
            return converters;
        }

        public List<MethodInfo> getFallbackConverters() {
            return fallbackConverters;
        }

        public long size() {
            return size;
        }

        public long sizeFallback() {
            return sizeFallback;
        }

        public boolean isEmpty() {
            return size == 0 && sizeFallback == 0;
        }

        private static String toString(Type type) {
            return type.toString()
                    .replaceAll("<.*>", "")
                    .replace('$', '.');
        }

    }

}
