/**
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
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.DocumentationTool;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;

import static org.apache.camel.tools.apt.AnnotationProcessorHelper.dumpExceptionToErrorFile;

@SupportedAnnotationTypes({"org.apache.camel.Converter", "org.apache.camel.FallbackConverter"})
public class TypeConverterLoaderProcessor extends AbstractCamelAnnotationProcessor {

    // TODO: fallback does not work
    // TODO: generate so you dont need to pass in CamelContext but register into a java set/thingy
    // so you can init this via static initializer block { ... } and then register on CamelContext later

    private static final class ClassConverters {

        private final Comparator<TypeMirror> comparator;
        private final Map<String, Map<TypeMirror, ExecutableElement>> converters = new TreeMap<>();
        private final List<ExecutableElement> fallbackConverters = new ArrayList<>();
        private int size;
        private boolean ignoreOnLoadError;

        ClassConverters(Comparator<TypeMirror> comparator) {
            this.comparator = comparator;
        }

        boolean isIgnoreOnLoadError() {
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
            size++;
        }

        Map<String, Map<TypeMirror, ExecutableElement>> getConverters() {
            return converters;
        }

        List<ExecutableElement> getFallbackConverters() {
            return fallbackConverters;
        }

        private static String toString(TypeMirror type) {
            return type.toString().replaceAll("<.*>", "");
        }

        long size() {
            return size;
        }

        boolean isEmpty() {
            return size == 0;
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
                if (!te.getNestingKind().isNested() && isLoaderEnabled(te)) {
                    // we only accept top-level classes and if loader is enabled
                    currentClass = te.getQualifiedName().toString();
                    ignoreOnLoadError = isIgnoreOnLoadError(element);
                }
            } else if (currentClass != null && element.getKind() == ElementKind.METHOD) {
                // is the method annotated with @Converter
                ExecutableElement ee = (ExecutableElement) element;
                if (isConverterMethod(ee)) {
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
                    converters.computeIfAbsent(currentClass, c -> new ClassConverters(comparator)).addTypeConverter(to, from, ee);
                    if (converters.containsKey(currentClass)) {
                        converters.get(currentClass).setIgnoreOnLoadError(ignoreOnLoadError);
                    }
                }
            }
        }

        TypeElement fallbackAnnotationType = this.processingEnv.getElementUtils().getTypeElement("org.apache.camel.FallbackConverter");
        currentClass = null;
        ignoreOnLoadError = false;
        for (Element element : roundEnv.getElementsAnnotatedWith(converterAnnotationType)) {
            if (element.getKind() == ElementKind.CLASS) {
                TypeElement te = (TypeElement) element;
                if (!te.getNestingKind().isNested() && isLoaderEnabled(te)) {
                    // we only accept top-level classes and if loader is enabled
                    currentClass = te.getQualifiedName().toString();
                    ignoreOnLoadError = isIgnoreOnLoadError(element);
                }
            } else if (currentClass != null && element.getKind() == ElementKind.METHOD) {
                ExecutableElement ee = (ExecutableElement) element;
                if (isFallbackConverterMethod(ee)) {
                    converters.computeIfAbsent(currentClass, c -> new ClassConverters(comparator)).addFallbackTypeConverter(ee);
                    if (converters.containsKey(currentClass)) {
                        converters.get(currentClass).setIgnoreOnLoadError(ignoreOnLoadError);
                    }
                }
            }
        }

        // now write all the converters
        for (Map.Entry<String, ClassConverters> entry : converters.entrySet()) {
            String key = entry.getKey();
            ClassConverters value = entry.getValue();
            writeConverterLoader(key, value, converterAnnotationType, fallbackAnnotationType);
        }
        writeConverterLoaderMetaInfo(converters);
    }

    private static boolean isLoaderEnabled(Element element) {
        for (AnnotationMirror ann : element.getAnnotationMirrors()) {
            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : ann.getElementValues().entrySet()) {
                if ("loader".equals(entry.getKey().getSimpleName().toString())) {
                    return (Boolean) entry.getValue().getValue();
                }
            }
        }
        return false;
    }

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

    private static boolean isConverterMethod(ExecutableElement element) {
        for (AnnotationMirror ann : element.getAnnotationMirrors()) {
            String name = ann.getAnnotationType().asElement().getSimpleName().toString();
            if ("Converter".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isFallbackConverterMethod(ExecutableElement element) {
        for (AnnotationMirror ann : element.getAnnotationMirrors()) {
            String name = ann.getAnnotationType().asElement().getSimpleName().toString();
            if ("FallbackConverter".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void writeConverterLoaderMetaInfo(Map<String, ClassConverters> converters) throws Exception {
        StringJoiner sj = new StringJoiner(",");
        for (Map.Entry<String, ClassConverters> entry : converters.entrySet()) {
            String key = entry.getKey();
            ClassConverters value = entry.getValue();
            if (!value.isEmpty()) {
                sj.add(key);
            }
        }

        if (sj.length() > 0) {
            FileObject fo = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/services/org/apache/camel/TypeConverterLoader");
            try (Writer writer = fo.openWriter()) {
                writer.append("# Generated by camel annotation processor\n");
                for (String fqn : sj.toString().split(",")) {
                    writer.append("class=").append(fqn).append("Loader\n");
                }
            }
        }
    }

    private void writeConverterLoader(String fqn, ClassConverters converters,
                                      TypeElement converterAnnotationType,
                                      TypeElement fallbackAnnotationType) throws Exception {

        int pos = fqn.lastIndexOf('.');
        String p = fqn.substring(0, pos);
        String c = fqn.substring(pos + 1) + "Loader";

        JavaFileObject jfo = processingEnv.getFiler().createSourceFile(p + "." + c);
        Set<String> converterClasses = new LinkedHashSet<>();
        try (Writer writer = jfo.openWriter()) {

            writer.append("package ").append(p).append(";\n");
            writer.append("\n");
            writer.append("import org.apache.camel.Exchange;\n");
            writer.append("import org.apache.camel.TypeConversionException;\n");
            writer.append("import org.apache.camel.TypeConverterLoaderException;\n");
            writer.append("import org.apache.camel.spi.TypeConverterLoader;\n");
            writer.append("import org.apache.camel.spi.TypeConverterRegistry;\n");
            writer.append("import org.apache.camel.support.TypeConverterSupport;\n");
            writer.append("import org.apache.camel.util.DoubleMap;\n");
            writer.append("\n");
            writer.append("@SuppressWarnings(\"unchecked\")\n");
            writer.append("public class ").append(c).append(" implements TypeConverterLoader {\n");
            writer.append("\n");
            writer.append("    public static final ").append(c).append(" INSTANCE = new ").append(c).append("();\n");
            writer.append("\n");
            writer.append("    static abstract class BaseTypeConverter extends TypeConverterSupport {\n");
            writer.append("        private final boolean allowNull;\n");
            writer.append("\n");
            writer.append("        public BaseTypeConverter(boolean allowNull) {\n");
            writer.append("            this.allowNull = allowNull;\n");
            writer.append("        }\n");
            writer.append("\n");
            writer.append("        @Override\n");
            writer.append("        public boolean allowNull() {\n");
            writer.append("            return allowNull;\n");
            writer.append("        }\n");
            writer.append("\n");
            writer.append("        @Override\n");
            writer.append("        public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {\n");
            writer.append("            try {\n");
            writer.append("                return (T) doConvert(exchange, value);\n");
            writer.append("            } catch (TypeConversionException e) {\n");
            writer.append("                throw e;\n");
            writer.append("            } catch (Exception e) {\n");
            writer.append("                throw new TypeConversionException(value, type, e);\n");
            writer.append("            }\n");
            writer.append("        }\n");
            writer.append("        protected abstract Object doConvert(Exchange exchange, Object value) throws Exception;\n");
            writer.append("    };\n");
            writer.append("\n");
            writer.append("    private final DoubleMap<Class<?>, Class<?>, BaseTypeConverter> converters = new DoubleMap<>(" + converters.size() + ");\n");
            writer.append("\n");
            writer.append("    private ").append(c).append("() {\n");

            for (Map.Entry<String, Map<TypeMirror, ExecutableElement>> to : converters.getConverters().entrySet()) {
                for (Map.Entry<TypeMirror, ExecutableElement> from : to.getValue().entrySet()) {
                    boolean allowNull = false;
                    for (AnnotationMirror ann : from.getValue().getAnnotationMirrors()) {
                        if (ann.getAnnotationType().asElement() == converterAnnotationType) {
                            for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : ann.getElementValues().entrySet()) {
                                switch (entry.getKey().getSimpleName().toString()) {
                                case "allowNull":
                                    allowNull = (Boolean)entry.getValue().getValue();
                                    break;
                                default:
                                    throw new IllegalStateException();
                                }
                            }
                        }
                    }
                    writer.append("        converters.put(").append(to.getKey()).append(".class").append(", ").append(toString(from.getKey()))
                        .append(".class, new BaseTypeConverter(").append(Boolean.toString(allowNull)).append(") {\n");
                    writer.append("            @Override\n");
                    writer.append("            public Object doConvert(Exchange exchange, Object value) throws Exception {\n");
                    writer.append("                return ").append(toJava(from.getValue(), converterClasses)).append(";\n");
                    writer.append("            }\n");
                    writer.append("        });\n");
                }
            }
            writer.append("    }\n");
            writer.append("\n");
            writer.append("    @Override\n");
            writer.append("    public void load(TypeConverterRegistry registry) throws TypeConverterLoaderException {\n");
            if (converters.isIgnoreOnLoadError()) {
                writer.append("        try {\n");
                writer.append("            converters.forEach((k, v, c) -> registry.addTypeConverter(k, v, c));\n");
                writer.append("        } catch (Throwable e) {\n");
                writer.append("            // ignore on load error\n");
                writer.append("        }\n");
            } else {
                writer.append("        converters.forEach((k, v, c) -> registry.addTypeConverter(k, v, c));\n");
            }
            for (ExecutableElement ee : converters.getFallbackConverters()) {
                boolean allowNull = false;
                boolean canPromote = false;
                for (AnnotationMirror ann : ee.getAnnotationMirrors()) {
                    if (ann.getAnnotationType().asElement() == fallbackAnnotationType) {
                        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry : ann.getElementValues().entrySet()) {
                            switch (entry.getKey().getSimpleName().toString()) {
                            case "allowNull":
                                allowNull = (Boolean)entry.getValue().getValue();
                                break;
                            case "canPromote":
                                canPromote = (Boolean)entry.getValue().getValue();
                                break;
                            default:
                                throw new IllegalStateException();
                            }
                        }
                    }
                }
                writer.append("        registry.addFallbackTypeConverter(new TypeConverterSupport() {\n");
                writer.append("            @Override\n");
                writer.append("            public boolean allowNull() {\n");
                writer.append("                return ").append(Boolean.toString(allowNull)).append(";\n");
                writer.append("            }\n");
                writer.append("            @Override\n");
                writer.append("            public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {\n");
                writer.append("                try {\n");
                writer.append("                    return (T) ").append(toJavaFallback(ee, converterClasses)).append(";\n");
                writer.append("                } catch (TypeConversionException e) {\n");
                writer.append("                    throw e;\n");
                writer.append("                } catch (Exception e) {\n");
                writer.append("                    throw new TypeConversionException(value, type, e);\n");
                writer.append("                }\n");
                writer.append("            }\n");
                writer.append("        }, ").append(Boolean.toString(canPromote)).append(");\n");
            }
            writer.append("    }\n");
            writer.append("\n");

            for (String f : converterClasses) {
                String s = f.substring(f.lastIndexOf('.') + 1);
                String v = s.substring(0, 1).toLowerCase() + s.substring(1);
                writer.append("    private volatile ").append(f).append(" ").append(v).append(";\n");
                writer.append("    private ").append(f).append(" get").append(s).append("() {\n");
                writer.append("        if (").append(v).append(" == null) {\n");
                writer.append("            synchronized (this) {\n");
                writer.append("                if (").append(v).append(" == null) {\n");
                writer.append("                    ").append(v).append(" = new ").append(f).append("();\n");
                writer.append("                }\n");
                writer.append("            }\n");
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
