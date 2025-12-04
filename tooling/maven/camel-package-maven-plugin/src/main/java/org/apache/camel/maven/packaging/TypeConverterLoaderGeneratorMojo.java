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

import static org.apache.camel.maven.packaging.SchemaHelper.dashToCamelCase;
import static org.apache.camel.maven.packaging.generics.PackagePluginUtils.readJandexIndex;

import java.io.File;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget.Kind;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo.NestingType;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

@Mojo(
        name = "generate-type-converter-loader",
        threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class TypeConverterLoaderGeneratorMojo extends AbstractGeneratorMojo {

    public static final DotName CONVERTER_ANNOTATION = DotName.createSimple("org.apache.camel.Converter");

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    protected File classesDirectory;

    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File sourcesOutputDir;

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    @Inject
    public TypeConverterLoaderGeneratorMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

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

        Index index = readJandexIndex(project);

        Map<String, ClassConverters> converters = new TreeMap<>();
        List<MethodInfo> bulkConverters = new ArrayList<>();
        AtomicInteger classesCounter = new AtomicInteger();

        List<AnnotationInstance> annotations = index.getAnnotations(CONVERTER_ANNOTATION);
        annotations.stream()
                .filter(annotation -> annotation.target().kind() == Kind.CLASS)
                .filter(annotation -> annotation.target().asClass().nestingType() == NestingType.TOP_LEVEL)
                .filter(annotation ->
                        asBoolean(annotation, "generateLoader") || asBoolean(annotation, "generateBulkLoader"))
                .forEach(annotation -> {
                    classesCounter.incrementAndGet();
                    String currentClass = annotation.target().asClass().name().toString();
                    ClassConverters classConverters = new ClassConverters();
                    if (asBoolean(annotation, "generateLoader")) {
                        converters.put(currentClass + "Loader", classConverters);
                    }
                    classConverters.setIgnoreOnLoadError(asBoolean(annotation, "ignoreOnLoadError"));
                    annotations.stream()
                            .filter(an -> an.target().kind() == Kind.METHOD)
                            .filter(an -> currentClass.equals(an.target()
                                    .asMethod()
                                    .declaringClass()
                                    .name()
                                    .toString()))
                            .forEach(an -> {
                                // is the method annotated with @Converter
                                MethodInfo ee = an.target().asMethod();
                                if (asBoolean(an, "fallback")) {
                                    classConverters.addFallbackTypeConverter(ee);
                                } else {
                                    Type to = ee.returnType();
                                    Type from = ee.parameterTypes().get(0);
                                    if (asBoolean(annotation, "generateBulkLoader")) {
                                        bulkConverters.add(ee);
                                    } else {
                                        classConverters.addTypeConverter(to, from, ee);
                                    }
                                }
                            });
                });

        // special for bulk loaders
        if (!bulkConverters.isEmpty()) {
            // calculate name from first found
            String pn = bulkConverters.get(0).declaringClass().name().prefix().toString();
            String name;
            if (classesCounter.get() > 1) {
                name = dashToCamelCase(project.getArtifactId());
                name = Character.toUpperCase(name.charAt(0)) + name.substring(1);
            } else {
                // okay there is only 1 class so let use that as the name, and avoid repeating converter in the name
                name = bulkConverters.get(0).declaringClass().name().local().replace("Converter", "");
            }
            String fqn = pn + "." + name + "BulkConverterLoader";
            // special optimized for camel-base type converters to be bulk and reduce memory usage
            boolean base = "camel-base".equals(project.getArtifactId());
            String source = writeBulkLoader(fqn, bulkConverters, base);
            updateResource(sourcesOutputDir.toPath(), fqn.replace('.', '/') + ".java", source);
            updateResource(
                    resourcesOutputDir.toPath(),
                    "META-INF/services/org/apache/camel/TypeConverterLoader",
                    "# " + GENERATED_MSG + NL + String.join(NL, fqn) + NL);
        }
        // and regular loaders
        if (!converters.isEmpty()) {
            converters.forEach((currentClass, classConverters) -> {
                String source = writeLoader(currentClass, classConverters);
                updateResource(sourcesOutputDir.toPath(), currentClass.replace('.', '/') + ".java", source);
            });
            updateResource(
                    resourcesOutputDir.toPath(),
                    "META-INF/services/org/apache/camel/TypeConverterLoader",
                    "# " + GENERATED_MSG + NL + String.join(NL, converters.keySet()) + NL);
        }
    }

    private String writeBulkLoader(String fqn, List<MethodInfo> converters, boolean base) {
        // sort by to so we can group them together
        converters.sort((o1, o2) -> {
            int sort = o1.returnType().name().compareTo(o2.returnType().name());
            if (sort == 0) {
                // same group then sort by order
                Integer order1 = asInteger(o1.annotation(CONVERTER_ANNOTATION), "order");
                Integer order2 = asInteger(o2.annotation(CONVERTER_ANNOTATION), "order");
                sort = order1.compareTo(order2);
                if (sort == 0) {
                    String str1 = o1.parameterTypes().stream()
                            .findFirst()
                            .map(Type::toString)
                            .orElse("");
                    String str2 = o2.parameterTypes().stream()
                            .findFirst()
                            .map(Type::toString)
                            .orElse("");
                    return str1.compareTo(str2);
                }
            }
            return sort;
        });

        Set<String> converterClasses = new TreeSet<>();

        int pos = fqn.lastIndexOf('.');
        String p = fqn.substring(0, pos);
        String c = fqn.substring(pos + 1);

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("package", p);
        ctx.put("className", c);
        ctx.put("converters", converters);
        ctx.put("mojo", this);
        ctx.put("converterClasses", converterClasses);
        return velocity("velocity/bulk-converter-loader.vm", ctx);
    }

    /**
     * This generates the template arguments for the type convertible.
     *
     * @param  method The converter method
     * @return        A string with the converter arguments
     */
    public String getGenericArgumentsForTypeConvertible(MethodInfo method) {
        StringBuilder writer = new StringBuilder(4096);

        if (method.parameterTypes().get(0).kind() == Type.Kind.ARRAY
                || method.parameterTypes().get(0).kind() == Type.Kind.CLASS) {
            writer.append(method.parameterTypes().get(0).toString());
        } else {
            writer.append(method.parameterTypes().get(0).name().toString());
        }

        writer.append(".class, ");
        writer.append(getToMethod(method));
        writer.append(".class");

        return writer.toString();
    }

    public String getToMethod(MethodInfo method) {
        if (Type.Kind.PRIMITIVE.equals(method.returnType().kind())) {
            return method.returnType().toString();
        } else if (Type.Kind.ARRAY.equals(method.returnType().kind())) {
            return method.returnType().toString();
        } else {
            return method.returnType().name().toString();
        }
    }

    public String asPrimitiveType(MethodInfo method) {
        if (!Type.Kind.PRIMITIVE.equals(method.returnType().kind())) {
            String to = method.returnType().name().toString();
            if ("java.lang.Integer".equals(to)) {
                return "int";
            } else if ("java.lang.Long".equals(to)) {
                return "long";
            } else if ("java.lang.Short".equals(to)) {
                return "short";
            } else if ("java.lang.Character".equals(to)) {
                return "char";
            } else if ("java.lang.Boolean".equals(to)) {
                return "boolean";
            } else if ("java.lang.Float".equals(to)) {
                return "float";
            } else if ("java.lang.Double".equals(to)) {
                return "double";
            } else if ("java.lang.Byte".equals(to)) {
                return "byte";
            }
        }
        return null;
    }

    private String writeLoader(String fqn, ClassConverters converters) {
        int pos = fqn.lastIndexOf('.');
        String p = fqn.substring(0, pos);
        String c = fqn.substring(pos + 1);
        Set<String> converterClasses = new LinkedHashSet<>();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("package", p);
        ctx.put("className", c);
        ctx.put("converters", converters);
        ctx.put("mojo", this);
        ctx.put("converterClasses", converterClasses);
        return velocity("velocity/type-converter-loader.vm", ctx);
    }

    public String toString(Type type) {
        return type.toString().replaceAll("<.*>", "").replace('$', '.');
    }

    public String toJava(MethodInfo converter, Set<String> converterClasses) {
        String pfx;
        if (Modifier.isStatic(converter.flags())) {
            pfx = converter.declaringClass().toString() + "." + converter.name();
        } else {
            converterClasses.add(converter.declaringClass().toString());
            pfx = "get" + converter.declaringClass().simpleName() + "()." + converter.name();
        }

        // the 2nd parameter is optional and can either be Exchange or CamelContext
        String param = "";
        String paramType = converter.parameterTypes().size() == 2
                ? converter.parameterTypes().get(1).asClassType().name().toString()
                : null;
        if (paramType != null) {
            if ("org.apache.camel.Exchange".equals(paramType)) {
                param = ", exchange";
            } else if ("org.apache.camel.CamelContext".equals(paramType)) {
                param = ", camelContext";
            }
        }
        String type = toString(converter.parameterTypes().get(0));
        String cast = type.equals("java.lang.Object") ? "" : "(" + type + ") ";
        return pfx + "(" + cast + "value" + param + ")";
    }

    public String toJavaFallback(MethodInfo converter, Set<String> converterClasses) {
        String pfx;
        if (Modifier.isStatic(converter.flags())) {
            pfx = converter.declaringClass().toString() + "." + converter.name();
        } else {
            converterClasses.add(converter.declaringClass().toString());
            pfx = "get" + converter.declaringClass().simpleName() + "()." + converter.name();
        }
        String type = toString(
                converter.parameterTypes().get(converter.parameterTypes().size() - 2));
        String cast = type.equals("java.lang.Object") ? "" : "(" + type + ") ";
        return pfx + "(type, " + (converter.parameterTypes().size() == 4 ? "exchange, " : "") + cast + "value"
                + ", registry)";
    }

    public boolean isFallbackCanPromote(MethodInfo element) {
        return asBoolean(element.annotation(CONVERTER_ANNOTATION), "fallbackCanPromote");
    }

    public boolean isAllowNull(MethodInfo element) {
        return asBoolean(element.annotation(CONVERTER_ANNOTATION), "allowNull");
    }

    private static boolean asBoolean(AnnotationInstance ai, String name) {
        AnnotationValue av = ai.value(name);
        return av != null && av.asBoolean();
    }

    private static int asInteger(AnnotationInstance ai, String name) {
        AnnotationValue av = ai.value(name);
        return av != null ? av.asInt() : 0;
    }

    public static final class ClassConverters {

        private final Comparator<Type> comparator;
        private final Map<String, Map<Type, MethodInfo>> converters = new TreeMap<>();
        private final List<MethodInfo> fallbackConverters = new ArrayList<>();
        private int size;
        private int sizeFallback;
        private boolean ignoreOnLoadError;

        ClassConverters() {
            this.comparator = Comparator.comparing(Type::toString);
        }

        public boolean isIgnoreOnLoadError() {
            return ignoreOnLoadError;
        }

        void setIgnoreOnLoadError(boolean ignoreOnLoadError) {
            this.ignoreOnLoadError = ignoreOnLoadError;
        }

        void addTypeConverter(Type to, Type from, MethodInfo ee) {
            converters
                    .computeIfAbsent(toString(to), c -> new TreeMap<>(comparator))
                    .put(from, ee);
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
            return type.toString().replaceAll("<.*>", "").replace('$', '.');
        }
    }
}
