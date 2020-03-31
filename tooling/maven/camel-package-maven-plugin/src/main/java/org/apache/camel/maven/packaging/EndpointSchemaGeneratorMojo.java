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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.camel.maven.packaging.generics.GenericsUtil;
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
import org.apache.camel.tooling.util.JavadocHelper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.tooling.util.srcgen.GenericType;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ASTNode;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Javadoc;
import org.jboss.forge.roaster.model.JavaDoc;
import org.jboss.forge.roaster.model.JavaDocCapable;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.IndexView;

@Mojo(name = "generate-endpoint-schema", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class EndpointSchemaGeneratorMojo extends AbstractGeneratorMojo {

    public static final DotName URI_ENDPOINT = DotName.createSimple(UriEndpoint.class.getName());
    public static final DotName COMPONENT = DotName.createSimple(Component.class.getName());

    private static final String HEADER_FILTER_STRATEGY_JAVADOC = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.";

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    protected File classesDirectory;
    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File sourcesOutputDir;
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    protected ClassLoader projectClassLoader;
    protected IndexView indexView;
    protected Map<String, String> resources = new HashMap<>();
    protected List<Path> sourceRoots;
    protected Map<String, String> sources = new HashMap<>();
    protected Map<String, JavaClassSource> parsed = new HashMap<>();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (classesDirectory == null) {
            classesDirectory = new File(project.getBuild().getOutputDirectory());
        }
        if (sourcesOutputDir == null) {
            sourcesOutputDir = new File(project.getBasedir(), "src/generated/java");
        }
        if (resourcesOutputDir == null) {
            resourcesOutputDir = new File(project.getBasedir(), "src/generated/resources");
        }
        if (!classesDirectory.isDirectory()) {
            return;
        }

        List<Class<?>> classes = new ArrayList<>();
        for (AnnotationInstance ai : getIndex().getAnnotations(URI_ENDPOINT)) {
            Class<?> classElement = loadClass(ai.target().asClass().name().toString());
            final UriEndpoint uriEndpoint = classElement.getAnnotation(UriEndpoint.class);
            if (uriEndpoint != null) {
                String scheme = uriEndpoint.scheme();
                if (!Strings.isNullOrEmpty(scheme)) {
                    classes.add(classElement);
                }
            }
        }
        // make sure we sort the classes in case one inherit from the other
        classes.sort((c1, c2) -> {
            if (c1.isAssignableFrom(c2)) {
                return -1;
            } else if (c2.isAssignableFrom(c1)) {
                return +1;
            } else {
                return c1.getName().compareTo(c2.getName());
            }
        });
        Map<Class, ComponentModel> models = new HashMap<>();
        for (Class<?> classElement : classes) {
            UriEndpoint uriEndpoint = classElement.getAnnotation(UriEndpoint.class);
            String scheme = uriEndpoint.scheme();
            String extendsScheme = uriEndpoint.extendsScheme();
            String title = uriEndpoint.title();
            final String label = uriEndpoint.label();
            validateSchemaName(scheme, classElement);
            // support multiple schemes separated by comma, which maps to
            // the exact same component
            // for example camel-mail has a bunch of component schema names
            // that does that
            String[] schemes = scheme.split(",");
            String[] titles = title.split(",");
            String[] extendsSchemes = extendsScheme.split(",");
            for (int i = 0; i < schemes.length; i++) {
                final String alias = schemes[i];
                final String extendsAlias = i < extendsSchemes.length ? extendsSchemes[i] : extendsSchemes[0];
                String aTitle = i < titles.length ? titles[i] : titles[0];

                // some components offer a secure alternative which we need
                // to amend the title accordingly
                if (secureAlias(schemes[0], alias)) {
                    aTitle += " (Secure)";
                }
                final String aliasTitle = aTitle;

                ComponentModel parentData = null;
                Class<?> superclass = classElement.getSuperclass();
                if (superclass != null) {
                    parentData = models.get(superclass);
                    if (parentData == null) {
                        UriEndpoint parentUriEndpoint = superclass.getAnnotation(UriEndpoint.class);
                        if (parentUriEndpoint != null) {
                            String parentScheme = parentUriEndpoint.scheme().split(",")[0];
                            String superClassName = superclass.getName();
                            String packageName = superClassName.substring(0, superClassName.lastIndexOf("."));
                            String fileName = packageName.replace('.', '/') + "/" + parentScheme + ".json";
                            String json = loadResource(fileName);
                            parentData = JsonMapper.generateComponentModel(json);
                        }
                    }
                }

                ComponentModel model = writeJSonSchemeAndPropertyConfigurer(classElement, uriEndpoint, aliasTitle, alias,
                        extendsAlias, label, schemes, parentData);
                models.put(classElement, model);
            }
        }
    }

    private void validateSchemaName(final String schemaName, final Class<?> classElement) {
        // our schema name has to be in lowercase
        if (!schemaName.equals(schemaName.toLowerCase())) {
            getLog().warn(String.format("Mixed case schema name in '%s' with value '%s' has been deprecated. Please use lowercase only!",
                    classElement.getName(), schemaName));
        }
    }

    protected ComponentModel writeJSonSchemeAndPropertyConfigurer(Class<?> classElement, UriEndpoint uriEndpoint, String title,
                                                        String scheme, String extendsScheme, String label,
                                                        String[] schemes, ComponentModel parentData) {
        // gather component information
        ComponentModel componentModel = findComponentProperties(uriEndpoint, classElement, title, scheme, extendsScheme, label, schemes);

        // get endpoint information which is divided into paths and options
        // (though there should really only be one path)

        // component options
        Class<?> componentClassElement = loadClass(componentModel.getJavaType());
        if (componentClassElement != null) {
            findComponentClassProperties(componentModel, componentClassElement, "", null, null);
        }

        // endpoint options
        findClassProperties(componentModel, classElement, new HashSet<>(), "", null, null, false);

        String excludedProperties = "";
        Metadata metadata = classElement.getAnnotation(Metadata.class);
        if (metadata != null) {
            excludedProperties = metadata.excludeProperties();
        }
        // enhance and generate
        enhanceComponentModel(componentModel, parentData, excludedProperties);

        // if the component has known class name
        if (!"@@@JAVATYPE@@@".equals(componentModel.getJavaType())) {
            generateComponentConfigurer(uriEndpoint, scheme, schemes, componentModel, parentData);
        }

        String json = JsonMapper.createParameterJsonSchema(componentModel);

        // write json schema
        String name = classElement.getName();
        String packageName = name.substring(0, name.lastIndexOf("."));
        String fileName = scheme + PackageHelper.JSON_SUFIX;

        String file = packageName.replace('.', '/') + "/" + fileName;
        updateResource(resourcesOutputDir.toPath(), file, json);

        generateEndpointConfigurer(classElement, uriEndpoint, scheme, schemes, componentModel, parentData);

        return componentModel;
    }

    protected boolean updateResource(Path dir, String file, String data) {
        resources.put(file, data);
        return super.updateResource(dir, file, data);
    }

    private String loadResource(String fileName) {
        if (resources.containsKey(fileName)) {
            return resources.get(fileName);
        }
        String data;
        try (InputStream is = getProjectClassLoader().getResourceAsStream(fileName)) {
            if (is == null) {
                throw new FileNotFoundException("Resource: " + fileName);
            }
            data = PackageHelper.loadText(is);
        } catch (Exception e) {
            throw new RuntimeException("Error: " + e.toString(), e);
        }
        resources.put(fileName, data);
        return data;
    }

    private void enhanceComponentModel(ComponentModel componentModel, ComponentModel parentData, String excludeProperties) {
        componentModel.getComponentOptions().removeIf(option -> filterOutOption(componentModel, option));
        componentModel.getComponentOptions().forEach(option -> fixDoc(option, parentData != null ? parentData.getComponentOptions() : null));
        componentModel.getComponentOptions().sort(EndpointHelper.createGroupAndLabelComparator());
        componentModel.getEndpointOptions().removeIf(option -> filterOutOption(componentModel, option));
        componentModel.getEndpointOptions().forEach(option -> fixDoc(option, parentData != null ? parentData.getEndpointOptions() : null));
        componentModel.getEndpointOptions().sort(EndpointHelper.createOverallComparator(componentModel.getSyntax()));
        // merge with parent, removing excluded and overriden properties
        if (parentData != null) {
            Set<String> componentOptionNames = componentModel.getComponentOptions().stream().map(BaseOptionModel::getName).collect(Collectors.toSet());
            Set<String> endpointOptionNames = componentModel.getEndpointOptions().stream().map(BaseOptionModel::getName).collect(Collectors.toSet());
            Collections.addAll(endpointOptionNames, excludeProperties.split(","));
            parentData.getComponentOptions().stream()
                    .filter(option -> !componentOptionNames.contains(option.getName()))
                    .forEach(option -> componentModel.getComponentOptions().add(option));
            parentData.getEndpointOptions().stream()
                    .filter(option -> !endpointOptionNames.contains(option.getName()))
                    .forEach(option -> componentModel.getEndpointOptions().add(option));
        }
    }

    private void fixDoc(BaseOptionModel option, List<? extends BaseOptionModel> parentOptions) {
        String doc = getDocumentationWithNotes(option);
        if (Strings.isNullOrEmpty(doc) && parentOptions != null) {
            doc = parentOptions.stream().filter(opt -> Objects.equals(opt.getName(), option.getName())).map(BaseOptionModel::getDescription).findFirst().orElse(null);
        }
        // as its json we need to sanitize the docs
        doc = JavadocHelper.sanitizeDescription(doc, false);
        option.setDescription(doc);

        if (isNullOrEmpty(doc)) {
            throw new IllegalStateException("Empty doc for option: " + option.getName() + ", parent options: "
                    + (parentOptions != null ? Jsoner.serialize(JsonMapper.asJsonObject(parentOptions)) : "<null>"));
        }
    }

    private boolean filterOutOption(ComponentModel component, BaseOptionModel option) {
        String label = option.getLabel();
        if (label != null) {
            return component.isConsumerOnly() && label.contains("producer") || component.isProducerOnly() && label.contains("consumer");
        } else {
            return false;
        }
    }

    public String getDocumentationWithNotes(BaseOptionModel option) {
        StringBuilder sb = new StringBuilder();
        sb.append(option.getDescription());

        if (!Strings.isNullOrEmpty(option.getDefaultValueNote())) {
            if (sb.charAt(sb.length() - 1) != '.') {
                sb.append('.');
            }
            sb.append(" Default value notice: ").append(option.getDefaultValueNote());
        }

        if (!Strings.isNullOrEmpty(option.getDeprecationNote())) {
            if (sb.charAt(sb.length() - 1) != '.') {
                sb.append('.');
            }
            sb.append(" Deprecation note: ").append(option.getDeprecationNote());
        }

        return sb.toString();
    }

    private void generateComponentConfigurer(UriEndpoint uriEndpoint, String scheme, String[] schemes, ComponentModel componentModel, ComponentModel parentData) {
        if (!uriEndpoint.generateConfigurer()) {
            return;
        }
        // only generate this once for the first scheme
        if (schemes != null && !schemes[0].equals(scheme)) {
            return;
        }
        String pfqn;
        boolean hasSuper;
        if (parentData != null
                && loadClass(componentModel.getJavaType()).getSuperclass() == loadClass(parentData.getJavaType())) {
            // special for activemq and amqp scheme which should reuse jms
            pfqn = parentData.getJavaType() + "Configurer";
            hasSuper = true;
        } else {
            pfqn = "org.apache.camel.support.component.PropertyConfigurerSupport";
            hasSuper = false;
            parentData = null;
        }
        String psn = pfqn.substring(pfqn.lastIndexOf('.') + 1);
        String fqComponentClassName = componentModel.getJavaType();
        String componentClassName = fqComponentClassName.substring(fqComponentClassName.lastIndexOf('.') + 1);
        String className = componentClassName + "Configurer";
        String packageName = fqComponentClassName.substring(0, fqComponentClassName.lastIndexOf('.'));
        String fqClassName = packageName + "." + className;

        List<ComponentOptionModel> options;
        if (parentData != null) {
            Set<String> parentOptionsNames = parentData.getComponentOptions().stream()
                    .map(ComponentOptionModel::getName).collect(Collectors.toSet());
            options = componentModel.getComponentOptions().stream().filter(o -> !parentOptionsNames.contains(o.getName()))
                    .collect(Collectors.toList());
        } else {
            options = componentModel.getComponentOptions();
        }
        generatePropertyConfigurer(packageName, className, fqClassName, componentClassName,
                pfqn, psn,
                componentModel.getScheme() + "-component", hasSuper, true,
                options);
    }

    private void generateEndpointConfigurer(Class<?> classElement, UriEndpoint uriEndpoint, String scheme, String[] schemes,
                                            ComponentModel componentModel, ComponentModel parentData) {
        if (!uriEndpoint.generateConfigurer()) {
            return;
        }
        // only generate this once for the first scheme
        if (schemes != null && !schemes[0].equals(scheme)) {
            return;
        }
        String pfqn;
        boolean hasSuper;
        if (parentData != null) {
            try {
                pfqn = classElement.getSuperclass().getName() + "Configurer";
                loadClass(pfqn);
                hasSuper = true;
            } catch (NoClassDefFoundError e) {
                pfqn = "org.apache.camel.support.component.PropertyConfigurerSupport";
                hasSuper = false;
                parentData = null;
            }
        } else {
            pfqn = "org.apache.camel.support.component.PropertyConfigurerSupport";
            hasSuper = false;
        }
        String psn = pfqn.substring(pfqn.lastIndexOf('.') + 1);
        String fqEndpointClassName = classElement.getName();
        String endpointClassName = fqEndpointClassName.substring(fqEndpointClassName.lastIndexOf('.') + 1);
        String className = endpointClassName + "Configurer";
        String packageName = fqEndpointClassName.substring(0, fqEndpointClassName.lastIndexOf('.'));
        String fqClassName = packageName + "." + className;

        List<EndpointOptionModel> options;
        if (parentData != null) {
            Set<String> parentOptionsNames = parentData.getEndpointParameterOptions().stream()
                    .map(EndpointOptionModel::getName).collect(Collectors.toSet());
            options = componentModel.getEndpointParameterOptions().stream().filter(o -> !parentOptionsNames.contains(o.getName()))
                    .collect(Collectors.toList());
        } else {
            options = componentModel.getEndpointParameterOptions();
        }
        generatePropertyConfigurer(packageName, className, fqClassName, endpointClassName,
                pfqn, psn,
                componentModel.getScheme() + "-endpoint", hasSuper, false,
                options);
    }

    protected ComponentModel findComponentProperties(UriEndpoint uriEndpoint, Class<?> endpointClassElement, String title, String scheme,
                                                     String extendsScheme, String label, String[] schemes) {
        ComponentModel model = new ComponentModel();
        model.setScheme(scheme);
        model.setName(scheme);
        model.setExtendsScheme(extendsScheme);
        // alternative schemes
        if (schemes != null && schemes.length > 1) {
            model.setAlternativeSchemes(String.join(",", schemes));
        }
        // if the scheme is an alias then replace the scheme name from the
        // syntax with the alias
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
        model.setAsync(loadClass("org.apache.camel.AsyncEndpoint").isAssignableFrom(endpointClassElement));

        // what is the first version this component was added to Apache Camel
        String firstVersion = uriEndpoint.firstVersion();
        if (Strings.isNullOrEmpty(firstVersion) && endpointClassElement.getAnnotation(Metadata.class) != null) {
            // fallback to @Metadata if not from @UriEndpoint
            firstVersion = endpointClassElement.getAnnotation(Metadata.class).firstVersion();
        }
        if (!Strings.isNullOrEmpty(firstVersion)) {
            model.setFirstVersion(firstVersion);
        }

        // get the java type class name via the @Component annotation from its
        // component class
        for (AnnotationInstance ai : getIndex().getAnnotations(COMPONENT)) {
            String[] cschemes = ai.value().asString().split(",");
            if (Arrays.asList(cschemes).contains(scheme) && ai.target().kind() == AnnotationTarget.Kind.CLASS) {
                String name = ai.target().asClass().name().toString();
                model.setJavaType(name);
                break;
            }
        }

        // we can mark a component as deprecated by using the annotation
        boolean deprecated = endpointClassElement.getAnnotation(Deprecated.class) != null
                                || project.getName().contains("(deprecated)");
        model.setDeprecated(deprecated);
        String deprecationNote = null;
        if (endpointClassElement.getAnnotation(Metadata.class) != null) {
            deprecationNote = endpointClassElement.getAnnotation(Metadata.class).deprecationNote();
        }
        model.setDeprecationNote(deprecationNote);

        // these information is not available at compile time and we enrich
        // these later during the camel-package-maven-plugin
        if (model.getJavaType() == null) {
            throw new IllegalStateException("Could not find component java type");
        }
        model.setDescription(project.getDescription());
        model.setGroupId(project.getGroupId());
        model.setArtifactId(project.getArtifactId());
        model.setVersion(project.getVersion());

        // favor to use endpoint class javadoc as description
        String doc = getDocComment(endpointClassElement);
        if (doc != null) {
            // need to sanitize the description first (we only want a
            // summary)
            doc = JavadocHelper.sanitizeDescription(doc, true);
            // the javadoc may actually be empty, so only change the doc if
            // we got something
            if (!Strings.isNullOrEmpty(doc)) {
                model.setDescription(doc);
            }
        }
        // project.getDescription may fallback and use parent description
        if ("Camel Components".equalsIgnoreCase(model.getDescription()) || Strings.isNullOrEmpty(model.getDescription())) {
            throw new IllegalStateException("Cannot find description to use for component: " + scheme
                    + ". Add <description> to Maven pom.xml or javadoc to the endpoint: " + endpointClassElement);
        }

        return model;
    }

    protected void findComponentClassProperties(ComponentModel componentModel, Class<?> classElement,
                                                String prefix, String nestedTypeName, String nestedFieldName) {
        final Class<?> orgClassElement = classElement;
        while (true) {
            Metadata componentAnnotation = classElement.getAnnotation(Metadata.class);
            if (componentAnnotation != null && Objects.equals("verifiers", componentAnnotation.label())) {
                componentModel.setVerifiers(componentAnnotation.enums());
            }

            List<Method> methods = Stream.of(classElement.getDeclaredMethods()).filter(method -> {
                Metadata metadata = method.getAnnotation(Metadata.class);
                String methodName = method.getName();
                if (metadata != null && metadata.skip()) {
                    return false;
                }
                if (method.isSynthetic() || !Modifier.isPublic(method.getModifiers())) {
                    return false;
                }
                // must be the setter
                boolean isSetter = methodName.startsWith("set")
                        && method.getParameters().length == 1
                        && method.getReturnType() == Void.TYPE;
                if (!isSetter) {
                    return false;
                }

                // skip unwanted methods as they are inherited from default
                // component and are not intended for end users to configure
                if ("setEndpointClass".equals(methodName) || "setCamelContext".equals(methodName)
                        || "setEndpointHeaderFilterStrategy".equals(methodName) || "setApplicationContext".equals(methodName)) {
                    return false;
                }
                if (isGroovyMetaClassProperty(method)) {
                    return false;
                }
                return true;
            }).collect(Collectors.toList());

            // if the component has options with annotations then we only want to generate options that are annotated
            // as ideally components should favour doing this, so we can control what is an option and what is not
            List<Field> fields = Stream.of(classElement.getDeclaredFields()).collect(Collectors.toList());
            boolean annotationBasedOptions =
                    fields.stream().anyMatch(f -> f.getAnnotation(Metadata.class) != null)
                    || methods.stream().anyMatch(m -> m.getAnnotation(Metadata.class) != null);

            for (Method method : methods) {
                String methodName = method.getName();
                Metadata metadata = method.getAnnotation(Metadata.class);
                boolean deprecated = method.getAnnotation(Deprecated.class) != null;
                String deprecationNote = null;
                if (metadata != null) {
                    deprecationNote = metadata.deprecationNote();
                }

                // we usually favor putting the @Metadata annotation on the
                // field instead of the setter, so try to use it if its there
                String fieldName = methodName.substring(3);
                fieldName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
                Field fieldElement;
                try {
                    fieldElement = classElement.getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    fieldElement = null;
                }
                if (fieldElement != null && metadata == null) {
                    metadata = fieldElement.getAnnotation(Metadata.class);
                }
                if (metadata != null && metadata.skip()) {
                    continue;
                }

                // skip methods/fields which has no annotation if we only look for annotation based
                if (annotationBasedOptions && metadata == null) {
                    continue;
                }

                // if the field type is a nested parameter then iterate
                // through its fields
                if (fieldElement != null) {
                    Class<?> fieldTypeElement = fieldElement.getType();
                    String fieldTypeName = getTypeName(GenericsUtil.resolveType(orgClassElement, fieldElement));
                    UriParams fieldParams = fieldTypeElement.getAnnotation(UriParams.class);
                    if (fieldParams != null) {
                        String nestedPrefix = prefix;
                        String extraPrefix = fieldParams.prefix();
                        if (!Strings.isNullOrEmpty(extraPrefix)) {
                            nestedPrefix += extraPrefix;
                        }
                        nestedTypeName = fieldTypeName;
                        nestedFieldName = fieldElement.getName();
                        findClassProperties(componentModel, fieldTypeElement, Collections.EMPTY_SET, nestedPrefix, nestedTypeName, nestedFieldName, true);
                        nestedTypeName = null;
                        nestedFieldName = null;
                        // we also want to include the configuration itself so continue and add ourselves
                    }
                }

                boolean required = metadata != null && metadata.required();
                String label = metadata != null ? metadata.label() : null;
                boolean secret = metadata != null && metadata.secret();

                // we do not yet have default values / notes / as no annotation
                // support yet
                // String defaultValueNote = param.defaultValueNote();
                Object defaultValue = metadata != null ? metadata.defaultValue() : "";
                String defaultValueNote = null;

                String name = prefix + fieldName;
                String displayName = metadata != null ? metadata.displayName() : null;
                // compute a display name if we don't have anything
                if (Strings.isNullOrEmpty(displayName)) {
                    displayName = Strings.asTitle(name);
                }

                Class<?> fieldType = method.getParameters()[0].getType();
                String fieldTypeName = getTypeName(GenericsUtil.resolveParameterTypes(orgClassElement, method)[0]);

                String docComment = findJavaDoc(method, fieldName, name, classElement, false);
                if (Strings.isNullOrEmpty(docComment)) {
                    docComment = metadata != null ? metadata.description() : null;
                }
                if (Strings.isNullOrEmpty(docComment)) {
                    // apt cannot grab javadoc from camel-core, only from
                    // annotations
                    if ("setHeaderFilterStrategy".equals(methodName)) {
                        docComment = HEADER_FILTER_STRATEGY_JAVADOC;
                    } else {
                        docComment = "";
                    }
                }

                // gather enums
                List<String> enums = null;
                if (metadata != null && !Strings.isNullOrEmpty(metadata.enums())) {
                    String[] values = metadata.enums().split(",");
                    enums = Stream.of(values).map(String::trim).collect(Collectors.toList());
                } else if (fieldType.isEnum()) {
                    enums = new ArrayList<>();
                    for (Object val : fieldType.getEnumConstants()) {
                        enums.add(val.toString());
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
                    Optional<ComponentOptionModel> prev = componentModel.getComponentOptions().stream()
                            .filter(opt -> name.equals(opt.getName())).findAny();
                    if (prev.isPresent()) {
                        String prv = prev.get().getJavaType();
                        String cur = fieldTypeName;
                        if (prv.equals("java.lang.String")
                                || prv.equals("java.lang.String[]") && cur.equals("java.util.Collection<java.lang.String>")) {
                            componentModel.getComponentOptions().remove(prev.get());
                        } else {
                            accept = false;
                        }
                    }
                }
                if (accept) {
                    ComponentOptionModel option = new ComponentOptionModel();
                    option.setKind("property");
                    option.setName(name);
                    option.setDisplayName(displayName);
                    option.setType(getType(fieldTypeName, false));
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
                    componentModel.addComponentOption(option);
                }
            }

            // check super classes which may also have fields
            Class<?> superclass = classElement.getSuperclass();
            if (superclass != null && superclass != Object.class) {
                classElement = superclass;
            } else {
                break;
            }
        }
    }

    protected void findClassProperties(ComponentModel componentModel, Class<?> classElement,
                                       Set<String> excludes, String prefix,
                                       String nestedTypeName, String nestedFieldName, boolean componentOption) {
        final Class<?> orgClassElement = classElement;
        excludes = new HashSet<>(excludes);
        while (true) {
            String excludedProperties = "";
            Metadata metadata = classElement.getAnnotation(Metadata.class);
            if (metadata != null) {
                excludedProperties = metadata.excludeProperties();
            }

            final UriEndpoint uriEndpoint = classElement.getAnnotation(UriEndpoint.class);
            if (uriEndpoint != null) {
                Collections.addAll(excludes, excludedProperties.split(","));
            }
            for (Field fieldElement : classElement.getDeclaredFields()) {

                metadata = fieldElement.getAnnotation(Metadata.class);
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
                String fieldName = fieldElement.getName();
                // component options should not include @UriPath as they are for endpoints only
                if (!componentOption && path != null) {
                    String name = prefix + (Strings.isNullOrEmpty(path.name()) ? fieldName : path.name());

                    // should we exclude the name?
                    if (excludes.contains(name)) {
                        continue;
                    }

                    Object defaultValue = path.defaultValue();
                    if ("".equals(defaultValue) && metadata != null) {
                        defaultValue = metadata.defaultValue();
                    }
                    String defaultValueNote = path.defaultValueNote();
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

                    Class<?> fieldTypeElement = fieldElement.getType();
                    String fieldTypeName = getTypeName(GenericsUtil.resolveType(orgClassElement, fieldElement));

                    String docComment = path.description();
                    if (Strings.isNullOrEmpty(docComment)) {
                        docComment = findJavaDoc(fieldElement, fieldName, name, classElement, false);
                    }

                    // gather enums
                    List<String> enums = null;

                    if (!Strings.isNullOrEmpty(path.enums())) {
                        String[] values = path.enums().split(",");
                        enums = Stream.of(values).map(String::trim).collect(Collectors.toList());
                    } else if (fieldTypeElement.isEnum()) {
                        enums = new ArrayList<>();
                        for (Object val : fieldTypeElement.getEnumConstants()) {
                            enums.add(val.toString());
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
                        defaultValue = null;
                    }

                    boolean isSecret = secret != null && secret || path.secret();
                    String group = EndpointHelper.labelAsGroupName(label, componentModel.isConsumerOnly(), componentModel.isProducerOnly());
                    BaseOptionModel option;
                    if (componentOption) {
                        option = new ComponentOptionModel();
                    } else {
                        option = new EndpointOptionModel();
                    }
                    option.setName(name);
                    option.setKind("path");
                    option.setDisplayName(displayName);
                    option.setType(getType(fieldTypeName, false));
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
                    if (componentModel.getEndpointOptions().stream().noneMatch(opt -> name.equals(opt.getName()))) {
                        componentModel.addEndpointOption((EndpointOptionModel) option);
                    }
                }

                UriParam param = fieldElement.getAnnotation(UriParam.class);
                fieldName = fieldElement.getName();
                if (param != null) {
                    String name = prefix + (Strings.isNullOrEmpty(param.name()) ? fieldName : param.name());

                    // should we exclude the name?
                    if (excludes.contains(name)) {
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

                    // if the field type is a nested parameter then iterate
                    // through its fields
                    Class<?> fieldTypeElement = fieldElement.getType();
                    String fieldTypeName = getTypeName(GenericsUtil.resolveType(orgClassElement, fieldElement));
                    UriParams fieldParams = fieldTypeElement.getAnnotation(UriParams.class);
                    if (fieldParams != null) {
                        String nestedPrefix = prefix;
                        String extraPrefix = fieldParams.prefix();
                        if (!Strings.isNullOrEmpty(extraPrefix)) {
                            nestedPrefix += extraPrefix;
                        }
                        nestedTypeName = fieldTypeName;
                        nestedFieldName = fieldElement.getName();
                        findClassProperties(componentModel, fieldTypeElement, excludes, nestedPrefix, nestedTypeName, nestedFieldName, componentOption);
                        nestedTypeName = null;
                        nestedFieldName = null;
                    } else {
                        String docComment = param.description();
                        if (Strings.isNullOrEmpty(docComment)) {
                            docComment = findJavaDoc(fieldElement, fieldName, name, classElement, false);
                        }
                        if (Strings.isNullOrEmpty(docComment)) {
                            docComment = "";
                        }

                        // gather enums
                        List<String> enums = null;

                        if (!Strings.isNullOrEmpty(param.enums())) {
                            String[] values = param.enums().split(",");
                            enums = Stream.of(values).map(String::trim).collect(Collectors.toList());
                        } else if (fieldTypeElement.isEnum()) {
                            enums = new ArrayList<>();
                            for (Object val : fieldTypeElement.getEnumConstants()) {
                                enums.add(val.toString());
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
                        BaseOptionModel option;
                        if (componentOption) {
                            option = new ComponentOptionModel();
                        } else {
                            option = new EndpointOptionModel();
                        }
                        option.setName(name);
                        option.setDisplayName(displayName);
                        option.setType(getType(fieldTypeName, false));
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
                        if (componentOption) {
                            option.setKind("property");
                            componentModel.addComponentOption((ComponentOptionModel) option);
                        } else {
                            option.setKind("parameter");
                            if (componentModel.getEndpointOptions().stream().noneMatch(opt -> name.equals(opt.getName()))) {
                                componentModel.addEndpointOption((EndpointOptionModel) option);
                            }
                        }
                    }
                }
            }

            // check super classes which may also have fields
            Class<?> superclass = classElement.getSuperclass();
            if (superclass != null) {
                classElement = superclass;
            } else {
                break;
            }
        }
    }

    private static boolean isNullOrEmpty(Object value) {
        return value == null || "".equals(value) || "null".equals(value);
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

    private static boolean isGroovyMetaClassProperty(final Method method) {
        final String methodName = method.getName();

        if (!"setMetaClass".equals(methodName)) {
            return false;
        }

        return "groovy.lang.MetaClass".equals(method.getReturnType().getName());
    }

    protected void generatePropertyConfigurer(String pn, String cn, String fqn, String en,
                                              String pfqn, String psn, String scheme, boolean hasSuper, boolean component,
                                              Collection<? extends BaseOptionModel> options) {

        try (Writer w = new StringWriter()) {
            PropertyConfigurerGenerator.generatePropertyConfigurer(pn, cn, en, pfqn, psn, hasSuper, component, options, w);
            updateResource(sourcesOutputDir.toPath(), fqn.replace('.', '/') + ".java", w.toString());
        } catch (Exception e) {
            throw new RuntimeException("Unable to generate source code file: " + fqn + ": " + e.getMessage(), e);
        }
        generateMetaInfConfigurer(scheme, fqn);
    }

    protected void generateMetaInfConfigurer(String name, String fqn) {
        try (Writer w = new StringWriter()) {
            w.append("# " + GENERATED_MSG + "\n");
            w.append("class=").append(fqn).append("\n");
            updateResource(resourcesOutputDir.toPath(), "META-INF/services/org/apache/camel/configurer/" + name, w.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Class<?> loadClass(String name) {
        try {
            return getProjectClassLoader().loadClass(name);
        } catch (ClassNotFoundException e) {
            throw (NoClassDefFoundError) new NoClassDefFoundError(name).initCause(e);
        }
    }

    private ClassLoader getProjectClassLoader() {
        if (projectClassLoader == null) {
            try {
                projectClassLoader = DynamicClassLoader.createDynamicClassLoader(project.getCompileClasspathElements());
            } catch (DependencyResolutionRequiredException e) {
                throw new RuntimeException("Unable to create project classloader", e);
            }
        }
        return projectClassLoader;
    }

    private IndexView getIndex() {
        if (indexView == null) {
            Path output = Paths.get(project.getBuild().getOutputDirectory());
            try (InputStream is = Files.newInputStream(output.resolve("META-INF/jandex.idx"))) {
                indexView = new IndexReader(is).read();
            } catch (IOException e) {
                throw new RuntimeException("IOException: " + e.getMessage(), e);
            }
        }
        return indexView;
    }

    private String findJavaDoc(AnnotatedElement member, String fieldName, String name, Class<?> classElement, boolean builderPattern) {
        if (member instanceof Method) {
            try {
                Field field = classElement.getDeclaredField(fieldName);
                Metadata md = field.getAnnotation(Metadata.class);
                if (md != null) {
                    String doc = md.description();
                    if (!Strings.isNullOrEmpty(doc)) {
                        return doc;
                    }
                }
            } catch (Exception e) {
                // ignore
            }
        }
        if (member != null) {
            Metadata md = member.getAnnotation(Metadata.class);
            if (md != null) {
                String doc = md.description();
                if (!Strings.isNullOrEmpty(doc)) {
                    return doc;
                }
            }
        }

        JavaClassSource source;
        try {
            source = javaClassSource(classElement.getName());
            if (source == null) {
                return "";
            }
        } catch (Exception e) {
            return "";
        }
        FieldSource<JavaClassSource> field = source.getField(fieldName);
        if (field != null) {
            String doc = getJavaDocText(loadJavaSource(classElement.getName()), field);
            if (!Strings.isNullOrEmpty(doc)) {
                return doc;
            }
        }

        String setterName = "set" + Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (MethodSource<JavaClassSource> setter : source.getMethods()) {
            if (setter.getParameters().size() == 1
                    && setter.getName().equals(setterName)) {
                String doc = getJavaDocText(loadJavaSource(classElement.getName()), setter);
                if (!Strings.isNullOrEmpty(doc)) {
                    return doc;
                }
            }
        }

        String propName = Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        for (MethodSource<JavaClassSource> getter : source.getMethods()) {
            if (getter.getParameters().size() == 0
                    && (getter.getName().equals("get" + propName) || getter.getName().equals("is" + propName))) {
                String doc = getJavaDocText(loadJavaSource(classElement.getName()), getter);
                if (!Strings.isNullOrEmpty(doc)) {
                    return doc;
                }
            }
        }

        if (builderPattern) {
            if (name != null && !name.equals(fieldName)) {
                for (MethodSource<JavaClassSource> builder : source.getMethods()) {
                    if (builder.getParameters().size() == 1 && builder.getName().equals(name)) {
                        String doc = getJavaDocText(loadJavaSource(classElement.getName()), builder);
                        if (!Strings.isNullOrEmpty(doc)) {
                            return doc;
                        }
                    }
                }
                for (MethodSource<JavaClassSource> builder : source.getMethods()) {
                    if (builder.getParameters().size() == 0 && builder.getName().equals(name)) {
                        String doc = getJavaDocText(loadJavaSource(classElement.getName()), builder);
                        if (!Strings.isNullOrEmpty(doc)) {
                            return doc;
                        }
                    }
                }
            }
            for (MethodSource<JavaClassSource> builder : source.getMethods()) {
                if (builder.getParameters().size() == 1 && builder.getName().equals(fieldName)) {
                    String doc = getJavaDocText(loadJavaSource(classElement.getName()), builder);
                    if (!Strings.isNullOrEmpty(doc)) {
                        return doc;
                    }
                }
            }
            for (MethodSource<JavaClassSource> builder : source.getMethods()) {
                if (builder.getParameters().size() == 0 && builder.getName().equals(fieldName)) {
                    String doc = getJavaDocText(loadJavaSource(classElement.getName()), builder);
                    if (!Strings.isNullOrEmpty(doc)) {
                        return doc;
                    }
                }
            }
        }

        return "";
    }

    static String getJavaDocText(String source, JavaDocCapable<?> member) {
        if (member == null) {
            return null;
        }
        JavaDoc<?> javaDoc = member.getJavaDoc();
        Javadoc jd = (Javadoc) javaDoc.getInternal();
        if (source != null && jd.tags().size() > 0) {
            ASTNode n = (ASTNode) jd.tags().get(0);
            String txt = source.substring(n.getStartPosition(), n.getStartPosition() + n.getLength());
            return txt
                    .replaceAll(" *\n *\\* *\n", "\n\n")
                    .replaceAll(" *\n *\\* +", "\n");
        }
        return null;
    }

    private String getDocComment(Class<?> classElement) {
        JavaClassSource source = javaClassSource(classElement.getName());
        return getJavaDocText(loadJavaSource(classElement.getName()), source);
    }

    private JavaClassSource javaClassSource(String className) {
        return parsed.computeIfAbsent(className, this::doParseJavaClassSource);
    }

    private List<Path> getSourceRoots() {
        if (sourceRoots == null) {
            sourceRoots = project.getCompileSourceRoots().stream()
                    .map(Paths::get)
                    .collect(Collectors.toList());
        }
        return sourceRoots;
    }

    private JavaClassSource doParseJavaClassSource(String className) {
        try {
            String source = loadJavaSource(className);
            if (source != null) {
                return (JavaClassSource) Roaster.parse(source);
            } else {
                return null;
            }
        } catch (Exception e) {
            throw new RuntimeException("Unable to parse java class " + className, e);
        }
    }

    private String loadJavaSource(String className) {
        return sources.computeIfAbsent(className, this::doLoadJavaSource);
    }

    private String doLoadJavaSource(String className) {
        try {
            Path file = getSourceRoots().stream()
                    .map(d -> d.resolve(className.replace('.', '/') + ".java"))
                    .filter(Files::isRegularFile)
                    .findFirst()
                    .orElse(null);

            // skip default from camel project itself as 3rd party cannot load source from core/camel-core
            if (file == null && className.startsWith("org.apache.camel.support.")) {
                return null;
            }

            if (file == null) {
                throw new FileNotFoundException("Unable to find source for " + className);
            }
            return PackageHelper.loadText(file);
        } catch (IOException e) {
            String classpath;
            try {
                classpath = project.getCompileClasspathElements().toString();
            } catch (Exception e2) {
                classpath = e2.toString();
            }
            throw new RuntimeException("Unable to load source for class " + className + " in folders " + getSourceRoots()
                    + " (classpath: " + classpath + ")");
        }
    }

    private static String getTypeName(Type fieldType) {
        String fieldTypeName = new GenericType(fieldType).toString();
        fieldTypeName = fieldTypeName.replace('$', '.');
        return fieldTypeName;
    }

    /**
     * Gets the JSon schema type.
     *
     * @param type the java type
     * @return the json schema type, is never null, but returns <tt>object</tt>
     *         as the generic type
     */
    public static String getType(String type, boolean enumType) {
        if (enumType) {
            return "enum";
        } else if (type == null) {
            // return generic type for unknown type
            return "object";
        } else if (type.equals(URI.class.getName()) || type.equals(URL.class.getName())) {
            return "string";
        } else if (type.equals(File.class.getName())) {
            return "string";
        } else if (type.equals(Date.class.getName())) {
            return "string";
        } else if (type.startsWith("java.lang.Class")) {
            return "string";
        } else if (type.startsWith("java.util.List") || type.startsWith("java.util.Collection")) {
            return "array";
        }

        String primitive = getPrimitiveType(type);
        if (primitive != null) {
            return primitive;
        }

        return "object";
    }

    /**
     * Gets the JSon schema primitive type.
     *
     * @param name the java type
     * @return the json schema primitive type, or <tt>null</tt> if not a
     *         primitive
     */
    public static String getPrimitiveType(String name) {
        // special for byte[] or Object[] as its common to use
        if ("java.lang.byte[]".equals(name) || "byte[]".equals(name)) {
            return "string";
        } else if ("java.lang.Byte[]".equals(name) || "Byte[]".equals(name)) {
            return "array";
        } else if ("java.lang.Object[]".equals(name) || "Object[]".equals(name)) {
            return "array";
        } else if ("java.lang.String[]".equals(name) || "String[]".equals(name)) {
            return "array";
        } else if ("java.lang.Character".equals(name) || "Character".equals(name) || "char".equals(name)) {
            return "string";
        } else if ("java.lang.String".equals(name) || "String".equals(name)) {
            return "string";
        } else if ("java.lang.Boolean".equals(name) || "Boolean".equals(name) || "boolean".equals(name)) {
            return "boolean";
        } else if ("java.lang.Integer".equals(name) || "Integer".equals(name) || "int".equals(name)) {
            return "integer";
        } else if ("java.lang.Long".equals(name) || "Long".equals(name) || "long".equals(name)) {
            return "integer";
        } else if ("java.lang.Short".equals(name) || "Short".equals(name) || "short".equals(name)) {
            return "integer";
        } else if ("java.lang.Byte".equals(name) || "Byte".equals(name) || "byte".equals(name)) {
            return "integer";
        } else if ("java.lang.Float".equals(name) || "Float".equals(name) || "float".equals(name)) {
            return "number";
        } else if ("java.lang.Double".equals(name) || "Double".equals(name) || "double".equals(name)) {
            return "number";
        }

        return null;
    }

}
