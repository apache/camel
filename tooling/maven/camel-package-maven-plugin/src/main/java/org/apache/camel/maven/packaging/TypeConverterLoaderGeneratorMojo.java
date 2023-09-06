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
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;

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
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

import static org.apache.camel.maven.packaging.SchemaHelper.dashToCamelCase;
import static org.apache.camel.maven.packaging.generics.PackagePluginUtils.readJandexIndex;

@Mojo(name = "generate-type-converter-loader", threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
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

        Index index = readJandexIndex(project);

        Map<String, ClassConverters> converters = new TreeMap<>();
        List<MethodInfo> bulkConverters = new ArrayList<>();
        AtomicInteger classesCounter = new AtomicInteger();

        List<AnnotationInstance> annotations = index.getAnnotations(CONVERTER_ANNOTATION);
        annotations.stream()
                .filter(annotation -> annotation.target().kind() == Kind.CLASS)
                .filter(annotation -> annotation.target().asClass().nestingType() == NestingType.TOP_LEVEL)
                .filter(annotation -> asBoolean(annotation, "generateLoader") || asBoolean(annotation, "generateBulkLoader"))
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
                            .filter(an -> currentClass.equals(an.target().asMethod().declaringClass().name().toString()))
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
            updateResource(resourcesOutputDir.toPath(),
                    "META-INF/services/org/apache/camel/TypeConverterLoader",
                    "# " + GENERATED_MSG + NL + String.join(NL, fqn) + NL);
        }
        // and regular loaders
        if (!converters.isEmpty()) {
            converters.forEach((currentClass, classConverters) -> {
                String source = writeLoader(currentClass, classConverters);
                updateResource(sourcesOutputDir.toPath(), currentClass.replace('.', '/') + ".java", source);
            });
            updateResource(resourcesOutputDir.toPath(),
                    "META-INF/services/org/apache/camel/TypeConverterLoader",
                    "# " + GENERATED_MSG + NL + String.join(NL, converters.keySet()) + NL);
        }
    }

    private String writeBulkLoader(String fqn, List<MethodInfo> converters, boolean base) {
        StringBuilder writer = new StringBuilder();

        // sort by to so we can group them together
        converters.sort((o1, o2) -> {
            int sort = o1.returnType().name().compareTo(o2.returnType().name());
            if (sort == 0) {
                // same group then sort by order
                Integer order1 = asInteger(o1.annotation(CONVERTER_ANNOTATION), "order");
                Integer order2 = asInteger(o2.annotation(CONVERTER_ANNOTATION), "order");
                sort = order1.compareTo(order2);
                if (sort == 0) {
                    String str1 = o1.parameterTypes().stream().findFirst().map(Type::toString).orElse("");
                    String str2 = o2.parameterTypes().stream().findFirst().map(Type::toString).orElse("");
                    return str1.compareTo(str2);
                }
            }
            return sort;
        });

        Set<String> converterClasses = new TreeSet<>();

        int pos = fqn.lastIndexOf('.');
        String p = fqn.substring(0, pos);
        String c = fqn.substring(pos + 1);

        writer.append("/* ").append(GENERATED_MSG).append(" */\n");
        writer.append("package ").append(p).append(";\n");
        writer.append("\n");
        writer.append("import org.apache.camel.CamelContext;\n");
        writer.append("import org.apache.camel.CamelContextAware;\n");
        writer.append("import org.apache.camel.DeferredContextBinding;\n");
        writer.append("import org.apache.camel.Exchange;\n");
        writer.append("import org.apache.camel.Ordered;\n");
        writer.append("import org.apache.camel.TypeConversionException;\n");
        writer.append("import org.apache.camel.TypeConverterLoaderException;\n");
        writer.append("import org.apache.camel.TypeConverter;\n");
        writer.append("import org.apache.camel.converter.TypeConvertible;\n");
        writer.append("import org.apache.camel.spi.BulkTypeConverters;\n");
        writer.append("import org.apache.camel.spi.TypeConverterLoader;\n");
        writer.append("import org.apache.camel.spi.TypeConverterRegistry;\n");
        writer.append("\n");
        writer.append("/**\n");
        writer.append(" * ").append(GENERATED_MSG).append("\n");
        writer.append(" */\n");
        writer.append("@SuppressWarnings(\"unchecked\")\n");
        writer.append("@DeferredContextBinding\n");
        writer.append("public final class ").append(c)
                .append(" implements TypeConverterLoader, BulkTypeConverters, CamelContextAware {\n");
        writer.append("\n");
        writer.append("    private CamelContext camelContext;\n");
        writer.append("\n");
        writer.append("    ").append("public ").append(c).append("() {\n");
        writer.append("    }\n");
        writer.append("\n");

        writer.append("    @Override\n");
        writer.append("    public void setCamelContext(CamelContext camelContext) {\n");
        writer.append("        this.camelContext = camelContext;\n");
        writer.append("    }\n");
        writer.append("\n");
        writer.append("    @Override\n");
        writer.append("    public CamelContext getCamelContext() {\n");
        writer.append("        return camelContext;\n");
        writer.append("    }\n");
        writer.append("\n");

        if (base) {
            // we want to be highest ordered for the base converters
            writer.append("    @Override\n");
            writer.append("    public int getOrder() {\n");
            writer.append("        return Ordered.HIGHEST;\n");
            writer.append("    }\n");
            writer.append("\n");
        }

        writer.append("    @Override\n");
        writer.append("    public int size() {\n");
        writer.append("        return ").append(converters.size()).append(";\n");
        writer.append("    }\n");
        writer.append("\n");

        writer.append("    @Override\n");
        writer.append("    public void load(TypeConverterRegistry registry) throws TypeConverterLoaderException {\n");
        writer.append("        registry.addBulkTypeConverters(this);\n");
        writer.append("        doRegistration(registry);\n");
        writer.append("    }\n");
        writer.append("\n");
        writer.append("    @Override\n");
        writer.append(
                "    public <T> T convertTo(Class<?> from, Class<T> to, Exchange exchange, Object value) throws TypeConversionException {\n");
        writer.append("        try {\n");
        writer.append("            Object obj = doConvertTo(from, to, exchange, value);\n");
        writer.append("            if (obj == Void.class) {;\n");
        writer.append("                return null;\n");
        writer.append("            } else {\n");
        writer.append("                return (T) obj;\n");
        writer.append("            }\n");
        writer.append("        } catch (TypeConversionException e) {\n");
        writer.append("            throw e;\n");
        writer.append("        } catch (Exception e) {\n");
        writer.append("            throw new TypeConversionException(value, to, e);\n");
        writer.append("        }\n");
        writer.append("    }\n");
        writer.append("\n");
        writer.append(
                "    private Object doConvertTo(Class<?> from, Class<?> to, Exchange exchange, Object value) throws Exception {\n");
        writeLoader(converters, writer, converterClasses, false);
        writer.append("        }\n");
        writer.append("        return null;\n");
        writer.append("    }\n");
        writer.append("\n");
        writer.append(
                "    private void doRegistration(TypeConverterRegistry registry) {\n");
        writeRegistration(converters, writer, converterClasses, false);
        writer.append("        \n");
        writer.append("        \n");
        writer.append("    }\n");
        writer.append("\n");

        writer.append(
                "    public TypeConverter lookup(Class<?> to, Class<?> from) {\n");
        writeLoader(converters, writer, converterClasses, true);
        writer.append("        }\n");
        writer.append("        return null;\n");
        writer.append("    }\n");
        writer.append("\n");
        for (String f : converterClasses) {
            String s = f.substring(f.lastIndexOf('.') + 1);
            String v = s.substring(0, 1).toLowerCase() + s.substring(1);
            writer.append("    private volatile ").append(f).append(" ").append(v).append(";\n");
            writer.append("    private ").append(f).append(" get").append(s).append("() {\n");
            writer.append("        if (").append(v).append(" == null) {\n");
            writer.append("            ").append(v).append(" = new ").append(f).append("();\n");
            writer.append("            CamelContextAware.trySetCamelContext(").append(v).append(", camelContext);\n");
            writer.append("        }\n");
            writer.append("        return ").append(v).append(";\n");
            writer.append("    }\n");
        }
        writer.append("}\n");

        return writer.toString();
    }

    private void writeRegistration(
            List<MethodInfo> converters, StringBuilder writer, Set<String> converterClasses, boolean lookup) {
        for (MethodInfo method : converters) {

            writer.append("        registry.addConverter(new TypeConvertible<>(")
                    .append(getGenericArgumentsForTypeConvertible(method))
                    .append("), ")
                    .append("this);")

                    .append("\n");
        }
    }

    /**
     * This resolves the method to be called for conversion. There are 2 possibilities here: either it calls a static
     * method, in which case we can refer to it directly ... Or it uses one of the converter classes to do so. In this
     * case, we do a bit of hacking "by convention" to call the getter of said converter and use it to call the
     * converter method (i.e.; getDomConverter().myConverter method). There are some cases (like when dealing with jaxp
     * that require this)
     *
     * @param  method The converter method
     * @return        The resolved converter method to use
     */
    private static String resolveMethod(MethodInfo method) {
        if (Modifier.isStatic(method.flags())) {
            return method.declaringClass().toString() + "." + method.name();
        } else {
            return "get" + method.declaringClass().simpleName() + "()." + method.name();
        }
    }

    /**
     * This generates the cast part of the method
     *
     * @param  method The converter method
     * @return        The cast string to use
     */
    private static String generateCast(MethodInfo method) {
        StringBuilder writer = new StringBuilder(128);

        if (method.parameterTypes().get(0).kind() == Type.Kind.ARRAY
                || method.parameterTypes().get(0).kind() == Type.Kind.CLASS) {
            writer.append(method.parameterTypes().get(0).toString());
        } else {
            writer.append(method.parameterTypes().get(0).name().toString());
        }

        return writer.toString();
    }

    /**
     * This generates the template arguments for the type convertible.
     *
     * @param  method The converter method
     * @return        A string with the converter arguments
     */

    private static String getGenericArgumentsForTypeConvertible(MethodInfo method) {
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

    /**
     * This generates the arguments to be passed to the converter method. For instance, given a list of methods, it
     * traverses the parameter types and generates something like this: "exchange, camelContext".
     *
     * @param  method The converter method
     * @return        A string instance with the converter methods or an empty String if none exist.
     */
    private static String getArgumentsForConverter(MethodInfo method) {
        StringBuilder writer = new StringBuilder(128);

        for (Type type : method.parameterTypes()) {

            if (type.name().withoutPackagePrefix().equalsIgnoreCase("CamelContext")) {
                writer.append(", camelContext");
            }

            if (type.name().withoutPackagePrefix().equalsIgnoreCase("Exchange")) {
                writer.append(", exchange");
            }
        }

        return writer.toString();
    }

    private static String getToMethod(MethodInfo method) {
        if (Type.Kind.PRIMITIVE.equals(method.returnType().kind())) {
            return method.returnType().toString();
        } else if (Type.Kind.ARRAY.equals(method.returnType().kind())) {
            return method.returnType().toString();
        } else {
            return method.returnType().name().toString();
        }
    }

    private void writeLoader(
            List<MethodInfo> converters, StringBuilder writer, Set<String> converterClasses, boolean lookup) {
        String prevTo = null;
        for (MethodInfo method : converters) {
            String to = getToMethod(method);

            String from = method.parameterTypes().get(0).toString();
            // clip generics
            if (to.indexOf('<') != -1) {
                to = to.substring(0, to.indexOf('<'));
            }
            if (from.indexOf('<') != -1) {
                from = from.substring(0, from.indexOf('<'));
            }

            boolean newTo = false;
            if (prevTo == null) {
                // first time
                // if (to == java.lang.Integer || to == int.class)
                writer.append("        if (to == ");
                newTo = true;
            } else if (!prevTo.equals(to)) {
                // new group
                // else if (to == java.lang.Integer || to == int.class)
                writer.append("        } else if (to == ");
                newTo = true;
            }
            if (newTo) {
                writer.append(to).append(".class");
                String primitiveTo = asPrimitiveType(method);
                if (primitiveTo != null) {
                    writer.append(" || to == ").append(primitiveTo).append(".class");
                }
                writer.append(") {\n");
            }

            if (lookup) {
                writer.append("            if (from == ").append(from).append(".class) {\n");
            } else {
                writer.append("            if (value instanceof ").append(from).append(") {\n");
            }
            if (lookup) {
                writer.append("                return this;\n");
            } else {
                if (isAllowNull(method)) {
                    writer.append("                Object obj = ").append(toJava(method, converterClasses))
                            .append(";\n");
                    writer.append("                if (obj == null) {\n");
                    writer.append("                    return Void.class;\n");
                    writer.append("                } else {\n");
                    writer.append("                    return obj;\n");
                    writer.append("                }\n");
                } else {
                    writer.append("                return ").append(toJava(method, converterClasses))
                            .append(";\n");
                }
            }
            writer.append("            }\n");

            prevTo = to;
        }
    }

    private static String asPrimitiveType(MethodInfo method) {
        if (!Type.Kind.PRIMITIVE.equals(method.returnType().kind())) {
            String to = method.returnType().name().toString();
            if ("java.lang.Integer".equals(to)) {
                return "int";
            } else if ("java.lang.Long".equals(to)) {
                return "long";
            } else if ("java.lang.Character".equals(to)) {
                return "char";
            } else if ("java.lang.Boolean".equals(to)) {
                return "boolean";
            } else if ("java.lang.Float".equals(to)) {
                return "float";
            } else if ("java.lang.Double".equals(to)) {
                return "double";
            }
        }
        return null;
    }

    private String writeLoader(String fqn, ClassConverters converters) {

        int pos = fqn.lastIndexOf('.');
        String p = fqn.substring(0, pos);
        String c = fqn.substring(pos + 1);

        Set<String> converterClasses = new LinkedHashSet<>();
        StringBuilder writer = new StringBuilder();

        writer.append("/* ").append(GENERATED_MSG).append(" */\n");
        writer.append("package ").append(p).append(";\n");
        writer.append("\n");
        writer.append("import org.apache.camel.CamelContext;\n");
        writer.append("import org.apache.camel.CamelContextAware;\n");
        writer.append("import org.apache.camel.DeferredContextBinding;\n");
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
        writer.append("@DeferredContextBinding\n");
        writer.append("public final class ").append(c).append(" implements TypeConverterLoader, CamelContextAware {\n");
        writer.append("\n");
        writer.append("    private CamelContext camelContext;\n");
        writer.append("\n");
        writer.append("    ").append("public ").append(c).append("() {\n");
        writer.append("    }\n");
        writer.append("\n");

        writer.append("    @Override\n");
        writer.append("    public void setCamelContext(CamelContext camelContext) {\n");
        writer.append("        this.camelContext = camelContext;\n");
        writer.append("    }\n");
        writer.append("\n");
        writer.append("    @Override\n");
        writer.append("    public CamelContext getCamelContext() {\n");
        writer.append("        return camelContext;\n");
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
                    writer.append("        addTypeConverter(registry, ").append(to.getKey()).append(".class").append(", ")
                            .append(toString(from.getKey())).append(".class, ")
                            .append(allowNull).append(",\n");
                    writer.append("            (type, exchange, value) -> ")
                            .append(toJava(from.getValue(), converterClasses))
                            .append(");\n");
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
                writer.append("        addFallbackTypeConverter(registry, ").append(allowNull).append(", ")
                        .append(canPromote).append(", ")
                        .append("(type, exchange, value) -> ").append(toJavaFallback(ee, converterClasses)).append(");\n");
            }
            writer.append("    }\n");
            writer.append("\n");

            writer
                    .append("    private static void addFallbackTypeConverter(TypeConverterRegistry registry, boolean allowNull, boolean canPromote, SimpleTypeConverter.ConversionMethod method) { \n");
            writer.append(
                    "        registry.addFallbackTypeConverter(new SimpleTypeConverter(allowNull, method), canPromote);\n");
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
            writer.append("            CamelContextAware.trySetCamelContext(").append(v).append(", camelContext);\n");
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

        // the 2nd parameter is optional and can either be Exchange or CamelContext
        String param = "";
        String paramType
                = converter.parameterTypes().size() == 2
                        ? converter.parameterTypes().get(1).asClassType().name().toString() : null;
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

    private String toJavaFallback(MethodInfo converter, Set<String> converterClasses) {
        String pfx;
        if (Modifier.isStatic(converter.flags())) {
            pfx = converter.declaringClass().toString() + "." + converter.name();
        } else {
            converterClasses.add(converter.declaringClass().toString());
            pfx = "get" + converter.declaringClass().simpleName() + "()." + converter.name();
        }
        String type = toString(converter.parameterTypes().get(converter.parameterTypes().size() - 2));
        String cast = type.equals("java.lang.Object") ? "" : "(" + type + ") ";
        return pfx + "(type, " + (converter.parameterTypes().size() == 4 ? "exchange, " : "") + cast + "value" + ", registry)";
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
