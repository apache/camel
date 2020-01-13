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
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
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

import org.apache.camel.model.OptionalIdentifiedDefinition;
import org.apache.camel.model.OutputDefinition;
import org.apache.camel.model.RoutesDefinition;
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
                .flatMap(this::fieldReferences)
                .distinct()
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
        List<Class<?>> allClasses = new ArrayList<>();
        for (Class<?> cl = clazz; cl != Object.class; cl = cl.getSuperclass()) {
            allClasses.add(cl);
        }
        return allClasses.stream();
    }
    private Stream<Class<?>> fieldReferences(Class<?> clazz) {
        return Stream.concat(
            Stream.of(clazz),
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

        for (Class<?> clazz : model) {
            if (clazz.getAnnotation(XmlEnum.class) != null || clazz.isInterface()) {
                continue;
            }
            String name = clazz.getSimpleName();
            boolean hasDerived = model.stream().anyMatch(cl -> cl.getSuperclass() == clazz);

            List<Member> members = getMembers(clazz);

            // XmlAttribute
            List<Member> attributeMembers = members.stream()
                    .filter(member -> ((AccessibleObject) member).getAnnotation(XmlAttribute.class) != null)
                    .collect(Collectors.toList());
            String baseAttributeHandler = null;
            for (Class<?> parent = clazz.getSuperclass(); parent != Object.class; parent = parent.getSuperclass()) {
                if (getMembers(parent).stream()
                        .anyMatch(member -> ((AccessibleObject) member).getAnnotation(XmlAttribute.class) != null)) {
                    baseAttributeHandler = lowercase(parent.getSimpleName()) + "AttributeHandler()";
                    break;
                }
            }
            String attributes;
            if (attributeMembers.isEmpty()) {
                attributes = "\n    " + (baseAttributeHandler != null ? baseAttributeHandler : "noAttributeHandler()");
            } else {
                SortedMap<String, String> cases = new TreeMap<>();
                for (Member member : attributeMembers) {
                    Type pt = member instanceof Method ? ((Method) member).getGenericParameterTypes()[0] : ((Field) member).getGenericType();
                    GenericType type = new GenericType(pt);
                    String mn = member.getName();
                    String an = ((AccessibleObject) member).getAnnotation(XmlAttribute.class).name();
                    if ("##default".equals(an)) {
                        an = member instanceof Method ? propname(mn) : mn;
                    }
                    String sn = member instanceof Method ? mn : "set" + uppercase(mn);
                    cases.put(an, "def." + sn + "(" + conversion(parser, type, "val") + ");");
                }
                String defaultCase = baseAttributeHandler != null ? baseAttributeHandler + ".accept(def, key, val)" : "false";
                if (attributeMembers.size() == 1) {
                    Map.Entry<String, String> entry = cases.entrySet().iterator().next();
                    attributes =
                            " (def, key, val) -> {\n" +
                            "    if (\"" + entry.getKey() + "\".equals(key)) {\n" +
                            "        " + entry.getValue() + "\n" +
                            "        return true;\n" +
                            "    } else {\n" +
                            "        return " + defaultCase + ";\n" +
                            "    }\n" +
                            "}";
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(" (def, key, val) -> {\n"
                            + "    switch (key) {\n");
                    for (Map.Entry<String, String> entry : cases.entrySet()) {
                        sb.append("        case \"").append(entry.getKey()).append("\": ").append(entry.getValue()).append(" break;\n");
                    }
                    sb.append("        default: return ").append(defaultCase).append(";\n"
                            + "    }\n"
                            + "    return true;\n"
                            + "}");
                    attributes = sb.toString();
                }
            }

            // @XmlAnyAttribute
            members.stream()
                    .filter(member -> ((AccessibleObject) member).getAnnotation(XmlAnyAttribute.class) != null)
                    .forEach(member -> {
                        if (!"otherAttributes".equals(member.getName())) {
                            throw new UnsupportedOperationException("Class " + clazz.getName() + " / member " + member + ": unsupported @XmlAnyAttribute");
                        }
                    });


            // @XmlElementRef @XmlElement @XmlElements
            List<Member> elementMembers = members.stream()
                    .filter(member -> ((AccessibleObject) member).getAnnotation(XmlAttribute.class) == null)
                    .filter(member -> ((AccessibleObject) member).getAnnotation(XmlAnyAttribute.class) == null)
                    .filter(member -> ((AccessibleObject) member).getAnnotation(XmlValue.class) == null)
                    .collect(Collectors.toList());
            SortedMap<String, String> cases = new TreeMap<>();
            // XmlElementRef
            elementMembers.stream()
                .filter(member -> ((AccessibleObject) member).getAnnotation(XmlElementRef.class) != null
                                || (clazz == OutputDefinition.class && "setOutputs".equals(member.getName())))
                .forEach(member -> {
                    Type pt = member instanceof Method ? ((Method) member).getGenericParameterTypes()[0] : ((Field) member).getGenericType();
                    GenericType type = new GenericType(pt);
                    boolean list = type.getRawClass() == List.class;
                    Class<?> root = list ? type.getActualTypeArgument(0).getRawClass() : type.getRawClass();
                    model.stream()
                        .filter(root::isAssignableFrom)
                        .filter(cl -> cl.getAnnotation(XmlRootElement.class) != null)
                        .forEach(cl -> {
                            String fn = member.getName();
                            String en = cl.getAnnotation(XmlRootElement.class).name();
                            if ("##default".equals(en)) {
                                en = lowercase(cl.getSimpleName());
                            }
                            String sn = member instanceof Method ? fn : "set" + uppercase(fn);
                            String gn = member instanceof Method ? "g" + sn.substring(1) : "get" + uppercase(fn);
                            String tn = cl.getSimpleName();
                            cases.put(en, list
                                ? "doAdd(doParse" + tn + "(), def." + gn + "(), def::" + sn + ");"
                                : "def." + sn + "(doParse" + tn + "());");
                        });
                });
            // @XmlElements
            elementMembers.stream()
                .filter(member -> ((AccessibleObject) member).getAnnotation(XmlElements.class) != null)
                .forEach(member -> {
                    Type pt = member instanceof Method ? ((Method) member).getGenericParameterTypes()[0] : ((Field) member).getGenericType();
                    GenericType type = new GenericType(pt);
                    boolean list = type.getRawClass() == List.class;
                    String fn = member.getName();
                    String sn = member instanceof Method ? fn : "set" + uppercase(fn);
                    String gn = member instanceof Method ? "g" + sn.substring(1) : "get" + uppercase(fn);
                    Stream.of(((AccessibleObject) member).getAnnotation(XmlElements.class).value()).forEach(xe ->
                        cases.put(xe.name(), list
                            ? "doAdd(doParse" + xe.type().getSimpleName() + "(), def." + gn + "(), def::" + sn + ");"
                            : "def." + sn + "(doParse" + xe.type().getSimpleName() + "());"));
                });
            elementMembers.stream()
                .filter(member -> ((AccessibleObject) member).getAnnotation(XmlElementRef.class) == null)
                .filter(member -> ((AccessibleObject) member).getAnnotation(XmlElements.class) == null)
                .filter(member -> clazz != OutputDefinition.class || !"setOutputs".equals(member.getName()))
                .forEach(member -> {
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
                    String en = "##default";
                    if (((AccessibleObject) member).getAnnotation(XmlElement.class) != null) {
                        en = ((AccessibleObject) member).getAnnotation(XmlElement.class).name();
                    }
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
                        String n = adapter.getDeclaringClass() != null
                                ? adapter.getDeclaringClass().getSimpleName() + "." + adapter.getSimpleName()
                                : adapter.getSimpleName();
                        if (c == String.class) {
                            pc = "unmarshal(new " + n + "(), doParseText())";
                        } else if (model.contains(c)) {
                            pc = "unmarshal(new " + n + "(), doParse" + c.getSimpleName() + "())";
                        } else{
                            throw new UnsupportedOperationException("Class " + clazz.getName() + " / member " + member + ": unsupported @XmlJavaTypeAdapter");
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
                    cases.put(en, list
                            ? "doAdd(" + pc + ", def." + gn + "(), def::" + sn + ");"
                            : "def." + sn + "(" + pc + ");");
                });
            String baseElementHandlers = null;
            for (Class<?> parent = clazz.getSuperclass(); parent != Object.class; parent = parent.getSuperclass()) {
                if (getMembers(parent).stream()
                    .anyMatch(member -> ((AccessibleObject) member).getAnnotation(XmlAttribute.class) == null
                            && ((AccessibleObject) member).getAnnotation(XmlAnyAttribute.class) == null
                            && ((AccessibleObject) member).getAnnotation(XmlValue.class) == null)) {
                    baseElementHandlers = lowercase(parent.getSimpleName()) + "ElementHandler()";
                    break;
                }
            }
            String elements;
            if (cases.isEmpty()) {
                if (baseElementHandlers == null) {
                    elements = " noElementHandler()";
                } else {
                    elements = " " + baseElementHandlers;
                }
            } else if (cases.size() == 1) {
                Map.Entry<String, String> entry = cases.entrySet().iterator().next();
                elements =
                        " (def, key) -> {\n" +
                        "    if (\"" + entry.getKey() + "\".equals(key)) {\n" +
                        "        " + entry.getValue() + "\n" +
                        "        return true;\n" +
                        "    } else {\n" +
                        "        return " + (baseElementHandlers != null ? baseElementHandlers + ".accept(def, key);" : "false;") + "\n" +
                        "    }\n" +
                        "}";
            } else {
                elements =
                        " (def, key) -> {\n" +
                        "    switch (key) {\n" +
                        cases.entrySet().stream().map(e ->
                        "        case \"" + e.getKey() + "\": " + e.getValue() + " break;\n"
                        ).collect(Collectors.joining()) +
                        "        default: return " + (baseElementHandlers != null ? baseElementHandlers + ".accept(def, key);" : "false;") + "\n" +
                        "    }\n" +
                        "    return true;\n" +
                        "}";
            }

            // @XmlValue
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
                    .orElseGet(() -> {
                        for (Class<?> parent = clazz.getSuperclass(); parent != Object.class; parent = parent.getSuperclass()) {
                            if (getMembers(parent).stream()
                                    .anyMatch(member -> ((AccessibleObject) member).getAnnotation(XmlValue.class) != null)) {
                                return " " + lowercase(parent.getSimpleName()) + "ValueHandler()";
                            }
                        }
                        return " noValueHandler()";
                    });
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
            String qname;
            if (clazz.getDeclaringClass() != null) {
                parser.addImport(clazz.getDeclaringClass());
                qname = clazz.getDeclaringClass().getSimpleName() + "." + name;
            } else {
                parser.addImport(clazz);
                qname = name;
            }
            if (hasDerived) {
                if (!attributeMembers.isEmpty()) {
                    parser.addMethod()
                            .setSignature("    protected <T extends " + qname + "> AttributeHandler<T> " + lowercase(name) + "AttributeHandler()")
                            .setBody("return" + attributes + ";");
                }
                if (!elementMembers.isEmpty()) {
                    parser.addMethod()
                            .setSignature("    protected <T extends " + qname + "> ElementHandler<T> " + lowercase(name) + "ElementHandler()")
                            .setBody("return" + elements + ";");
                }
                if (!Modifier.isAbstract(clazz.getModifiers())) {
                    parser.addMethod()
                            .setSignature("    protected " + qname + " doParse" + name + "() throws IOException, XmlPullParserException")
                            .setBody("return doParse(new " + qname + "(), " +
                                    (attributeMembers.isEmpty() ? attributes : lowercase(name) + "AttributeHandler()") + ", " +
                                    (elementMembers.isEmpty() ? elements : lowercase(name) + "ElementHandler()") + "," + value + ");\n");
                }
            } else {
                parser.addMethod()
                        .setSignature("    protected " + qname + " doParse" + name + "() throws IOException, XmlPullParserException")
                        .setBody("return doParse(new " + qname + "()," + attributes + "," + elements + "," + value + ");\n");
            }
        }

        return parser;
    }

    private String conversion(JavaClass parser, GenericType type, String val) {
        Class<?> rawClass = type.getRawClass();
        if (rawClass == String.class) {
            return val;
        } else if (rawClass.isEnum() || rawClass == Integer.class || rawClass == Long.class
                || rawClass == Boolean.class || rawClass == Float.class) {
            parser.addImport(rawClass);
            return rawClass.getSimpleName() + ".valueOf(" + val + ")";
        } else if (rawClass == List.class && type.getActualTypeArgument(0).getRawClass() == String.class) {
            return "asStringList(" + val + ")";
        } else if (rawClass == Set.class && type.getActualTypeArgument(0).getRawClass() == String.class) {
            return "asStringSet(" + val + ")";
        } else if (rawClass == Class.class) {
            return "asClass(" + val + ")";
        } else if (rawClass == Class[].class) {
            return "asClassArray(" + val + ")";
        } else if (rawClass == byte[].class) {
            return "asByteArray(" + val + ")";
        } else {
            throw new UnsupportedOperationException();
        }
    }

    private List<Member> getMembers(Class<?> clazz) {
        List<Member> members = Stream.concat(
                    findFieldsForClass(clazz),
                    findMethodsForClass(clazz))
                .filter(m -> ((AnnotatedElement) m).getAnnotation(XmlTransient.class) == null)
                .sorted(Comparator.comparing(member -> member instanceof Method ? propname(member.getName()) : member.getName()))
                .collect(Collectors.toList());
        if (clazz != OutputDefinition.class && OutputDefinition.class.isAssignableFrom(clazz)) {
            members.removeIf(m -> "setOutputs".equals(m.getName()));
        }
        return members;
    }

    private Stream<? extends Member> findMethodsForClass(Class<?> c) {
        XmlAccessType accessType;
        if (c.getAnnotation(XmlAccessorType.class) != null && c != OutputDefinition.class) {
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
