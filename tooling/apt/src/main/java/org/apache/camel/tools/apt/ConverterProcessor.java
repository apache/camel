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

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes({"org.apache.camel.Converter"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class ConverterProcessor extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        try {
            if (this.processingEnv.getElementUtils().getTypeElement("org.apache.camel.impl.converter.CoreFallbackConverter") != null) {
                return false;
            }

            if (roundEnv.processingOver()) {
                return false;
            }

            Comparator<TypeMirror> comparator = (o1, o2) -> processingEnv.getTypeUtils().isAssignable(o1, o2)
                    ? -1 : processingEnv.getTypeUtils().isAssignable(o2, o1) ? +1 : o1.toString().compareTo(o2.toString());

            Map<String, Map<TypeMirror, ExecutableElement>> converters = new HashMap<>();
            TypeElement annotationType = this.processingEnv.getElementUtils().getTypeElement("org.apache.camel.Converter");
            for (Element element : roundEnv.getElementsAnnotatedWith(annotationType)) {
                if (element.getKind() == ElementKind.METHOD) {
                    ExecutableElement ee = (ExecutableElement) element;
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
                    converters.computeIfAbsent(toString(to), c -> new TreeMap<>(comparator)).put(from, ee);
                }
            }

            // We're in tests, do not generate anything
            if (this.processingEnv.getElementUtils().getTypeElement("org.apache.camel.converter.ObjectConverter") == null) {
                return false;
            }

            String p = "org.apache.camel.impl.converter";
            String c = "CoreFallbackConverter";
            JavaFileObject jfo = processingEnv.getFiler().createSourceFile(p + "." + c);
            Set<String> converterClasses = new LinkedHashSet<>();
            try (Writer writer = jfo.openWriter()) {

                writer.append("package ").append(p).append(";\n");
                writer.append("\n");
                writer.append("import org.apache.camel.support.TypeConverterSupport;\n");
                writer.append("import org.apache.camel.Exchange;\n");
                writer.append("import org.apache.camel.TypeConversionException;\n");
                writer.append("\n");
                writer.append("@SuppressWarnings(\"unchecked\")\n");
                writer.append("public class ").append(c).append(" extends TypeConverterSupport {\n");
                writer.append("\n");
                writer.append("    public <T> T convertTo(Class<T> type, Exchange exchange, Object value) throws TypeConversionException {\n");
                writer.append("        try {\n");
                writer.append("            return (T) doConvert(type, exchange, value);\n");
                writer.append("        } catch (TypeConversionException e) {\n");
                writer.append("            throw e;\n");
                writer.append("        } catch (Exception e) {\n");
                writer.append("            throw new TypeConversionException(value, type, e);\n");
                writer.append("        }\n");
                writer.append("    }\n");
                writer.append("\n");
                writer.append("    private Object doConvert(Class<?> type, Exchange exchange, Object value) throws Exception {\n");
                writer.append("        switch (type.getName()) {\n");
                for (Map.Entry<String, Map<TypeMirror, ExecutableElement>> to : converters.entrySet()) {
                    writer.append("            case \"").append(to.getKey()).append("\": {\n");
                    for (Map.Entry<TypeMirror, ExecutableElement> from : to.getValue().entrySet()) {
                        String name = toString(from.getKey());
                        if ("java.lang.Object".equals(name)) {
                            writer.append("                if (value != null) {\n");
                        } else {
                            writer.append("                if (value instanceof ").append(name).append(") {\n");
                        }
                        writer.append("                    return ").append(toJava(from.getValue(), converterClasses)).append(";\n");
                        writer.append("                }\n");
                    }
                    writer.append("                break;\n");
                    writer.append("            }\n");
                }
                writer.append("        }\n");
                writer.append("        return null;\n");
                writer.append("    }\n");

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

        } catch (Throwable e) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Unable to process elements annotated with @UriEndpoint: " + e.getMessage());
            dumpExceptionToErrorFile("camel-apt-error.log", "Error processing @Converter", e);
        }
        return false;
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

    public static void dumpExceptionToErrorFile(String fileName, String message, Throwable e) {
        File file = new File(fileName);
        try {
            FileOutputStream fos = new FileOutputStream(file);
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            fos.write(message.getBytes());
            fos.write("\n\n".getBytes());
            fos.write(sw.toString().getBytes());
            pw.close();
            sw.close();
            fos.close();
        } catch (Throwable t) {
            // ignore
        }
    }

}
