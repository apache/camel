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
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.camel.Category;
import org.apache.camel.maven.packaging.generics.ClassUtil;
import org.apache.camel.maven.packaging.generics.GenericsUtil;
import org.apache.camel.maven.packaging.generics.PackagePluginUtils;
import org.apache.camel.spi.ApiMethod;
import org.apache.camel.spi.ApiParam;
import org.apache.camel.spi.ApiParams;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.tooling.model.ApiMethodModel;
import org.apache.camel.tooling.model.ApiModel;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.ComponentModel.ComponentOptionModel;
import org.apache.camel.tooling.model.ComponentModel.EndpointHeaderModel;
import org.apache.camel.tooling.model.ComponentModel.EndpointOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.SupportLevel;
import org.apache.camel.tooling.util.JavadocHelper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.tooling.util.srcgen.GenericType;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ASTNode;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Javadoc;
import org.jboss.forge.roaster.model.JavaDoc;
import org.jboss.forge.roaster.model.JavaDocCapable;
import org.jboss.forge.roaster.model.source.FieldHolderSource;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.DotName;
import org.jboss.jandex.IndexView;

import static java.lang.reflect.Modifier.isStatic;
import static org.apache.camel.tooling.model.ComponentModel.ApiOptionModel;

@Mojo(name = "generate-endpoint-schema", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
      defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class EndpointSchemaGeneratorMojo extends AbstractGeneratorMojo {

    public static final DotName URI_ENDPOINT = DotName.createSimple(UriEndpoint.class.getName());
    public static final DotName COMPONENT = DotName.createSimple(Component.class.getName());
    public static final DotName API_PARAMS = DotName.createSimple(ApiParams.class.getName());

    private static final String HEADER_FILTER_STRATEGY_JAVADOC
            = "To use a custom HeaderFilterStrategy to filter header to and from Camel message.";

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    protected File classesDirectory;
    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File sourcesOutputDir;
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    protected IndexView indexView;
    protected Map<String, String> resources = new HashMap<>();
    protected List<Path> sourceRoots;
    protected Map<String, String> sources = new HashMap<>();
    protected Map<String, JavaSource<?>> parsed = new HashMap<>();

    @Inject
    public EndpointSchemaGeneratorMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    // for testing purposes
    EndpointSchemaGeneratorMojo() {
        this(null, null);
    }

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

        executeUriEndpoint();
    }

    private void executeUriEndpoint() {
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
        classes.sort(this::compareClasses);

        Map<Class<?>, ComponentModel> models = new HashMap<>();
        for (Class<?> classElement : classes) {
            UriEndpoint uriEndpoint = classElement.getAnnotation(UriEndpoint.class);
            String scheme = uriEndpoint.scheme();
            String extendsScheme = uriEndpoint.extendsScheme();
            String title = uriEndpoint.title();
            Category[] categories = uriEndpoint.category();
            String label = null;
            if (categories.length > 0) {
                label = Arrays.stream(categories)
                        .map(Category::getValue)
                        .collect(Collectors.joining(","));
            }
            validateSchemaName(scheme, classElement);
            // support multiple schemes separated by comma, which maps to
            // the exact same component
            // for example camel-mail has a bunch of component schema names
            // that does that
            String[] schemes = scheme.split(",");
            String[] titles = title.split(",");
            String[] extendsSchemes = extendsScheme.split(",");

            processSchemas(models, classElement, uriEndpoint, label, schemes, titles, extendsSchemes);
        }
    }

    private void processSchemas(
            Map<Class<?>, ComponentModel> models, Class<?> classElement, UriEndpoint uriEndpoint, String label,
            String[] schemes,
            String[] titles, String[] extendsSchemes) {
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

            ComponentModel parentData = collectParentData(models, classElement);

            ComponentModel model = writeJSonSchemeAndPropertyConfigurer(classElement, uriEndpoint, aliasTitle, alias,
                    extendsAlias, label, schemes, parentData);

            models.put(classElement, model);
        }
    }

    private ComponentModel collectParentData(Map<Class<?>, ComponentModel> models, Class<?> classElement) {
        ComponentModel parentData = null;
        final Class<?> superclass = classElement.getSuperclass();

        if (superclass != null) {
            parentData = models.get(superclass);
            if (parentData == null) {
                UriEndpoint parentUriEndpoint = superclass.getAnnotation(UriEndpoint.class);
                if (parentUriEndpoint != null) {
                    String parentScheme = parentUriEndpoint.scheme().split(",")[0];
                    String superClassName = superclass.getName();
                    String packageName = superClassName.substring(0, superClassName.lastIndexOf('.'));
                    String fileName
                            = "META-INF/" + packageName.replace('.', '/') + "/" + parentScheme + PackageHelper.JSON_SUFIX;
                    String json = loadResource(fileName);
                    parentData = JsonMapper.generateComponentModel(json);
                }
            }
        }

        return parentData;
    }

    private int compareClasses(Class<?> c1, Class<?> c2) {
        if (c1.isAssignableFrom(c2)) {
            return -1;
        } else if (c2.isAssignableFrom(c1)) {
            return +1;
        } else {
            return c1.getName().compareTo(c2.getName());
        }
    }

    private void validateSchemaName(final String schemaName, final Class<?> classElement) {
        // our schema name has to be in lowercase
        if (!schemaName.equals(schemaName.toLowerCase())) {
            getLog().warn(String.format(
                    "Mixed case schema name in '%s' with value '%s' has been deprecated. Please use lowercase only!",
                    classElement.getName(), schemaName));
        }
    }

    protected ComponentModel writeJSonSchemeAndPropertyConfigurer(
            Class<?> classElement, UriEndpoint uriEndpoint, String title,
            String scheme, String extendsScheme, String label,
            String[] schemes, ComponentModel parentData) {
        // gather component information
        ComponentModel componentModel
                = findComponentProperties(uriEndpoint, classElement, title, scheme, extendsScheme, label, schemes);

        // get endpoint information which is divided into paths and options
        // (though there should really only be one path)

        // component options
        Class<?> componentClassElement = loadClass(componentModel.getJavaType());
        String excludedComponentProperties = "";
        if (componentClassElement != null) {
            findComponentClassProperties(componentModel, componentClassElement, "", null, null);
            Metadata componentMetadata = componentClassElement.getAnnotation(Metadata.class);
            if (componentMetadata != null) {
                excludedComponentProperties = componentMetadata.excludeProperties();
            }
        }

        // component headers
        addEndpointHeaders(componentModel, uriEndpoint, scheme);

        // endpoint options
        findClassProperties(componentModel, classElement, new HashSet<>(), "", null, null, false);

        String excludedEndpointProperties = getExcludedEnd(classElement.getAnnotation(Metadata.class));

        // enhance and generate
        enhanceComponentModel(componentModel, parentData, excludedEndpointProperties, excludedComponentProperties);

        // if the component has known class name
        if (!"@@@JAVATYPE@@@".equals(componentModel.getJavaType())) {
            generateComponentConfigurer(uriEndpoint, scheme, schemes, componentModel, parentData);
        }

        // enrich the component model with additional configurations for api components
        if (componentModel.isApi()) {
            enhanceComponentModelWithApiModel(componentModel);
        }

        SchemaHelper.addModelMetadata(componentModel, project);
        SchemaHelper.addModelMetadata(componentModel, classElement.getAnnotation(Metadata.class));

        String json = JsonMapper.createParameterJsonSchema(componentModel);

        // write json schema
        String name = classElement.getName();
        String packageName = name.substring(0, name.lastIndexOf('.'));
        String fileName = scheme + PackageHelper.JSON_SUFIX;

        String file = "META-INF/" + packageName.replace('.', '/') + "/" + fileName;
        updateResource(resourcesOutputDir.toPath(), file, json);

        generateEndpointConfigurer(classElement, uriEndpoint, scheme, schemes, componentModel, parentData);

        return componentModel;
    }

    /**
     * Retrieve the metadata added to all the {@code String} constants defined in the class corresponding to the element
     * {@code headersClass} of the annotation {@code UriEndpoint} along with all its super classes and implemented
     * interfaces, convert the metadata found into instances of {@link EndpointHeaderModel} and finally add the
     * instances of {@link EndpointHeaderModel} to the given component model.
     * <p/>
     * Only headers applicable for the given scheme are added.
     *
     * @param componentModel the component model to which the headers should be added.
     * @param uriEndpoint    the annotation from which the headers class is retrieved.
     * @param scheme         the scheme for which we want to add the headers.
     */
    void addEndpointHeaders(ComponentModel componentModel, UriEndpoint uriEndpoint, String scheme) {
        final Class<?> headersClass = uriEndpoint.headersClass();
        if (headersClass == void.class) {
            getLog().debug(String.format("The endpoint %s has not defined any headers class", uriEndpoint.scheme()));
            return;
        }
        if (!addEndpointHeaders(componentModel, scheme, headersClass, uriEndpoint.headersNameProvider())) {
            getLog().debug(String.format("No headers have been detected in the headers class %s", headersClass.getName()));
        }
    }

    /**
     * Retrieve the metadata added to all the {@code String} constants defined in the given headers class, convert the
     * metadata found into instances of {@link EndpointHeaderModel} and finally add the instances of
     * {@link EndpointHeaderModel} to the given component model.
     * <p/>
     * Only headers applicable for the given scheme are added.
     *
     * @param  componentModel      the component model to which the headers should be added.
     * @param  scheme              the scheme for which we want to add the headers.
     * @param  headersClass        the class from which we extract the headers.
     * @param  headersNameProvider the name of the field to get or the name of the method to invoke to get the name of
     *                             the headers.
     * @return                     {@code true} if at least one header has been added, {@code false} otherwise.
     */
    private boolean addEndpointHeaders(
            ComponentModel componentModel, String scheme, Class<?> headersClass, String headersNameProvider) {
        final boolean isEnum = headersClass.isEnum();
        boolean foundHeader = false;
        for (Field field : headersClass.getFields()) {
            if ((isEnum || isStatic(field.getModifiers()) && field.getType() == String.class)
                    && field.isAnnotationPresent(Metadata.class)) {

                if (getLog().isDebugEnabled()) {
                    getLog().debug(
                            String.format("Trying to add the constant %s in the class %s as header.", field.getName(),
                                    headersClass.getName()));
                }
                if (addEndpointHeader(componentModel, scheme, field, headersNameProvider)) {
                    foundHeader = true;
                    continue;
                }
            }

            if (getLog().isDebugEnabled()) {
                getLog().debug(
                        String.format(
                                "The field %s of the class %s is not considered as a name of a header, thus it is skipped",
                                field.getName(), headersClass.getName()));
            }
        }
        return foundHeader;
    }

    /**
     * Retrieve the metadata added to the given field, convert the metadata found into an instance of
     * {@link EndpointHeaderModel} and finally add the instance of {@link EndpointHeaderModel} to the given component
     * model.
     * <p/>
     * The header is only added if it is applicable for the given scheme.
     *
     * @param  componentModel      the component to which the header should be added.
     * @param  scheme              the scheme for which we want to add the header.
     * @param  field               the field corresponding to the constant from which the metadata should be extracted.
     * @param  headersNameProvider the name of the field to get or the name of the method to invoke to get the name of
     *                             the headers.
     * @return                     {@code true} if the header has been added, {@code false} otherwise.
     */
    private boolean addEndpointHeader(ComponentModel componentModel, String scheme, Field field, String headersNameProvider) {
        final Metadata metadata = field.getAnnotation(Metadata.class);
        if (metadata == null) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(String.format("The field %s in class %s has no Metadata", field.getName(),
                        field.getDeclaringClass().getName()));
            }
            return false;
        }
        final String[] applicableFor = metadata.applicableFor();
        if (applicableFor.length > 0 && Arrays.stream(applicableFor).noneMatch(s -> s.equals(scheme))) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(String.format("The field %s in class %s is not applicable for %s", field.getName(),
                        field.getDeclaringClass().getName(), scheme));
            }
            return false;
        }
        final EndpointHeaderModel header = new EndpointHeaderModel();
        String description = metadata.description().trim();
        if (description.isEmpty()) {
            description = getHeaderFieldJavadoc(field);
        }
        header.setDescription(description);
        header.setKind("header");
        header.setDisplayName(metadata.displayName());
        header.setJavaType(metadata.javaType());
        header.setRequired(metadata.required());
        header.setDefaultValue(metadata.defaultValue());
        header.setDeprecated(field.isAnnotationPresent(Deprecated.class));
        header.setDeprecationNote(metadata.deprecationNote());
        header.setSecret(metadata.secret());
        header.setGroup(EndpointHelper.labelAsGroupName(metadata.label(), componentModel.isConsumerOnly(),
                componentModel.isProducerOnly()));
        header.setLabel(metadata.label());
        header.setImportant(metadata.important());
        try {
            header.setEnums(getEnums(metadata, header.getJavaType().isEmpty() ? null : loadClass(header.getJavaType())));
        } catch (NoClassDefFoundError e) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(String.format("The java type %s could not be found", header.getJavaType()), e);
            }
        }
        try {
            setHeaderNames(header, field, headersNameProvider);
            componentModel.addEndpointHeader(header);
        } catch (Exception e) {
            if (getLog().isDebugEnabled()) {
                getLog().debug(
                        String.format("The name of the header corresponding to the field %s in class %s cannot be retrieved",
                                field.getName(),
                                field.getDeclaringClass().getName()));
            }
        }
        return true;
    }

    /**
     * Set the name of the header and the name of the constant corresponding to the header.
     * <p/>
     * The name of the header and the name of the constant are set as follows:
     * <ul>
     * <li><u>In case of an interface or a class:</u> <b>The name of the header</b> is the value of the field as we
     * assume that it is a {@code String} constant and <b>the name of the constant</b> is in the following format
     * <i>${declaring-class-name}#${constant-name}</i></li>
     * <li><u>In case of an enum:</u>
     * <ul>
     * <li><u>If {@code headersNameProvider} is set to a name of field:</u> <b>The name of the header</b> is the value
     * of this particular field for the corresponding enum constant and <b>the name of the constant</b> is in the
     * following format <i>${declaring-class-name}#${enum-constant-name}@${field-name}</i></li>
     * <li><u>If {@code headersNameProvider} is set to a name of method:</u> <b>The name of the header</b> is the
     * returned value of this particular method for the corresponding enum constant and <b>the name of the constant</b>
     * is in the following format <i>${declaring-class-name}#${enum-constant-name}@${method-name}()</i></li>
     * <li><u>Otherwise:</u> <b>The name of the header</b> is the name of the enum constant and <b>the name of the
     * constant</b> is in the following format <i>${declaring-class-name}#${enum-constant-name}</i></li>
     * </ul>
     * </li>
     * </ul>
     *
     * @param  header              the header in which the name of the header and its corresponding constant should be
     *                             set.
     * @param  field               the field corresponding to the name of a header.
     * @param  headersNameProvider the name of the field to get or the name of the method to invoke to get the name of
     *                             the headers.
     * @throws Exception           if an error occurred while getting the name of the header
     */
    private void setHeaderNames(EndpointHeaderModel header, Field field, String headersNameProvider) throws Exception {
        final Class<?> declaringClass = field.getDeclaringClass();
        if (field.getType().isEnum()) {
            if (!headersNameProvider.isEmpty()) {
                final Optional<?> value = Arrays.stream(declaringClass.getEnumConstants())
                        .filter(c -> ((Enum<?>) c).name().equals(field.getName()))
                        .findAny();
                if (value.isPresent()) {
                    getLog().debug(String.format("The headers name provider has been set to %s", headersNameProvider));
                    final Optional<Field> headersNameProviderField = Arrays.stream(declaringClass.getFields())
                            .filter(f -> f.getName().equals(headersNameProvider))
                            .findAny();
                    if (headersNameProviderField.isPresent()) {
                        getLog().debug("A field corresponding to the headers name provider has been found");
                        header.setConstantName(
                                String.format("%s#%s@%s", declaringClass.getName(), field.getName(), headersNameProvider));
                        header.setName((String) headersNameProviderField.get().get(value.get()));
                        return;
                    }
                    getLog().debug(
                            String.format("No field %s could be found in the class %s", headersNameProvider, declaringClass));
                    final Optional<Method> headersNameProviderMethod = Arrays.stream(declaringClass.getMethods())
                            .filter(m -> m.getName().equals(headersNameProvider) && m.getParameterCount() == 0)
                            .findAny();
                    if (headersNameProviderMethod.isPresent()) {
                        getLog().debug("A method without parameters corresponding to the headers name provider has been found");
                        header.setConstantName(
                                String.format("%s#%s@%s()", declaringClass.getName(), field.getName(), headersNameProvider));
                        header.setName((String) headersNameProviderMethod.get().invoke(value.get()));
                        return;
                    }
                    getLog().debug(String.format("No method %s without parameters could be found in the class %s",
                            headersNameProvider, declaringClass));
                }
            }
            header.setConstantName(String.format("%s#%s", declaringClass.getName(), field.getName()));
            header.setName(field.getName());
            return;
        }
        header.setConstantName(String.format("%s#%s", declaringClass.getName(), field.getName()));
        header.setName((String) field.get(null));
    }

    /**
     * @param  headerField the field for which we want to extract the related Javadoc.
     * @return             the Javadoc of the header field if any. An empty string otherwise.
     */
    private String getHeaderFieldJavadoc(Field headerField) {
        JavaSource<?> source;
        final String className = headerField.getDeclaringClass().getName();
        try {
            source = javaSource(className, JavaSource.class);
            if (source == null) {
                getLog().debug(String.format("The source of the class %s could not be found", className));
                return "";
            }
        } catch (Exception e) {
            getLog().debug(
                    String.format("An error occurred while loading the source of the class %s could not be found", className),
                    e);
            return "";
        }
        JavaDocCapable<?> member = null;
        if (source instanceof JavaEnumSource) {
            member = ((JavaEnumSource) source).getEnumConstant(headerField.getName());
        } else if (source instanceof FieldHolderSource) {
            member = ((FieldHolderSource<?>) source).getField(headerField.getName());
        } else {
            getLog().debug(String.format("The header field cannot be retrieved from a source of type %s", source.getName()));
        }
        if (member != null) {
            String doc = getJavaDocText(loadJavaSource(className), member);
            if (!Strings.isNullOrEmpty(doc)) {
                return doc;
            }
        }
        return "";
    }

    private String getExcludedEnd(Metadata classElement) {
        String excludedEndpointProperties = "";
        if (classElement != null) {
            excludedEndpointProperties = classElement.excludeProperties();
        }
        return excludedEndpointProperties;
    }

    /**
     * Used for enhancing the component model with apiProperties for API based components (such as twilio, olingo and
     * others)
     */
    private void enhanceComponentModelWithApiModel(ComponentModel componentModel) {
        for (AnnotationInstance ai : getIndex().getAnnotations(API_PARAMS)) {
            Class<?> classElement = loadClass(ai.target().asClass().name().toString());
            final ApiParams apiParams = classElement.getAnnotation(ApiParams.class);
            if (apiParams != null) {
                String apiName = apiParams.apiName();
                if (!Strings.isNullOrEmpty(apiName)) {
                    final UriParams uriParams = classElement.getAnnotation(UriParams.class);
                    String extraPrefix = uriParams != null ? uriParams.prefix() : "";
                    findClassProperties(componentModel, classElement, Collections.emptySet(), extraPrefix,
                            null, null, false);
                }
            }
        }
    }

    @Override
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
            throw new RuntimeException("Error while loading " + fileName + ": " + e, e);
        }
        resources.put(fileName, data);
        return data;
    }

    void enhanceComponentModel(
            ComponentModel componentModel, ComponentModel parentData, String excludedEndpointProperties,
            String excludedComponentProperties) {
        componentModel.getComponentOptions().removeIf(option -> filterOutOption(componentModel, option));
        componentModel.getEndpointHeaders().forEach(option -> fixDoc(option, null));
        componentModel.getComponentOptions()
                .forEach(option -> fixDoc(option, parentData != null ? parentData.getComponentOptions() : null));
        componentModel.getComponentOptions().sort(EndpointHelper.createGroupAndLabelComparator());
        componentModel.getEndpointOptions().removeIf(option -> filterOutOption(componentModel, option));
        componentModel.getEndpointOptions()
                .forEach(option -> fixDoc(option, parentData != null ? parentData.getEndpointOptions() : null));
        componentModel.getEndpointOptions().sort(EndpointHelper.createOverallComparator(componentModel.getSyntax()));
        // merge with parent, remove excluded and override properties
        if (parentData != null) {
            Set<String> componentOptionNames
                    = componentModel.getComponentOptions().stream().map(BaseOptionModel::getName).collect(Collectors.toSet());
            Set<String> endpointOptionNames
                    = componentModel.getEndpointOptions().stream().map(BaseOptionModel::getName).collect(Collectors.toSet());
            Set<String> headerNames
                    = componentModel.getEndpointHeaders().stream().map(BaseOptionModel::getName).collect(Collectors.toSet());
            Collections.addAll(componentOptionNames, excludedComponentProperties.split(","));
            Collections.addAll(endpointOptionNames, excludedEndpointProperties.split(","));
            parentData.getComponentOptions().stream()
                    .filter(option -> !componentOptionNames.contains(option.getName()))
                    .forEach(option -> componentModel.getComponentOptions().add(option));
            parentData.getEndpointOptions().stream()
                    .filter(option -> !endpointOptionNames.contains(option.getName()))
                    .forEach(option -> componentModel.getEndpointOptions().add(option));
            parentData.getEndpointHeaders().stream()
                    .filter(header -> !headerNames.contains(header.getName()))
                    .forEach(header -> componentModel.getEndpointHeaders().add(header));
        }
    }

    private void fixDoc(BaseOptionModel option, List<? extends BaseOptionModel> parentOptions) {
        String doc = getDocumentationWithNotes(option);
        if (Strings.isNullOrEmpty(doc) && parentOptions != null) {
            doc = parentOptions.stream().filter(opt -> Objects.equals(opt.getName(), option.getName()))
                    .map(this::getDocumentationWithNotes).findFirst().orElse(null);
        }
        // as its json we need to sanitize the docs
        doc = JavadocHelper.sanitizeDescription(doc, false);
        option.setDescription(doc);

        if (isNullOrEmpty(doc)) {
            throw new IllegalStateException(
                    "Empty doc for option: " + option.getName() + ", parent options: "
                                            + (parentOptions != null
                                                    ? Jsoner.serialize(JsonMapper.asJsonObject(parentOptions)) : "<null>"));
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
        String description = option.getDescription();
        if (description == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(description.length() * 64);
        sb.append(description);

        if (option.isMultiValue() && option.getPrefix() != null) {
            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '.') {
                sb.append('.');
            }
            sb.append(" This is a multi-value option with prefix: ").append(option.getPrefix());
        }

        if (!Strings.isNullOrEmpty(option.getDefaultValueNote())) {
            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '.') {
                sb.append('.');
            }
            sb.append(" Default value notice: ").append(option.getDefaultValueNote());
        }

        if (!Strings.isNullOrEmpty(option.getDeprecationNote())) {
            if (!sb.isEmpty() && sb.charAt(sb.length() - 1) != '.') {
                sb.append('.');
            }
            sb.append(" Deprecation note: ").append(option.getDeprecationNote());
        }

        return sb.toString();
    }

    private void generateComponentConfigurer(
            UriEndpoint uriEndpoint, String scheme, String[] schemes, ComponentModel componentModel,
            ComponentModel parentData) {

        if (!uriEndpoint.generateConfigurer()) {
            return;
        }
        // only generate this once for the first scheme
        if (isFirstScheme(scheme, schemes)) {
            return;
        }
        String pfqn;
        boolean hasSuper;

        Class<?> superClazz = loadClass(componentModel.getJavaType()).getSuperclass();
        if (parentData != null && superClazz.getName().equals(parentData.getJavaType())) {
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
                    .toList();
        } else {
            options = componentModel.getComponentOptions();
        }
        generatePropertyConfigurer(packageName, className, fqClassName, componentClassName,
                pfqn, psn,
                componentModel.getScheme() + "-component", hasSuper, true,
                options, componentModel);
    }

    private boolean isFirstScheme(String scheme, String[] schemes) {
        if (schemes != null && !schemes[0].equals(scheme)) {
            return true;
        }
        return false;
    }

    private void generateEndpointConfigurer(
            Class<?> classElement, UriEndpoint uriEndpoint, String scheme, String[] schemes,
            ComponentModel componentModel, ComponentModel parentData) {
        if (!uriEndpoint.generateConfigurer()) {
            return;
        }
        // only generate this once for the first scheme
        if (isFirstScheme(scheme, schemes)) {
            return;
        }

        Class<?> superClazz = loadClass(componentModel.getJavaType()).getSuperclass();

        String pfqn;
        boolean hasSuper;
        if (parentData != null && superClazz.getName().equals(parentData.getJavaType())) {
            try {
                pfqn = classElement.getSuperclass().getName() + "Configurer";
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
            options = componentModel.getEndpointParameterOptions().stream()
                    .filter(o -> !parentOptionsNames.contains(o.getName()))
                    .toList();
        } else {
            options = componentModel.getEndpointParameterOptions();
        }
        generatePropertyConfigurer(packageName, className, fqClassName, endpointClassName,
                pfqn, psn,
                componentModel.getScheme() + "-endpoint", hasSuper, false,
                options, componentModel);
    }

    protected ComponentModel findComponentProperties(
            UriEndpoint uriEndpoint, Class<?> endpointClassElement, String title, String scheme,
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
        model.setRemote(uriEndpoint.remote());
        model.setAsync(loadClass("org.apache.camel.AsyncEndpoint").isAssignableFrom(endpointClassElement));
        model.setApi(loadClass("org.apache.camel.ApiEndpoint").isAssignableFrom(endpointClassElement));
        model.setBrowsable(loadClass("org.apache.camel.spi.BrowsableEndpoint").isAssignableFrom(endpointClassElement));
        model.setApiSyntax(uriEndpoint.apiSyntax());

        // what is the first version this component was added to Apache Camel
        String firstVersion = uriEndpoint.firstVersion();
        if (Strings.isNullOrEmpty(firstVersion) && endpointClassElement.getAnnotation(Metadata.class) != null) {
            // fallback to @Metadata if not from @UriEndpoint
            firstVersion = endpointClassElement.getAnnotation(Metadata.class).firstVersion();
        }
        if (!Strings.isNullOrEmpty(firstVersion)) {
            model.setFirstVersion(firstVersion);
        }

        model.setDescription(project.getDescription());
        model.setGroupId(project.getGroupId());
        model.setArtifactId(project.getArtifactId());
        model.setVersion(project.getVersion());

        // grab level from annotation, pom.xml or default to stable
        String level = project.getProperties().getProperty("supportLevel");
        boolean experimental = ClassUtil.hasAnnotation("org.apache.camel.Experimental", endpointClassElement);
        if (experimental) {
            model.setSupportLevel(SupportLevel.Experimental);
        } else if (level != null) {
            model.setSupportLevel(SupportLevel.safeValueOf(level));
        } else {
            model.setSupportLevel(SupportLevelHelper.defaultSupportLevel(model.getFirstVersion(), model.getVersion()));
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
        if (!isNullOrEmpty(deprecationNote)) {
            model.setDeprecationNote(deprecationNote);
        }
        model.setDeprecatedSince(project.getProperties().getProperty("deprecatedSince"));

        // this information is not available at compile time, and we enrich
        // these later during the camel-package-maven-plugin
        if (model.getJavaType() == null) {
            throw new IllegalStateException("Could not find @Component(\"" + scheme + "\") annotated class.");
        }

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
            throw new IllegalStateException(
                    "Cannot find description to use for component: " + scheme
                                            + ". Add <description> to Maven pom.xml or javadoc to the endpoint: "
                                            + endpointClassElement);
        }

        return model;
    }

    protected void findComponentClassProperties(
            ComponentModel componentModel, Class<?> classElement,
            String prefix, String nestedTypeName, String nestedFieldName) {
        final Class<?> orgClassElement = classElement;
        Set<String> excludes = new HashSet<>();
        while (true) {
            processMetadataClassAnnotation(componentModel, classElement, excludes);

            List<Method> methods = findCandidateClassMethods(classElement);

            // if the component has options with annotations then we only want to generate options that are annotated
            // as ideally components should favour doing this, so we can control what is an option and what is not
            List<Field> fields = Stream.of(classElement.getDeclaredFields()).toList();
            boolean annotationBasedOptions = fields.stream().anyMatch(f -> f.getAnnotation(Metadata.class) != null)
                    || methods.stream().anyMatch(m -> m.getAnnotation(Metadata.class) != null);

            if (!methods.isEmpty() && !annotationBasedOptions) {
                getLog().warn("Component class " + classElement.getName() + " has not been marked up with @Metadata for "
                              + methods.size() + " options.");
            }

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
                Field fieldElement = getFieldElement(classElement, fieldName);
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
                        findClassProperties(componentModel, fieldTypeElement, Collections.emptySet(), nestedPrefix,
                                nestedTypeName, nestedFieldName, true);
                        nestedTypeName = null;
                        nestedFieldName = null;
                        // we also want to include the configuration itself so continue and add ourselves
                    }
                }

                boolean required = metadata != null && metadata.required();
                String label = metadata != null ? metadata.label() : null;
                boolean secret = metadata != null && metadata.secret();
                boolean autowired = metadata != null && metadata.autowired();
                boolean supportFileReference = metadata != null && metadata.supportFileReference();
                boolean largeInput = metadata != null && metadata.largeInput();
                String inputLanguage = metadata != null ? metadata.inputLanguage() : null;
                boolean important = metadata != null && metadata.important();

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
                List<String> enums = getEnums(metadata, fieldType);

                // the field type may be overloaded by another type
                boolean isDuration = false;
                if (metadata != null && !Strings.isNullOrEmpty(metadata.javaType())) {
                    String mjt = metadata.javaType();
                    if ("java.time.Duration".equals(mjt)) {
                        isDuration = true;
                    } else {
                        fieldTypeName = mjt;
                    }
                }

                // generics for collection types
                String nestedType = null;
                String desc = fieldTypeName;
                if (desc.contains("<") && desc.contains(">")) {
                    desc = Strings.between(desc, "<", ">");
                    // if it has additional nested types, then we only want the outer type
                    int pos = desc.indexOf('<');
                    if (pos != -1) {
                        desc = desc.substring(0, pos);
                    }
                    // if its a map then it has a key/value, so we only want the last part
                    pos = desc.indexOf(',');
                    if (pos != -1) {
                        desc = desc.substring(pos + 1);
                    }
                    desc = desc.replace('$', '.');
                    desc = desc.trim();
                    // skip if the type is generic or a wildcard
                    if (!desc.isEmpty() && desc.indexOf('?') == -1 && !desc.contains(" extends ")) {
                        nestedType = desc;
                    }
                }

                // prepare default value so its value is correct according to its type
                defaultValue = getDefaultValue(defaultValue, fieldTypeName, isDuration);

                String group = EndpointHelper.labelAsGroupName(label, componentModel.isConsumerOnly(),
                        componentModel.isProducerOnly());
                // filter out consumer/producer only
                boolean accept = !excludes.contains(name);
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
                    option.setType(MojoHelper.getType(fieldTypeName, enums != null && !enums.isEmpty(), isDuration));
                    option.setJavaType(fieldTypeName);
                    option.setRequired(required);
                    option.setDefaultValue(defaultValue);
                    option.setDefaultValueNote(defaultValueNote);
                    option.setDescription(docComment.trim());
                    option.setDeprecated(deprecated);
                    option.setDeprecationNote(deprecationNote);
                    option.setSecret(secret);
                    option.setAutowired(autowired);
                    option.setGroup(group);
                    option.setLabel(label);
                    option.setEnums(enums);
                    option.setNestedType(nestedType);
                    option.setConfigurationClass(nestedTypeName);
                    option.setConfigurationField(nestedFieldName);
                    option.setSupportFileReference(supportFileReference);
                    option.setLargeInput(largeInput);
                    option.setInputLanguage(inputLanguage);
                    option.setImportant(important);
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

    private List<String> getEnums(Metadata metadata, Class<?> fieldType) {
        List<String> enums = null;
        if (metadata != null && !Strings.isNullOrEmpty(metadata.enums())) {
            String[] values = metadata.enums().split(",");
            enums = Stream.of(values).map(String::trim).toList();
        } else if (fieldType != null && fieldType.isEnum()) {
            enums = new ArrayList<>();
            for (Object val : fieldType.getEnumConstants()) {
                String str = val.toString();
                if (!enums.contains(str)) {
                    enums.add(str);
                }
            }
        }
        return enums;
    }

    private Field getFieldElement(Class<?> classElement, String fieldName) {
        Field fieldElement;
        try {
            fieldElement = classElement.getDeclaredField(fieldName);
        } catch (NoSuchFieldException e) {
            fieldElement = null;
        }
        return fieldElement;
    }

    private List<Method> findCandidateClassMethods(Class<?> classElement) {
        return Stream.of(classElement.getDeclaredMethods()).filter(method -> {
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
    }

    private void processMetadataClassAnnotation(ComponentModel componentModel, Class<?> classElement, Set<String> excludes) {
        Metadata componentAnnotation = classElement.getAnnotation(Metadata.class);
        if (componentAnnotation != null) {
            if (Objects.equals("verifiers", componentAnnotation.label())) {
                componentModel.setVerifiers(componentAnnotation.enums());
            }
            Collections.addAll(excludes, componentAnnotation.excludeProperties().split(","));
        }
    }

    protected void findClassProperties(
            ComponentModel componentModel, Class<?> classElement,
            Set<String> excludes, String prefix,
            String nestedTypeName, String nestedFieldName, boolean componentOption) {
        final Class<?> orgClassElement = classElement;
        excludes = new HashSet<>(excludes);
        while (true) {
            String apiName = null;
            boolean apiOption = false;
            // only check for api if component is API based
            ApiParams apiParams = null;
            if (componentModel.isApi()) {
                apiParams = classElement.getAnnotation(ApiParams.class);
                if (apiParams != null) {
                    apiName = apiParams.apiName();
                    apiOption = !Strings.isNullOrEmpty(apiName);
                }
            }

            collectExcludes(classElement, excludes);

            Metadata metadata;
            for (final Field fieldElement : classElement.getDeclaredFields()) {
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

                if (collectUriPathProperties(componentModel, classElement, excludes, prefix, nestedTypeName, nestedFieldName,
                        componentOption, orgClassElement, metadata, fieldElement, deprecated, deprecationNote, secret)) {
                    continue;
                }
                String fieldName;

                UriParam param = fieldElement.getAnnotation(UriParam.class);
                if (param != null) {
                    fieldName = fieldElement.getName();
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
                        findClassProperties(componentModel, fieldTypeElement, excludes, nestedPrefix, nestedTypeName,
                                nestedFieldName, componentOption);
                        nestedTypeName = null;
                        nestedFieldName = null;
                    } else {
                        ApiParam apiParam = fieldElement.getAnnotation(ApiParam.class);

                        collectNonNestedField(componentModel, classElement, nestedTypeName, nestedFieldName, componentOption,
                                apiName, apiOption, apiParams, metadata, fieldElement, deprecated, deprecationNote, secret,
                                fieldName, param, apiParam, name, paramOptionalPrefix, paramPrefix, multiValue, defaultValue,
                                defaultValueNote, required, label, displayName, fieldTypeElement, fieldTypeName);
                    }
                }
            }

            if (apiOption) {
                // include extra methods that has no parameters and are only included in the class annotation
                final String apiModelName = apiName;
                Optional<ApiModel> op = componentModel.getApiOptions().stream()
                        .filter(o -> o.getName().equals(apiModelName))
                        .findFirst();
                if (op.isPresent()) {
                    ApiModel am = op.get();
                    apiParams = classElement.getAnnotation(ApiParams.class);
                    if (apiParams != null) {
                        ApiMethod[] extra = apiParams.apiMethods();
                        if (extra != null) {
                            for (ApiMethod m : extra) {
                                boolean exists = am.getMethods().stream()
                                        .anyMatch(o -> m.methodName().equals(o.getName()));
                                if (!exists) {
                                    ApiMethodModel o = am.newMethod(m.methodName());
                                    o.setDescription(m.description());
                                    for (String sig : m.signatures()) {
                                        o.addSignature(sig);
                                    }
                                }
                            }
                        }
                    }
                }
                // do not check super classes for api options as we only check one level (to include new options and not common)
                // if there are no options added then add the api name as empty option so we have it marked
                break;
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

    private void collectNonNestedField(
            ComponentModel componentModel, Class<?> classElement, String nestedTypeName, String nestedFieldName,
            boolean componentOption, String apiName, boolean apiOption, ApiParams apiParams, Metadata metadata,
            Field fieldElement, boolean deprecated, String deprecationNote, Boolean secret, String fieldName, UriParam param,
            ApiParam apiParam, String name, String paramOptionalPrefix, String paramPrefix, boolean multiValue,
            Object defaultValue, String defaultValueNote, boolean required, String label, String displayName,
            Class<?> fieldTypeElement, String fieldTypeName) {
        String docComment = param.description();
        if (Strings.isNullOrEmpty(docComment)) {
            docComment = findJavaDoc(fieldElement, fieldName, name, classElement, false);
        }
        if (Strings.isNullOrEmpty(docComment)) {
            docComment = "";
        }

        // gather enums
        List<String> enums = gatherEnums(param, fieldTypeElement);

        // the field type may be overloaded by another type
        boolean isDuration = false;
        if (!Strings.isNullOrEmpty(param.javaType())) {
            String jt = param.javaType();
            if ("java.time.Duration".equals(jt)) {
                isDuration = true;
            } else {
                fieldTypeName = param.javaType();
            }
        }

        // prepare default value so its value is correct according to its type
        defaultValue = getDefaultValue(defaultValue, fieldTypeName, isDuration);

        boolean isSecret = secret != null && secret || param.secret();
        boolean isAutowired = metadata != null && metadata.autowired();
        boolean supportFileReference = metadata != null && metadata.supportFileReference();
        boolean important = metadata != null && metadata.important();
        String group = EndpointHelper.labelAsGroupName(label, componentModel.isConsumerOnly(),
                componentModel.isProducerOnly());

        // generics for collection types
        String nestedType = null;
        String desc = fieldTypeName;
        if (desc.contains("<") && desc.contains(">")) {
            desc = Strings.between(desc, "<", ">");
            // if it has additional nested types, then we only want the outer type
            int pos = desc.indexOf('<');
            if (pos != -1) {
                desc = desc.substring(0, pos);
            }
            // if its a map then it has a key/value, so we only want the last part
            pos = desc.indexOf(',');
            if (pos != -1) {
                desc = desc.substring(pos + 1);
            }
            desc = desc.replace('$', '.');
            desc = desc.trim();
            // skip if the type is generic or a wildcard
            if (!desc.isEmpty() && desc.indexOf('?') == -1 && !desc.contains(" extends ")) {
                nestedType = desc;
            }
        }

        BaseOptionModel option;
        if (componentOption) {
            option = new ComponentOptionModel();
        } else if (apiOption) {
            option = new ApiOptionModel();
        } else {
            option = new EndpointOptionModel();
        }
        option.setName(name);
        option.setDisplayName(displayName);
        option.setType(MojoHelper.getType(fieldTypeName, enums != null && !enums.isEmpty(), isDuration));
        option.setJavaType(fieldTypeName);
        option.setRequired(required);
        option.setDefaultValue(defaultValue);
        option.setDefaultValueNote(defaultValueNote);
        option.setDescription(docComment.trim());
        option.setDeprecated(deprecated);
        option.setDeprecationNote(deprecationNote);
        option.setSecret(isSecret);
        option.setAutowired(isAutowired);
        option.setGroup(group);
        option.setLabel(label);
        option.setEnums(enums);
        option.setNestedType(nestedType);
        option.setConfigurationClass(nestedTypeName);
        option.setConfigurationField(nestedFieldName);
        option.setPrefix(paramPrefix);
        option.setOptionalPrefix(paramOptionalPrefix);
        option.setMultiValue(multiValue);
        option.setSupportFileReference(supportFileReference);
        option.setImportant(important);
        if (componentOption) {
            option.setKind("property");
            componentModel.addComponentOption((ComponentOptionModel) option);
        } else if (apiOption && apiParam != null) {
            option.setKind("parameter");
            final String targetApiName = apiName;
            ApiModel api;
            Optional<ApiModel> op = componentModel.getApiOptions().stream()
                    .filter(o -> o.getName().equals(targetApiName))
                    .findFirst();
            if (op.isEmpty()) {
                api = new ApiModel();
                api.setName(apiName);
                componentModel.getApiOptions().add(api);
                if (apiParams != null) {
                    for (String alias : apiParams.aliases()) {
                        api.addAlias(alias);
                    }
                }
                if (apiParams != null) {
                    api.setDescription(apiParams.description());
                    // component model takes precedence
                    api.setConsumerOnly(componentModel.isConsumerOnly() || apiParams.consumerOnly());
                    api.setProducerOnly(componentModel.isProducerOnly() || apiParams.producerOnly());
                }
            } else {
                api = op.get();
            }
            for (ApiMethod method : apiParam.apiMethods()) {
                ApiMethodModel apiMethod = null;
                for (ApiMethodModel m : api.getMethods()) {
                    if (m.getName().equals(method.methodName())) {
                        apiMethod = m;
                        break;
                    }
                }
                if (apiMethod == null) {
                    apiMethod = api.newMethod(method.methodName());
                }
                // the method description is stored on @ApiParams
                if (apiParams != null) {
                    for (ApiMethod m : apiParams.apiMethods()) {
                        if (m.methodName().equals(method.methodName())) {
                            apiMethod.setDescription(m.description());
                            for (String sig : m.signatures()) {
                                apiMethod.addSignature(sig);
                            }
                            break;
                        }
                    }
                }
                // copy the option and override with the correct description
                ApiOptionModel copy = ((ApiOptionModel) option).copy();
                apiMethod.addApiOptionModel(copy);
                // the option description is stored on @ApiMethod
                copy.setDescription(method.description());
                // whether we are consumer or producer only
                group = EndpointHelper.labelAsGroupName(copy.getLabel(), api.isConsumerOnly(),
                        api.isProducerOnly());
                copy.setGroup(group);
                copy.setOptional(apiParam.optional());
            }
        } else {
            option.setKind("parameter");
            if (componentModel.getEndpointOptions().stream().noneMatch(opt -> name.equals(opt.getName()))) {
                componentModel.addEndpointOption((EndpointOptionModel) option);
            }
        }
    }

    private boolean collectUriPathProperties(
            ComponentModel componentModel, Class<?> classElement, Set<String> excludes, String prefix, String nestedTypeName,
            String nestedFieldName, boolean componentOption, Class<?> orgClassElement, Metadata metadata, Field fieldElement,
            boolean deprecated, String deprecationNote, Boolean secret) {
        UriPath path = fieldElement.getAnnotation(UriPath.class);
        String fieldName = fieldElement.getName();
        // component options should not include @UriPath as they are for endpoints only
        if (!componentOption && path != null) {
            String name = prefix + (Strings.isNullOrEmpty(path.name()) ? fieldName : path.name());

            // should we exclude the name?
            if (excludes.contains(name)) {
                return true;
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
            List<String> enums = gatherEnums(path, fieldTypeElement);

            // the field type may be overloaded by another type
            boolean isDuration = false;
            if (!Strings.isNullOrEmpty(path.javaType())) {
                String mjt = path.javaType();
                if ("java.time.Duration".equals(mjt)) {
                    isDuration = true;
                } else {
                    fieldTypeName = mjt;
                }
            }

            // prepare default value so its value is correct according to its type
            defaultValue = getDefaultValue(defaultValue, fieldTypeName, isDuration);

            boolean isSecret = secret != null && secret || path.secret();
            boolean isAutowired = metadata != null && metadata.autowired();
            boolean supportFileReference = metadata != null && metadata.supportFileReference();
            boolean largeInput = metadata != null && metadata.largeInput();
            boolean important = metadata != null && metadata.important();
            String inputLanguage = metadata != null ? metadata.inputLanguage() : null;
            String group = EndpointHelper.labelAsGroupName(label, componentModel.isConsumerOnly(),
                    componentModel.isProducerOnly());

            // generics for collection types
            String nestedType = null;
            String desc = fieldTypeName;
            if (desc.contains("<") && desc.contains(">")) {
                desc = Strings.between(desc, "<", ">");
                // if it has additional nested types, then we only want the outer type
                int pos = desc.indexOf('<');
                if (pos != -1) {
                    desc = desc.substring(0, pos);
                }
                // if its a map then it has a key/value, so we only want the last part
                pos = desc.indexOf(',');
                if (pos != -1) {
                    desc = desc.substring(pos + 1);
                }
                desc = desc.replace('$', '.');
                desc = desc.trim();
                // skip if the type is generic or a wildcard
                if (!desc.isEmpty() && desc.indexOf('?') == -1 && !desc.contains(" extends ")) {
                    nestedType = desc;
                }
            }

            BaseOptionModel option;
            if (componentOption) {
                option = new ComponentOptionModel();
            } else {
                option = new EndpointOptionModel();
            }
            option.setName(name);
            option.setKind("path");
            option.setDisplayName(displayName);
            option.setType(MojoHelper.getType(fieldTypeName, enums != null && !enums.isEmpty(), isDuration));
            option.setJavaType(fieldTypeName);
            option.setRequired(required);
            option.setDefaultValue(defaultValue);
            option.setDefaultValueNote(defaultValueNote);
            option.setDescription(docComment.trim());
            option.setDeprecated(deprecated);
            option.setDeprecationNote(deprecationNote);
            option.setSecret(isSecret);
            option.setAutowired(isAutowired);
            option.setGroup(group);
            option.setLabel(label);
            option.setEnums(enums);
            option.setNestedType(nestedType);
            option.setConfigurationClass(nestedTypeName);
            option.setConfigurationField(nestedFieldName);
            option.setSupportFileReference(supportFileReference);
            option.setLargeInput(largeInput);
            option.setInputLanguage(inputLanguage);
            option.setImportant(important);
            if (componentModel.getEndpointOptions().stream().noneMatch(opt -> name.equals(opt.getName()))) {
                componentModel.addEndpointOption((EndpointOptionModel) option);
            }
        }
        return false;
    }

    private void collectExcludes(Class<?> classElement, Set<String> excludes) {
        final UriEndpoint uriEndpoint = classElement.getAnnotation(UriEndpoint.class);
        if (uriEndpoint != null) {
            String excludedProperties = getExcludedEnd(classElement.getAnnotation(Metadata.class));

            Collections.addAll(excludes, excludedProperties.split(","));
        }
    }

    private static List<String> doGatherFromEnum(Class<?> fieldTypeElement) {
        final List<String> enums = new ArrayList<>();

        for (Object val : fieldTypeElement.getEnumConstants()) {
            String str = val.toString();
            if (!enums.contains(str)) {
                enums.add(str);
            }
        }

        return enums;
    }

    private static List<String> gatherEnums(UriParam param, Class<?> fieldTypeElement) {
        if (!Strings.isNullOrEmpty(param.enums())) {
            String[] values = param.enums().split(",");
            return Stream.of(values).map(String::trim).toList();
        } else if (fieldTypeElement.isEnum()) {
            return doGatherFromEnum(fieldTypeElement);
        }

        return null;
    }

    private static List<String> gatherEnums(UriPath path, Class<?> fieldTypeElement) {
        if (!Strings.isNullOrEmpty(path.enums())) {
            String[] values = path.enums().split(",");
            return Stream.of(values).map(String::trim).toList();
        } else if (fieldTypeElement.isEnum()) {
            return doGatherFromEnum(fieldTypeElement);
        }

        return null;
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

    private static boolean isGroovyMetaClassProperty(final Method method) {
        final String methodName = method.getName();

        if (!"setMetaClass".equals(methodName)) {
            return false;
        }

        // NOTE: we check the return type and the type may not be available
        return "groovy.lang.MetaClass".equals(method.getReturnType().getName()); // NOSONAR
    }

    protected void generatePropertyConfigurer(
            String pn, String cn, String fqn, String en,
            String pfqn, String psn, String scheme, boolean hasSuper, boolean component,
            Collection<? extends BaseOptionModel> options, ComponentModel model) {

        try {
            boolean extended = model.isApi(); // if the component is api then the generated configurer should be an extended configurer

            options = options.stream().sorted(Comparator.comparing(BaseOptionModel::getName)).collect(Collectors.toList());

            Map<String, Object> ctx = new HashMap<>();
            ctx.put("generatorClass", getClass().getName());
            ctx.put("package", pn);
            ctx.put("className", cn);
            ctx.put("type", en);
            ctx.put("pfqn", pfqn);
            ctx.put("psn", psn);
            ctx.put("hasSuper", hasSuper);
            ctx.put("component", component);
            ctx.put("extended", extended);
            ctx.put("bootstrap", false);
            ctx.put("options", options);
            ctx.put("model", model);
            ctx.put("mojo", this);
            String source = velocity("velocity/property-configurer.vm", ctx);

            updateResource(sourcesOutputDir.toPath(), fqn.replace('.', '/') + ".java", source);
        } catch (Exception e) {
            throw new RuntimeException("Unable to generate source code file: " + fqn + ": " + e.getMessage(), e);
        }
        generateMetaInfConfigurer(scheme, fqn);
    }

    protected void generateMetaInfConfigurer(String name, String fqn) {
        StringBuilder w = new StringBuilder(256);

        w.append("# ").append(GENERATED_MSG).append("\n");
        w.append("class=").append(fqn).append("\n");
        updateResource(resourcesOutputDir.toPath(), "META-INF/services/org/apache/camel/configurer/" + name, w.toString());
    }

    private IndexView getIndex() {
        if (indexView == null) {
            indexView = PackagePluginUtils.readJandexIndexQuietly(project);
        }

        return indexView;
    }

    private String findJavaDoc(
            AnnotatedElement member, String fieldName, String name, Class<?> classElement, boolean builderPattern) {
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
            source = javaSource(classElement.getName(), JavaClassSource.class);
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
            if (getter.getParameters().isEmpty()
                    && (getter.getName().equals("get" + propName) || getter.getName().equals("is" + propName))) {
                String doc = getJavaDocText(loadJavaSource(classElement.getName()), getter);
                if (!Strings.isNullOrEmpty(doc)) {
                    return doc;
                }
            }
        }

        if (builderPattern) {
            if (name != null && !name.equals(fieldName)) {
                String doc = getJavaDoc(source, name, classElement.getName());
                if (doc != null) {
                    return doc;
                }
            }
            String doc = getJavaDoc(source, fieldName, classElement.getName());
            if (doc != null) {
                return doc;
            }
        }

        return "";
    }

    private String getJavaDoc(JavaClassSource source, String fieldName, String classElement) {
        for (MethodSource<JavaClassSource> builder : source.getMethods()) {
            if (builder.getParameters().size() == 1 && builder.getName().equals(fieldName)) {
                String doc = getJavaDocText(loadJavaSource(classElement), builder);
                if (!Strings.isNullOrEmpty(doc)) {
                    return doc;
                }
            }
        }
        for (MethodSource<JavaClassSource> builder : source.getMethods()) {
            if (builder.getParameters().isEmpty() && builder.getName().equals(fieldName)) {
                String doc = getJavaDocText(loadJavaSource(classElement), builder);
                if (!Strings.isNullOrEmpty(doc)) {
                    return doc;
                }
            }
        }
        return null;
    }

    static String getJavaDocText(String source, JavaDocCapable<?> member) {
        if (member == null) {
            return null;
        }
        JavaDoc<?> javaDoc = member.getJavaDoc();
        Javadoc jd = (Javadoc) javaDoc.getInternal();
        if (source != null && !jd.tags().isEmpty()) {
            ASTNode n = (ASTNode) jd.tags().get(0);
            String txt = source.substring(n.getStartPosition(), n.getStartPosition() + n.getLength());

            return txt
                    .replaceAll(" *\n *\\* *\n", "\n\n")
                    .replaceAll(" *\n *\\* +", "\n");
        }
        return null;
    }

    private String getDocComment(Class<?> classElement) {
        JavaClassSource source = javaSource(classElement.getName(), JavaClassSource.class);
        return getJavaDocText(loadJavaSource(classElement.getName()), source);
    }

    private <T extends JavaSource<?>> T javaSource(String className, Class<T> targetType) {
        return targetType.cast(parsed.computeIfAbsent(className, this::doParseJavaSource));
    }

    private List<Path> getSourceRoots() {
        if (sourceRoots == null) {
            sourceRoots = project.getCompileSourceRoots().stream()
                    .map(Paths::get)
                    .toList();
        }
        return sourceRoots;
    }

    private JavaSource<?> doParseJavaSource(String className) {
        try {
            String source = loadJavaSource(className);
            if (source == null) {
                return null;
            } else {
                return (JavaSource<?>) Roaster.parse(source);
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
            throw new RuntimeException(
                    "Unable to load source for class " + className + " in folders " + getSourceRoots()
                                       + " (classpath: " + classpath + ")");
        }
    }

    private static String getTypeName(Type fieldType) {
        String fieldTypeName = new GenericType(fieldType).toString();
        fieldTypeName = fieldTypeName.replace('$', '.');
        return fieldTypeName;
    }

    /**
     * Gets the default value accordingly to its type
     *
     * @param defaultValue  the current default value
     * @param fieldTypeName the field type such as int, boolean, String etc
     */
    private static Object getDefaultValue(Object defaultValue, String fieldTypeName, boolean isDuration) {
        // special for boolean as it should not be literal
        if ("boolean".equals(fieldTypeName)) {
            if (isNullOrEmpty(defaultValue)) {
                defaultValue = false;
            } else {
                defaultValue = "true".equalsIgnoreCase(defaultValue.toString());
            }
        }
        if (!isDuration) {
            // special for integer as it should not be literal
            if ("int".equals(fieldTypeName)) {
                if (!isNullOrEmpty(defaultValue) && defaultValue instanceof String) {
                    defaultValue = Integer.parseInt(defaultValue.toString());
                }
            }
            // special for long as it should not be literal
            if ("long".equals(fieldTypeName)) {
                if (!isNullOrEmpty(defaultValue) && defaultValue instanceof String) {
                    defaultValue = Long.parseLong(defaultValue.toString());
                }
            }
            // special for double as it should not be literal
            if ("double".equals(fieldTypeName)) {
                if (!isNullOrEmpty(defaultValue) && defaultValue instanceof String) {
                    defaultValue = Double.parseDouble(defaultValue.toString());
                }
            }
            // special for double as it should not be literal
            if ("float".equals(fieldTypeName)) {
                if (!isNullOrEmpty(defaultValue) && defaultValue instanceof String) {
                    defaultValue = Float.parseFloat(defaultValue.toString());
                }
            }
        }
        if (isNullOrEmpty(defaultValue)) {
            defaultValue = "";
        }
        return defaultValue;
    }

}
