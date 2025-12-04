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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

import jakarta.xml.bind.annotation.*;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;
import jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import javax.inject.Inject;

import org.apache.camel.maven.packaging.generics.JandexStore;
import org.apache.camel.spi.annotations.ExternalSchemaElement;
import org.apache.camel.tooling.util.srcgen.GenericType;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;

/**
 * Generate Model lightweight XML Parser source code.
 */
@Mojo(
        name = "generate-xml-parser",
        threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class ModelXmlParserGeneratorMojo extends AbstractGeneratorMojo {

    public static final String PARSER_PACKAGE = "org.apache.camel.xml.in";
    public static final String MODEL_PACKAGE = "org.apache.camel.model";

    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File sourcesOutputDir;

    @Parameter(defaultValue = "${camel-generate-xml-parser}")
    protected boolean generateXmlParser;

    private Class<?> outputDefinitionClass;

    @Inject
    public ModelXmlParserGeneratorMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    @Override
    public void execute(MavenProject project) throws MojoFailureException, MojoExecutionException {
        sourcesOutputDir = new File(project.getBasedir(), "src/generated/java");
        generateXmlParser =
                Boolean.parseBoolean(project.getProperties().getProperty("camel-generate-xml-parser", "false"));
        super.execute(project);
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

        String resName = outputDefinitionClass.getName().replace('.', '/') + ".class";
        String url = classLoader.getResource(resName).toExternalForm().replace(resName, JandexStore.DEFAULT_NAME);
        Index index;
        try (InputStream is = URI.create(url).toURL().openStream()) {
            index = new IndexReader(is).read();
        } catch (IOException e) {
            throw new MojoExecutionException("IOException: " + e.getMessage(), e);
        }
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
                // we should skip this model as we do not want this in the JAXB model
                .filter(n -> !n.equals("org.apache.camel.model.WhenSkipSendToEndpointDefinition"))
                .map(name -> loadClass(classLoader, name))
                .flatMap(this::references)
                .flatMap(this::fieldReferences)
                .distinct()
                .toList();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("mojo", this);
        ctx.put("model", model.stream().map(ClassWrapper::new).toList());
        ctx.put("package", PARSER_PACKAGE);
        return velocity("velocity/model-parser.vm", ctx);
    }

    private Class<?> loadClass(ClassLoader loader, String name) {
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
        return Stream.concat(
                Stream.of(clazz),
                Stream.of(clazz.getDeclaredFields())
                        .filter(f -> f.getAnnotation(XmlTransient.class) == null)
                        .map(f -> {
                            if (f.getAnnotation(XmlJavaTypeAdapter.class) != null) {
                                Class<?> cl = f.getAnnotation(XmlJavaTypeAdapter.class)
                                        .value();
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
                        .filter(c -> c.getName().startsWith("org.apache.camel.")));
    }

    private Stream<? extends Member> findMethodsForClass(Class<?> c) {
        XmlAccessType accessType;
        if (c.getAnnotation(XmlAccessorType.class) != null && c != outputDefinitionClass) {
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

    public TreeMap<String, String> newTreeMap() {
        return new TreeMap<>();
    }

    public String lowercase(String fn) {
        return fn.substring(0, 1).toLowerCase() + fn.substring(1);
    }

    public String uppercase(String fn) {
        return fn.substring(0, 1).toUpperCase() + fn.substring(1);
    }

    public void failure(String message) {
        throw new RuntimeException(message);
    }

    public ClassWrapper wrap(Class<?> clazz) {
        return new ClassWrapper(clazz);
    }

    public class ClassWrapper {
        private final Class<?> clazz;

        public ClassWrapper(Class<?> clazz) {
            this.clazz = clazz;
        }

        public String getName() {
            return clazz.getName();
        }

        public String getSimpleName() {
            return clazz.getSimpleName();
        }

        public String getPackageName() {
            return clazz.getPackageName();
        }

        public ClassWrapper getSuperclass() {
            return clazz.getSuperclass() != null ? new ClassWrapper(clazz.getSuperclass()) : null;
        }

        public boolean isAssignableFrom(ClassWrapper wrapper) {
            return clazz.isAssignableFrom(wrapper.clazz);
        }

        public GenericType getSuperclassType() {
            return clazz.getGenericSuperclass() != null ? new GenericType(clazz.getGenericSuperclass()) : null;
        }

        public boolean isInterface() {
            return clazz.isInterface();
        }

        public boolean isAbstract() {
            return Modifier.isAbstract(clazz.getModifiers());
        }

        public XmlEnum getXmlEnum() {
            return ((AnnotatedElement) clazz).getAnnotation(XmlEnum.class);
        }

        public XmlRootElement getXmlRootElement() {
            return ((AnnotatedElement) clazz).getAnnotation(XmlRootElement.class);
        }

        public List<MemberWrapper> getAttributes() {
            return getMembers()
                    .filter(m -> m.getXmlAttribute() != null)
                    .sorted(Comparator.comparing(MemberWrapper::getName))
                    .toList();
        }

        public List<MemberWrapper> getElements() {
            return getMembers()
                    .filter(m ->
                            m.getXmlAttribute() == null && m.getXmlAnyAttribute() == null && m.getXmlValue() == null)
                    .sorted(Comparator.comparing(MemberWrapper::getName))
                    .toList();
        }

        public Optional<MemberWrapper> getValue() {
            return getMembers().filter(m -> m.getXmlValue() != null).findAny();
        }

        private Stream<MemberWrapper> getMembers() {
            return Stream.concat(findFieldsForClass(clazz), findMethodsForClass(clazz))
                    .map(MemberWrapper::new)
                    .filter(m -> clazz == outputDefinitionClass
                            || !outputDefinitionClass.isAssignableFrom(clazz)
                            || !m.member.getName().equals("setOutputs"));
        }

        public List<ClassWrapper> getClassAndSuper() {
            return doGetClassAndSuper().toList();
        }

        private Stream<ClassWrapper> doGetClassAndSuper() {
            return clazz != Object.class && clazz != null
                    ? Stream.concat(Stream.of(this), getSuperclass().doGetClassAndSuper())
                    : Stream.empty();
        }

        public Class<?> getWrappedClass() {
            return clazz;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClassWrapper that = (ClassWrapper) o;
            return Objects.equals(clazz, that.clazz);
        }

        @Override
        public int hashCode() {
            return Objects.hash(clazz);
        }
    }

    public class MemberWrapper {
        private final Member member;

        public MemberWrapper(Member member) {
            this.member = member;
        }

        public ClassWrapper getDeclaringClass() {
            return new ClassWrapper(member.getDeclaringClass());
        }

        public XmlAttribute getXmlAttribute() {
            return ((AnnotatedElement) member).getAnnotation(XmlAttribute.class);
        }

        public XmlAnyAttribute getXmlAnyAttribute() {
            return ((AnnotatedElement) member).getAnnotation(XmlAnyAttribute.class);
        }

        public XmlValue getXmlValue() {
            return ((AnnotatedElement) member).getAnnotation(XmlValue.class);
        }

        public XmlTransient getXmlTransient() {
            return ((AnnotatedElement) member).getAnnotation(XmlTransient.class);
        }

        public XmlElementRef getXmlElementRef() {
            return ((AnnotatedElement) member).getAnnotation(XmlElementRef.class);
        }

        public XmlElements getXmlElements() {
            return ((AnnotatedElement) member).getAnnotation(XmlElements.class);
        }

        public XmlElement getXmlElement() {
            return ((AnnotatedElement) member).getAnnotation(XmlElement.class);
        }

        public XmlElementWrapper getXmlElementWrapper() {
            return ((AnnotatedElement) member).getAnnotation(XmlElementWrapper.class);
        }

        public XmlJavaTypeAdapter getXmlJavaTypeAdapter() {
            return ((AnnotatedElement) member).getAnnotation(XmlJavaTypeAdapter.class);
        }

        public XmlAnyElement getXmlAnyElement() {
            return ((AnnotatedElement) member).getAnnotation(XmlAnyElement.class);
        }

        public ExternalSchemaElement getExternalSchemaElement() {
            return ((AnnotatedElement) member).getAnnotation(ExternalSchemaElement.class);
        }

        public GenericType getType() {
            return new GenericType(
                    member instanceof Method
                            ? ((Method) member).getGenericParameterTypes()[0]
                            : ((Field) member).getGenericType());
        }

        public String getName() {
            String name = null;
            XmlAttribute attr = getXmlAttribute();
            if (attr != null) {
                String aname = attr.name();
                if (!"##default".equals(aname)) {
                    name = aname;
                }
            }
            if (name == null) {
                String mn = member.getName();
                if (member instanceof Method) {
                    name = lowercase(mn.substring(3));
                } else {
                    name = mn;
                }
            }
            return name;
        }

        public String getSetter() {
            String mn = member.getName();
            return member instanceof Method ? mn : "set" + uppercase(mn);
        }

        public String getGetter() {
            return "g" + getSetter().substring(1);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MemberWrapper that = (MemberWrapper) o;
            return Objects.equals(member, that.member);
        }

        @Override
        public int hashCode() {
            return Objects.hash(member);
        }
    }
}
