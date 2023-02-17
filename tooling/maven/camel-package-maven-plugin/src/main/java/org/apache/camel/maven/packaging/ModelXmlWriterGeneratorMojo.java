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
import java.io.Writer;
import java.lang.annotation.Annotation;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAnyAttribute;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementRef;
import jakarta.xml.bind.annotation.XmlElementRefs;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;
import jakarta.xml.bind.annotation.XmlValue;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import org.apache.camel.maven.packaging.generics.JandexStore;
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
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.sonatype.plexus.build.incremental.BuildContext;

/**
 * Generate Model lightweight XML Writer source code.
 */
@Mojo(name = "generate-xml-writer", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
      defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class ModelXmlWriterGeneratorMojo extends AbstractGeneratorMojo {

    public static final String XML_SERIALIZER_PACKAGE = "org.apache.camel.xml.io";
    public static final String WRITER_PACKAGE = "org.apache.camel.xml.out";
    public static final String MODEL_PACKAGE = "org.apache.camel.model";

    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File sourcesOutputDir;

    @Parameter(defaultValue = "${camel-generate-xml-writer}")
    protected boolean generateXmlWriter;

    @Override
    public void execute(MavenProject project, MavenProjectHelper projectHelper, BuildContext buildContext)
            throws MojoFailureException, MojoExecutionException {
        sourcesOutputDir = new File(project.getBasedir(), "src/generated/java");
        generateXmlWriter = Boolean.parseBoolean(project.getProperties().getProperty("camel-generate-xml-writer", "false"));
        super.execute(project, projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (!generateXmlWriter) {
            return;
        }
        Path javaDir = sourcesOutputDir.toPath();
        String parser = generateWriter();
        updateResource(javaDir, (WRITER_PACKAGE + ".ModelWriter").replace('.', '/') + ".java", parser);
    }

    public String generateWriter() throws MojoExecutionException {
        ClassLoader classLoader;
        try {
            classLoader = DynamicClassLoader.createDynamicClassLoader(project.getCompileClasspathElements());
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("DependencyResolutionRequiredException: " + e.getMessage(), e);
        }

        Class<?> routesDefinitionClass = loadClass(classLoader, MODEL_PACKAGE + ".RoutesDefinition");
        String resName = routesDefinitionClass.getName().replace('.', '/') + ".class";
        String url = classLoader.getResource(resName).toExternalForm().replace(resName, JandexStore.DEFAULT_NAME);
        Index index;
        try (InputStream is = new URL(url).openStream()) {
            index = new IndexReader(is).read();
        } catch (IOException e) {
            throw new MojoExecutionException("IOException: " + e.getMessage(), e);
        }
        List<String> names = Stream.of(XmlRootElement.class, XmlEnum.class, XmlType.class)
                .map(c -> index.getAnnotations(DotName.createSimple(c.getName())))
                .flatMap(Collection::stream)
                .map(ai -> ai.target().asClass().name().toString())
                .sorted().distinct()
                .toList();
        List<Class<?>> model = names
                .stream()
                // we should skip this model as we do not want this in the JAXB model
                .filter(n -> !n.equals("org.apache.camel.model.WhenSkipSendToEndpointDefinition"))
                .map(name -> loadClass(classLoader, name))
                .flatMap(this::references).flatMap(this::fieldReferences).distinct()
                .sorted(Comparator.comparing(Class::getName))
                .toList();

        JavaClass writer = generateWriter(model, classLoader);
        return "/*\n" + " * Licensed to the Apache Software Foundation (ASF) under one or more\n"
               + " * contributor license agreements.  See the NOTICE file distributed with\n"
               + " * this work for additional information regarding copyright ownership.\n"
               + " * The ASF licenses this file to You under the Apache License, Version 2.0\n"
               + " * (the \"License\"); you may not use this file except in compliance with\n"
               + " * the License.  You may obtain a copy of the License at\n" + " *\n"
               + " *      http://www.apache.org/licenses/LICENSE-2.0\n" + " *\n"
               + " * Unless required by applicable law or agreed to in writing, software\n"
               + " * distributed under the License is distributed on an \"AS IS\" BASIS,\n"
               + " * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n"
               + " * See the License for the specific language governing permissions and\n"
               + " * limitations under the License.\n" + " */\n" + "\n" + "//CHECKSTYLE:OFF\n" + "\n"
               + "\n/**\n * Generated by Camel build tools - do NOT edit this file!\n */\n"
               + writer.printClass() + "\n" + "//CHECKSTYLE:ON\n";
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
        return Stream.concat(Stream.of(clazz),
                getProperties(clazz).map(f -> {
                    if (f.getAnnotation(XmlJavaTypeAdapter.class) != null) {
                        Class<?> cl = f.getAnnotation(XmlJavaTypeAdapter.class).value();
                        while (cl.getSuperclass() != XmlAdapter.class) {
                            cl = cl.getSuperclass();
                        }
                        return ((ParameterizedType) cl.getGenericSuperclass()).getActualTypeArguments()[0];
                    } else {
                        return f.getType();
                    }
                })
                        .map(t -> {
                            GenericType gt = new GenericType(t);
                            GenericType ac = gt.getRawClass() == List.class ? gt.getActualTypeArgument(0) : gt;
                            return ac.getRawClass();
                        })
                        .filter(c -> c.getName().startsWith("org.apache.camel.")));
    }

    // CHECKSTYLE:OFF
    private JavaClass generateWriter(List<Class<?>> model, ClassLoader classLoader) {
        JavaClass writer = new JavaClass(classLoader);
        writer.setMaxImportPerPackage(4);
        writer.setPackage(WRITER_PACKAGE);
        writer.setName("ModelWriter");
        writer.extendSuperType("BaseWriter");
        writer.addImport(MODEL_PACKAGE + ".OptionalIdentifiedDefinition");
        writer.addImport(IOException.class);
        writer.addImport(Array.class);
        writer.addImport(List.class);
        writer.addImport(ArrayList.class);
        writer.addAnnotation(SuppressWarnings.class).setLiteralValue("\"all\"");

        writer.addMethod()
                .setConstructor(true)
                .setPublic()
                .setName("ModelWriter")
                .addParameter(Writer.class, "writer")
                .addParameter(String.class, "namespace")
                .addThrows(IOException.class)
                .setBody(
                        "super(writer, namespace);");
        writer.addMethod()
                .setConstructor(true)
                .setPublic()
                .setName("ModelWriter")
                .addParameter(Writer.class, "writer")
                .addThrows(IOException.class)
                .setBody("super(writer, null);");

        List<Class<?>> rootElements = model.stream().filter(this::isRootElement).toList();

        for (Class<?> clazz : rootElements) {
            String element = clazz.getAnnotation(XmlRootElement.class).name();
            String name = clazz.getSimpleName();
            writer.addMethod().setPublic()
                    .addParameter(clazz, "def")
                    .setReturnType(Void.TYPE)
                    .setName("write" + name)
                    .addThrows(IOException.class)
                    .setBody(
                            "doWrite" + name + "(\"" + element + "\", def);"
                    );

        }

        Set<Class<?>> elementRefs = new TreeSet<>(Comparator.comparing(Class::getName));

        // Special case for OptionalIdentifiedDefinition
        model.stream().filter(cl -> "OptionalIdentifiedDefinition".equals(cl.getSimpleName()))
                .forEach(elementRefs::add);
        writer.addMethod()
                .setSignature("public void writeOptionalIdentifiedDefinitionRef(OptionalIdentifiedDefinition def) throws IOException")
                .setBody("doWriteOptionalIdentifiedDefinitionRef(null, def);");

        for (Class<?> clazz : model) {
            if (clazz.getAnnotation(XmlEnum.class) != null || clazz.isInterface()) {
                continue;
            }
            String name = clazz.getSimpleName();
            String qname;
            if (clazz.getDeclaringClass() != null) {
                writer.addImport(clazz.getDeclaringClass());
                qname = clazz.getDeclaringClass().getSimpleName() + "." + name;
            } else {
                writer.addImport(clazz);
                qname = name;
            }
            boolean hasDerived = model.stream().anyMatch(cl -> cl.getSuperclass() == clazz);

            List<Property> members = getProperties(clazz).toList();

            // XmlAttribute
            List<String> attributes = new ArrayList<>();
            // call super class attributes writer
            getClassAndSuper(clazz.getSuperclass())
                    .filter(c -> getProperties(c).anyMatch(Property::isAttribute))
                    .findFirst()
                    .ifPresent(cl -> attributes.add("doWrite" + cl.getSimpleName() + "Attributes(def);"));
            // Add attributes
            List<Property> attributeMembers = members.stream().filter(Property::isAttribute).toList();
            attributeMembers.forEach(member -> {
                Type pt = member.getType();
                GenericType type = new GenericType(pt);
                String an = member.getAnnotation(XmlAttribute.class).name();
                if ("##default".equals(an)) {
                    an = member.getName();
                }
                String gn = member.getGetter();
                attributes.add("doWriteAttribute(\"" + an + "\", " + conversion(writer, type, "def." + gn + "()", clazz.getName()) + ");");
            });

            // @XmlAnyAttribute
            members.stream().filter(Property::isAnyAttribute).forEach(member -> {
                throw new UnsupportedOperationException("Class " + clazz.getName() + " / member " + member + ": unsupported @XmlAnyAttribute");
            });

            List<String> elements = new ArrayList<>();
            // Add super class element writer
            getClassAndSuper(clazz.getSuperclass())
                    .filter(c -> getProperties(c).anyMatch(Property::isElement))
                    .findFirst()
                    .ifPresent(cl -> elements.add("doWrite" + cl.getSimpleName() + "Elements(def);"));
            // Loop through elements
            List<Property> elementMembers = members.stream().filter(Property::isElement).toList();
            elementMembers.forEach(member -> {
                Type pt = member.getType();
                GenericType type = new GenericType(pt);
                boolean list = type.getRawClass() == List.class;
                String gn = member.getGetter();
                String pn = member.getName();
                Class<?> root = list ? type.getActualTypeArgument(0).getRawClass() : type.getRawClass();
                if (member.getAnnotation(XmlElementRefs.class) != null) {
                    elements.add("// TODO: @XmlElementRefs: " + member);
                } else if (member.getAnnotation(XmlElementRef.class) != null) {
                    //elements.add("// @XmlElementRef: " + member);
                    if (list) {
                        Class<?> parent = new GenericType(member.getType()).getActualTypeArgument(0).getRawClass();
                        elementRefs.add(parent);
                        elements.add("doWriteList(null, null, def." + gn + "(), this::doWrite" + parent.getSimpleName() + "Ref);");
                    } else {
                        Class<?> parent = new GenericType(member.getType()).getRawClass();
                        elementRefs.add(parent);
                        elements.add("doWriteElement(null, def." + gn + "(), this::doWrite" + parent.getSimpleName() + "Ref);");
                    }
                } else if (member.getAnnotation(XmlElements.class) != null) {
                    if (list) {
                        // elements.add("// @XmlElements: " + member);
                        elements.add("doWriteList(null, null, def." + gn + "(), (n, v) -> {");
                        elements.add("    switch (v.getClass().getSimpleName()) {");
                        for (XmlElement elem : member.getAnnotation(XmlElements.class).value()) {
                            String t = elem.type().getSimpleName();
                            String n = elem.name();
                            elements.add("        case \"" + t + "\" -> doWrite" + t + "(\"" + n + "\", (" + t + ") v);");
                        }
                        elements.add("    }");
                        elements.add("});");
                    } else {
                        // elements.add("// @XmlElements: " + member);
                        elements.add("doWriteElement(null, def." + gn + "(), (n, v) -> {");
                        elements.add("    switch (v.getClass().getSimpleName()) {");
                        for (XmlElement elem : member.getAnnotation(XmlElements.class).value()) {
                            String t = elem.type().getSimpleName();
                            String n = elem.name();
                            elements.add("        case \"" + t + "\" -> doWrite" + t + "(\"" + n + "\", (" + t + ") def." + gn + "());");
                        }
                        elements.add("    }");
                        elements.add("});");
                    }
                } else if (member.getAnnotation(XmlElement.class) != null) {
                    String t = root.getSimpleName();
                    String n = member.getAnnotation(XmlElement.class).name();
                    if ("##default".equals(n)) {
                        n = pn;
                    }
                    // elements.add("// @XmlElement: " + member);
                    if (list) {
                        String w = member.getAnnotation(XmlElementWrapper.class) != null
                                ? member.getAnnotation(XmlElementWrapper.class).name() : null;
                        elements.add("doWriteList(" + (w != null ? "\"" + w + "\"" : "null") + ", " +
                                "\"" + n + "\", def." + gn + "(), this::doWrite" + t + ");");
                    } else {
                        elements.add("doWriteElement(" +
                                "\"" + n + "\", def." + gn + "(), this::doWrite" + t + ");");
                    }
                } else {
                    String t = root.getSimpleName();
                    String n = root.getAnnotation(XmlRootElement.class) != null
                            ? root.getAnnotation(XmlRootElement.class).name() : "##default";
                    if ("##default".equals(n)) {
                        // TODO: handle default name
                    }
                    // elements.add("// " + member);
                    if (list) {
                        elements.add("doWriteList(\"" + pn + "\", " +
                                "\"" + n + "\", def." + gn + "(), this::doWrite" + t + ");");
                    } else {
                        elements.add("doWriteElement(\"" + pn + "\", def." + gn + "(), this::doWrite" + t + ");");
                    }
                }
            });

            // @XmlValue
            List<String> value =
                    getClassAndSuper(clazz).flatMap(this::getProperties)
                            .filter(Property::isValue).findFirst()
                    .map(member -> "doWriteValue(def." + member.getGetter() + "());")
                    .stream().toList();

            String qgname = qname;
            if (clazz.getTypeParameters().length > 0) {
                qgname = qname + "<" + Stream.of(clazz.getTypeParameters()).map(t -> "?")
                        .collect(Collectors.joining(", ")) + ">";
            }
            if (!attributeMembers.isEmpty() || !elementMembers.isEmpty() || isRootElement(clazz)
                    || isReferenced(clazz, model)) {
                List<String> statements = new ArrayList<>();
                statements.add("startElement(name);");
                // Attributes
                if (hasDerived && !attributes.isEmpty()) {
                    writer.addMethod()
                            .setProtected()
                            .setReturnType(Void.TYPE)
                            .setName("doWrite" + name + "Attributes")
                            .addParameter(qgname, "def")
                            .addThrows(IOException.class)
                            .setBody(String.join("\n", attributes));
                    statements.add("doWrite" + name + "Attributes(def);");
                } else {
                    statements.addAll(attributes);
                }
                // Value
                statements.addAll(value);
                // Elements
                if (hasDerived && !elements.isEmpty()) {
                    writer.addMethod()
                            .setProtected()
                            .setReturnType(Void.TYPE)
                            .setName("doWrite" + name + "Elements")
                            .addParameter(qgname, "def")
                            .addThrows(IOException.class)
                            .setBody(String.join("\n", elements));
                    statements.add("doWrite" + name + "Elements(def);");
                } else {
                    statements.addAll(elements);
                }
                statements.add("endElement();");
                writer.addMethod()
                        .setProtected()
                        .setReturnType(Void.TYPE)
                        .setName("doWrite" + name)
                        .addParameter(String.class, "name")
                        .addParameter(qgname, "def")
                        .addThrows(IOException.class)
                        .setBody(String.join("\n", statements));
            }
        }

        elementRefs.forEach(clazz -> {
            List<String> elements = new ArrayList<>();
            elements.add("if (v != null) {");
            elements.add("    switch (v.getClass().getSimpleName()) {");
            model.stream()
                    .filter(c -> c.getAnnotation(XmlRootElement.class) != null)
                    .filter(c -> getClassAndSuper(c).anyMatch(cl -> cl == clazz))
                    .forEach(cl -> {
                        String t = cl.getSimpleName();
                        String n = cl.getAnnotation(XmlRootElement.class).name();
                        if ("##default".equals(n)) {
                            n = lowercase(t);
                        }
                        elements.add("        case \"" + t + "\" -> doWrite" + t + "(\"" + n + "\", (" + t + ") v);");
                    });
            elements.add("    }");
            elements.add("}");
            String qname = clazz.getSimpleName();
            if (clazz.getTypeParameters().length > 0) {
                qname = qname + "<" + Stream.of(clazz.getTypeParameters()).map(t -> "?")
                        .collect(Collectors.joining(", ")) + ">";
            }
            writer.addMethod()
                    .setProtected()
                    .setReturnType(Void.TYPE)
                    .setName("doWrite" + clazz.getSimpleName() + "Ref")
                    .addParameter("String", "n")
                    .addParameter(qname, "v")
                    .addThrows(IOException.class)
                    .setBody(String.join("\n", elements));
        });

        writer.addMethod()
                .setProtected()
                .setReturnType(Void.TYPE)
                .setName("doWriteAttribute")
                .addParameter(String.class, "attribute")
                .addParameter(String.class, "value")
                .addThrows(IOException.class)
                .setBody("if (value != null) {",
                        "    attribute(attribute, value);",
                        "}");
        writer.addMethod()
                .setProtected()
                .setReturnType(Void.TYPE)
                .setName("doWriteValue")
                .addParameter(String.class, "value")
                .addThrows(IOException.class)
                .setBody("if (value != null) {",
                        "    text(value);",
                        "}");
        writer.addMethod()
                .setSignature("protected <T> void doWriteList(String wrapperName, String name, List<T> list, ElementSerializer<T> elementSerializer) throws IOException")
                .setBody("""
                            if (list != null) {
                                if (wrapperName != null) {
                                    startElement(wrapperName);
                                }
                                for (T v : list) {
                                    elementSerializer.doWriteElement(name, v);
                                }
                                if (wrapperName != null) {
                                    endElement();
                                }
                            }""");
        writer.addMethod()
                .setSignature("protected <T> void doWriteElement(String name, T v, ElementSerializer<T> elementSerializer) throws IOException")
                .setBody("""
                            if (v != null) {
                                elementSerializer.doWriteElement(name, v);
                            }""");
        writer.addNestedType()
                .setClass(false)
                .setAbstract(true)
                .setName("ElementSerializer<T>")
                .addMethod()
                        .setAbstract()
                        .setSignature("void doWriteElement(String name, T value) throws IOException");

        writer.addMethod()
                .setProtected()
                .setReturnType(String.class)
                .setName("toString")
                .addParameter("Boolean", "b")
                .setBody("return b != null ? b.toString() : null;");
        writer.addMethod()
                .setProtected()
                .setReturnType(String.class)
                .setName("toString")
                .addParameter("Enum<?>", "e")
                .setBody("return e != null ? e.name() : null;");
        writer.addMethod()
                .setProtected()
                .setReturnType(String.class)
                .setName("toString")
                .addParameter("Number", "n")
                .setBody("return n != null ? n.toString() : null;");
        writer.addImport("java.util.Base64");
        writer.addMethod()
                .setProtected()
                .setReturnType(String.class)
                .setName("toString")
                .addParameter("byte[]", "b")
                .setBody("return b != null ? Base64.getEncoder().encodeToString(b) : null;");

        writer.addMethod()
                .setProtected()
                .setReturnType(Void.TYPE)
                .setName("doWriteString")
                .addParameter(String.class, "name")
                .addParameter(String.class, "value")
                .addThrows(IOException.class)
                .setBody("if (value != null) {",
                        "    startElement(name);",
                        "    text(value);",
                        "    endElement();",
                        "}");

        return writer;
    }

    private boolean isReferenced(Class<?> clazz, List<Class<?>> model) {
        return model.stream()
                .flatMap(this::getProperties)
                .anyMatch(p -> {
                    GenericType t = new GenericType(p.getType());
                    Class<?> cl = t.getRawClass() == List.class ? t.getActualTypeArgument(0).getRawClass() : t.getRawClass();
                    return cl == clazz;
                });
    }

    private String conversion(JavaClass writer, GenericType type, String val, String clazzName) {
        Class<?> rawClass = type.getRawClass();
        if (rawClass == String.class) {
            return val;
        } else if (rawClass.isEnum()
                || rawClass == Integer.class || rawClass == Long.class || rawClass == Boolean.class
                || rawClass == Float.class) {
            writer.addImport(rawClass);
            return "toString(" + val + ")";
        } else if (rawClass == byte[].class) {
            return "toString(" + val + ")";
        } else {
            throw new UnsupportedOperationException("Unsupported type " + rawClass.getSimpleName() + " in class " + clazzName);
        }
    }

    class Property {
        private final Member field;
        private final Member getter;
        private final Member setter;
        private final String name;
        private final Type type;

        public Property(Member field, Member getter, Member setter, String name, Type type) {
            this.field = field;
            this.getter = getter;
            this.setter = setter;
            this.name = name;
            this.type = type;
        }

        @Override
        public String toString() {
            return "Property{" +
                    "name='" + name + '\'' +
                    ", type=" + type +
                    ", field=" + field +
                    ", getter=" + getter +
                    ", setter=" + setter +
                    '}';
        }

        private Stream<Member> members() {
            return Stream.of(field, getter, setter).filter(Objects::nonNull);
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return type;
        }

        public String getGetter() {
            return Optional.ofNullable(getter)
                    .orElseThrow(() -> new IllegalArgumentException("No getter for property defined by " + members().toList()))
                    .getName();
        }

        public String getSetter() {
            return Optional.ofNullable(setter)
                    .orElseThrow(() -> new IllegalArgumentException("No setter for property defined by " + members().toList()))
                    .getName();
        }

        @SuppressWarnings("unchecked")
        public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
            return (T) annotations().filter(annotationClass::isInstance).findFirst().orElse(null);
        }

        public <T extends Annotation> boolean hasAnnotation(Class<T> annotationClass) {
            return getAnnotation(annotationClass) != null;
        }

        private Stream<? extends Annotation> annotations() {
            return members().flatMap(m -> Stream.of(((AnnotatedElement) m).getAnnotations()));

        }

        public boolean isAttribute() {
            return hasAnnotation(XmlAttribute.class);
        }

        public boolean isAnyAttribute() {
            return hasAnnotation(XmlAnyAttribute.class);
        }

        public boolean isValue() {
            return hasAnnotation(XmlValue.class);
        }

        public boolean isElement() {
            return !isAttribute() && !isAnyAttribute() && !isValue();
        }

        public boolean isElementRefs() {
            return hasAnnotation(XmlElementRefs.class);
        }

        public boolean isElementRef() {
            return hasAnnotation(XmlElementRef.class);
            // || member.getDeclaringClass() == outputDefinitionClass && "setOutputs".equals(member.getName());
        }
    }

    private static Type type(Member member) {
        return member instanceof Method
                ? member.getName().startsWith("set")
                        ? ((Method) member).getGenericParameterTypes()[0]
                        : ((Method) member).getGenericReturnType()
                : ((Field) member).getGenericType();
    }

    private Map<Class<?>, List<Property>> properties = new ConcurrentHashMap<>();
    private Stream<Property> getProperties(Class<?> clazz) {
        return properties.computeIfAbsent(clazz, cl -> doGetProperties(cl)).stream();
    }
    private List<Property> doGetProperties(Class<?> clazz) {
        List<Member> allMembers = getClassAndSuper(clazz)
                .flatMap(cl -> getMembers(clazz).stream())
                .toList();
        Stream<Property> properties = allMembers.stream()
                .filter(accessible(clazz))
                .collect(Collectors.groupingBy(this::propname))
                .entrySet()
                .stream()
                .map(l -> {
                    List<Member> members = l.getValue();
                    if (!members.stream().allMatch(this::isNotTransient)) {
                        return null;
                    }
                    String name = l.getKey();
                    Type type = type(members.get(0));
                    Member field = members.stream()
                            .filter(this::isField)
                            .findFirst().or(() -> allMembers.stream()
                                    .filter(m -> isField(m)
                                            && Objects.equals(propname(m), name)
                                            && Objects.equals(type(m), type))
                                    .findFirst())
                            .orElse(null);
                    Member getter = members.stream()
                            .filter(this::isGetter)
                            .findFirst().or(() -> allMembers.stream()
                                    .filter(m -> isGetter(m)
                                            && Objects.equals(propname(m), name)
                                            && Objects.equals(type(m), type))
                                    .findFirst())
                            .orElse(null);
                    Member setter = members.stream()
                            .filter(this::isSetter)
                            .findFirst().or(() -> allMembers.stream()
                                    .filter(m -> isSetter(m)
                                            && Objects.equals(propname(m), name)
                                            && Objects.equals(type(m), type))
                                    .findFirst())
                            .orElse(null);
                    if (getter != null && setter != null) {
                        return new Property(field, getter, setter, name, type);
                    } else {
                        if (field != null && isXmlBindAnnotated(field)) {
                            getLog().warn("Unsupported annotated field: " + field);
                        }
                        if (getter != null && isXmlBindAnnotated(getter)) {
                            getLog().warn("Unsupported annotated getter: " + getter);
                        }
                        if (setter != null && isXmlBindAnnotated(setter)) {
                            getLog().warn("Unsupported annotated setter: " + setter);
                        }
                        return null;
                    }
                })
                .filter(Objects::nonNull);
        XmlType xmlType = clazz.getAnnotation(XmlType.class);
        if (xmlType != null) {
            String[] propOrder = xmlType.propOrder();
            if (propOrder != null && propOrder.length > 0) {
                properties = properties
                        .sorted(Comparator.comparing(p -> Arrays.binarySearch(propOrder, p.getName())));
            }
        }
        return properties.toList();
    }

    private Map<Class<?>, List<Member>> members = new ConcurrentHashMap<>();
    private List<Member> getMembers(Class<?> clazz) {
        return members.computeIfAbsent(clazz, cl -> Stream.<Member>concat(
                    Arrays.stream(cl.getDeclaredMethods())
                        .filter(m -> isSetter(m) || isGetter(m))
                        .filter(m -> !m.isSynthetic()),
                    Arrays.stream(cl.getDeclaredFields()))
                .toList());
    }

    private Predicate<Member> accessible(Class<?> clazz) {
        XmlAccessType accessType;
        if (clazz.getAnnotation(XmlAccessorType.class) != null) {
            accessType = clazz.getAnnotation(XmlAccessorType.class).value();
        } else {
            accessType = XmlAccessType.PUBLIC_MEMBER;
        }
        if (accessType == XmlAccessType.PROPERTY) {
            return m -> m.getDeclaringClass() == clazz
                    && isSetter(m) || isGetter(m) || (isField(m) && isXmlBindAnnotated(m));
        } else if (accessType == XmlAccessType.FIELD) {
            return m -> m.getDeclaringClass() == clazz
                    && ((isSetter(m) || isGetter(m)) && isXmlBindAnnotated(m)
                            || isField(m) && !Modifier.isStatic(m.getModifiers()) && !Modifier.isTransient(m.getModifiers()));
        } else if (accessType == XmlAccessType.PUBLIC_MEMBER) {
            return m -> m.getDeclaringClass() == clazz
                    && (Modifier.isPublic(m.getModifiers()) || isXmlBindAnnotated(m));
        } else /* if (accessType == XmlAccessType.NONE) */ {
            return m -> m.getDeclaringClass() == clazz
                    && isXmlBindAnnotated(m);
        }
    }

    private boolean isXmlBindAnnotated(Member m) {
        return Stream.of(((AnnotatedElement) m).getAnnotations())
                .anyMatch(a -> a.getClass().getAnnotatedInterfaces()[0].getType().getTypeName().startsWith("jakarta.xml.bind.annotation."));
    }

    private boolean isField(Member member) {
        return member instanceof Field;
    }

    private boolean isSetter(Member member) {
        return (member instanceof Method m)
                && !Modifier.isStatic(m.getModifiers())
                && m.getName().startsWith("set") && m.getName().length() > 3
                && m.getParameterCount() == 1
                && m.getReturnType() == Void.TYPE;
    }

    private boolean isGetter(Member member) {
        return (member instanceof Method m)
                && !Modifier.isStatic(m.getModifiers())
                && m.getParameterCount() == 0
                && (m.getName().startsWith("get") && m.getName().length() > 3
                        || m.getName().startsWith("is") && m.getName().length() > 2
                                && (m.getReturnType() == Boolean.TYPE || m.getReturnType() == Boolean.class));
    }

    private Stream<Class<?>> getClassAndSuper(Class<?> clazz) {
        return clazz != Object.class && clazz != null
                ? Stream.concat(Stream.of(clazz), getClassAndSuper(clazz.getSuperclass()))
                : Stream.empty();
    }

    private String propname(Member member) {
        String name = member.getName();
        if (member instanceof Method) {
            if (name.startsWith("is") && name.length() > 2) {
                return lowercase(name.substring(2));
            } else if ((name.startsWith("get") || name.startsWith("set")) && name.length() > 3) {
                return lowercase(name.substring(3));
            }
        } else if (member instanceof Field) {
            return name;
        }
        throw new IllegalArgumentException("Unable to determine property name for: " + member);
    }

    private boolean isNotTransient(Member member) {
        return ((AnnotatedElement) member).getAnnotation(XmlTransient.class) == null;
    }

    private boolean isRootElement(Class<?> clazz) {
        return clazz.getAnnotation(XmlRootElement.class) != null;
    }

    private String lowercase(String fn) {
        return fn.substring(0, 1).toLowerCase() + fn.substring(1);
    }

    private String uppercase(String fn) {
        return fn.substring(0, 1).toUpperCase() + fn.substring(1);
    }

}
