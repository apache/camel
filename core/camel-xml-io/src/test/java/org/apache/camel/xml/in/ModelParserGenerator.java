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
package org.apache.camel.xml.in;

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAnyAttribute;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementRef;
import javax.xml.bind.annotation.XmlElements;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.XmlValue;
import javax.xml.bind.annotation.adapters.XmlAdapter;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.IdentifiedType;
import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.OutputDefinition;
import org.apache.camel.model.OutputExpressionNode;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.cloud.ServiceCallConfiguration;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.rest.RestsDefinition;
import org.apache.camel.tooling.util.srcgen.GenericType;
import org.apache.camel.tooling.util.srcgen.JavaClass;
import org.apache.camel.xml.io.XmlPullParserException;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.Indexer;

public class ModelParserGenerator {

    public String generateParser() {
        String url = RoutesDefinition.class.getClassLoader().getResource(
                RoutesDefinition.class.getName().replace('.', '/') + ".class").toString();
        if (url.startsWith("file:")) {
            url = url.substring("file:".length(), url.indexOf("org/apache/camel"));
        } else if (url.startsWith("jar:file:")) {
            url = url.substring("jar:file:".length(), url.indexOf("!"));
        }
        Indexer indexer = new Indexer();
        Stream.of(url)
                .map(this::asFolder)
                .filter(Files::isDirectory)
                .flatMap(this::walk)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".class"))
                .forEach(p -> index(indexer, p));
        IndexView index = indexer.complete();
        List<Class<?>> model = Stream.of(XmlRootElement.class, XmlEnum.class, XmlType.class)
                .map(Class::getName)
                .map(DotName::createSimple)
                .map(index::getAnnotations)
                .flatMap(Collection::stream)
                .map(AnnotationInstance::target)
                .map(AnnotationTarget::asClass)
                .map(ClassInfo::name)
                .map(DotName::toString)
                .sorted()
                .distinct()
                .map(this::forName)
                .flatMap(this::references)
                .distinct()
                .filter(c -> c != OptionalIdentifiedDefinition.class)
                .collect(Collectors.toList());
        JavaClass parser = generateParser(model);
        return  "/*\n" +
                " * Licensed to the Apache Software Foundation (ASF) under one or more\n" +
                " * contributor license agreements.  See the NOTICE file distributed with\n" +
                " * this work for additional information regarding copyright ownership.\n" +
                " * The ASF licenses this file to You under the Apache License, Version 2.0\n" +
                " * (the \"License\"); you may not use this file except in compliance with\n" +
                " * the License.  You may obtain a copy of the License at\n" +
                " *\n" +
                " *      http://www.apache.org/licenses/LICENSE-2.0\n" +
                " *\n" +
                " * Unless required by applicable law or agreed to in writing, software\n" +
                " * distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
                " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
                " * See the License for the specific language governing permissions and\n" +
                " * limitations under the License.\n" +
                " */\n" + parser.printClass();
    }

    private Stream<Class<?>> references(Class<?> clazz) {
        return Stream.concat(Stream.of(clazz),
            Stream.of(clazz.getDeclaredFields())
                .filter(f -> f.getAnnotation(XmlTransient.class) == null)
                .map(f -> {
                    if (f.getAnnotation(XmlJavaTypeAdapter.class) != null) {
                        Class<?> cl = f.getAnnotation(XmlJavaTypeAdapter.class).value();
                        while (cl.getSuperclass() != XmlAdapter.class) {
                            cl = cl.getSuperclass();
                        }
                        return ((ParameterizedType) cl.getGenericSuperclass()).getActualTypeArguments()[0];
                    } else {
                        return f.getGenericType();
                    }
                })
                .map(GenericType::new)
                .map(t -> t.getRawClass() == List.class ? t.getActualTypeArgument(0) : t)
                .map(GenericType::getRawClass)
                .filter(c -> c.getName().startsWith("org.apache.camel."))
        );
    }

    private JavaClass generateParser(List<Class<?>> model) {
        JavaClass parser = new JavaClass(ModelParser.class.getClassLoader());
        parser.setMaxImportPerPackage(4);
        parser.setPackage("org.apache.camel.xml.in");
        parser.setName("ModelParser");
        parser.extendSuperType("BaseParser");
        parser.addImport(OptionalIdentifiedDefinition.class);
        parser.addImport(IOException.class);
        parser.addImport(XmlPullParserException.class);
        parser.addImport(Array.class);
        parser.addAnnotation(SuppressWarnings.class)
                .setLiteralValue("\"unused\"");
        parser.addAnnotation(Generated.class)
                .setLiteralValue("\"" + getClass().getName() + "\"");
        parser.addMethod()
                .setConstructor(true)
                .setPublic()
                .setName("ModelParser")
                .addParameter(InputStream.class, "input")
                .addThrows(IOException.class)
                .addThrows(XmlPullParserException.class)
                .setBody("super(input);");
        parser.addMethod()
                .setConstructor(true)
                .setPublic()
                .setName("ModelParser")
                .addParameter(Reader.class, "reader")
                .addThrows(IOException.class)
                .addThrows(XmlPullParserException.class)
                .setBody("super(reader);");
        parser.addMethod()
                .setConstructor(true)
                .setPublic()
                .setName("ModelParser")
                .addParameter(InputStream.class, "input")
                .addParameter(String.class, "namespace")
                .addThrows(IOException.class)
                .addThrows(XmlPullParserException.class)
                .setBody("super(input, namespace);");
        parser.addMethod()
                .setConstructor(true)
                .setPublic()
                .setName("ModelParser")
                .addParameter(Reader.class, "reader")
                .addParameter(String.class, "namespace")
                .addThrows(IOException.class)
                .addThrows(XmlPullParserException.class)
                .setBody("super(reader, namespace);");

        // XmlRootElement
        for (Class<?> clazz : model) {
            if ((clazz.getModifiers() & Modifier.ABSTRACT) != 0) {
                continue;
            }
            if (clazz.getAnnotation(XmlEnum.class) != null) {
                continue;
            }
            String name = clazz.getSimpleName();

            List<Class<?>> allClasses = new ArrayList<>();
            for (Class<?> cl = clazz; cl != Object.class; cl = cl.getSuperclass()) {
                allClasses.add(0, cl);
            }
            List<Member> members = Stream.concat(
                        allClasses.stream().flatMap(this::findFieldsForClass),
                        allClasses.stream().flatMap(this::findMethodsForClass))
                    .filter(m -> ((AnnotatedElement) m).getAnnotation(XmlTransient.class) == null)
                    .sorted(Comparator.comparing(member -> member instanceof Method ? propname(member.getName()) : member.getName()))
                    .collect(Collectors.toList());

            if (allClasses.contains(OutputDefinition.class) || allClasses.contains(OutputExpressionNode.class)) {
                members.removeIf(m -> "setOutputs".equals(m.getName()));
                members.removeIf(m -> "outputs".equals(m.getName()));
            }
            if (allClasses.contains(ExpressionNode.class) || allClasses.contains(OutputExpressionNode.class)) {
                members.removeIf(m -> "setExpression".equals(m.getName()));
                members.removeIf(m -> "expression".equals(m.getName()));
            }
            if (allClasses.contains(OptionalIdentifiedDefinition.class)) {
                members.removeIf(m -> "setDescription".equals(m.getName()));
                members.removeIf(m -> "setId".equals(m.getName()));
                members.removeIf(m -> "setCustomId".equals(m.getName()));
            }
            if (allClasses.contains(ProcessorDefinition.class)) {
                members.removeIf(m -> "inheritErrorHandler".equals(m.getName()));
            }
            if (allClasses.contains(IdentifiedType.class)) {
                members.removeIf(m -> "id".equals(m.getName()));
            }
            if (allClasses.contains(ServiceCallConfiguration.class)) {
                members.removeIf(m -> "properties".equals(m.getName()));
            }
            if (allClasses.contains(ExpressionDefinition.class)) {
                members.removeIf(m -> "id".equals(m.getName()));
                members.removeIf(m -> "trim".equals(m.getName()));
            }

            List<Member> attributeMembers = members.stream()
                    .filter(member -> ((AccessibleObject) member).getAnnotation(XmlAttribute.class) != null)
                    .collect(Collectors.toList());
            String baseAttributeHandler;
            if (allClasses.contains(ProcessorDefinition.class)) {
                baseAttributeHandler = "processorDefinitionAttributeHandler()";
            } else if (allClasses.contains(OptionalIdentifiedDefinition.class)) {
                baseAttributeHandler = "optionalIdentifiedDefinitionAttributeHandler()";
            } else if (allClasses.contains(IdentifiedType.class)) {
                baseAttributeHandler = "identifiedTypeAttributeHandler()";
            } else if (allClasses.contains(ExpressionDefinition.class)) {
                baseAttributeHandler = "expressionDefinitionAttributeHandler()";
            } else {
                baseAttributeHandler = null;
            }
            String attributes;
            if (attributeMembers.isEmpty()) {
                attributes = "\n    " +
                        (baseAttributeHandler != null ? baseAttributeHandler : "noAttributeHandler()");
            } else {
                StringBuilder sb = new StringBuilder();
                sb.append(" (def, key, val) -> {\n" +
                          "    switch (key) {\n");
                for (Member member : attributeMembers) {
                    Type pt = member instanceof Method ? ((Method) member).getGenericParameterTypes()[0] : ((Field) member).getGenericType();
                    GenericType type = new GenericType(pt);
                    String mn = member.getName();
                    String an = ((AccessibleObject) member).getAnnotation(XmlAttribute.class).name();
                    if ("##default".equals(an)) {
                        an = member instanceof Method ? propname(mn) : mn;
                    }
                    String sn = member instanceof Method ? mn : "set" + uppercase(mn);
                    if (pt == String.class) {
                        sb.append("        case \"" + an + "\": def." + sn + "(val); break;\n");
                    } else if (type.getRawClass().isEnum() || pt == Integer.class || pt == Long.class || pt == Boolean.class || pt == Float.class) {
                        sb.append("        case \"" + an + "\": def." + sn + "(" + type.getRawClass().getName() + ".valueOf(val)); break;\n");
                    } else if (type.getRawClass() == List.class && type.getActualTypeArgument(0).getRawClass() == String.class) {
                        sb.append("        case \"" + an + "\": def." + sn + "(asStringList(val)); break;\n");
                    } else if (type.getRawClass() == Set.class && type.getActualTypeArgument(0).getRawClass() == String.class) {
                        sb.append("        case \"" + an + "\": def." + sn + "(asStringSet(val)); break;\n");
                    } else if (type.getRawClass() == Class.class) {
                        sb.append("        case \"" + an + "\": def." + sn + "(asClass(val)); break;\n");
                    } else if (type.getRawClass() == Class[].class) {
                        sb.append("        case \"" + an + "\": def." + sn + "(asClassArray(val)); break;\n");
                    } else if (type.getRawClass() == byte[].class) {
                        sb.append("        case \"" + an + "\": def." + sn + "(asByteArray(val)); break;\n");
                    } else {
                        System.err.println("Class " + clazz.getName() + " / member " + member + ": unsupported type: " + pt);
                    }
                }
                sb.append("        default: return " +
                                (baseAttributeHandler != null
                                        ? baseAttributeHandler + ".accept(def, key, val)" : "false") + ";\n" +
                          "    }\n" +
                          "    return true;\n" +
                          "}");
                attributes = sb.toString();
            }

            String elements = members.stream()
                    .filter(member -> ((AccessibleObject) member).getAnnotation(XmlAttribute.class) == null)
                    .filter(member -> ((AccessibleObject) member).getAnnotation(XmlValue.class) == null)
                    .map(member -> {
                        AccessibleObject ao = (AccessibleObject) member;
                        if (ao.getAnnotation(XmlElementRef.class) != null) {
                            Type pt = member instanceof Method ? ((Method) member).getGenericParameterTypes()[0] : ((Field) member).getGenericType();
                            GenericType type = new GenericType(pt);
                            boolean list = type.getRawClass() == List.class;
                            Class<?> root = list ? type.getActualTypeArgument(0).getRawClass() : type.getRawClass();
                            return model.stream()
                                    .filter(root::isAssignableFrom)
                                    .filter(cl -> cl.getAnnotation(XmlRootElement.class) != null)
                                    .map(cl -> {
                                        String fn = member.getName();
                                        String en = cl.getAnnotation(XmlRootElement.class).name();
                                        if ("##default".equals(en)) {
                                            en = lowercase(cl.getSimpleName());
                                        }
                                        String sn = member instanceof Method ? fn : "set" + uppercase(fn);
                                        String gn = member instanceof Method ? "g" + sn.substring(1) : "get" + uppercase(fn);
                                        String tn = cl.getSimpleName();
                                        if (list) {
                                            return "        case \"" + en + "\": doAdd(doParse" + tn + "(), def." + gn + "(), def::" + sn + "); break;\n";
                                        } else {
                                            return "        case \"" + en + "\": def." + sn + "(doParse" + tn + "()); break;\n";
                                        }
                                    })
                                    .collect(Collectors.joining());
                        } else if (ao.getAnnotation(XmlElements.class) != null) {
                            Type pt = member instanceof Method ? ((Method) member).getGenericParameterTypes()[0] : ((Field) member).getGenericType();
                            GenericType type = new GenericType(pt);
                            boolean list = type.getRawClass() == List.class;
                            String fn = member.getName();
                            String sn = member instanceof Method ? fn : "set" + uppercase(fn);
                            String gn = member instanceof Method ? "g" + sn.substring(1) : "get" + uppercase(fn);
                            return Stream.of(ao.getAnnotation(XmlElements.class).value())
                                    .map(xe -> {
                                        if (list) {
                                            return "        case \"" + xe.name() + "\": doAdd(doParse" + xe.type().getSimpleName() + "(), def." + gn + "(), def::" + sn + "); break;\n";
                                        } else {
                                            return "        case \"" + xe.name() + "\": def." + sn + "(doParse" + xe.type().getSimpleName() + "()); break;\n";
                                        }
                                    })
                                    .collect(Collectors.joining());
                        } else if (ao.getAnnotation(XmlAnyAttribute.class) != null) {
                            if (!"otherAttributes".equals(member.getName())) {
                                System.err.println("Class " + clazz.getName() + " / member " + member + ": unsupported @XmlAnyAttribute");
                            }
                            // ignore
                            return "";
                        } else {
                            Type pt = member instanceof Method ? ((Method) member).getGenericParameterTypes()[0] : ((Field) member).getGenericType();
                            GenericType type = new GenericType(pt);
                            boolean list;
                            Class<?> root;
                            if (type.getRawClass() == List.class) {
                                list = true;
                                root = type.getActualTypeArgument(0).getRawClass();
                            } else if (type.getRawClass().isArray()) {
                                list = true;
                                root = type.getRawClass().getComponentType();
                            } else {
                                list = false;
                                root = type.getRawClass();
                            }
                            String fn = member.getName();
                            String en = Optional.ofNullable(((AccessibleObject) member).getAnnotation(XmlElement.class))
                                            .map(XmlElement::name).orElse("##default");
                            if ("##default".equals(en)) {
                                en = member instanceof Method ? propname(fn) : fn;
                            }
                            String sn = member instanceof Method ? fn : "set" + uppercase(fn);
                            String gn = member instanceof Method ? "g" + sn.substring(1) : "get" + uppercase(fn);
                            String tn = root.getSimpleName();
                            String pc;
                            if (((AccessibleObject) member).getAnnotation(XmlJavaTypeAdapter.class) != null) {
                                Class<? extends XmlAdapter> adapter = ((AccessibleObject) member).getAnnotation(XmlJavaTypeAdapter.class).value();
                                Class<?> cl = adapter;
                                while (cl.getSuperclass() != XmlAdapter.class) {
                                    cl = cl.getSuperclass();
                                }
                                Type t = ((ParameterizedType) cl.getGenericSuperclass()).getActualTypeArguments()[0];
                                Class<?> c = new GenericType(t).getRawClass();
                                if (c == String.class) {
                                    pc = "unmarshal(new " + adapter.getName().replace('$', '.') + "(), doParseText())";
                                } else if (model.contains(c)) {
                                    pc = "unmarshal(new " + adapter.getName().replace('$', '.') + "(), doParse" + c.getSimpleName() + "())";
                                } else{
                                    System.err.println("Class " + clazz.getName() + " / member " + member + ": unsupported @XmlJavaTypeAdapter");
                                    return "";
                                }
                                if (list && type.equals(new GenericType(((ParameterizedType) cl.getGenericSuperclass()).getActualTypeArguments()[1]))) {
                                    list = false;
                                }
                            } else if (model.contains(root)) {
                                pc = "doParse" + tn + "()";
                            } else if (root == String.class) {
                                pc = "doParseText()";
                            } else {
                                String n = root.getName();
                                if (n.startsWith("java.lang.")) {
                                    n = tn;
                                }
                                pc = n + ".valueOf(doParseText())";
                            }
                            if (list) {
                                return "        case \"" + en + "\": doAdd(" + pc + ", def." + gn + "(), def::" + sn + "); break;\n";
                            } else {
                                return "        case \"" + en + "\": def." + sn + "(" + pc + "); break;\n";
                            }
                        }
                    })
                    .collect(Collectors.joining());
            String baseElementHandlers;
            if (allClasses.contains(OutputDefinition.class)) {
                baseElementHandlers = "outputDefinitionElementHandler()";
            } else if (allClasses.contains(OutputExpressionNode.class)) {
                baseElementHandlers = "outputExpressionNodeElementHandler()";
            } else if (allClasses.contains(ExpressionNode.class)) {
                baseElementHandlers = "expressionNodeElementHandler()";
            } else if (allClasses.contains(OptionalIdentifiedDefinition.class)) {
                baseElementHandlers = "optionalIdentifiedDefinitionElementHandler()";
            } else if (allClasses.contains(ServiceCallConfiguration.class)) {
                baseElementHandlers = "serviceCallConfigurationElementHandler()";
            } else {
                baseElementHandlers = null;
            }
            if (elements.isEmpty()) {
                if (baseElementHandlers == null) {
                    elements = " emptyElementHandler()";
                } else {
                    elements = " " + baseElementHandlers;
                }
            } else {
                elements =
                        " (def, key) -> {\n" +
                        "    switch (key) {\n" +
                        elements +
                        "        default: return " + (baseElementHandlers != null
                                    ? baseElementHandlers + ".accept(def, key);" : "false;") + "\n" +
                        "    }\n" +
                        "    return true;\n" +
                        "}";
            }
            String value = members.stream()
                    .filter(member -> ((AccessibleObject) member).getAnnotation(XmlValue.class) != null)
                    .findFirst()
                    .map(member -> {
                        String fn = member.getName();
                        String sn = member instanceof Method ? fn : "set" + uppercase(fn);
                        if (ExpressionDefinition.class.isAssignableFrom(member.getDeclaringClass())) {
                            return " expressionDefinitionValueHandler()";
                        } else {
                            return " (def, val) -> def." + sn + "(val)";
                        }
                    })
                    .orElse(" noValueHandler()");
            if (clazz == RoutesDefinition.class || clazz == RestsDefinition.class) {
                String element = clazz.getAnnotation(XmlRootElement.class).name();
                parser.addMethod()
                        .setPublic()
                        .setReturnType(clazz)
                        .setName("parse" + name)
                        .addThrows(IOException.class)
                        .addThrows(XmlPullParserException.class)
                        .setBody("expectTag(\"" + element + "\");\nreturn doParse" + name + "();");
            }
            String className;
            if (clazz.getDeclaringClass() != null) {
                parser.addImport(clazz.getDeclaringClass());
                className = clazz.getDeclaringClass().getSimpleName() + "." + clazz.getSimpleName();
            } else {
                parser.addImport(clazz);
                className = clazz.getSimpleName();
            }
            parser.addMethod()
                    .setSignature("    protected " + className + " doParse" + name + "() throws IOException, XmlPullParserException")
                    .setBody("return doParse(new " + className + "()," + attributes + "," + elements + "," + value + ");\n");
        }

        parser.addMethod()
                .setSignature("    protected <T extends OutputExpressionNode> ElementHandler<T> outputExpressionNodeElementHandler()")
                .setBody("return (def, key) ->  expressionNodeElementHandler().accept(def, key) || outputDefinitionElementHandler().accept(def, key);");
        parser.addImport(OutputExpressionNode.class);

        String expressionNodeElementHandlerBody =
                model.stream()
                        .filter(ExpressionDefinition.class::isAssignableFrom)
                        .filter(cl -> cl.getAnnotation(XmlRootElement.class) != null)
                        .map(cl -> {
                            String en = cl.getAnnotation(XmlRootElement.class).name();
                            if ("##default".equals(en)) {
                                en = lowercase(cl.getSimpleName());
                            }
                            String tn = cl.getSimpleName();
                            return "        case \"" + en + "\": def.setExpression(doParse" + tn + "()); break;\n";
                        })
                        .collect(Collectors.joining());
        parser.addMethod()
                .setSignature("    protected <T extends ExpressionNode> ElementHandler<T> expressionNodeElementHandler()")
                .setBody("return (def, key) -> {\n" +
                         "    switch (key) {\n" +
                        expressionNodeElementHandlerBody +
                         "        default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);\n" +
                         "    }\n" +
                         "    return true;\n" +
                         "};\n");
        parser.addImport(ExpressionNode.class);

        String outputDefinitionElementHandlerBody =
                model.stream()
                        .filter(ProcessorDefinition.class::isAssignableFrom)
                        .filter(cl -> cl.getAnnotation(XmlRootElement.class) != null)
                        .map(cl -> {
                            String en = cl.getAnnotation(XmlRootElement.class).name();
                            if ("##default".equals(en)) {
                                en = lowercase(cl.getSimpleName());
                            }
                            String tn = cl.getSimpleName();
                            return "        case \"" + en + "\": pd = doParse" + tn + "(); break;\n";
                        })
                        .collect(Collectors.joining());
        parser.addMethod()
                .setSignature("    protected <T extends ProcessorDefinition<?>> ElementHandler<T> outputDefinitionElementHandler()")
                .setBody("return (def, key) -> {\n" +
                         "    ProcessorDefinition<?> pd;\n" +
                         "    switch (key) {\n" +
                         outputDefinitionElementHandlerBody +
                         "        default: return optionalIdentifiedDefinitionElementHandler().accept(def, key);\n" +
                         "    }\n" +
                         "    def.getOutputs().add(pd);\n" +
                         "    return true;\n" +
                         "};");
        parser.addImport(ProcessorDefinition.class);

        parser.addMethod()
                .setSignature("    protected <T extends OptionalIdentifiedDefinition<?>> ElementHandler<T> optionalIdentifiedDefinitionElementHandler()")
                .setBody("return (def, name) -> {\n" +
                         "    if (\"description\".equals(name)) {\n" +
                         "        def.setDescription(doParseDescriptionDefinition());\n" +
                         "        return true;\n" +
                         "    } else {\n" +
                         "        return false;\n" +
                         "    }\n" +
                         "};");
        parser.addImport(OptionalIdentifiedDefinition.class);

        parser.addMethod()
                .setSignature("    protected <T extends ServiceCallConfiguration> ElementHandler<T> serviceCallConfigurationElementHandler()")
                .setBody("return (def, name) -> {\n" +
                        "    if (\"properties\".equals(name)) {\n" +
                        "        doAdd(doParsePropertyDefinition(), def.getProperties(), def::setProperties);\n" +
                        "        return true;\n" +
                        "    } else {\n" +
                        "        return false;\n" +
                        "    }\n" +
                        "};");

        return parser;
    }

    private Stream<? extends Member> findMethodsForClass(Class<?> c) {
        XmlAccessType accessType;
        if (c.getAnnotation(XmlAccessorType.class) != null) {
            accessType = c.getAnnotation(XmlAccessorType.class).value();
        } else {
            accessType = XmlAccessType.PUBLIC_MEMBER;
        }
        if (accessType == XmlAccessType.FIELD || accessType == XmlAccessType.NONE) {
            return Stream.of(c.getDeclaredMethods())
                    .filter(m -> m.getName().startsWith("set") && m.getParameterCount() == 1)
                    .filter(m -> m.getAnnotation(XmlAttribute.class) != null
                              || m.getAnnotation(XmlElement.class) != null
                              || m.getAnnotation(XmlElementRef.class) != null
                              || m.getAnnotation(XmlValue.class) != null)
                    .sorted(Comparator.comparing(Method::getName));
        } else {
            return Stream.of(c.getDeclaredMethods())
                    .filter(m -> Modifier.isPublic(m.getModifiers()) || accessType == XmlAccessType.PROPERTY)
                    .filter(m -> m.getName().startsWith("set") && m.getParameterCount() == 1)
                    .filter(m -> m.getAnnotation(XmlTransient.class) == null)
                    .sorted(Comparator.comparing(Method::getName));
        }
    }

    private Stream<? extends Member> findFieldsForClass(Class<?> c) {
        XmlAccessType accessType;
        if (c.getAnnotation(XmlAccessorType.class) != null) {
            accessType = c.getAnnotation(XmlAccessorType.class).value();
        } else {
            accessType = XmlAccessType.PUBLIC_MEMBER;
        }
        if (accessType == XmlAccessType.PROPERTY || accessType == XmlAccessType.NONE) {
            return Stream.of(c.getDeclaredFields())
                    .filter(f -> f.getAnnotation(XmlAttribute.class) != null
                              || f.getAnnotation(XmlElement.class) != null
                              || f.getAnnotation(XmlElementRef.class) != null
                              || f.getAnnotation(XmlValue.class) != null)
                    .sorted(Comparator.comparing(Field::getName));
        } else {
            return Stream.of(c.getDeclaredFields())
                    .filter(f -> !Modifier.isTransient(f.getModifiers()) && !Modifier.isStatic(f.getModifiers()))
                    .filter(f -> Modifier.isPublic(f.getModifiers()) || accessType == XmlAccessType.FIELD)
                    .filter(f -> f.getAnnotation(XmlTransient.class) == null)
                    .sorted(Comparator.comparing(Field::getName));
        }
    }

    private String lowercase(String fn) {
        return fn.substring(0, 1).toLowerCase() + fn.substring(1);
    }

    private String uppercase(String fn) {
        return fn.substring(0, 1).toUpperCase() + fn.substring(1);
    }

    private String propname(String name) {
        return lowercase(name.substring(3));
    }

    private Path asFolder(String p) {
        if (p.endsWith(".jar")) {
            File fp = new File(p);
            try {
                Map<String, String> env = new HashMap<>();
                return FileSystems.newFileSystem(URI.create("jar:" + fp.toURI().toString()), env).getPath("/");
            } catch (FileSystemAlreadyExistsException e) {
                return FileSystems.getFileSystem(URI.create("jar:" + fp.toURI().toString())).getPath("/");
            } catch (IOException e) {
                throw new IOError(e);
            }
        } else {
            return Paths.get(p);
        }
    }

    private Stream<Path> walk(Path p) {
        try {
            return Files.walk(p);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    private void index(Indexer indexer, Path p) {
        try (InputStream is = Files.newInputStream(p)) {
            indexer.index(is);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    private Class<?> forName(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

}
