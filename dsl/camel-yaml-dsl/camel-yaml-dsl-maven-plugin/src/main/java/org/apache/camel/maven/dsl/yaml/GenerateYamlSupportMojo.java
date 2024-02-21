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
package org.apache.camel.maven.dsl.yaml;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import org.apache.camel.maven.dsl.yaml.support.IndexerSupport;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.util.AntPathMatcher;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.FieldInfo;
import org.jboss.jandex.IndexView;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

public abstract class GenerateYamlSupportMojo extends AbstractMojo {

    public static final DotName LIST_CLASS
            = DotName.createSimple("java.util.List");
    public static final DotName SET_CLASS
            = DotName.createSimple("java.util.Set");
    public static final DotName STRING_CLASS
            = DotName.createSimple("java.lang.String");
    public static final DotName CLASS_CLASS
            = DotName.createSimple("java.lang.Class");

    public static final DotName DEPRECATED_ANNOTATION_CLASS
            = DotName.createSimple("java.lang.Deprecated");

    public static final DotName XML_ROOT_ELEMENT_ANNOTATION_CLASS
            = DotName.createSimple("jakarta.xml.bind.annotation.XmlRootElement");
    public static final DotName XML_TYPE_CLASS
            = DotName.createSimple("jakarta.xml.bind.annotation.XmlType");
    public static final DotName XML_ENUM_CLASS
            = DotName.createSimple("jakarta.xml.bind.annotation.XmlEnum");
    public static final DotName XML_VALUE_CLASS
            = DotName.createSimple("jakarta.xml.bind.annotation.XmlValue");
    public static final DotName XML_ATTRIBUTE_ANNOTATION_CLASS
            = DotName.createSimple("jakarta.xml.bind.annotation.XmlAttribute");
    public static final DotName XML_VALUE_ANNOTATION_CLASS
            = DotName.createSimple("jakarta.xml.bind.annotation.XmlValue");
    public static final DotName XML_ELEMENT_ANNOTATION_CLASS
            = DotName.createSimple("jakarta.xml.bind.annotation.XmlElement");
    public static final DotName XML_ELEMENT_REF_ANNOTATION_CLASS
            = DotName.createSimple("jakarta.xml.bind.annotation.XmlElementRef");
    public static final DotName XML_ELEMENTS_ANNOTATION_CLASS
            = DotName.createSimple("jakarta.xml.bind.annotation.XmlElements");
    public static final DotName XML_TRANSIENT_CLASS
            = DotName.createSimple("jakarta.xml.bind.annotation.XmlTransient");
    public static final DotName XML_ANY_ELEMENT_CLASS
            = DotName.createSimple("jakarta.xml.bind.annotation.XmlAnyElement");
    public static final DotName XML_JAVA_TYPE_ADAPTER_CLASS
            = DotName.createSimple("jakarta.xml.bind.annotation.adapters.XmlJavaTypeAdapter");

    public static final DotName METADATA_ANNOTATION_CLASS
            = DotName.createSimple("org.apache.camel.spi.Metadata");
    public static final DotName EXPRESSION_DEFINITION_CLASS
            = DotName.createSimple("org.apache.camel.model.language.ExpressionDefinition");
    public static final DotName EXPRESSION_SUBELEMENT_DEFINITION_CLASS
            = DotName.createSimple("org.apache.camel.model.ExpressionSubElementDefinition");
    public static final DotName HAS_EXPRESSION_TYPE_CLASS
            = DotName.createSimple("org.apache.camel.model.HasExpressionType");
    public static final DotName OUTPUT_NODE_CLASS
            = DotName.createSimple("org.apache.camel.model.OutputNode");
    public static final DotName PROCESSOR_DEFINITION_CLASS
            = DotName.createSimple("org.apache.camel.model.ProcessorDefinition");
    public static final DotName SEND_DEFINITION_CLASS
            = DotName.createSimple("org.apache.camel.model.SendDefinition");
    public static final DotName TO_DYNAMIC_DEFINITION_CLASS
            = DotName.createSimple("org.apache.camel.model.ToDynamicDefinition");
    public static final DotName ERROR_HANDLER_BUILDER_CLASS
            = DotName.createSimple("org.apache.camel.builder.ErrorHandlerBuilder");
    public static final DotName VERB_DEFINITION_CLASS
            = DotName.createSimple("org.apache.camel.model.rest.VerbDefinition");
    public static final DotName ID_AWARE_CLASS
            = DotName.createSimple("org.apache.camel.spi.IdAware");
    public static final DotName ERROR_HANDLER_DEFINITION_CLASS
            = DotName.createSimple("org.apache.camel.model.errorhandler.BaseErrorHandlerDefinition");
    public static final DotName REF_ERROR_HANDLER_DEFINITION_CLASS
            = DotName.createSimple("org.apache.camel.model.errorhandler.RefErrorHandlerDefinition");

    public static final DotName YAML_TYPE_ANNOTATION
            = DotName.createSimple("org.apache.camel.spi.annotations.YamlType");
    public static final DotName YAML_IN_ANNOTATION
            = DotName.createSimple("org.apache.camel.spi.annotations.YamlIn");
    public static final DotName YAML_OUT_ANNOTATION
            = DotName.createSimple("org.apache.camel.spi.annotations.YamlOut");
    public static final DotName DSL_PROPERTY_ANNOTATION
            = DotName.createSimple("org.apache.camel.spi.annotations.DslProperty");

    public static final ClassName CN_DESERIALIZER_RESOLVER
            = ClassName.get("org.apache.camel.dsl.yaml.common", "YamlDeserializerResolver");
    public static final ClassName CN_DESERIALIZER_SUPPORT
            = ClassName.get("org.apache.camel.dsl.yaml.common", "YamlDeserializerSupport");
    public static final ClassName CN_DESERIALIZER_BASE
            = ClassName.get("org.apache.camel.dsl.yaml.common", "YamlDeserializerBase");
    public static final ClassName CN_ENDPOINT_AWARE_DESERIALIZER_BASE
            = ClassName.get("org.apache.camel.dsl.yaml.common", "YamlDeserializerEndpointAwareBase");
    public static final ClassName CN_DESERIALIZATION_CONTEXT
            = ClassName.get("org.apache.camel.dsl.yaml.common", "YamlDeserializationContext");
    public static final ClassName CN_YAML_SUPPORT
            = ClassName.get("org.apache.camel.dsl.yaml.common", "YamlSupport");
    public static final ClassName CN_YAML_TYPE
            = ClassName.get("org.apache.camel.spi.annotations", "YamlType");
    public static final ClassName CN_YAML_PROPERTY
            = ClassName.get("org.apache.camel.spi.annotations", "YamlProperty");

    public static final ClassName CN_YAML_PROPERTY_GROUP
            = ClassName.get("org.apache.camel.spi.annotations", "YamlPropertyGroup");
    public static final ClassName CN_YAML_IN
            = ClassName.get("org.apache.camel.spi.annotations", "YamlIn");
    public static final ClassName CN_EXPRESSION_DEFINITION
            = ClassName.get("org.apache.camel.model.language", "ExpressionDefinition");
    public static final ClassName CN_NODE
            = ClassName.get("org.snakeyaml.engine.v2.nodes", "Node");
    public static final ClassName CN_MAPPING_NODE
            = ClassName.get("org.snakeyaml.engine.v2.nodes", "MappingNode");
    public static final ClassName CN_NODE_TUPLE
            = ClassName.get("org.snakeyaml.engine.v2.nodes", "NodeTuple");
    public static final ClassName CN_SEQUENCE_NODE
            = ClassName.get("org.snakeyaml.engine.v2.nodes", "SequenceNode");
    public static final ClassName CN_PROCESSOR_DEFINITION
            = ClassName.get("org.apache.camel.model", "ProcessorDefinition");

    public static final Set<String> PRIMITIVE_CLASSES = new HashSet<>(
            Arrays.asList(
                    String.class.getName(),
                    Character.class.getName(),
                    Boolean.class.getName(),
                    Byte.class.getName(),
                    Short.class.getName(),
                    Integer.class.getName(),
                    Long.class.getName(),
                    Float.class.getName(),
                    Double.class.getName(),
                    char.class.getName(),
                    boolean.class.getName(),
                    byte.class.getName(),
                    short.class.getName(),
                    int.class.getName(),
                    long.class.getName(),
                    float.class.getName(),
                    double.class.getName()));
    /**
     * The default value the String attributes of all the JAXB annotations.
     */
    private static final String XML_ANNOTATION_DEFAULT_VALUE = "##default";

    protected IndexView view;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;
    @Parameter
    protected List<String> bannedDefinitions;
    @Parameter
    protected List<String> additionalDefinitions;

    protected static boolean hasAnnotation(ClassInfo target, DotName annotationName) {
        if (target == null) {
            return false;
        }
        return target.declaredAnnotation(annotationName) != null;
    }

    protected static boolean hasAnnotation(FieldInfo target, DotName annotationName) {
        if (target == null) {
            return false;
        }
        return target.annotation(annotationName) != null;
    }

    // **************************
    //
    // Indexer
    //
    // **************************

    protected static boolean hasAnnotationValue(ClassInfo target, DotName annotationName, String name) {
        if (target == null) {
            return false;
        }
        return annotationValue(
                target.declaredAnnotation(annotationName),
                name).isPresent();
    }

    protected static Optional<AnnotationValue> annotationValue(AnnotationInstance instance, String name) {
        return instance != null
                ? Optional.ofNullable(instance.value(name))
                : Optional.empty();
    }

    protected static Optional<AnnotationValue> annotationValue(ClassInfo target, DotName annotationName, String name) {
        if (target == null) {
            return Optional.empty();
        }
        return annotationValue(
                target.declaredAnnotation(annotationName),
                name);
    }

    protected static Optional<AnnotationValue> annotationValue(FieldInfo target, DotName annotationName, String name) {
        if (target == null) {
            return Optional.empty();
        }
        return annotationValue(
                target.annotation(annotationName),
                name);
    }

    private static Optional<AnnotationInstance> annotation(FieldInfo target, DotName annotationName) {
        if (target == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(target.annotation(annotationName));
    }

    /**
     * Combines the given items assuming they can be also composed by comma separated elements.
     *
     * @param  items the items
     * @return       a stream of individual items
     */
    protected static Stream<String> combine(String... items) {
        Set<String> answer = new TreeSet<>();

        for (String item : items) {
            if (item == null) {
                continue;
            }

            String[] elements = item.split(",");
            answer.addAll(Arrays.asList(elements));
        }

        return answer.stream();
    }

    protected static AnnotationSpec yamlProperty(String name, String type, String oneOf) {
        return yamlProperty(name, type, false, false, oneOf);
    }

    protected static AnnotationSpec yamlProperty(String name, String type) {
        return yamlProperty(name, type, false, false, "");
    }

    protected static AnnotationSpec yamlProperty(String name, String type, boolean required, boolean deprecated, String oneOf) {
        AnnotationSpec.Builder builder = AnnotationSpec.builder(CN_YAML_PROPERTY);
        builder.addMember("name", "$S", name);
        builder.addMember("type", "$S", type);
        if (required) {
            builder.addMember("required", "$L", required);
        }
        if (deprecated) {
            builder.addMember("deprecated", "$L", deprecated);
        }
        if (!Strings.isNullOrEmpty(oneOf)) {
            builder.addMember("oneOf", "$S", oneOf);
        }

        return builder.build();
    }

    protected static AnnotationSpec yamlPropertyWithFormat(String name, String type, String format) {
        return yamlPropertyWithFormat(name, type, format, false, false);
    }

    protected static AnnotationSpec yamlPropertyWithFormat(
            String name, String type, String format, boolean required, boolean deprecated) {
        AnnotationSpec.Builder builder = AnnotationSpec.builder(CN_YAML_PROPERTY);
        builder.addMember("name", "$S", name);
        builder.addMember("type", "$S", type);
        builder.addMember("format", "$S", format);

        if (required) {
            builder.addMember("required", "$L", required);
        }
        if (deprecated) {
            builder.addMember("deprecated", "$L", deprecated);
        }

        return builder.build();
    }

    // **************************
    //
    // Class loading
    //
    // **************************

    protected static AnnotationSpec yamlPropertyWithSubtype(String name, String type, String subType, String oneOf) {
        return yamlPropertyWithSubtype(name, type, subType, false, oneOf);
    }

    protected static AnnotationSpec yamlPropertyWithSubtype(
            String name, String type, String subType, boolean required, String oneOf) {
        return yamlProperty(name, type + ":" + subType, required, false, oneOf);
    }

    // **************************
    //
    // Helpers
    //
    // **************************

    protected static int getYamlTypeOrder(ClassInfo ci) {
        return annotationValue(ci, YAML_TYPE_ANNOTATION, "order").map(AnnotationValue::asInt).orElse(Integer.MAX_VALUE);
    }

    @Override
    public void execute() throws MojoFailureException {
        view = IndexerSupport.get(project);

        generate();
    }

    protected abstract void generate() throws MojoFailureException;

    protected Stream<ClassInfo> implementors(DotName type) {
        return view.getAllKnownImplementors(type).stream();
    }

    protected Stream<ClassInfo> annotated(DotName type) {
        return view.getAnnotations(type).stream()
                .map(AnnotationInstance::target)
                .filter(t -> t.kind() == AnnotationTarget.Kind.CLASS)
                .map(AnnotationTarget::asClass)
                .filter(ci -> !isBanned(ci));
    }

    protected Map<String, ClassInfo> elementsOf(DotName type) {
        Map<String, ClassInfo> answer = new TreeMap<>();

        for (ClassInfo ci : view.getAllKnownSubclasses(type)) {
            AnnotationInstance instance = ci.declaredAnnotation(XML_ROOT_ELEMENT_ANNOTATION_CLASS);
            if (instance != null) {
                AnnotationValue name = instance.value("name");
                if (name != null) {
                    answer.put(name.asString(), ci);
                }
            }
        }

        return Collections.unmodifiableMap(answer);
    }

    protected Class<?> loadClass(ClassInfo ci) {
        return loadClass(ci.name().toString());
    }

    protected Class<?> loadClass(String className) {
        try {
            return IndexerSupport.getClassLoader(project).loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    protected Stream<ClassInfo> all() {
        Stream<ClassInfo> discovered = Stream.of(XML_ROOT_ELEMENT_ANNOTATION_CLASS, XML_TYPE_CLASS)
                .map(view::getAnnotations)
                .flatMap(Collection::stream)
                .map(AnnotationInstance::target)
                .filter(at -> at.kind() == AnnotationTarget.Kind.CLASS)
                .map(AnnotationTarget::asClass);

        Stream<ClassInfo> additional = additionalDefinitions != null
                ? additionalDefinitions.stream().map(DotName::createSimple).map(view::getClassByName)
                : Stream.empty();

        return Stream.concat(discovered, additional)
                .filter(ci -> (ci.flags() & Modifier.ABSTRACT) == 0)
                .filter(ci -> !isBanned(ci))
                .filter(ci -> !ci.isEnum())
                .sorted(Comparator.comparing(o -> o.name().toString()))
                .distinct();
    }

    /**
     * Load all the models.
     */
    protected Map<String, ClassInfo> models() {
        Map<String, ClassInfo> answer = new TreeMap<>();

        annotated(XML_ROOT_ELEMENT_ANNOTATION_CLASS)
                .forEach(
                        i -> {
                            AnnotationInstance meta = i.declaredAnnotation(METADATA_ANNOTATION_CLASS);
                            AnnotationInstance root = i.declaredAnnotation(XML_ROOT_ELEMENT_ANNOTATION_CLASS);

                            if (meta == null || root == null) {
                                return;
                            }

                            AnnotationValue name = root.value("name");
                            AnnotationValue label = meta.value("label");

                            if (name == null || label == null) {
                                return;
                            }

                            if (bannedDefinitions != null) {
                                for (String bannedDefinition : bannedDefinitions) {
                                    if (AntPathMatcher.INSTANCE.match(bannedDefinition.replace('.', '/'),
                                            i.name().toString('/'))) {
                                        getLog().debug("Skipping definition: " + i.name().toString());
                                        return;
                                    }
                                }
                            }

                            Set<String> labels = new TreeSet<>(
                                    Arrays.asList(label.asString().split(",")));

                            if (labels.contains("eip")) {
                                answer.put(name.asString(), i);
                            }
                        });

        return answer;
    }

    /**
     * Load all the definitions.
     */
    protected Set<ClassInfo> definitions() {
        final Set<ClassInfo> answer = new LinkedHashSet<>();

        final Set<ClassInfo> discovered = new LinkedHashSet<>(models().values());

        for (ClassInfo type : discovered) {
            answer.addAll(definitions(type));
            if ((type.flags() & Modifier.ABSTRACT) == 0) {
                answer.add(type);
            }
        }

        return answer;
    }

    /**
     * Load all the definitions.
     */
    protected Set<ClassInfo> definitions(ClassInfo ci) {
        final Set<ClassInfo> types = new LinkedHashSet<>();

        for (FieldInfo fi : ci.fields()) {
            if (hasAnnotation(fi, XML_ELEMENTS_ANNOTATION_CLASS)) {
                AnnotationInstance[] elements = fi.annotation(XML_ELEMENTS_ANNOTATION_CLASS).value().asNestedArray();

                for (AnnotationInstance element : elements) {
                    AnnotationValue type = element.value("type");

                    if (type != null) {
                        ClassInfo fti = view.getClassByName(fi.type().name());
                        types.addAll(definitions(fti));
                        if ((fti.flags() & Modifier.ABSTRACT) == 0) {
                            types.add(fti);
                        }
                    }
                }
            }

            if (!hasAnnotation(fi, XML_ELEMENT_ANNOTATION_CLASS) &&
                    !hasAnnotation(fi, XML_ELEMENTS_ANNOTATION_CLASS)) {
                continue;
            }
            if (fi.type().name().toString().startsWith("java.")) {
                continue;
            }
            if (fi.type().name().toString().startsWith("sun.")) {
                continue;
            }
            if (fi.type().kind() == Type.Kind.PARAMETERIZED_TYPE) {
                // TODO: support
                continue;
            }

            ClassInfo fti = view.getClassByName(fi.type().name());
            if (fti != null) {
                types.addAll(definitions(fti));
                if ((fti.flags() & Modifier.ABSTRACT) == 0) {
                    types.add(fti);
                }
            }
        }

        DotName superName = ci.superName();
        if (superName != null) {
            ClassInfo sci = view.getClassByName(superName);
            if (sci != null) {
                types.addAll(definitions(sci));
                if ((sci.flags() & Modifier.ABSTRACT) == 0) {
                    types.add(sci);
                }
            }
        }

        return types;
    }

    // ***********************************
    //
    // YamlProperty
    //
    // ***********************************

    protected Set<FieldInfo> fields(ClassInfo ci) {
        Set<FieldInfo> fields = new TreeSet<>(Comparator.comparing(FieldInfo::name));

        ClassInfo current = ci;
        while (current != null) {
            fields.addAll(current.fields());

            DotName superName = current.superName();
            if (superName == null) {
                break;
            }

            current = view.getClassByName(superName);
        }

        return fields;
    }

    protected Set<MethodInfo> methods(ClassInfo ci) {
        Set<MethodInfo> methods = new TreeSet<>(Comparator.comparing(MethodInfo::name));

        ClassInfo current = ci;
        while (current != null) {
            methods.addAll(current.methods());

            DotName superName = current.superName();
            if (superName == null) {
                break;
            }

            current = view.getClassByName(superName);
        }

        return methods;
    }

    @SafeVarargs
    protected final <T> Optional<T> firstPresent(Optional<T>... optionals) {
        for (Optional<T> optional : optionals) {
            if (optional.isPresent()) {
                return optional;
            }
        }

        return Optional.empty();
    }

    /**
     * @see #fieldName(ClassInfo, FieldInfo)
     */
    protected String fieldName(FieldInfo field) {
        return fieldName(view.getClassByName(field.type().name()), field);
    }

    /**
     * @return the name from the given annotation or from the annotation {@code @XmlRootElement} of the provided class.
     */
    private Optional<String> getNameFromAnnotationOrRef(AnnotationInstance annotation, ClassInfo refClass, String emptyValue) {
        return annotationValue(annotation, "name")
                .map(AnnotationValue::asString)
                .filter(value -> !emptyValue.equals(value))
                .or(() -> annotationValue(refClass, XML_ROOT_ELEMENT_ANNOTATION_CLASS, "name")
                        .map(AnnotationValue::asString)
                        .filter(v -> !XML_ANNOTATION_DEFAULT_VALUE.equals(v)));
    }

    protected boolean isRequired(FieldInfo fi) {
        return firstPresent(
                annotationValue(fi, METADATA_ANNOTATION_CLASS, "required")
                        .map(AnnotationValue::asBoolean),
                annotationValue(fi, XML_VALUE_ANNOTATION_CLASS, "required")
                        .map(AnnotationValue::asBoolean),
                annotationValue(fi, XML_ATTRIBUTE_ANNOTATION_CLASS, "required")
                        .map(AnnotationValue::asBoolean))
                .orElse(false);
    }

    protected String getEnums(FieldInfo fi) {
        return annotationValue(fi, METADATA_ANNOTATION_CLASS, "enums")
                .map(AnnotationValue::asString).orElse("");
    }

    protected String getJavaType(FieldInfo fi) {
        return annotationValue(fi, METADATA_ANNOTATION_CLASS, "javaType")
                .map(AnnotationValue::asString).orElse("");
    }

    protected boolean isEnum(FieldInfo fi) {
        return !getEnums(fi).isBlank();
    }

    protected boolean isDeprecated(FieldInfo fi) {
        return fi.hasAnnotation(DEPRECATED_ANNOTATION_CLASS);
    }

    protected boolean extendsType(Type type, DotName superType) {
        return extendsType(
                view.getClassByName(type.name()),
                superType);
    }

    protected boolean extendsType(ClassInfo ci, DotName superType) {
        if (ci == null) {
            return false;
        }
        if (ci.name().equals(superType)) {
            return true;
        }

        DotName superName = ci.superName();
        if (superName != null) {
            return extendsType(
                    view.getClassByName(superName),
                    superType);
        }

        return false;
    }

    protected boolean implementType(Type type, DotName interfaceType) {
        return implementType(
                view.getClassByName(type.name()),
                interfaceType);
    }

    protected boolean implementType(ClassInfo ci, DotName interfaceType) {
        if (ci == null) {
            return false;
        }
        if (ci.name().equals(interfaceType)) {
            return true;
        }

        for (DotName name : ci.interfaceNames()) {
            if (name.equals(interfaceType)) {
                return true;
            }
        }

        DotName superName = ci.superName();
        if (superName != null) {
            return implementType(
                    view.getClassByName(superName),
                    interfaceType);
        }

        return false;
    }

    protected boolean isBanned(ClassInfo ci) {
        if (bannedDefinitions != null) {
            for (String bannedDefinition : bannedDefinitions) {
                if (AntPathMatcher.INSTANCE.match(bannedDefinition.replace('.', '/'), ci.name().toString('/'))) {
                    getLog().debug("Skipping definition: " + ci.name().toString());
                    return true;
                }
            }
        }

        return false;
    }

    protected Stream<ClassInfo> implementsOrExtends(Type ci) {
        return Stream.concat(
                view.getAllKnownSubclasses(ci.name()).stream(),
                view.getAllKnownSubclasses(ci.name()).stream())
                .distinct()
                .sorted(Comparator.comparing(ClassInfo::name));
    }

    /**
     * As stated in the JAXB specification:
     * <ul>
     * <li>In case of {@code @XmlAttribute} and {@code @XmlElement}, the name is retrieved from the annotation if it has
     * been set, otherwise the field name is used</li>
     * <li>In case of {@code @XmlElementRef} and {@code @DslProperty} (the latter is specific to Camel), the name is
     * retrieved from the annotation if it has been set, otherwise it is retrieved from the annotation
     * {@code @XmlRootElement} on the type being referenced.</li>
     * </ul>
     */
    protected String fieldName(ClassInfo ci, FieldInfo fi) {
        return firstPresent(
                annotation(fi, DSL_PROPERTY_ANNOTATION)
                        .flatMap(annotation -> getNameFromAnnotationOrRef(annotation, ci, "")),
                annotationValue(fi, XML_ATTRIBUTE_ANNOTATION_CLASS, "name")
                        .map(AnnotationValue::asString)
                        .filter(value -> !XML_ANNOTATION_DEFAULT_VALUE.equals(value)),
                annotationValue(fi, XML_ELEMENT_ANNOTATION_CLASS, "name")
                        .map(AnnotationValue::asString)
                        .filter(value -> !XML_ANNOTATION_DEFAULT_VALUE.equals(value)),
                annotation(fi, XML_ELEMENT_REF_ANNOTATION_CLASS)
                        .flatMap(annotation -> getNameFromAnnotationOrRef(annotation, ci, XML_ANNOTATION_DEFAULT_VALUE)))
                .orElseGet(fi::name);
    }
}
