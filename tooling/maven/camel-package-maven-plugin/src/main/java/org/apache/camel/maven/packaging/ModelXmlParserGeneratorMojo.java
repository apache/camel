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
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
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

import org.apache.camel.tooling.util.srcgen.GenericType;
import org.apache.camel.tooling.util.srcgen.JavaClass;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Generate Model lightweight XML Parser source code.
 */
@Mojo(name = "generate-xml-parser", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class ModelXmlParserGeneratorMojo extends AbstractGeneratorMojo {

    public static final String XML_PARSER_PACKAGE = "org.apache.camel.xml.io";
    public static final String XML_PULL_PARSER_EXCEPTION = XML_PARSER_PACKAGE + ".XmlPullParserException";
    public static final String PARSER_PACKAGE = "org.apache.camel.xml.in";
    public static final String MODEL_PACKAGE = "org.apache.camel.model";

    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File sourcesOutputDir;

    @Parameter(defaultValue = "${camel-generate-xml-parser}")
    protected boolean generateXmlParser;

    private Class<?> outputDefinitionClass;
    private Class<?> expressionDefinitionClass;
    private Class<?> routesDefinitionClass;
    private Class<?> restsDefinitionClass;
    private Class<?> processorDefinitionClass;
    private Class<?> dataFormatDefinitionClass;

    @Override
    public void execute(MavenProject project, MavenProjectHelper projectHelper, BuildContext buildContext) throws MojoFailureException, MojoExecutionException {
        sourcesOutputDir = new File(project.getBasedir(), "src/generated/java");
        generateXmlParser = Boolean.parseBoolean(project.getProperties().getProperty("camel-generate-xml-parser", "false"));
        super.execute(project, projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (!generateXmlParser) {
            return;
        }
        Path javaDir = sourcesOutputDir.toPath();
        String parser = generateParser();
        updateResource(javaDir, (PARSER_PACKAGE + ".ModelParser").replace('.', '/') + ".java", parser);
    }

    public String generateParser() throws MojoExecutionException {
        ClassLoader classLoader;
        try {
            classLoader = DynamicClassLoader.createDynamicClassLoader(project.getCompileClasspathElements());
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("DependencyResolutionRequiredException: " + e.getMessage(), e);
        }

        outputDefinitionClass = loadClass(classLoader, MODEL_PACKAGE + ".OutputDefinition");
        routesDefinitionClass = loadClass(classLoader, MODEL_PACKAGE + ".RoutesDefinition");
        dataFormatDefinitionClass = loadClass(classLoader, MODEL_PACKAGE + ".DataFormatDefinition");
        processorDefinitionClass = loadClass(classLoader, MODEL_PACKAGE + ".ProcessorDefinition");
        restsDefinitionClass = loadClass(classLoader, MODEL_PACKAGE + ".rest.RestsDefinition");
        expressionDefinitionClass = loadClass(classLoader, MODEL_PACKAGE + ".language.ExpressionDefinition");

        String resName = routesDefinitionClass.getName().replace('.', '/') + ".class";
        String url = classLoader.getResource(resName).toExternalForm().replace(resName, "META-INF/jandex.idx");
        Index index;
        try (InputStream is = new URL(url).openStream()) {
            index = new IndexReader(is).read();
        } catch (IOException e) {
            throw new MojoExecutionException("IOException: " + e.getMessage(), e);
        }
        List<Class<?>> model = Stream.of(XmlRootElement.class, XmlEnum.class, XmlType.class).map(Class::getName).map(DotName::createSimple).map(index::getAnnotations)
            .flatMap(Collection::stream).map(AnnotationInstance::target).map(AnnotationTarget::asClass).map(ClassInfo::name).map(DotName::toString).sorted().distinct()
            .map(name -> loadClass(classLoader, name)).flatMap(this::references).flatMap(this::fieldReferences).distinct().collect(Collectors.toList());

        JavaClass parser = generateParser(model, classLoader);
        return "/*\n" + " * Licensed to the Apache Software Foundation (ASF) under one or more\n" + " * contributor license agreements.  See the NOTICE file distributed with\n"
               + " * this work for additional information regarding copyright ownership.\n" + " * The ASF licenses this file to You under the Apache License, Version 2.0\n"
               + " * (the \"License\"); you may not use this file except in compliance with\n" + " * the License.  You may obtain a copy of the License at\n" + " *\n"
               + " *      http://www.apache.org/licenses/LICENSE-2.0\n" + " *\n" + " * Unless required by applicable law or agreed to in writing, software\n"
               + " * distributed under the License is distributed on an \"AS IS\" BASIS,\n" + " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
               + " * See the License for the specific language governing permissions and\n" + " * limitations under the License.\n" + " */\n" + "\n" + "//CHECKSTYLE:OFF\n" + "\n"
               + parser.printClass() + "\n" + "//CHECKSTYLE:ON\n";
    }

    protected Class<?> loadClass(ClassLoader loader, String name) {
        try {
            return loader.loadClass(name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unable to load class " + name, e);
        }
    }

    private Stream<Class<?>> references(Class<?> clazz) {
        List<Class<?>> allClasses = new ArrayList<>();
        for (Class<?> cl = clazz; cl != Object.class; cl = cl.getSuperclass()) {
            allClasses.add(cl);
        }
        return allClasses.stream();
    }

    private Stream<Class<?>> fieldReferences(Class<?> clazz) {
        return Stream.concat(Stream.of(clazz), Stream.of(clazz.getDeclaredFields()).filter(f -> f.getAnnotation(XmlTransient.class) == null).map(f -> {
            if (f.getAnnotation(XmlJavaTypeAdapter.class) != null) {
                Class<?> cl = f.getAnnotation(XmlJavaTypeAdapter.class).value();
                while (cl.getSuperclass() != XmlAdapter.class) {
                    cl = cl.getSuperclass();
                }
                return ((ParameterizedType)cl.getGenericSuperclass()).getActualTypeArguments()[0];
            } else {
                return f.getGenericType();
            }
        }).map(GenericType::new).map(t -> t.getRawClass() == List.class ? t.getActualTypeArgument(0) : t).map(GenericType::getRawClass)
            .filter(c -> c.getName().startsWith("org.apache.camel.")));
    }

    // CHECKSTYLE:OFF
    private JavaClass generateParser(List<Class<?>> model, ClassLoader classLoader) {
        JavaClass parser = new JavaClass(classLoader);
        parser.setMaxImportPerPackage(4);
        parser.setPackage(PARSER_PACKAGE);
        parser.setName("ModelParser");
        parser.extendSuperType("BaseParser");
        parser.addImport(MODEL_PACKAGE + ".OptionalIdentifiedDefinition");
        parser.addImport(IOException.class);
        parser.addImport(XML_PULL_PARSER_EXCEPTION);
        parser.addImport(Array.class);
        parser.addAnnotation(SuppressWarnings.class).setLiteralValue("\"unused\"");
        parser.addAnnotation(Generated.class).setLiteralValue("\"" + getClass().getName() + "\"");
        parser.addMethod().setConstructor(true).setPublic().setName("ModelParser").addParameter(InputStream.class, "input").addThrows(IOException.class)
            .addThrows(XML_PULL_PARSER_EXCEPTION).setBody("super(input);");
        parser.addMethod().setConstructor(true).setPublic().setName("ModelParser").addParameter(Reader.class, "reader").addThrows(IOException.class)
            .addThrows(XML_PULL_PARSER_EXCEPTION).setBody("super(reader);");
        parser.addMethod().setConstructor(true).setPublic().setName("ModelParser").addParameter(InputStream.class, "input").addParameter(String.class, "namespace")
            .addThrows(IOException.class).addThrows(XML_PULL_PARSER_EXCEPTION).setBody("super(input, namespace);");
        parser.addMethod().setConstructor(true).setPublic().setName("ModelParser").addParameter(Reader.class, "reader").addParameter(String.class, "namespace")
            .addThrows(IOException.class).addThrows(XML_PULL_PARSER_EXCEPTION).setBody("super(reader, namespace);");

        List<Class<?>> elementRefs = Arrays.asList(processorDefinitionClass, expressionDefinitionClass, dataFormatDefinitionClass);

        for (Class<?> clazz : model) {
            if (clazz.getAnnotation(XmlEnum.class) != null || clazz.isInterface()) {
                continue;
            }
            String name = clazz.getSimpleName();
            String qname;
            if (clazz.getDeclaringClass() != null) {
                parser.addImport(clazz.getDeclaringClass());
                qname = clazz.getDeclaringClass().getSimpleName() + "." + name;
            } else {
                parser.addImport(clazz);
                qname = name;
            }
            boolean hasDerived = model.stream().anyMatch(cl -> cl.getSuperclass() == clazz);

            List<Member> members = getMembers(clazz);

            // XmlAttribute
            List<Member> attributeMembers = members.stream().filter(member -> ((AccessibleObject)member).getAnnotation(XmlAttribute.class) != null).collect(Collectors.toList());
            String baseAttributeHandler = null;
            for (Class<?> parent = clazz.getSuperclass(); parent != Object.class; parent = parent.getSuperclass()) {
                if (getMembers(parent).stream().anyMatch(member -> ((AccessibleObject)member).getAnnotation(XmlAttribute.class) != null)) {
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
                    Type pt = member instanceof Method ? ((Method)member).getGenericParameterTypes()[0] : ((Field)member).getGenericType();
                    GenericType type = new GenericType(pt);
                    String mn = member.getName();
                    String an = ((AccessibleObject)member).getAnnotation(XmlAttribute.class).name();
                    if ("##default".equals(an)) {
                        an = member instanceof Method ? propname(mn) : mn;
                    }
                    String sn = member instanceof Method ? mn : "set" + uppercase(mn);
                    cases.put(an, "def." + sn + "(" + conversion(parser, type, "val") + ");");
                }
                String defaultCase = baseAttributeHandler != null ? baseAttributeHandler + ".accept(def, key, val)" : "false";
                if (attributeMembers.size() == 1) {
                    Map.Entry<String, String> entry = cases.entrySet().iterator().next();
                    attributes = " (def, key, val) -> {\n" + "    if (\"" + entry.getKey() + "\".equals(key)) {\n" + "        " + entry.getValue() + "\n" + "        return true;\n"
                                 + "    }\n" + "    return " + defaultCase + ";\n" + "}";
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(" (def, key, val) -> {\n" + "    switch (key) {\n");
                    for (Map.Entry<String, String> entry : cases.entrySet()) {
                        sb.append("        case \"").append(entry.getKey()).append("\": ").append(entry.getValue()).append(" break;\n");
                    }
                    sb.append("        default: return ").append(defaultCase).append(";\n" + "    }\n" + "    return true;\n" + "}");
                    attributes = sb.toString();
                }
            }

            // @XmlAnyAttribute
            members.stream().filter(member -> ((AccessibleObject)member).getAnnotation(XmlAnyAttribute.class) != null).forEach(member -> {
                if (!"otherAttributes".equals(member.getName())) {
                    throw new UnsupportedOperationException("Class " + clazz.getName() + " / member " + member + ": unsupported @XmlAnyAttribute");
                }
            });

            // @XmlElementRef @XmlElement @XmlElements
            List<Member> elementMembers = members.stream().filter(member -> ((AccessibleObject)member).getAnnotation(XmlAttribute.class) == null)
                .filter(member -> ((AccessibleObject)member).getAnnotation(XmlAnyAttribute.class) == null)
                .filter(member -> ((AccessibleObject)member).getAnnotation(XmlValue.class) == null).collect(Collectors.toList());
            List<Member> multiElements = members.stream()
                .filter(member -> ((AccessibleObject)member).getAnnotation(XmlElementRef.class) != null || ((AccessibleObject)member).getAnnotation(XmlElements.class) != null
                                  || (clazz == outputDefinitionClass && "setOutputs".equals(member.getName())))
                .collect(Collectors.toList());
            Map<String, String> expressionHandlersDefs = new LinkedHashMap<>();
            Map<String, String> cases = new LinkedHashMap<>();
            // XmlElementRef
            elementMembers.stream().filter(member -> ((AccessibleObject)member).getAnnotation(XmlElementRef.class) != null
                                                     || (clazz == outputDefinitionClass && "setOutputs".equals(member.getName())))
                .forEach(member -> {
                    Type pt = member instanceof Method ? ((Method)member).getGenericParameterTypes()[0] : ((Field)member).getGenericType();
                    GenericType type = new GenericType(pt);
                    boolean list = type.getRawClass() == List.class;
                    String fn = member.getName();
                    String sn = member instanceof Method ? fn : "set" + uppercase(fn);
                    String gn = member instanceof Method ? "g" + sn.substring(1) : "get" + uppercase(fn);
                    Class<?> root = list ? type.getActualTypeArgument(0).getRawClass() : type.getRawClass();
                    if (elementRefs.contains(root)) {
                        expressionHandlersDefs.put(lowercase(sn.substring(3)),
                                                   "    " + root.getSimpleName() + " v = doParse" + root.getSimpleName() + "Ref(key);\n" + "    if (v != null) { \n" + "        "
                                                                               + (list ? "doAdd(v, def." + gn + "(), def::" + sn + ");" : "def." + sn + "(v);") + "\n"
                                                                               + "        return true;\n" + "    }\n");
                    } else {
                        model.stream().filter(root::isAssignableFrom).filter(cl -> cl.getAnnotation(XmlRootElement.class) != null).forEach(cl -> {
                            String en = cl.getAnnotation(XmlRootElement.class).name();
                            if ("##default".equals(en)) {
                                en = lowercase(cl.getSimpleName());
                            }
                            String tn = cl.getSimpleName();
                            cases.put(en, list ? "doAdd(doParse" + tn + "(), def." + gn + "(), def::" + sn + ");" : "def." + sn + "(doParse" + tn + "());");
                        });
                    }
                });
            // @XmlElements
            elementMembers.stream().filter(member -> ((AccessibleObject)member).getAnnotation(XmlElements.class) != null).forEach(member -> {
                Type pt = member instanceof Method ? ((Method)member).getGenericParameterTypes()[0] : ((Field)member).getGenericType();
                GenericType type = new GenericType(pt);
                boolean list = type.getRawClass() == List.class;
                String fn = member.getName();
                String sn = member instanceof Method ? fn : "set" + uppercase(fn);
                String gn = member instanceof Method ? "g" + sn.substring(1) : "get" + uppercase(fn);
                Class<?> root = list ? type.getActualTypeArgument(0).getRawClass() : type.getRawClass();
                if (elementRefs.contains(root)) {
                    expressionHandlersDefs.put(lowercase(sn.substring(3)),
                                               "    " + root.getSimpleName() + " v = doParse" + root.getSimpleName() + "Ref(key);\n" + "    if (v != null) { \n" + "        "
                                                                           + (list ? "doAdd(v, def." + gn + "(), def::" + sn + ");" : "def." + sn + "(v);") + "\n"
                                                                           + "        return true;\n" + "    }\n");
                } else {
                    Stream.of(((AccessibleObject)member).getAnnotation(XmlElements.class).value()).forEach(xe -> {
                        String en = xe.name();
                        String tn = xe.type().getSimpleName();
                        cases.put(en, list ? "doAdd(doParse" + tn + "(), def." + gn + "(), def::" + sn + ");" : "def." + sn + "(doParse" + tn + "());");
                    });
                }
            });
            elementMembers.stream().filter(member -> !multiElements.contains(member)).forEach(member -> {
                Type pt = member instanceof Method ? ((Method)member).getGenericParameterTypes()[0] : ((Field)member).getGenericType();
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
                if (((AccessibleObject)member).getAnnotation(XmlElement.class) != null) {
                    en = ((AccessibleObject)member).getAnnotation(XmlElement.class).name();
                }
                if ("##default".equals(en)) {
                    en = member instanceof Method ? propname(fn) : fn;
                }
                String sn = member instanceof Method ? fn : "set" + uppercase(fn);
                String gn = member instanceof Method ? "g" + sn.substring(1) : "get" + uppercase(fn);
                String tn = root.getSimpleName();
                String pc;
                if (((AccessibleObject)member).getAnnotation(XmlJavaTypeAdapter.class) != null) {
                    Class<? extends XmlAdapter> adapter = ((AccessibleObject)member).getAnnotation(XmlJavaTypeAdapter.class).value();
                    Class<?> cl = adapter;
                    while (cl.getSuperclass() != XmlAdapter.class) {
                        cl = cl.getSuperclass();
                    }
                    Type t = ((ParameterizedType)cl.getGenericSuperclass()).getActualTypeArguments()[0];
                    Class<?> c = new GenericType(t).getRawClass();
                    String n = adapter.getDeclaringClass() != null ? adapter.getDeclaringClass().getSimpleName() + "." + adapter.getSimpleName() : adapter.getSimpleName();
                    if (c == String.class) {
                        pc = "unmarshal(new " + n + "(), doParseText())";
                    } else if (model.contains(c)) {
                        pc = "unmarshal(new " + n + "(), doParse" + c.getSimpleName() + "())";
                    } else {
                        throw new UnsupportedOperationException("Class " + clazz.getName() + " / member " + member + ": unsupported @XmlJavaTypeAdapter");
                    }
                    if (list && type.equals(new GenericType(((ParameterizedType)cl.getGenericSuperclass()).getActualTypeArguments()[1]))) {
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
                cases.put(en, list ? "doAdd(" + pc + ", def." + gn + "(), def::" + sn + ");" : "def." + sn + "(" + pc + ");");
            });
            String expressionHandler = null;
            for (Class<?> parent = clazz.getSuperclass(); parent != Object.class; parent = parent.getSuperclass()) {
                if (getMembers(parent).stream()
                    .anyMatch(member -> ((AccessibleObject)member).getAnnotation(XmlAttribute.class) == null
                                        && ((AccessibleObject)member).getAnnotation(XmlAnyAttribute.class) == null
                                        && ((AccessibleObject)member).getAnnotation(XmlValue.class) == null)) {
                    expressionHandler = lowercase(parent.getSimpleName()) + "ElementHandler()";
                    break;
                }
            }
            if (expressionHandlersDefs.size() > 1) {
                throw new IllegalStateException();
            }
            String elements;
            if (cases.isEmpty()) {
                if (expressionHandlersDefs.isEmpty()) {
                    elements = (expressionHandler == null ? " noElementHandler()" : " " + expressionHandler);
                } else {
                    elements = " (def, key) -> {\n" + expressionHandlersDefs.values().iterator().next() + "    return "
                               + (expressionHandler == null ? "false" : expressionHandler + ".accept(def, key)") + ";\n" + "}";
                }
            } else {
                String returnClause = (expressionHandlersDefs.isEmpty() ? "" : expressionHandlersDefs.values().iterator().next() + "    ")
                                      + (expressionHandler == null ? "return false;" : "return " + expressionHandler + ".accept(def, key);");
                if (cases.size() == 1) {
                    Map.Entry<String, String> entry = cases.entrySet().iterator().next();
                    elements = " (def, key) -> {\n" + "    if (\"" + entry.getKey() + "\".equals(key)) {\n" + "        " + entry.getValue() + "\n" + "        return true;\n"
                               + "    }\n" + "    " + returnClause + "\n" + "}";
                } else {
                    StringBuilder sb = new StringBuilder();
                    sb.append(" (def, key) -> {\n" + "    switch (key) {\n");
                    for (Map.Entry<String, String> entry : cases.entrySet()) {
                        sb.append("        case \"").append(entry.getKey()).append("\": ").append(entry.getValue()).append(" break;\n");
                    }
                    sb.append("        default: ");
                    if (expressionHandlersDefs.isEmpty()) {
                        sb.append(returnClause);
                    } else {
                        Stream.of(returnClause.split("\n")).forEach(s -> sb.append("\n        ").append(s));
                    }
                    sb.append("\n");
                    sb.append("    }\n" + "    return true;\n" + "}");
                    elements = sb.toString();
                }
            }

            // @XmlValue
            String value = members.stream().filter(member -> ((AccessibleObject)member).getAnnotation(XmlValue.class) != null).findFirst().map(member -> {
                String fn = member.getName();
                String sn = member instanceof Method ? fn : "set" + uppercase(fn);
                if (expressionDefinitionClass.isAssignableFrom(member.getDeclaringClass())) {
                    return " expressionDefinitionValueHandler()";
                } else {
                    return " (def, val) -> def." + sn + "(val)";
                }
            }).orElseGet(() -> {
                for (Class<?> parent = clazz.getSuperclass(); parent != Object.class; parent = parent.getSuperclass()) {
                    if (getMembers(parent).stream().anyMatch(member -> ((AccessibleObject)member).getAnnotation(XmlValue.class) != null)) {
                        return " " + lowercase(parent.getSimpleName()) + "ValueHandler()";
                    }
                }
                return " noValueHandler()";
            });
            if (clazz == routesDefinitionClass || clazz == restsDefinitionClass) {
                String element = clazz.getAnnotation(XmlRootElement.class).name();
                parser.addMethod().setPublic().setReturnType(clazz).setName("parse" + name).addThrows(IOException.class).addThrows(XML_PULL_PARSER_EXCEPTION)
                    .setBody("expectTag(\"" + element + "\");\nreturn doParse" + name + "();");
            }
            if (hasDerived) {
                if (!attributeMembers.isEmpty()) {
                    parser.addMethod().setSignature("protected <T extends " + qname + "> AttributeHandler<T> " + lowercase(name) + "AttributeHandler()")
                        .setBody("return" + attributes + ";");
                }
                if (!elementMembers.isEmpty()) {
                    parser.addMethod().setSignature("protected <T extends " + qname + "> ElementHandler<T> " + lowercase(name) + "ElementHandler()")
                        .setBody("return" + elements + ";");
                }
                if (!Modifier.isAbstract(clazz.getModifiers())) {
                    parser.addMethod().setSignature("protected " + qname + " doParse" + name + "() throws IOException, XmlPullParserException")
                        .setBody("return doParse(new " + qname + "(), " + (attributeMembers.isEmpty() ? attributes : lowercase(name) + "AttributeHandler()") + ", "
                                 + (elementMembers.isEmpty() ? elements : lowercase(name) + "ElementHandler()") + "," + value + ");\n");
                }
            } else {
                parser.addMethod().setSignature("protected " + qname + " doParse" + name + "() throws IOException, XmlPullParserException")
                    .setBody("return doParse(new " + qname + "()," + attributes + "," + elements + "," + value + ");\n");
            }
        }

        for (Class<?> root : elementRefs) {
            parser.addMethod().setSignature("protected " + root.getSimpleName() + " doParse" + root.getSimpleName() + "Ref(String key) throws IOException, XmlPullParserException")
                .setBody("switch (key) {\n" + model.stream().filter(root::isAssignableFrom).filter(cl -> cl.getAnnotation(XmlRootElement.class) != null).map(cl -> {
                    String en = cl.getAnnotation(XmlRootElement.class).name();
                    if ("##default".equals(en)) {
                        en = lowercase(cl.getSimpleName());
                    }
                    String tn = cl.getSimpleName();
                    return "    case \"" + en + "\": return doParse" + tn + "();\n";
                }).collect(Collectors.joining()) + "    default: return null;\n" + "}");
        }

        return parser;
    }
    // CHECKSTYLE:ON

    private String conversion(JavaClass parser, GenericType type, String val) {
        Class<?> rawClass = type.getRawClass();
        if (rawClass == String.class) {
            return val;
        } else if (rawClass.isEnum() || rawClass == Integer.class || rawClass == Long.class || rawClass == Boolean.class || rawClass == Float.class) {
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
        List<Member> members = Stream.concat(findFieldsForClass(clazz), findMethodsForClass(clazz)).filter(m -> ((AnnotatedElement)m).getAnnotation(XmlTransient.class) == null)
            .sorted(Comparator.comparing(member -> member instanceof Method ? propname(member.getName()) : member.getName())).collect(Collectors.toList());
        if (clazz != outputDefinitionClass && outputDefinitionClass.isAssignableFrom(clazz)) {
            members.removeIf(m -> "setOutputs".equals(m.getName()));
        }
        return members;
    }

    private Stream<? extends Member> findMethodsForClass(Class<?> c) {
        XmlAccessType accessType;
        if (c.getAnnotation(XmlAccessorType.class) != null && c != outputDefinitionClass) {
            accessType = c.getAnnotation(XmlAccessorType.class).value();
        } else {
            accessType = XmlAccessType.PUBLIC_MEMBER;
        }
        if (accessType == XmlAccessType.FIELD || accessType == XmlAccessType.NONE) {
            return Stream.of(c.getDeclaredMethods()).filter(m -> m.getName().startsWith("set") && m.getParameterCount() == 1)
                .filter(m -> m.getAnnotation(XmlAttribute.class) != null || m.getAnnotation(XmlElement.class) != null || m.getAnnotation(XmlElementRef.class) != null
                             || m.getAnnotation(XmlValue.class) != null)
                .sorted(Comparator.comparing(Method::getName));
        } else {
            return Stream.of(c.getDeclaredMethods()).filter(m -> Modifier.isPublic(m.getModifiers()) || accessType == XmlAccessType.PROPERTY)
                .filter(m -> m.getName().startsWith("set") && m.getParameterCount() == 1).filter(m -> m.getAnnotation(XmlTransient.class) == null)
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
            return Stream
                .of(c.getDeclaredFields()).filter(f -> f.getAnnotation(XmlAttribute.class) != null || f.getAnnotation(XmlElement.class) != null
                                                       || f.getAnnotation(XmlElementRef.class) != null || f.getAnnotation(XmlValue.class) != null)
                .sorted(Comparator.comparing(Field::getName));
        } else {
            return Stream.of(c.getDeclaredFields()).filter(f -> !Modifier.isTransient(f.getModifiers()) && !Modifier.isStatic(f.getModifiers()))
                .filter(f -> Modifier.isPublic(f.getModifiers()) || accessType == XmlAccessType.FIELD).filter(f -> f.getAnnotation(XmlTransient.class) == null)
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

}
