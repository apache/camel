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
package org.apache.camel.tools.apt;

import java.io.Writer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

public abstract class AbstractTypeConverterGenerator extends AbstractCamelAnnotationProcessor {

    public static final class ClassConverters {

        private final Comparator<TypeMirror> comparator;
        private final Map<String, Map<TypeMirror, ExecutableElement>> converters = new TreeMap<>();
        private final List<ExecutableElement> fallbackConverters = new ArrayList<>();
        private int size;
        private int sizeFallback;
        private boolean ignoreOnLoadError;

        ClassConverters(Comparator<TypeMirror> comparator) {
            this.comparator = comparator;
        }

        public boolean isIgnoreOnLoadError() {
            return ignoreOnLoadError;
        }

        void setIgnoreOnLoadError(boolean ignoreOnLoadError) {
            this.ignoreOnLoadError = ignoreOnLoadError;
        }

        void addTypeConverter(TypeMirror to, TypeMirror from, ExecutableElement ee) {
            converters.computeIfAbsent(toString(to), c -> new TreeMap<>(comparator)).put(from, ee);
            size++;
        }

        void addFallbackTypeConverter(ExecutableElement ee) {
            fallbackConverters.add(ee);
            sizeFallback++;
        }

        public Map<String, Map<TypeMirror, ExecutableElement>> getConverters() {
            return converters;
        }

        public List<ExecutableElement> getFallbackConverters() {
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

        private static String toString(TypeMirror type) {
            return type.toString().replaceAll("<.*>", "");
        }

    }

    @Override
    protected void doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) throws Exception {
        Map<String, ClassConverters> converters = new TreeMap<>();

        Comparator<TypeMirror> comparator = (o1, o2) -> processingEnv.getTypeUtils().isAssignable(o1, o2)
            ? -1 : processingEnv.getTypeUtils().isAssignable(o2, o1) ? +1 : o1.toString().compareTo(o2.toString());

        TypeElement converterAnnotationType = this.processingEnv.getElementUtils().getTypeElement("org.apache.camel.Converter");
        // the current class with type converters
        String currentClass = null;
        boolean ignoreOnLoadError = false;
        for (Element element : roundEnv.getElementsAnnotatedWith(converterAnnotationType)) {
            // we need a top level class first
            if (element.getKind() == ElementKind.CLASS) {
                TypeElement te = (TypeElement) element;
                if (!te.getNestingKind().isNested() && acceptClass(te)) {
                    // we only accept top-level classes and if loader is enabled
                    currentClass = te.getQualifiedName().toString();
                    ignoreOnLoadError = isIgnoreOnLoadError(element);
                }
            } else if (currentClass != null && element.getKind() == ElementKind.METHOD) {
                String key = convertersKey(currentClass);
                // is the method annotated with @Converter
                ExecutableElement ee = (ExecutableElement) element;
                if (isFallbackConverter(ee)) {
                    converters.computeIfAbsent(key, c -> new ClassConverters(comparator)).addFallbackTypeConverter(ee);
                    if (converters.containsKey(key)) {
                        converters.get(key).setIgnoreOnLoadError(ignoreOnLoadError);
                    }
                } else {
                    TypeMirror to = ee.getReturnType();
                    TypeMirror from = ee.getParameters().get(0).asType();
                    String fromStr = toString(from);
                    if (!fromStr.endsWith("[]")) {
                        TypeElement e = this.processingEnv.getElementUtils().getTypeElement(fromStr);
                        if (e != null) {
                            from = e.asType();
                        } else {
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, "Could not retrieve type element for " + fromStr);
                        }

                    }
                    converters.computeIfAbsent(key, c -> new ClassConverters(comparator)).addTypeConverter(to, from, ee);
                    if (converters.containsKey(key)) {
                        converters.get(key).setIgnoreOnLoadError(ignoreOnLoadError);
                    }
                }
            }
        }

        writeConverters(converters);
    }

    abstract String convertersKey(String currentClass);

    abstract void writeConverters(Map<String, ClassConverters> converters) throws Exception;

    abstract boolean acceptClass(Element element);

    private static boolean isIgnoreOnLoadError(Element element) {
        for (AnnotationMirror ann : element.getAnnotationMirrors()) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : ann.getElementValues().entrySet()) {
                if ("ignoreOnLoadError".equals(entry.getKey().getSimpleName().toString())) {
                    return (Boolean) entry.getValue().getValue();
                }
            }
        }
        return false;
    }

    private static boolean isFallbackCanPromote(Element element) {
        for (AnnotationMirror ann : element.getAnnotationMirrors()) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : ann.getElementValues().entrySet()) {
                if ("fallbackCanPromote".equals(entry.getKey().getSimpleName().toString())) {
                    return (Boolean) entry.getValue().getValue();
                }
            }
        }
        return false;
    }

    private static boolean isAllowNull(Element element) {
        for (AnnotationMirror ann : element.getAnnotationMirrors()) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : ann.getElementValues().entrySet()) {
                if ("allowNull".equals(entry.getKey().getSimpleName().toString())) {
                    return (Boolean) entry.getValue().getValue();
                }
            }
        }
        return false;
    }

    private static boolean isFallbackConverter(ExecutableElement element) {
        for (AnnotationMirror ann : element.getAnnotationMirrors()) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : ann.getElementValues().entrySet()) {
                if ("fallback".equals(entry.getKey().getSimpleName().toString())) {
                    return (Boolean) entry.getValue().getValue();
                }
            }
        }
        return false;
    }

    void writeConverters(String fqn, String suffix, ClassConverters converters) throws Exception {

        int pos = fqn.lastIndexOf('.');
        String p = fqn.substring(0, pos);
        String c = fqn.substring(pos + 1) + (suffix != null ? suffix : "");

        JavaFileObject jfo = processingEnv.getFiler().createSourceFile(p + "." + c);
        Set<String> converterClasses = new LinkedHashSet<>();
        try (Writer writer = jfo.openWriter()) {

            writer.append("/* Generated by org.apache.camel:apt */\n");
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
            writer.append(" * Source code generated by org.apache.camel:apt\n");
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
                for (Map.Entry<String, Map<TypeMirror, ExecutableElement>> to : converters.getConverters().entrySet()) {
                    for (Map.Entry<TypeMirror, ExecutableElement> from : to.getValue().entrySet()) {
                        boolean allowNull = isAllowNull(from.getValue());
                        writer.append("        addTypeConverter(registry, ").append(to.getKey()).append(".class").append(", ").append(toString(from.getKey()))
                                .append(".class, ").append(Boolean.toString(allowNull)).append(",\n");
                        writer.append("            (type, exchange, value) -> ").append(toJava(from.getValue(), converterClasses)).append(");\n");
                    }
                }
                writer.append("    }\n");
                writer.append("\n");

                writer.append(
                              "    private static void addTypeConverter(TypeConverterRegistry registry, Class<?> toType, Class<?> fromType, boolean allowNull, SimpleTypeConverter.ConversionMethod method)"
                              + " { \n");
                writer.append("        registry.addTypeConverter(toType, fromType, new SimpleTypeConverter(allowNull, method));\n");
                writer.append("    }\n");
                writer.append("\n");
            }

            if (converters.sizeFallback() > 0) {
                writer.append("    private void registerFallbackConverters(TypeConverterRegistry registry) {\n");
                for (ExecutableElement ee : converters.getFallbackConverters()) {
                    boolean allowNull = isAllowNull(ee);
                    boolean canPromote = isFallbackCanPromote(ee);
                    writer.append("        addFallbackTypeConverter(registry, ")
                            .append(Boolean.toString(allowNull)).append(", ")
                            .append(Boolean.toString(canPromote)).append(", ")
                            .append("(type, exchange, value) -> ")
                            .append(toJavaFallback(ee, converterClasses))
                            .append(");\n");
                }
                writer.append("    }\n");
                writer.append("\n");

                writer.append("    private static void addFallbackTypeConverter(TypeConverterRegistry registry, boolean allowNull, boolean canPromote, SimpleTypeConverter.ConversionMethod method) { \n");
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
            writer.flush();
        }
    }

    private String toString(TypeMirror type) {
        return type.toString().replaceAll("<.*>", "");
    }

    private String toJava(ExecutableElement converter, Set<String> converterClasses) {
        String pfx;
        if (converter.getModifiers().contains(Modifier.STATIC)) {
            pfx = converter.getEnclosingElement().toString() + "." + converter.getSimpleName();
        } else {
            converterClasses.add(converter.getEnclosingElement().toString());
            pfx = "get" + converter.getEnclosingElement().getSimpleName() + "()." + converter.getSimpleName();
        }
        String type = toString(converter.getParameters().get(0).asType());
        String cast = type.equals("java.lang.Object") ? "" : "(" + type + ") ";
        return pfx + "(" + cast + "value" + (converter.getParameters().size() == 2 ? ", exchange" : "") + ")";
    }

    private String toJavaFallback(ExecutableElement converter, Set<String> converterClasses) {
        String pfx;
        if (converter.getModifiers().contains(Modifier.STATIC)) {
            pfx = converter.getEnclosingElement().toString() + "." + converter.getSimpleName();
        } else {
            converterClasses.add(converter.getEnclosingElement().toString());
            pfx = "get" + converter.getEnclosingElement().getSimpleName() + "()." + converter.getSimpleName();
        }
        String type = toString(converter.getParameters().get(converter.getParameters().size() - 2).asType());
        String cast = type.equals("java.lang.Object") ? "" : "(" + type + ") ";
        return pfx + "(type, " + (converter.getParameters().size() == 4 ? "exchange, " : "") + cast + "value" + ", registry)";
    }

}
