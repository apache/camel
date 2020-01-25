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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.Writer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.ComponentModel.ComponentOptionModel;
import org.apache.camel.tooling.model.ComponentModel.EndpointOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.tools.apt.helper.EndpointHelper;
import org.apache.camel.util.json.Jsoner;

/**
 * Processes all Camel {@link UriEndpoint}s and generate json schema documentation for the endpoint/component.
 */
@SupportedAnnotationTypes({"org.apache.camel.spi.*"})
public class EndpointAnnotationProcessor extends AbstractCamelAnnotationProcessor {

    // CHECKSTYLE:OFF

    private static final String HEADER_FILTER_STRATEGY_JAVADOC = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.";

    @Override
    protected void doProcess(Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(UriEndpoint.class);
        for (Element element : elements) {
            if (element instanceof TypeElement) {
                processEndpointClass(roundEnv, (TypeElement) element);
            }
        }
    }

    private void processEndpointClass(final RoundEnvironment roundEnv, final TypeElement classElement) {
        final UriEndpoint uriEndpoint = classElement.getAnnotation(UriEndpoint.class);
        if (uriEndpoint != null) {
            String scheme = uriEndpoint.scheme();
            String extendsScheme = uriEndpoint.extendsScheme();
            String title = uriEndpoint.title();
            final String label = uriEndpoint.label();
            validateSchemaName(scheme, classElement);
            if (!Strings.isNullOrEmpty(scheme)) {
                // support multiple schemes separated by comma, which maps to the exact same component
                // for example camel-mail has a bunch of component schema names that does that
                String[] schemes = scheme.split(",");
                String[] titles = title.split(",");
                String[] extendsSchemes = extendsScheme.split(",");
                for (int i = 0; i < schemes.length; i++) {
                    final String alias = schemes[i];
                    final String extendsAlias = i < extendsSchemes.length ? extendsSchemes[i] : extendsSchemes[0];
                    String aTitle = i < titles.length ? titles[i] : titles[0];

                    // some components offer a secure alternative which we need to amend the title accordingly
                    if (secureAlias(schemes[0], alias)) {
                        aTitle += " (Secure)";
                    }
                    final String aliasTitle = aTitle;

                    // write json schema
                    String name = Strings.canonicalClassName(classElement.getQualifiedName().toString());
                    String packageName = name.substring(0, name.lastIndexOf("."));
                    String fileName = alias + PackageHelper.JSON_SUFIX;
                    AnnotationProcessorHelper.processFile(processingEnv, packageName, fileName,
                            writer -> writeJSonSchemeAndPropertyConfigurer(writer, roundEnv, classElement, uriEndpoint, aliasTitle, alias, extendsAlias, label, schemes));
                }
            }
        }
    }

    private void validateSchemaName(final String schemaName, final TypeElement classElement) {
        // our schema name has to be in lowercase
        if (!schemaName.equals(schemaName.toLowerCase())) {
            processingEnv.getMessager().printMessage(Kind.WARNING, String.format("Mixed case schema name in '%s' with value '%s' has been deprecated. Please use lowercase only!", classElement.getQualifiedName(), schemaName));
        }
    }

    protected void writeJSonSchemeAndPropertyConfigurer(PrintWriter writer, RoundEnvironment roundEnv, TypeElement classElement, UriEndpoint uriEndpoint,
                                                        String title, String scheme, String extendsScheme, String label, String[] schemes) {
        // gather component information
        ComponentModel componentModel = findComponentProperties(roundEnv, uriEndpoint, classElement, title, scheme, extendsScheme, label, schemes);

        // get endpoint information which is divided into paths and options (though there should really only be one path)
        ComponentModel parentData = null;
        String parentScheme = isNullOrEmpty(extendsScheme) ? null : extendsScheme;
        TypeMirror superclass = classElement.getSuperclass();
        if (superclass != null) {
            String superClassName = Strings.canonicalClassName(superclass.toString());
            TypeElement baseTypeElement = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, superClassName);
            if (baseTypeElement != null && !roundEnv.getRootElements().contains(baseTypeElement)) {
                UriEndpoint parentUriEndpoint = baseTypeElement.getAnnotation(UriEndpoint.class);
                if (parentUriEndpoint != null) {
                    parentScheme = parentUriEndpoint.scheme().split(",")[0];
                }
            }
        }
        if (parentScheme != null) {
            try {
                FileObject res = processingEnv.getFiler().getResource(StandardLocation.CLASS_PATH,
                        "", "META-INF/services/org/apache/camel/component/" + parentScheme);
                String propsStr = res.getCharContent(false).toString();
                Properties props = new Properties();
                props.load(new StringReader(propsStr));
                String clazzName = props.getProperty("class");
                String packageName = clazzName.substring(0, clazzName.lastIndexOf("."));
                res = processingEnv.getFiler().getResource(StandardLocation.CLASS_PATH,
                        packageName, parentScheme + PackageHelper.JSON_SUFIX);
                String json = res.getCharContent(false).toString();
                parentData = JsonMapper.generateComponentModel(json);
            } catch (Exception e) {
                // ignore
                if (!Objects.equals(parentScheme, extendsScheme)) {
                    throw new RuntimeException("Error: " + e.toString(), e);
                }
            }
        }

        // component options
        TypeElement componentClassElement = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, componentModel.getJavaType());
        if (componentClassElement != null) {
            findComponentClassProperties(roundEnv, componentModel, componentClassElement, "", parentData, null, null);
        }

        // endpoint options
        findClassProperties(roundEnv, componentModel, classElement, "", uriEndpoint.excludeProperties(), parentData, null, null);

        // enhance and generate
        enhanceComponentModel(componentModel, parentData, uriEndpoint.excludeProperties());

        // if the component has known class name
        if (!"@@@JAVATYPE@@@".equals(componentModel.getJavaType())) {
            generateComponentConfigurer(roundEnv, uriEndpoint, scheme, schemes, componentModel);
        }

        String json = JsonMapper.createParameterJsonSchema(componentModel);
        writer.println(json);
        generateEndpointConfigurer(roundEnv, classElement, uriEndpoint, scheme, schemes, componentModel);
    }

    private void enhanceComponentModel(ComponentModel componentModel, ComponentModel parentData, String excludeProperties) {
        componentModel.getComponentOptions().removeIf(option -> filterOutOption(componentModel, option));
        componentModel.getComponentOptions().forEach(option -> fixDoc(option, parentData != null ? parentData.getComponentOptions() : null));
        componentModel.getEndpointOptions().removeIf(option -> filterOutOption(componentModel, option));
        componentModel.getEndpointOptions().forEach(option -> fixDoc(option, parentData != null ? parentData.getEndpointOptions() : null));
        componentModel.getEndpointOptions().sort(EndpointHelper.createOverallComparator(componentModel.getSyntax()));
        // merge with parent, removing excluded and overriden properties
        if (parentData != null) {
            Set<String> componentOptionNames = componentModel.getComponentOptions().stream().map(BaseOptionModel::getName).collect(Collectors.toSet());
            Set<String> endpointOptionNames = componentModel.getEndpointOptions().stream().map(BaseOptionModel::getName).collect(Collectors.toSet());
            Collections.addAll(endpointOptionNames, excludeProperties.split(","));
            parentData.getComponentOptions().removeIf(option -> componentOptionNames.contains(option.getName()));
            parentData.getEndpointOptions().removeIf(option -> endpointOptionNames.contains(option.getName()));
            componentModel.getComponentOptions().addAll(parentData.getComponentOptions());
            componentModel.getEndpointOptions().addAll(parentData.getEndpointOptions());
        }

    }

    private void fixDoc(BaseOptionModel option, List<? extends BaseOptionModel> parentOptions) {
        String doc = getDocumentationWithNotes(option);
        if (Strings.isNullOrEmpty(doc) && parentOptions != null) {
            doc = parentOptions.stream()
                    .filter(opt -> Objects.equals(opt.getName(), option.getName()))
                    .map(BaseOptionModel::getDescription)
                    .findFirst().orElse(null);
        }
        // as its json we need to sanitize the docs
        doc = AnnotationProcessorHelper.sanitizeDescription(doc, false);
        option.setDescription(doc);

        if (isNullOrEmpty(doc)) {
            throw new IllegalStateException("Empty doc for option: " + option.getName() + ", parent options:\n" +
                    (parentOptions != null ? Jsoner.serialize(JsonMapper.asJsonObject(parentOptions)) : "<null>"));
        }
    }

    private boolean filterOutOption(ComponentModel component, BaseOptionModel option) {
        String label = option.getLabel();
        if (label != null) {
            return component.isConsumerOnly() && label.contains("producer")
                    || component.isProducerOnly() && label.contains("consumer");
        } else {
            return false;
        }
    }

    public String getDocumentationWithNotes(BaseOptionModel option) {
        StringBuilder sb = new StringBuilder();
        sb.append(option.getDescription());

        if (!Strings.isNullOrEmpty(option.getDefaultValueNote())) {
            sb.append(". Default value notice: ").append(option.getDefaultValueNote());
        }

        if (!Strings.isNullOrEmpty(option.getDeprecationNote())) {
            sb.append(". Deprecation note: ").append(option.getDeprecationNote());
        }

        return sb.toString();
    }

    private void generateComponentConfigurer(RoundEnvironment roundEnv, UriEndpoint uriEndpoint, String scheme, String[] schemes,
                                             ComponentModel componentModel) {
        TypeElement parent;
        if ("activemq".equals(scheme) || "amqp".equals(scheme)) {
            // special for activemq and amqp scheme which should reuse jms
            parent = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, "org.apache.camel.component.jms.JmsComponentConfigurer");
        } else {
            parent = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, "org.apache.camel.spi.GeneratedPropertyConfigurer");
        }
        String fqComponentClassName = componentModel.getJavaType();
        String componentClassName = fqComponentClassName.substring(fqComponentClassName.lastIndexOf('.') + 1);
        String className = componentClassName + "Configurer";
        String packageName = fqComponentClassName.substring(0, fqComponentClassName.lastIndexOf('.'));
        String fqClassName = packageName + "." + className;

        if ("activemq".equals(scheme) || "amqp".equals(scheme)) {
            generateExtendConfigurer(processingEnv, parent, packageName, className, fqClassName, componentModel.getScheme() + "-component");
        } else if (uriEndpoint.generateConfigurer() && !componentModel.getComponentOptions().isEmpty()) {
            // only generate this once for the first scheme
            if (schemes == null || schemes[0].equals(scheme)) {
                generatePropertyConfigurer(processingEnv, parent, packageName, className, fqClassName, componentClassName, componentModel.getScheme() + "-component", componentModel.getComponentOptions());
            }
        }
    }

    private void generateEndpointConfigurer(RoundEnvironment roundEnv,
                                            TypeElement classElement,
                                            UriEndpoint uriEndpoint,
                                            String scheme,
                                            String[] schemes,
                                            ComponentModel componentModel) {
        TypeElement parent;
        if ("activemq".equals(scheme) || "amqp".equals(scheme)) {
            // special for activemq and amqp scheme which should reuse jms
            parent = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, "org.apache.camel.component.jms.JmsEndpointConfigurer");
        } else {
            parent = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, "org.apache.camel.spi.GeneratedPropertyConfigurer");
        }
        String fqEndpointClassName = classElement.getQualifiedName().toString();
        String packageName = fqEndpointClassName.substring(0, fqEndpointClassName.lastIndexOf('.'));
        String endpointClassName = classElement.getSimpleName().toString();
        String className = endpointClassName + "Configurer";
        String fqClassName = packageName + "." + className;

        if ("activemq".equals(scheme) || "amqp".equals(scheme)) {
            generateExtendConfigurer(processingEnv, parent, packageName, className, fqClassName, componentModel.getScheme() + "-endpoint");
        } else if (uriEndpoint.generateConfigurer() && !componentModel.getComponentOptions().isEmpty()) {
            // only generate this once for the first scheme
            if (schemes == null || schemes[0].equals(scheme)) {
                generatePropertyConfigurer(processingEnv, parent, packageName, className, fqClassName, endpointClassName, componentModel.getScheme() + "-endpoint", componentModel.getEndpointParameterOptions());
            }
        }
    }

    protected ComponentModel findComponentProperties(RoundEnvironment roundEnv, UriEndpoint uriEndpoint, TypeElement endpointClassElement,
                                                     String title, String scheme, String extendsScheme, String label, String[] schemes) {
        ComponentModel model = new ComponentModel();
        model.setScheme(scheme);
        model.setExtendsScheme(extendsScheme);
        // alternative schemes
        if (schemes != null && schemes.length > 1) {
            model.setAlternativeSchemes(String.join(",", schemes));
        }
        // if the scheme is an alias then replace the scheme name from the syntax with the alias
        String syntax = scheme + ":" + Strings.after(uriEndpoint.syntax(), ":");
        // alternative syntax is optional
        if (!Strings.isNullOrEmpty(uriEndpoint.alternativeSyntax())) {
            String alternativeSyntax = scheme + ":" + Strings.after(uriEndpoint.alternativeSyntax(), ":");
            model.setAlternativeSyntax(alternativeSyntax);
        }
        model.setSyntax(syntax);
        model.setTitle(title);
        model.setLabel(label);
        model.setConsumerOnly(uriEndpoint.consumerOnly());
        model.setProducerOnly(uriEndpoint.producerOnly());
        model.setLenientProperties(uriEndpoint.lenientProperties());
        model.setAsync(AnnotationProcessorHelper.implementsInterface(processingEnv, roundEnv, endpointClassElement, "org.apache.camel.AsyncEndpoint"));

        // what is the first version this component was added to Apache Camel
        String firstVersion = uriEndpoint.firstVersion();
        if (Strings.isNullOrEmpty(firstVersion) && endpointClassElement.getAnnotation(Metadata.class) != null) {
            // fallback to @Metadata if not from @UriEndpoint
            firstVersion = endpointClassElement.getAnnotation(Metadata.class).firstVersion();
        }
        if (!Strings.isNullOrEmpty(firstVersion)) {
            model.setFirstVersion(firstVersion);
        }

        // get the java type class name via the @Component annotation from its component class
        Set<? extends Element> elements = roundEnv.getElementsAnnotatedWith(Component.class);
        if (elements != null) {
            for (Element e : elements) {
                Component comp = e.getAnnotation(Component.class);
                String[] cschemes = comp.value().split(",");
                if (Arrays.asList(cschemes).contains(scheme) && e.getKind() == ElementKind.CLASS) {
                    TypeElement te = (TypeElement) e;
                    String name = te.getQualifiedName().toString();
                    model.setJavaType(name);
                    break;
                }
            }
        }

        // we can mark a component as deprecated by using the annotation
        boolean deprecated = endpointClassElement.getAnnotation(Deprecated.class) != null;
        model.setDeprecated(deprecated);
        String deprecationNote = null;
        if (endpointClassElement.getAnnotation(Metadata.class) != null) {
            deprecationNote = endpointClassElement.getAnnotation(Metadata.class).deprecationNote();
        }
        model.setDeprecationNote(deprecationNote);

        // these information is not available at compile time and we enrich these later during the camel-package-maven-plugin
        if (model.getJavaType() == null) {
            model.setJavaType("@@@JAVATYPE@@@");
        }
        model.setDescription("@@@DESCRIPTION@@@");
        model.setGroupId("@@@GROUPID@@@");
        model.setArtifactId("@@@ARTIFACTID@@@");
        model.setVersion("@@@VERSIONID@@@");

        // favor to use endpoint class javadoc as description
        Elements elementUtils = processingEnv.getElementUtils();
        TypeElement typeElement = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, endpointClassElement.getQualifiedName().toString());
        if (typeElement != null) {
            String doc = elementUtils.getDocComment(typeElement);
            if (doc != null) {
                // need to sanitize the description first (we only want a summary)
                doc = AnnotationProcessorHelper.sanitizeDescription(doc, true);
                // the javadoc may actually be empty, so only change the doc if we got something
                if (!Strings.isNullOrEmpty(doc)) {
                    model.setDescription(doc);
                }
            }
        }

        return model;
    }

    protected void findComponentClassProperties(RoundEnvironment roundEnv, ComponentModel componentModel,
                                                TypeElement classElement, String prefix,
                                                ComponentModel parentData, String nestedTypeName, String nestedFieldName) {
        Elements elementUtils = processingEnv.getElementUtils();
        while (true) {
            Metadata componentAnnotation = classElement.getAnnotation(Metadata.class);
            if (componentAnnotation != null && Objects.equals("verifiers", componentAnnotation.label())) {
                componentModel.setVerifiers(componentAnnotation.enums());
            }

            List<ExecutableElement> methods = ElementFilter.methodsIn(classElement.getEnclosedElements());
            for (ExecutableElement method : methods) {
                String methodName = method.getSimpleName().toString();
                boolean deprecated = method.getAnnotation(Deprecated.class) != null;
                Metadata metadata = method.getAnnotation(Metadata.class);
                if (metadata != null && metadata.skip()) {
                    continue;
                }

                String deprecationNote = null;
                if (metadata != null) {
                    deprecationNote = metadata.deprecationNote();
                }

                // must be the setter
                boolean isSetter = methodName.startsWith("set") && method.getParameters().size() == 1 & method.getReturnType().getKind().equals(TypeKind.VOID);
                if (!isSetter) {
                    continue;
                }

                // skip unwanted methods as they are inherited from default component and are not intended for end users to configure
                if ("setEndpointClass".equals(methodName) || "setCamelContext".equals(methodName)
                    || "setEndpointHeaderFilterStrategy".equals(methodName) || "setApplicationContext".equals(methodName)) {
                    continue;
                }

                if (isGroovyMetaClassProperty(method)) {
                    continue;
                }

                // we usually favor putting the @Metadata annotation on the field instead of the setter, so try to use it if its there
                String fieldName = methodName.substring(3);
                fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
                VariableElement field = AnnotationProcessorHelper.findFieldElement(classElement, fieldName);
                if (field != null && metadata == null) {
                    metadata = field.getAnnotation(Metadata.class);
                }
                if (metadata != null && metadata.skip()) {
                    continue;
                }

                boolean required = metadata != null && metadata.required();
                String label = metadata != null ? metadata.label() : null;
                boolean secret = metadata != null && metadata.secret();

                // we do not yet have default values / notes / as no annotation support yet
                // String defaultValueNote = param.defaultValueNote();
                Object defaultValue = metadata != null ? metadata.defaultValue() : "";
                String defaultValueNote = null;

                String name = prefix + fieldName;
                String displayName = metadata != null ? metadata.displayName() : null;
                // compute a display name if we don't have anything
                if (Strings.isNullOrEmpty(displayName)) {
                    displayName = Strings.asTitle(name);
                }

                TypeMirror fieldType = method.getParameters().get(0).asType();
                String fieldTypeName = fieldType.toString();
                TypeElement fieldTypeElement = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, fieldTypeName);

                String docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, method, fieldName, name, classElement, false);
                if (Strings.isNullOrEmpty(docComment)) {
                    docComment = metadata != null ? metadata.description() : null;
                }
                if (Strings.isNullOrEmpty(docComment)) {
                    // apt cannot grab javadoc from camel-core, only from annotations
                    if ("setHeaderFilterStrategy".equals(methodName)) {
                        docComment = HEADER_FILTER_STRATEGY_JAVADOC;
                    } else {
                        docComment = "";
                    }
                }

                // gather enums
                List<String> enums = null;

                boolean isEnum;
                if (metadata != null && !Strings.isNullOrEmpty(metadata.enums())) {
                    isEnum = true;
                    String[] values = metadata.enums().split(",");
                    enums = Stream.of(values).map(String::trim).collect(Collectors.toList());
                } else {
                    isEnum = fieldTypeElement != null && fieldTypeElement.getKind() == ElementKind.ENUM;
                    if (isEnum) {
                        TypeElement enumClass = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, fieldTypeElement.asType().toString());
                        if (enumClass != null) {
                            // find all the enum constants which has the possible enum value that can be used
                            enums = ElementFilter.fieldsIn(enumClass.getEnclosedElements()).stream()
                                    .filter(var -> var.getKind() == ElementKind.ENUM_CONSTANT)
                                    .map(Object::toString)
                                    .collect(Collectors.toList());
                        }
                    }
                }

                // the field type may be overloaded by another type
                if (metadata != null && !Strings.isNullOrEmpty(metadata.javaType())) {
                    fieldTypeName = metadata.javaType();
                }

                if (isNullOrEmpty(defaultValue) && "boolean".equals(fieldTypeName)) {
                    defaultValue = false;
                }
                if (isNullOrEmpty(defaultValue)) {
                    defaultValue = "";
                }

                String group = EndpointHelper.labelAsGroupName(label, componentModel.isConsumerOnly(), componentModel.isProducerOnly());
                // filter out consumer/producer only
                boolean accept = true;
                if (componentModel.isConsumerOnly() && "producer".equals(group)) {
                    accept = false;
                } else if (componentModel.isProducerOnly() && "consumer".equals(group)) {
                    accept = false;
                }
                if (accept) {
                    ComponentOptionModel option = new ComponentOptionModel();
                    option.setKind("property");
                    option.setName(name);
                    option.setDisplayName(displayName);
                    option.setType(AnnotationProcessorHelper.getType(fieldTypeName, false));
                    option.setJavaType(fieldTypeName);
                    option.setRequired(required);
                    option.setDefaultValue(defaultValue);
                    option.setDefaultValueNote(defaultValueNote);
                    option.setDescription(docComment.trim());
                    option.setDeprecated(deprecated);
                    option.setDeprecationNote(deprecationNote);
                    option.setSecret(secret);
                    option.setGroup(group);
                    option.setLabel(label);
                    option.setEnums(enums);
                    option.setConfigurationClass(nestedTypeName);
                    option.setConfigurationField(nestedFieldName);
                    if (componentModel.getComponentOptions().stream().noneMatch(opt -> name.equals(opt.getName()))) {
                        componentModel.addComponentOption(option);
                    }
                }
            }

            // check super classes which may also have fields
            TypeElement baseTypeElement = null;
            if (parentData == null) {
                TypeMirror superclass = classElement.getSuperclass();
                if (superclass != null) {
                    String superClassName = Strings.canonicalClassName(superclass.toString());
                    baseTypeElement = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, superClassName);
                }
            }
            if (baseTypeElement != null) {
                classElement = baseTypeElement;
            } else {
                break;
            }
        }
    }

    protected void findClassProperties(RoundEnvironment roundEnv, ComponentModel componentModel,
                                       TypeElement classElement, String prefix, String excludeProperties,
                                       ComponentModel parentData, String nestedTypeName, String nestedFieldName) {
        Elements elementUtils = processingEnv.getElementUtils();
        while (true) {
            List<VariableElement> fieldElements = ElementFilter.fieldsIn(classElement.getEnclosedElements());
            for (VariableElement fieldElement : fieldElements) {

                Metadata metadata = fieldElement.getAnnotation(Metadata.class);
                if (metadata != null && metadata.skip()) {
                    continue;
                }
                boolean deprecated = fieldElement.getAnnotation(Deprecated.class) != null;
                String deprecationNote = null;
                if (metadata != null) {
                    deprecationNote = metadata.deprecationNote();
                }
                Boolean secret = metadata != null ? metadata.secret() : null;

                UriPath path = fieldElement.getAnnotation(UriPath.class);
                String fieldName = fieldElement.getSimpleName().toString();
                if (path != null) {
                    String name = prefix + (Strings.isNullOrEmpty(path.name()) ? fieldName : path.name());

                    // should we exclude the name?
                    if (excludeProperty(excludeProperties, name)) {
                        continue;
                    }

                    Object defaultValue = path.defaultValue();
                    if ("".equals(defaultValue) && metadata != null) {
                        defaultValue = metadata.defaultValue();
                    }
                    boolean required = metadata != null && metadata.required();
                    String label = path.label();
                    if (Strings.isNullOrEmpty(label) && metadata != null) {
                        label = metadata.label();
                    }
                    String displayName = path.displayName();
                    if (Strings.isNullOrEmpty(displayName)) {
                        displayName = metadata != null ? metadata.displayName() : null;
                    }
                    // compute a display name if we don't have anything
                    if (Strings.isNullOrEmpty(displayName)) {
                        displayName = Strings.asTitle(name);
                    }

                    TypeMirror fieldType = fieldElement.asType();
                    String fieldTypeName = fieldType.toString();
                    TypeElement fieldTypeElement = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, fieldTypeName);

                    String docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, fieldElement, fieldName, name, classElement, false);
                    if (Strings.isNullOrEmpty(docComment)) {
                        docComment = path.description();
                    }

                    // gather enums
                    List<String> enums = null;

                    if (!Strings.isNullOrEmpty(path.enums())) {
                        String[] values = path.enums().split(",");
                        enums = Stream.of(values).map(String::trim).collect(Collectors.toList());
                    } else if (fieldTypeElement != null && fieldTypeElement.getKind() == ElementKind.ENUM) {
                        TypeElement enumClass = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, fieldTypeElement.asType().toString());
                        // find all the enum constants which has the possible enum value that can be used
                        if (enumClass != null) {
                            enums = ElementFilter.fieldsIn(enumClass.getEnclosedElements()).stream()
                                    .filter(var -> var.getKind() == ElementKind.ENUM_CONSTANT)
                                    .map(Object::toString)
                                    .collect(Collectors.toList());
                        }
                    }

                    // the field type may be overloaded by another type
                    if (!Strings.isNullOrEmpty(path.javaType())) {
                        fieldTypeName = path.javaType();
                    }
                    if (isNullOrEmpty(defaultValue) && "boolean".equals(fieldTypeName)) {
                        defaultValue = false;
                    }
                    if (isNullOrEmpty(defaultValue)) {
                        defaultValue = "";
                    }

                    boolean isSecret = secret != null && secret || path.secret();
                    String group = EndpointHelper.labelAsGroupName(label, componentModel.isConsumerOnly(), componentModel.isProducerOnly());
                    EndpointOptionModel option = new EndpointOptionModel();
                    option.setKind("path");
                    option.setName(name);
                    option.setDisplayName(displayName);
                    option.setType(AnnotationProcessorHelper.getType(fieldTypeName, false));
                    option.setJavaType(fieldTypeName);
                    option.setRequired(required);
                    option.setDefaultValue(defaultValue);
//                    option.setDefaultValueNote(defaultValueNote);
                    option.setDescription(docComment.trim());
                    option.setDeprecated(deprecated);
                    option.setDeprecationNote(deprecationNote);
                    option.setSecret(isSecret);
                    option.setGroup(group);
                    option.setLabel(label);
                    option.setEnums(enums);
                    option.setConfigurationClass(nestedTypeName);
                    option.setConfigurationField(nestedFieldName);
                    if (componentModel.getEndpointOptions().stream().noneMatch(opt -> name.equals(opt.getName()))) {
                        componentModel.addEndpointOption(option);
                    }
                }

                UriParam param = fieldElement.getAnnotation(UriParam.class);
                fieldName = fieldElement.getSimpleName().toString();
                if (param != null) {
                    String name = prefix + (Strings.isNullOrEmpty(param.name()) ? fieldName : param.name());

                    // should we exclude the name?
                    if (excludeProperty(excludeProperties, name)) {
                        continue;
                    }

                    String paramOptionalPrefix = param.optionalPrefix();
                    String paramPrefix = param.prefix();
                    boolean multiValue = param.multiValue();
                    Object defaultValue = param.defaultValue();
                    if (isNullOrEmpty(defaultValue) && metadata != null) {
                        defaultValue = metadata.defaultValue();
                    }
                    String defaultValueNote = param.defaultValueNote();
                    boolean required = metadata != null && metadata.required();
                    String label = param.label();
                    if (Strings.isNullOrEmpty(label) && metadata != null) {
                        label = metadata.label();
                    }
                    String displayName = param.displayName();
                    if (Strings.isNullOrEmpty(displayName)) {
                        displayName = metadata != null ? metadata.displayName() : null;
                    }
                    // compute a display name if we don't have anything
                    if (Strings.isNullOrEmpty(displayName)) {
                        displayName = Strings.asTitle(name);
                    }

                    // if the field type is a nested parameter then iterate through its fields
                    TypeMirror fieldType = fieldElement.asType();
                    String fieldTypeName = fieldType.toString();
                    TypeElement fieldTypeElement = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, fieldTypeName);
                    UriParams fieldParams = null;
                    if (fieldTypeElement != null) {
                        fieldParams = fieldTypeElement.getAnnotation(UriParams.class);
                    }
                    if (fieldParams != null) {
                        String nestedPrefix = prefix;
                        String extraPrefix = fieldParams.prefix();
                        if (!Strings.isNullOrEmpty(extraPrefix)) {
                            nestedPrefix += extraPrefix;
                        }
                        nestedTypeName = fieldTypeName;
                        nestedFieldName = fieldElement.getSimpleName().toString();
                        findClassProperties(roundEnv, componentModel, fieldTypeElement, nestedPrefix, excludeProperties, null, nestedTypeName, nestedFieldName);
                        nestedTypeName = null;
                        nestedFieldName = null;
                    } else {
                        String docComment = AnnotationProcessorHelper.findJavaDoc(elementUtils, fieldElement, fieldName, name, classElement, false);
                        if (Strings.isNullOrEmpty(docComment)) {
                            docComment = param.description();
                        }
                        if (Strings.isNullOrEmpty(docComment)) {
                            docComment = "";
                        }

                        // gather enums
                        List<String> enums = null;

                        if (!Strings.isNullOrEmpty(param.enums())) {
                            String[] values = param.enums().split(",");
                            enums = Stream.of(values).map(String::trim).collect(Collectors.toList());
                        } else if (fieldTypeElement != null && fieldTypeElement.getKind() == ElementKind.ENUM) {
                            TypeElement enumClass = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, fieldTypeElement.asType().toString());
                            if (enumClass != null) {
                                // find all the enum constants which has the possible enum value that can be used
                                enums = ElementFilter.fieldsIn(enumClass.getEnclosedElements()).stream()
                                        .filter(var -> var.getKind() == ElementKind.ENUM_CONSTANT)
                                        .map(Object::toString)
                                        .collect(Collectors.toList());
                            }
                        }

                        // the field type may be overloaded by another type
                        if (!Strings.isNullOrEmpty(param.javaType())) {
                            fieldTypeName = param.javaType();
                        }

                        if (isNullOrEmpty(defaultValue) && "boolean".equals(fieldTypeName)) {
                            defaultValue = false;
                        }
                        if (isNullOrEmpty(defaultValue)) {
                            defaultValue = "";
                        }

                        boolean isSecret = secret != null && secret || param.secret();
                        String group = EndpointHelper.labelAsGroupName(label, componentModel.isConsumerOnly(), componentModel.isProducerOnly());
                        EndpointOptionModel option = new EndpointOptionModel();
                        option.setKind("parameter");
                        option.setName(name);
                        option.setDisplayName(displayName);
                        option.setType(AnnotationProcessorHelper.getType(fieldTypeName, false));
                        option.setJavaType(fieldTypeName);
                        option.setRequired(required);
                        option.setDefaultValue(defaultValue);
                        option.setDefaultValueNote(defaultValueNote);
                        option.setDescription(docComment.trim());
                        option.setDeprecated(deprecated);
                        option.setDeprecationNote(deprecationNote);
                        option.setSecret(isSecret);
                        option.setGroup(group);
                        option.setLabel(label);
                        option.setEnums(enums);
                        option.setConfigurationClass(nestedTypeName);
                        option.setConfigurationField(nestedFieldName);
                        option.setPrefix(paramPrefix);
                        option.setOptionalPrefix(paramOptionalPrefix);
                        option.setMultiValue(multiValue);
                        if (componentModel.getEndpointOptions().stream().noneMatch(opt -> name.equals(opt.getName()))) {
                            componentModel.addEndpointOption(option);
                        }
                    }
                }
            }

            // check super classes which may also have @UriParam fields
            TypeElement baseTypeElement = null;
            if (parentData == null) {
                TypeMirror superclass = classElement.getSuperclass();
                if (superclass != null) {
                    String superClassName = Strings.canonicalClassName(superclass.toString());
                    baseTypeElement = AnnotationProcessorHelper.findTypeElement(processingEnv, roundEnv, superClassName);
                }
            }
            if (baseTypeElement != null) {
                classElement = baseTypeElement;
            } else {
                break;
            }
        }
    }

    private static boolean isNullOrEmpty(Object value) {
        return value == null || "".equals(value) || "null".equals(value);
    }

    private static boolean excludeProperty(String excludeProperties, String name) {
        String[] excludes = excludeProperties.split(",");
        for (String exclude : excludes) {
            if (name.equals(exclude)) {
                return true;
            }
        }
        return false;
    }

    private static boolean secureAlias(String scheme, String alias) {
        if (scheme.equals(alias)) {
            return false;
        }

        // if alias is like scheme but with ending s its secured
        if ((scheme + "s").equals(alias)) {
            return true;
        }

        return false;
    }

    // CHECKSTYLE:ON

    private static boolean isGroovyMetaClassProperty(final ExecutableElement method) {
        final String methodName = method.getSimpleName().toString();
        
        if (!"setMetaClass".equals(methodName)) {
            return false;
        }

        if (method.getReturnType() instanceof DeclaredType) {
            final DeclaredType returnType = (DeclaredType) method.getReturnType();

            return "groovy.lang.MetaClass".equals(returnType.asElement().getSimpleName().toString());
        } else {
            // Eclipse (Groovy?) compiler returns javax.lang.model.type.NoType, no other way to check but to look at toString output
            return method.toString().contains("(groovy.lang.MetaClass)");
        }
    }

    protected void generateExtendConfigurer(ProcessingEnvironment processingEnv, TypeElement parent,
                                            String pn, String cn, String fqn, String scheme) {

        String pfqn = parent.getQualifiedName().toString();
        String psn = pfqn.substring(pfqn.lastIndexOf('.') + 1);
        try (Writer w = processingEnv.getFiler().createSourceFile(fqn, parent).openWriter()) {
            PropertyConfigurerGenerator.generateExtendConfigurer(pn, cn, pfqn, psn, w);
            generateMetaInfConfigurer(processingEnv, scheme + "-component", fqn);
        } catch (Exception e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to generate source code file: " + fqn + ": " + e.getMessage());
            AnnotationProcessorHelper.dumpExceptionToErrorFile("camel-apt-error.log", "Unable to generate source code file: " + fqn, e);
        }
    }

    protected void generatePropertyConfigurer(ProcessingEnvironment processingEnv, TypeElement parent,
                                              String pn, String cn, String fqn, String en, String scheme,
                                              Collection<? extends BaseOptionModel> options) {

        try (Writer w = processingEnv.getFiler().createSourceFile(fqn, parent).openWriter()) {
            PropertyConfigurerGenerator.generatePropertyConfigurer(pn, cn, en, options, w);
            generateMetaInfConfigurer(processingEnv, scheme + "-component", fqn);
        } catch (Exception e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Unable to generate source code file: " + fqn + ": " + e.getMessage());
            AnnotationProcessorHelper.dumpExceptionToErrorFile("camel-apt-error.log", "Unable to generate source code file: " + fqn, e);
        }
    }

    protected void generateMetaInfConfigurer(ProcessingEnvironment processingEnv, String name, String fqn) {
        try {
            FileObject resource = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "",
                    "META-INF/services/org/apache/camel/configurer/" + name);
            try (Writer w = resource.openWriter()) {
                w.append("# Generated by camel annotation processor\n");
                w.append("class=").append(fqn).append("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
