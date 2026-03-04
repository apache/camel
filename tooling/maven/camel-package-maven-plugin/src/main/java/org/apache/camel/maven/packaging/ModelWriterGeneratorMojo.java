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
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAnyAttribute;
import jakarta.xml.bind.annotation.XmlAnyElement;
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
import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.EipModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.srcgen.GenericType;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;

public abstract class ModelWriterGeneratorMojo extends AbstractGeneratorMojo {

    private static final String FALLBACK = "doWriteElement(\"onFallback\"";

    public static final String MODEL_PACKAGE = "org.apache.camel.model";

    private final Map<Class<?>, List<Property>> properties = new ConcurrentHashMap<>();
    private final Map<Class<?>, List<Member>> members = new ConcurrentHashMap<>();
    private final Map<String, EipModel> allModels = new HashMap<>();

    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File sourcesOutputDir;

    @Parameter(defaultValue = "${project.basedir}/../camel-core-model/src/generated/resources")
    protected File modelDir;

    protected ModelWriterGeneratorMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    private static Type type(Member member) {
        return member instanceof Method
                ? member.getName().startsWith("set")
                        ? ((Method) member).getGenericParameterTypes()[0]
                        : ((Method) member).getGenericReturnType()
                : ((Field) member).getGenericType();
    }

    String getModelPackage() {
        return MODEL_PACKAGE;
    }

    abstract String getWriterPackage();

    protected String generateWriter() throws MojoExecutionException {
        ClassLoader classLoader;
        try {
            classLoader = DynamicClassLoader.createDynamicClassLoader(project.getCompileClasspathElements());
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("DependencyResolutionRequiredException: " + e.getMessage(), e);
        }

        List<Path> jsonFiles;
        try (Stream<Path> stream = PackageHelper.findJsonFiles(modelDir.toPath())) {
            jsonFiles = stream.toList();
        }
        for (Path p : jsonFiles) {
            try {
                String json = Files.readString(p);
                if (json.contains("\"kind\": \"model\"")) {
                    var m = JsonMapper.generateEipModel(json);
                    allModels.put(m.getJavaType(), m);
                }
            } catch (Exception e) {
                throw new MojoExecutionException(e);
            }
        }
        getLog().debug("Loaded " + allModels.size() + " eip models");

        Class<?> routesDefinitionClass
                = loadClass(classLoader, XmlModelWriterGeneratorMojo.MODEL_PACKAGE + ".RoutesDefinition");
        String resName = routesDefinitionClass.getName().replace('.', '/') + ".class";
        String url = classLoader.getResource(resName).toExternalForm().replace(resName, JandexStore.DEFAULT_NAME);
        Index index;
        try (InputStream is = URI.create(url).toURL().openStream()) {
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

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("package", getWriterPackage());
        ctx.put("model", model);
        ctx.put("mojo", this);
        return velocity("velocity/model-writer.vm", ctx);
    }

    protected String postGenerateWriter(String writer) {
        // the velocity templates are ugly to maintain so lets hack here to control the order in circuit-breaker
        // we want onFallback to be after outputs, but because the writer is hard-coded to not be able to generate code
        // that respect this order, then we swap the lines after the source code is generated
        String[] lines = writer.split(System.lineSeparator());
        int pos = -1;
        for (int i = 0; i < lines.length; i++) {
            String s = lines[i].trim();
            if (s.startsWith(FALLBACK)) {
                pos = i;
                break;
            }
        }
        if (pos != -1) {
            // swap lines
            String prev = lines[pos];
            String next = lines[pos + 1];
            lines[pos] = next;
            lines[pos + 1] = prev;
        }
        return String.join(System.lineSeparator(), lines);
    }

    protected Class<?> loadClass(ClassLoader loader, String name) {
        try {
            return loader.loadClass(name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unable to load class " + name, e);
        }
    }

    public TreeSet<Class<?>> newClassTreeSet() {
        return new TreeSet<>(Comparator.comparing(Class::getName));
    }

    public Stream<Class<?>> getClassAndSuper(Class<?> clazz) {
        return clazz != Object.class && clazz != null
                ? Stream.concat(Stream.of(clazz), getClassAndSuper(clazz.getSuperclass()))
                : Stream.empty();
    }

    public String getGenericSimpleName(Class<?> clazz) {
        String name = clazz.getSimpleName();
        if (clazz.getTypeParameters().length > 0) {
            name += Stream.of(clazz.getTypeParameters()).map(t -> "?")
                    .collect(Collectors.joining(", ", "<", ">"));
        }
        return name;
    }

    public boolean isReferenced(Class<?> clazz, List<Class<?>> model) {
        return model.stream()
                .flatMap(this::getProperties)
                .anyMatch(p -> {
                    GenericType t = p.getGenericType();
                    Class<?> cl = t.getRawClass() == List.class ? t.getActualTypeArgument(0).getRawClass() : t.getRawClass();
                    return cl == clazz;
                });
    }

    public XmlRootElement getXmlRootElement(Class<?> clazz) {
        return clazz.getAnnotation(XmlRootElement.class);
    }

    public XmlEnum getXmlEnum(Class<?> clazz) {
        return clazz.getAnnotation(XmlEnum.class);
    }

    public String lowercase(String fn) {
        return fn.substring(0, 1).toLowerCase() + fn.substring(1);
    }

    public Stream<Property> getAttributes(Class<?> clazz) {
        return getProperties(clazz).filter(Property::isAttribute);
    }

    public Stream<Property> getElements(Class<?> clazz) {
        return getProperties(clazz).filter(Property::isElement)
                .sorted(Comparator.comparing(p -> "outputs".equals(p.name)));
    }

    public Stream<Property> getValues(Class<?> clazz) {
        return getProperties(clazz).filter(Property::isValue);
    }

    public Stream<Property> getValues(Stream<Class<?>> classStream) {
        return getProperties(classStream).filter(Property::isValue);
    }

    public Stream<Property> getProperties(Class<?> clazz) {
        return properties.computeIfAbsent(clazz, this::doGetProperties).stream();
    }

    public Stream<Property> getProperties(Stream<Class<?>> classStream) {
        return classStream.flatMap(this::getProperties);
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
                    Type type = ModelWriterGeneratorMojo.type(members.get(0));
                    Member field = members.stream()
                            .filter(this::isField)
                            .findFirst().or(() -> allMembers.stream()
                                    .filter(m -> isField(m)
                                            && Objects.equals(propname(m), name)
                                            && Objects.equals(ModelWriterGeneratorMojo.type(m), type))
                                    .findFirst())
                            .orElse(null);
                    Member getter = members.stream()
                            .filter(this::isGetter)
                            .findFirst().or(() -> allMembers.stream()
                                    .filter(m -> isGetter(m)
                                            && Objects.equals(propname(m), name)
                                            && Objects.equals(ModelWriterGeneratorMojo.type(m), type))
                                    .findFirst())
                            .orElse(null);
                    Member setter = members.stream()
                            .filter(this::isSetter)
                            .findFirst().or(() -> allMembers.stream()
                                    .filter(m -> isSetter(m)
                                            && Objects.equals(propname(m), name)
                                            && Objects.equals(ModelWriterGeneratorMojo.type(m), type))
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

        // special for base class as it is abstract so lets use another model
        // as we only need this to know the order of the options for sorting
        String modelName = clazz.getName();
        if ("OptionalIdentifiedDefinition".equals(clazz.getSimpleName())) {
            modelName = "org.apache.camel.model.ProcessDefinition";
        }

        EipModel m = allModels.get(modelName);
        if (m != null) {
            // special for DSL where XML vs YAML have different names, and we must use as-is due to JAXB @XmlType propOrder
            final Map<String, String> alias = Map.of(
                    "whenClauses", "when",
                    "componentScanning", "component-scan",
                    "beans", "bean",
                    "dataFormats", "dataFormat",
                    "restConfigurations", "restConfiguration",
                    "rests", "rest",
                    "routeConfigurations", "routeConfiguration",
                    "routeTemplates", "routeTemplate",
                    "templatedRoutes", "templatedRoute",
                    "routes", "route");
            // some EIPs have @XmlType propOrder
            final List<String> propOrderList = new ArrayList<>();
            XmlType xmlType = clazz.getAnnotation(XmlType.class);
            if (xmlType != null) {
                String[] propOrder = xmlType.propOrder();
                if (propOrder != null && propOrder.length > 1) {
                    propOrderList.addAll(Arrays.stream(propOrder).map(o -> alias.getOrDefault(o, o)).toList());
                }
            }
            // sort according to @XmlType propOrder and EIP model
            properties = properties.sorted((o1, o2) -> {
                String n1 = alias.getOrDefault(o1.name, o1.name);
                String n2 = alias.getOrDefault(o2.name, o2.name);
                // prioritize prop order
                int idx1 = propOrderList.indexOf(n1);
                int idx2 = propOrderList.indexOf(n2);
                // fallback to sort after model
                for (var o : m.getOptions()) {
                    if (idx1 == -1 && o.getName().equals(n1)) {
                        idx1 = o.getIndex();
                    } else if (idx2 == -1 && o.getName().equals(n2)) {
                        idx2 = o.getIndex();
                    }
                }
                return Integer.compare(idx1, idx2);
            });
        }
        return properties.toList();
    }

    private List<Member> getMembers(Class<?> clazz) {
        return members.computeIfAbsent(clazz, cl -> Stream.<Member> concat(
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
                .anyMatch(a -> a.getClass().getAnnotatedInterfaces()[0].getType().getTypeName()
                        .startsWith("jakarta.xml.bind.annotation."));
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

    public static class Property {
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

        public GenericType getGenericType() {
            return new GenericType(type);
        }

        public String getGetter() {
            return Optional.ofNullable(getter)
                    .orElseThrow(() -> new IllegalArgumentException("No getter for property defined by " + members().toList()))
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

        public XmlElementRefs getXmlElementRefs() {
            return getAnnotation(XmlElementRefs.class);
        }

        public XmlElementRef getXmlElementRef() {
            return getAnnotation(XmlElementRef.class);
        }

        public XmlElements getXmlElements() {
            return getAnnotation(XmlElements.class);
        }

        public XmlElement getXmlElement() {
            return getAnnotation(XmlElement.class);
        }

        public XmlAnyElement getXmlAnyElement() {
            return getAnnotation(XmlAnyElement.class);
        }

        public XmlRootElement getXmlRootElement() {
            return getAnnotation(XmlRootElement.class);
        }

        public XmlElementWrapper getXmlElementWrapper() {
            return getAnnotation(XmlElementWrapper.class);
        }

        public XmlJavaTypeAdapter getXmlJavaTypeAdapter() {
            return getAnnotation(XmlJavaTypeAdapter.class);
        }

        public String getAttributeName() {
            String an = getAnnotation(XmlAttribute.class).name();
            if ("##default".equals(an)) {
                an = getName();
            }
            return an;
        }

        public String getDefaultValue() {
            String answer = null;
            Metadata m = getAnnotation(Metadata.class);
            if (m != null) {
                answer = m.defaultValue();
            }
            if (answer == null || answer.isBlank()) {
                answer = "null";
            } else {
                if (answer.equals("\"")) {
                    answer = "\\\"";
                }
                if (answer.equals("\\")) {
                    answer = "\\\\";
                }
                answer = "\"" + answer + "\"";
            }
            return answer;
        }

    }
}
