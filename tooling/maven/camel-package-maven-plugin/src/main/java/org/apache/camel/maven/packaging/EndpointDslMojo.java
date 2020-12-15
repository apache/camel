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
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.LineNumberReader;
import java.io.StringReader;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Generated;

import org.apache.camel.maven.packaging.generics.GenericsUtil;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.ComponentModel.EndpointOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.JavadocHelper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.tooling.util.srcgen.GenericType;
import org.apache.camel.tooling.util.srcgen.GenericType.BoundType;
import org.apache.camel.tooling.util.srcgen.JavaClass;
import org.apache.camel.tooling.util.srcgen.Method;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ASTNode;
import org.jboss.forge.roaster.model.Type;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.ParameterSource;
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;
import static org.apache.camel.tooling.util.PackageHelper.loadText;

/**
 * Generate Endpoint DSL source files for Components.
 */
@Mojo(name = "generate-endpoint-dsl", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
      defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class EndpointDslMojo extends AbstractGeneratorMojo {

    private static final Map<String, Class<?>> PRIMITIVEMAP;

    static {
        PRIMITIVEMAP = new HashMap<>();
        PRIMITIVEMAP.put("boolean", java.lang.Boolean.class);
        PRIMITIVEMAP.put("char", java.lang.Character.class);
        PRIMITIVEMAP.put("long", java.lang.Long.class);
        PRIMITIVEMAP.put("int", java.lang.Integer.class);
        PRIMITIVEMAP.put("integer", java.lang.Integer.class);
        PRIMITIVEMAP.put("byte", java.lang.Byte.class);
        PRIMITIVEMAP.put("short", java.lang.Short.class);
        PRIMITIVEMAP.put("double", java.lang.Double.class);
        PRIMITIVEMAP.put("float", java.lang.Float.class);
    }

    /**
     * The project build directory
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    /**
     * The base directory
     */
    @Parameter(defaultValue = "${project.basedir}")
    protected File baseDir;

    /**
     * The package where to generate component Endpoint factories
     */
    @Parameter(defaultValue = "org.apache.camel.builder.endpoint")
    protected String endpointFactoriesPackageName;

    /**
     * The package where to generate component specific Endpoint factories
     */
    @Parameter(defaultValue = "org.apache.camel.builder.endpoint.dsl")
    protected String componentsFactoriesPackageName;

    /**
     * Generate or not the EndpointBuilderFactory interface.
     */
    @Parameter(defaultValue = "true")
    protected boolean generateEndpointBuilderFactory;

    /**
     * Generate or not the EndpointBuilders interface.
     */
    @Parameter(defaultValue = "true")
    protected boolean generateEndpointBuilders;

    @Parameter(defaultValue = "true")
    protected boolean generateEndpointDsl;

    /**
     * The output directory
     */
    @Parameter
    protected File sourcesOutputDir;

    /**
     * Component Metadata file
     */
    @Parameter
    protected File componentsMetadata;

    /**
     * Components DSL Metadata
     */
    @Parameter
    protected File outputResourcesDir;

    @Override
    public void execute(MavenProject project, MavenProjectHelper projectHelper, BuildContext buildContext)
            throws MojoFailureException, MojoExecutionException {
        buildDir = new File(project.getBuild().getDirectory());
        baseDir = project.getBasedir();
        endpointFactoriesPackageName = "org.apache.camel.builder.endpoint";
        componentsFactoriesPackageName = "org.apache.camel.builder.endpoint.dsl";
        generateEndpointBuilderFactory = true;
        generateEndpointBuilders = true;
        super.execute(project, projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File camelDir = findCamelDirectory(baseDir, "core/camel-endpointdsl");
        if (camelDir == null) {
            getLog().debug("No core/camel-endpointdsl folder found, skipping execution");
            return;
        }
        Path root = camelDir.toPath();
        if (sourcesOutputDir == null) {
            sourcesOutputDir = root.resolve("src/generated/java").toFile();
        }
        if (outputResourcesDir == null) {
            outputResourcesDir = root.resolve("src/generated/resources").toFile();
        }
        if (componentsMetadata == null) {
            componentsMetadata = outputResourcesDir.toPath().resolve("metadata.json").toFile();
        }

        Map<File, Supplier<String>> files;

        try {
            files = Files
                    .find(buildDir.toPath(), Integer.MAX_VALUE,
                            (p, a) -> a.isRegularFile() && p.toFile().getName().endsWith(PackageHelper.JSON_SUFIX))
                    .collect(Collectors.toMap(Path::toFile, s -> cache(() -> loadJson(s.toFile()))));
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        // generate component endpoint DSL files and write them
        executeComponent(files);
    }

    private static String loadJson(File file) {
        try {
            return loadText(file);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    private void executeComponent(Map<File, Supplier<String>> jsonFiles) throws MojoFailureException {
        // find the component names
        Set<String> componentNames = new TreeSet<>();
        findComponentNames(buildDir, componentNames);

        // create auto configuration for the components
        if (!componentNames.isEmpty()) {
            getLog().debug("Found " + componentNames.size() + " components");

            List<ComponentModel> allModels = new LinkedList<>();
            for (String componentName : componentNames) {
                String json = loadComponentJson(jsonFiles, componentName);
                if (json != null) {
                    ComponentModel model = JsonMapper.generateComponentModel(json);
                    allModels.add(model);
                }
            }

            // Group the models by implementing classes
            Map<String, List<ComponentModel>> grModels
                    = allModels.stream().collect(Collectors.groupingBy(ComponentModel::getJavaType));
            for (String componentClass : grModels.keySet()) {
                List<ComponentModel> compModels = grModels.get(componentClass);
                ComponentModel model = compModels.get(0); // They should be
                                                         // equivalent
                List<String> aliases = compModels.stream().map(ComponentModel::getScheme).sorted().collect(Collectors.toList());

                String overrideComponentName = null;
                if (aliases.size() > 1) {
                    // determine component name when there are multiple ones
                    overrideComponentName = model.getArtifactId().replace("camel-", "");
                }

                createEndpointDsl(model, compModels, overrideComponentName);
            }
        }
    }

    private void createEndpointDsl(ComponentModel model, List<ComponentModel> aliases, String overrideComponentName)
            throws MojoFailureException {
        List<Method> staticBuilders = new ArrayList<>();
        boolean updated = doCreateEndpointDsl(model, aliases, staticBuilders);

        // Update components metadata
        getLog().debug("Load components EndpointFactories");
        List<File> endpointFactories = loadAllComponentsDslEndpointFactoriesAsFile();

        getLog().debug("Regenerate EndpointBuilderFactory");
        // make sure EndpointBuilderFactory is synced
        updated |= synchronizeEndpointBuilderFactoryInterface(endpointFactories);

        getLog().debug("Regenerate EndpointBuilders");
        // make sure EndpointBuilders is synced
        updated |= synchronizeEndpointBuildersInterface(endpointFactories);

        getLog().debug("Regenerate StaticEndpointBuilders");
        // make sure StaticEndpointBuilders is synced
        updated |= synchronizeEndpointBuildersStaticClass(staticBuilders);

        if (updated) {
            getLog().info("Updated EndpointDsl: " + model.getScheme());
        }
    }

    @SuppressWarnings({ "checkstyle:executablestatementcount", "checkstyle:methodlength" })
    private boolean doCreateEndpointDsl(ComponentModel model, List<ComponentModel> aliases, List<Method> staticBuilders)
            throws MojoFailureException {
        String componentClassName = model.getJavaType();
        String builderName = getEndpointName(componentClassName);
        Class<?> realComponentClass = loadClass(componentClassName);
        Class<?> realEndpointClass = loadClass(findEndpointClassName(componentClassName));

        final JavaClass javaClass = new JavaClass(getProjectClassLoader());
        javaClass.setPackage(componentsFactoriesPackageName);
        javaClass.setName(builderName + "Factory");
        javaClass.setClass(false);
        javaClass.addImport("org.apache.camel.builder.EndpointConsumerBuilder");
        javaClass.addImport("org.apache.camel.builder.EndpointProducerBuilder");
        javaClass.addImport("org.apache.camel.builder.endpoint.AbstractEndpointBuilder");

        Map<String, JavaClass> enumClasses = new HashMap<>();

        boolean hasAdvanced = false;
        for (EndpointOptionModel option : model.getEndpointOptions()) {
            if (option.getLabel() != null && option.getLabel().contains("advanced")) {
                hasAdvanced = true;
                break;
            }
        }

        JavaClass consumerClass = null;
        JavaClass advancedConsumerClass = null;
        JavaClass producerClass = null;
        JavaClass advancedProducerClass = null;

        if (!realEndpointClass.getAnnotation(UriEndpoint.class).producerOnly()
                && !realEndpointClass.getAnnotation(UriEndpoint.class).consumerOnly()) {
            String consumerName = builderName.replace("Endpoint", "EndpointConsumer");
            consumerClass = javaClass.addNestedType().setPublic().setClass(false);
            consumerClass.setName(consumerName);
            consumerClass.implementInterface("EndpointConsumerBuilder");
            generateDummyClass(consumerClass.getCanonicalName());
            consumerClass.getJavaDoc().setText("Builder for endpoint consumers for the " + model.getTitle() + " component.");
            if (hasAdvanced) {
                advancedConsumerClass = javaClass.addNestedType().setPublic().setClass(false);
                advancedConsumerClass.setName("Advanced" + consumerName);
                advancedConsumerClass.implementInterface("EndpointConsumerBuilder");
                generateDummyClass(advancedConsumerClass.getCanonicalName());
                advancedConsumerClass.getJavaDoc()
                        .setText("Advanced builder for endpoint consumers for the " + model.getTitle() + " component.");
                consumerClass.addMethod().setName("advanced").setReturnType(loadClass(advancedConsumerClass.getCanonicalName()))
                        .setDefault()
                        .setBody("return (Advanced" + consumerName + ") this;");
                advancedConsumerClass.addMethod().setName("basic").setReturnType(loadClass(consumerClass.getCanonicalName()))
                        .setDefault()
                        .setBody("return (" + consumerName + ") this;");
            }

            String producerName = builderName.replace("Endpoint", "EndpointProducer");
            producerClass = javaClass.addNestedType().setPublic().setClass(false);
            producerClass.setName(producerName);
            producerClass.implementInterface("EndpointProducerBuilder");
            generateDummyClass(producerClass.getCanonicalName());
            producerClass.getJavaDoc().setText("Builder for endpoint producers for the " + model.getTitle() + " component.");
            if (hasAdvanced) {
                advancedProducerClass = javaClass.addNestedType().setPublic().setClass(false);
                advancedProducerClass.setName("Advanced" + producerName);
                advancedProducerClass.implementInterface("EndpointProducerBuilder");
                generateDummyClass(advancedProducerClass.getCanonicalName());
                advancedProducerClass.getJavaDoc()
                        .setText("Advanced builder for endpoint producers for the " + model.getTitle() + " component.");

                producerClass.addMethod().setName("advanced").setReturnType(loadClass(advancedProducerClass.getCanonicalName()))
                        .setDefault()
                        .setBody("return (Advanced" + producerName + ") this;");
                advancedProducerClass.addMethod().setName("basic").setReturnType(loadClass(producerClass.getCanonicalName()))
                        .setDefault()
                        .setBody("return (" + producerName + ") this;");
            }
        }

        JavaClass builderClass;
        JavaClass advancedBuilderClass = null;
        builderClass = javaClass.addNestedType().setPublic().setClass(false);
        builderClass.setName(builderName);
        if (realEndpointClass.getAnnotation(UriEndpoint.class).producerOnly()) {
            builderClass.implementInterface("EndpointProducerBuilder");
        } else if (realEndpointClass.getAnnotation(UriEndpoint.class).consumerOnly()) {
            builderClass.implementInterface("EndpointConsumerBuilder");
        } else {
            builderClass.implementInterface(consumerClass.getName());
            builderClass.implementInterface(producerClass.getName());
        }
        generateDummyClass(builderClass.getCanonicalName());
        builderClass.getJavaDoc().setText("Builder for endpoint for the " + model.getTitle() + " component.");

        if (hasAdvanced) {
            advancedBuilderClass = javaClass.addNestedType().setPublic().setClass(false);
            advancedBuilderClass.setName("Advanced" + builderName);
            if (realEndpointClass.getAnnotation(UriEndpoint.class).producerOnly()) {
                advancedBuilderClass.implementInterface("EndpointProducerBuilder");
            } else if (realEndpointClass.getAnnotation(UriEndpoint.class).consumerOnly()) {
                advancedBuilderClass.implementInterface("EndpointConsumerBuilder");
            } else {
                advancedBuilderClass.implementInterface(advancedConsumerClass.getName());
                advancedBuilderClass.implementInterface(advancedProducerClass.getName());
            }
            generateDummyClass(advancedBuilderClass.getCanonicalName());
            advancedBuilderClass.getJavaDoc()
                    .setText("Advanced builder for endpoint for the " + model.getTitle() + " component.");

            builderClass.addMethod().setName("advanced").setReturnType(loadClass(advancedBuilderClass.getCanonicalName()))
                    .setDefault()
                    .setBody("return (Advanced" + builderName + ") this;");
            advancedBuilderClass.addMethod().setName("basic").setReturnType(loadClass(builderClass.getCanonicalName()))
                    .setDefault().setBody("return (" + builderName + ") this;");
        }

        generateDummyClass(componentsFactoriesPackageName + ".T");

        String doc = GENERATED_MSG;
        if (!Strings.isEmpty(model.getDescription())) {
            doc = model.getDescription() + "\n\n" + doc;
        }
        javaClass.getJavaDoc().setText(doc);

        javaClass.addAnnotation(Generated.class).setStringValue("value", EndpointDslMojo.class.getName());

        for (EndpointOptionModel option : model.getEndpointOptions()) {

            // skip all @UriPath parameters as the endpoint DSL is for query
            // parameters
            if ("path".equals(option.getKind())) {
                continue;
            }

            List<JavaClass> targets = new ArrayList<>();
            String label = option.getLabel() != null ? option.getLabel() : "";
            if (label != null) {
                if (label.contains("producer")) {
                    if (label.contains("advanced")) {
                        targets.add(advancedProducerClass != null ? advancedProducerClass : advancedBuilderClass);
                    } else {
                        targets.add(producerClass != null ? producerClass : builderClass);
                    }
                } else if (label.contains("consumer")) {
                    if (label.contains("advanced")) {
                        targets.add(advancedConsumerClass != null ? advancedConsumerClass : advancedBuilderClass);
                    } else {
                        targets.add(consumerClass != null ? consumerClass : builderClass);
                    }
                } else {
                    if (label.contains("advanced")) {
                        targets.add(advancedConsumerClass);
                        targets.add(advancedProducerClass);
                        targets.add(advancedBuilderClass);
                    } else {
                        targets.add(consumerClass);
                        targets.add(producerClass);
                        targets.add(builderClass);
                    }
                }
            }

            GenericType ogtype;
            GenericType gtype;
            try {
                Field field = findField(realComponentClass, realEndpointClass, option);
                ogtype = new GenericType(GenericsUtil.resolveType(realEndpointClass, field));
                gtype = getType(javaClass, enumClasses, option.getEnums(), ogtype.toString());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            for (JavaClass target : targets) {
                if (target == null) {
                    continue;
                }

                // basic description
                String baseDesc = option.getDescription();
                if (!Strings.isEmpty(baseDesc)) {
                    // must xml encode description as in some rare cases it contains & chars which is invalid javadoc
                    baseDesc = JavadocHelper.xmlEncode(baseDesc);

                    if (!baseDesc.endsWith(".")) {
                        baseDesc += ".";
                    }
                    baseDesc += "\n";
                    baseDesc += "@@REPLACE_ME@@";
                    if (option.isMultiValue()) {
                        baseDesc += "\nThe option is multivalued, and you can use the " + option.getName()
                                    + "(String, Object) method to add a value (call the method multiple times to set more values).";
                    }
                    baseDesc += "\n";
                    // the Endpoint DSL currently requires to provide the entire
                    // context-path and not as individual options
                    // so lets only mark query parameters that are required as
                    // required
                    if ("parameter".equals(option.getKind()) && option.isRequired()) {
                        baseDesc += "\nRequired: true";
                    }
                    // include default value (if any)
                    if (option.getDefaultValue() != null) {
                        // must xml encode default value so its valid as javadoc
                        String value = JavadocHelper.xmlEncode(option.getDefaultValue().toString());
                        baseDesc += "\nDefault: " + value;
                    }
                    baseDesc += "\nGroup: " + option.getGroup();
                }

                boolean multiValued = option.isMultiValue();
                if (multiValued) {
                    // multi value option that takes one value
                    String desc = baseDesc.replace("@@REPLACE_ME@@",
                            "\nThe option is a: <code>" + ogtype.toString().replace("<", "&lt;").replace(">", "&gt;")
                                                                     + "</code> type.");
                    desc = JavadocHelper.xmlEncode(desc);

                    Method fluent = target.addMethod().setDefault().setName(option.getName())
                            .setReturnType(new GenericType(loadClass(target.getCanonicalName())))
                            .addParameter(new GenericType(String.class), "key")
                            .addParameter(new GenericType(Object.class), "value")
                            .setBody("doSetMultiValueProperty(\"" + option.getName() + "\", \"" + option.getPrefix()
                                     + "\" + key, value);",
                                    "return this;\n");
                    if (option.isDeprecated()) {
                        fluent.addAnnotation(Deprecated.class);
                    }

                    String text = desc;
                    text += "\n\n@param key the option key";
                    text += "\n@param value the option value";
                    text += "\n@return the dsl builder\n";
                    fluent.getJavaDoc().setText(text);
                    // add multi value method that takes a Map
                    fluent = target.addMethod().setDefault().setName(option.getName())
                            .setReturnType(new GenericType(loadClass(target.getCanonicalName())))
                            .addParameter(new GenericType(Map.class), "values")
                            .setBody("doSetMultiValueProperties(\"" + option.getName() + "\", \"" + option.getPrefix()
                                     + "\", values);",
                                    "return this;\n");
                    if (option.isDeprecated()) {
                        fluent.addAnnotation(Deprecated.class);
                    }
                    text = desc;
                    text += "\n\n@param values the values";
                    text += "\n@return the dsl builder\n";
                    fluent.getJavaDoc().setText(text);
                } else {
                    // regular option
                    String desc = baseDesc.replace("@@REPLACE_ME@@",
                            "\nThe option is a: <code>" + ogtype.toString().replace("<", "&lt;").replace(">", "&gt;")
                                                                     + "</code> type.");
                    desc = JavadocHelper.xmlEncode(desc);

                    Method fluent = target.addMethod().setDefault().setName(option.getName())
                            .setReturnType(new GenericType(loadClass(target.getCanonicalName())))
                            .addParameter(isPrimitive(ogtype.toString()) ? ogtype : gtype, option.getName())
                            .setBody("doSetProperty(\"" + option.getName() + "\", " + option.getName() + ");",
                                    "return this;\n");
                    if (option.isDeprecated()) {
                        fluent.addAnnotation(Deprecated.class);
                    }
                    String text = desc;
                    text += "\n\n@param " + option.getName() + " the value to set";
                    text += "\n@return the dsl builder\n";
                    fluent.getJavaDoc().setText(text);

                    if (ogtype.getRawClass() != String.class) {
                        // regular option by String parameter variant
                        desc = baseDesc.replace("@@REPLACE_ME@@",
                                "\nThe option will be converted to a <code>"
                                                                  + ogtype.toString().replace("<", "&lt;").replace(">", "&gt;")
                                                                  + "</code> type.");
                        desc = JavadocHelper.xmlEncode(desc);

                        fluent = target.addMethod().setDefault().setName(option.getName())
                                .setReturnType(new GenericType(loadClass(target.getCanonicalName())))
                                .addParameter(new GenericType(String.class), option.getName())
                                .setBody("doSetProperty(\"" + option.getName() + "\", " + option.getName() + ");",
                                        "return this;\n");
                        if (option.isDeprecated()) {
                            fluent.addAnnotation(Deprecated.class);
                        }
                        text = desc;
                        text += "\n\n@param " + option.getName() + " the value to set";
                        text += "\n@return the dsl builder\n";
                        fluent.getJavaDoc().setText(text);
                    }
                }
            }
        }

        javaClass.removeImport("T");

        JavaClass dslClass = javaClass.addNestedType();
        dslClass.setName(getComponentNameFromType(componentClassName) + "Builders");
        dslClass.setClass(false);

        Method method = javaClass.addMethod().setStatic().setName("endpointBuilder")
                .addParameter(String.class, "componentName")
                .addParameter(String.class, "path")
                .setReturnType(new GenericType(loadClass(builderClass.getCanonicalName())))
                .setBody("class " + builderName + "Impl extends AbstractEndpointBuilder implements " + builderName
                         + ", Advanced" + builderName + " {",
                        "    public " + builderName + "Impl(String path) {", "        super(componentName, path);", "    }",
                        "}",
                        "return new " + builderName + "Impl(path);", "");
        if (model.isDeprecated()) {
            method.addAnnotation(Deprecated.class);
        }

        if (aliases.size() == 1) {
            String desc = getMainDescription(model);
            String methodName = camelCaseLower(model.getScheme());
            method = dslClass.addMethod().setStatic().setName(methodName)
                    .addParameter(String.class, "path")
                    .setReturnType(new GenericType(loadClass(builderClass.getCanonicalName())))
                    .setDefault()
                    .setBodyF("return %s.%s(%s);", javaClass.getName(), "endpointBuilder",
                            "\"" + model.getScheme() + "\", path");
            String javaDoc = desc;
            javaDoc += "\n\n@param path " + pathParameterJavaDoc(model);
            javaDoc += "\n@return the dsl builder\n";
            method.getJavaDoc().setText(javaDoc);
            if (model.isDeprecated()) {
                method.addAnnotation(Deprecated.class);
            }

            // copy method for the static builders (which allows to use the endpoint-dsl from outside EndpointRouteBuilder)
            method = method.copy();
            method.setPublic().setStatic();
            method.setReturnType(builderClass.getCanonicalName().replace('$', '.'));
            method.setBodyF("return %s.%s(%s);", javaClass.getCanonicalName(), "endpointBuilder",
                    "\"" + model.getScheme() + "\", path");
            staticBuilders.add(method);

            method = dslClass.addMethod().setStatic().setName(methodName)
                    .addParameter(String.class, "componentName")
                    .addParameter(String.class, "path")
                    .setReturnType(new GenericType(loadClass(builderClass.getCanonicalName())))
                    .setDefault()
                    .setBodyF("return %s.%s(%s);", javaClass.getName(), "endpointBuilder", "componentName, path");
            javaDoc = desc;
            javaDoc += "\n\n@param componentName to use a custom component name for the endpoint instead of the default name";
            javaDoc += "\n@param path " + pathParameterJavaDoc(model);
            javaDoc += "\n@return the dsl builder\n";
            method.getJavaDoc().setText(javaDoc);
            if (model.isDeprecated()) {
                method.addAnnotation(Deprecated.class);
            }

            // copy method for the static builders (which allows to use the endpoint-dsl from outside EndpointRouteBuilder)
            method = method.copy();
            method.setPublic().setStatic();
            method.setReturnType(builderClass.getCanonicalName().replace('$', '.'));
            method.setBodyF("return %s.%s(%s);", javaClass.getCanonicalName(), "endpointBuilder", "componentName, path");
            staticBuilders.add(method);
        } else {
            // we only want the first alias (master scheme) as static builders
            boolean firstAlias = true;

            for (ComponentModel componentModel : aliases) {
                String desc = getMainDescription(componentModel);
                String methodName = camelCaseLower(componentModel.getScheme());
                method = dslClass.addMethod().setStatic().setName(methodName)
                        .addParameter(String.class, "path")
                        .setReturnType(new GenericType(loadClass(builderClass.getCanonicalName())))
                        .setDefault()
                        .setBodyF("return %s.%s(%s);", javaClass.getName(), "endpointBuilder",
                                "\"" + componentModel.getScheme() + "\", path");
                String javaDoc = desc;
                javaDoc += "\n\n@param path " + pathParameterJavaDoc(componentModel);
                javaDoc += "\n@return the dsl builder\n";
                method.getJavaDoc().setText(javaDoc);
                if (componentModel.isDeprecated()) {
                    method.addAnnotation(Deprecated.class);
                }

                // we only want the first alias (master scheme) as static builders
                if (firstAlias) {
                    // copy method for the static builders (which allows to use the endpoint-dsl from outside EndpointRouteBuilder)
                    method = method.copy();
                    method.setPublic().setStatic();
                    method.setReturnType(builderClass.getCanonicalName().replace('$', '.'));
                    method.setBodyF("return %s.%s(%s);", javaClass.getCanonicalName(), "endpointBuilder",
                            "\"" + componentModel.getScheme() + "\", path");
                    staticBuilders.add(method);
                }

                // we only want first alias for variation with custom component name
                if (firstAlias) {
                    method = dslClass.addMethod().setStatic().setName(methodName)
                            .addParameter(String.class, "componentName")
                            .addParameter(String.class, "path")
                            .setReturnType(new GenericType(loadClass(builderClass.getCanonicalName())))
                            .setDefault()
                            .setBodyF("return %s.%s(%s);", javaClass.getName(), "endpointBuilder", "componentName, path");
                    javaDoc = desc;
                    javaDoc += "\n\n@param componentName to use a custom component name for the endpoint instead of the default name";
                    javaDoc += "\n@param path " + pathParameterJavaDoc(componentModel);
                    javaDoc += "\n@return the dsl builder\n";
                    method.getJavaDoc().setText(javaDoc);
                    if (componentModel.isDeprecated()) {
                        method.addAnnotation(Deprecated.class);
                    }
                }

                // we only want the first alias (master scheme) as static builders
                if (firstAlias) {
                    // copy method for the static builders (which allows to use the endpoint-dsl from outside EndpointRouteBuilder)
                    method = method.copy();
                    method.setPublic().setStatic();
                    method.setReturnType(builderClass.getCanonicalName().replace('$', '.'));
                    method.setBodyF("return %s.%s(%s);", javaClass.getCanonicalName(), "endpointBuilder",
                            "componentName, path");
                    staticBuilders.add(method);
                }

                firstAlias = false;
            }
        }

        return writeSourceIfChanged(javaClass, componentsFactoriesPackageName.replace('.', '/'), builderName + "Factory.java",
                false);
    }

    private static String pathParameterJavaDoc(ComponentModel model) {
        int pos = model.getSyntax().indexOf(':');
        if (pos != -1) {
            return model.getSyntax().substring(pos + 1);
        } else {
            return model.getSyntax();
        }
    }

    private boolean synchronizeEndpointBuilderFactoryInterface(List<File> factories) throws MojoFailureException {
        JavaClass javaClass = new JavaClass(getProjectClassLoader());
        javaClass.setPackage(endpointFactoriesPackageName);
        javaClass.setName("EndpointBuilderFactory");
        javaClass.setClass(false);
        javaClass.setPublic();
        javaClass.getJavaDoc().setText(GENERATED_MSG);
        javaClass.addAnnotation(Generated.class).setStringValue("value", EndpointDslMojo.class.getName());
        javaClass.addImport("java.util.List");
        javaClass.addImport("java.util.stream.Collectors");
        javaClass.addImport("java.util.stream.Stream");
        javaClass.addMethod().setDefault().setReturnType("org.apache.camel.Expression").setName("endpoints")
                .addParameter("org.apache.camel.builder.EndpointProducerBuilder", "endpoints", true)
                .setBody("return new org.apache.camel.support.ExpressionAdapter() {",
                        "    List<org.apache.camel.Expression> expressions = Stream.of(endpoints)",
                        "        .map(org.apache.camel.builder.EndpointProducerBuilder::expr)",
                        "        .collect(Collectors.toList());", "", "    @Override",
                        "    public Object evaluate(org.apache.camel.Exchange exchange) {",
                        "        return expressions.stream().map(e -> e.evaluate(exchange, Object.class)).collect(Collectors.toList());",
                        "    }", "};");

        for (File factory : factories) {
            String factoryName = Strings.before(factory.getName(), ".");
            String endpointsName = factoryName.replace("EndpointBuilderFactory", "Builders");
            javaClass.implementInterface(componentsFactoriesPackageName + "." + factoryName + "." + endpointsName);
        }

        return writeSourceIfChanged("//CHECKSTYLE:OFF\n" + javaClass.printClass() + "\n//CHECKSTYLE:ON",
                endpointFactoriesPackageName.replace('.', '/'), "EndpointBuilderFactory.java");
    }

    private boolean synchronizeEndpointBuildersInterface(List<File> factories) throws MojoFailureException {
        JavaClass javaClass = new JavaClass(getProjectClassLoader());
        javaClass.setPackage(endpointFactoriesPackageName);
        javaClass.setName("EndpointBuilders");
        javaClass.setClass(false);
        javaClass.setPublic();
        javaClass.getJavaDoc().setText(GENERATED_MSG);
        javaClass.addAnnotation(Generated.class).setStringValue("value", EndpointDslMojo.class.getName());

        for (File factory : factories) {
            javaClass.implementInterface(componentsFactoriesPackageName + "." + Strings.before(factory.getName(), "."));
        }

        return writeSourceIfChanged("//CHECKSTYLE:OFF\n" + javaClass.printClass() + "\n//CHECKSTYLE:ON",
                endpointFactoriesPackageName.replace(".", "/"), "EndpointBuilders.java");
    }

    private boolean synchronizeEndpointBuildersStaticClass(List<Method> methods) throws MojoFailureException {
        File file = new File(
                sourcesOutputDir.getPath() + "/" + endpointFactoriesPackageName.replace(".", "/"),
                "StaticEndpointBuilders.java");
        if (file.exists()) {
            // does the file already exists
            try {
                // parse existing source file with roaster to find existing methods which we should keep
                String sourceCode = loadText(file);
                JavaClassSource source = (JavaClassSource) Roaster.parse(sourceCode);
                // add existing methods
                for (MethodSource ms : source.getMethods()) {
                    boolean exist = methods.stream().anyMatch(
                            m -> m.getName().equals(ms.getName()) && m.getParameters().size() == ms.getParameters().size());
                    if (!exist) {
                        // the existing file has a method we dont have so create a method and add
                        Method method = new Method();
                        if (ms.isStatic()) {
                            method.setStatic();
                        }
                        method.setPublic();
                        method.setName(ms.getName());
                        // roaster dont preserve the message body with nicely formatted space after comma
                        String body = ms.getBody();
                        body = body.replaceAll(",(\\S)", ", $1");
                        method.setBody(body);
                        method.setReturnType(getQualifiedType(ms.getReturnType()));
                        for (Object o : ms.getParameters()) {
                            if (o instanceof ParameterSource) {
                                ParameterSource ps = (ParameterSource) o;
                                method.addParameter(getQualifiedType(ps.getType()), ps.getName());
                            }
                        }
                        String doc = extractJavaDoc(sourceCode, ms);
                        method.getJavaDoc().setFullText(doc);
                        if (ms.getAnnotation(Deprecated.class) != null) {
                            method.addAnnotation(Deprecated.class);
                        }
                        methods.add(method);
                    }
                }
            } catch (IOException e) {
                throw new MojoFailureException(
                        "Cannot parse existing java source file: " + file + " due to " + e.getMessage(), e);
            }
        }

        JavaClass javaClass = new JavaClass(getProjectClassLoader());
        javaClass.setPackage(endpointFactoriesPackageName);
        javaClass.setName("StaticEndpointBuilders");
        javaClass.setClass(true);
        javaClass.setPublic();
        javaClass.getJavaDoc().setText(GENERATED_MSG);
        javaClass.addAnnotation(Generated.class).setStringValue("value", EndpointDslMojo.class.getName());

        // sort methods
        Collections.sort(methods, (m1, m2) -> m1.getName().compareToIgnoreCase(m2.getName()));
        // create method
        for (Method method : methods) {
            javaClass.addMethod(method);
        }

        String printClass = javaClass.printClass();

        return writeSourceIfChanged("//CHECKSTYLE:OFF\n" + printClass + "\n//CHECKSTYLE:ON",
                endpointFactoriesPackageName.replace(".", "/"), "StaticEndpointBuilders.java");
    }

    private static String getQualifiedType(Type type) {
        String val = type.getQualifiedName();
        if (val.startsWith("java.lang.")) {
            val = val.substring(10);
        }
        return val;
    }

    protected static String extractJavaDoc(String sourceCode, MethodSource ms) throws IOException {
        // the javadoc is mangled by roaster (sadly it does not preserve newlines and original formatting)
        // so we need to load it from the original source file
        Object internal = ms.getJavaDoc().getInternal();
        if (internal instanceof ASTNode) {
            int pos = ((ASTNode) internal).getStartPosition();
            int len = ((ASTNode) internal).getLength();
            if (pos > 0 && len > 0) {
                String doc = sourceCode.substring(pos, pos + len);
                LineNumberReader ln = new LineNumberReader(new StringReader(doc));
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = ln.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("/**") || line.startsWith("*/")) {
                        continue;
                    }
                    if (line.startsWith("*")) {
                        line = line.substring(1).trim();
                    }
                    sb.append(line);
                    sb.append("\n");
                }
                doc = sb.toString();
                return doc;
            }
        }
        return null;
    }

    private List<File> loadAllComponentsDslEndpointFactoriesAsFile() {
        final File allComponentsDslEndpointFactory
                = new File(sourcesOutputDir, componentsFactoriesPackageName.replace('.', '/'));
        final File[] files = allComponentsDslEndpointFactory.listFiles();

        if (files == null) {
            return Collections.emptyList();
        }

        // load components
        return Arrays.stream(files).filter(file -> file.isFile() && file.getName().endsWith(".java") && file.exists()).sorted()
                .collect(Collectors.toList());
    }

    private static String camelCaseLower(String s) {
        int i;
        while (s != null && (i = s.indexOf('-')) > 0) {
            s = s.substring(0, i) + s.substring(i + 1, i + 2).toUpperCase() + s.substring(i + 2);
        }
        while (s != null && (i = s.indexOf('+')) > 0) {
            s = s.substring(0, i) + s.substring(i + 1, i + 2).toUpperCase() + s.substring(i + 2);
        }
        if (s != null) {
            s = s.substring(0, 1).toLowerCase() + s.substring(1);
            switch (s) {
                case "class":
                    s = "clas";
                    break;
                case "package":
                    s = "packag";
                    break;
                case "rest":
                    s = "restEndpoint";
                    break;
                default:
                    break;
            }
        }
        return s;
    }

    private String getMainDescription(ComponentModel model) {
        String desc = model.getTitle() + " (" + model.getArtifactId() + ")";
        desc += "\n" + model.getDescription();
        desc += "\n";
        desc += "\nCategory: " + model.getLabel();
        desc += "\nSince: " + model.getFirstVersionShort();
        desc += "\nMaven coordinates: " + project.getGroupId() + ":" + project.getArtifactId();

        // include javadoc for all path parameters and mark which are required
        desc += "\n";
        desc += "\nSyntax: <code>" + model.getSyntax() + "</code>";
        for (EndpointOptionModel option : model.getEndpointOptions()) {
            if ("path".equals(option.getKind())) {
                desc += "\n";
                desc += "\nPath parameter: " + option.getName();
                if (option.isRequired()) {
                    desc += " (required)";
                }
                if (option.isDeprecated()) {
                    desc += " <strong>deprecated</strong>";
                }
                desc += "\n" + option.getDescription();
                if (option.getDefaultValue() != null) {
                    desc += "\nDefault value: " + option.getDefaultValue();
                }
                // TODO: default value note ?
                if (option.getEnums() != null && !option.getEnums().isEmpty()) {
                    desc += "\nThere are " + option.getEnums().size() + " enums and the value can be one of: "
                            + wrapEnumValues(option.getEnums());
                }
            }
        }
        return desc;
    }

    private String wrapEnumValues(List<String> enumValues) {
        // comma to space so we can wrap words (which uses space)
        return String.join(", ", enumValues);
    }

    private String getComponentNameFromType(String type) {
        int pos = type.lastIndexOf('.');
        String name = type.substring(pos + 1).replace("Component", "");

        switch (type) {
            case "org.apache.camel.component.atmosphere.websocket.WebsocketComponent":
                return "AtmosphereWebsocket";
            case "org.apache.camel.component.zookeepermaster.MasterComponent":
                return "ZooKeeperMaster";
            case "org.apache.camel.component.jetty9.JettyHttpComponent9":
                return "JettyHttp";
            default:
                return name;
        }
    }

    private String getEndpointName(String type) {
        return getComponentNameFromType(type) + "EndpointBuilder";
    }

    private String findEndpointClassName(String type) {
        String endpointName = type.replaceFirst("Component", "Endpoint");
        //
        // HACKS
        //
        switch (type) {
            case "org.apache.camel.component.disruptor.vm.DisruptorVmComponent":
                return "org.apache.camel.component.disruptor.DisruptorEndpoint";
            case "org.apache.camel.component.etcd.EtcdComponent":
                return "org.apache.camel.component.etcd.AbstractEtcdPollingEndpoint";
            case "org.apache.camel.websocket.jsr356.JSR356WebSocketComponent":
                return "org.apache.camel.websocket.jsr356.JSR356Endpoint";
            default:
                return endpointName;
        }
    }

    private Field findField(Class<?> realComponentClass, Class<?> realEndpointClass, EndpointOptionModel option)
            throws NoSuchFieldException {
        Field field = null;
        List<Class<?>> classes = new ArrayList<>();
        classes.add(realComponentClass);
        classes.add(realEndpointClass);
        while (!classes.isEmpty()) {
            Class cl = classes.remove(0);
            for (Field f : cl.getDeclaredFields()) {
                String n = f.getName();
                UriPath path = f.getAnnotation(UriPath.class);
                if (path != null && !Strings.isEmpty(path.name())) {
                    n = path.name();
                }
                UriParam param = f.getAnnotation(UriParam.class);
                if (param != null && !Strings.isEmpty(param.name())) {
                    n = param.name();
                }
                if (n.equals(option.getName())) {
                    field = f;
                    break;
                }
                if (f.getType().isAnnotationPresent(UriParams.class)) {
                    classes.add(f.getType());
                }
            }
            if (field != null) {
                break;
            }
            cl = cl.getSuperclass();
            if (cl != null) {
                classes.add(cl);
            }
        }
        if (field == null) {
            throw new NoSuchFieldException("Could not find field for option " + option.getName());
        }
        return field;
    }

    private static boolean isPrimitive(String type) {
        return PRIMITIVEMAP.containsKey(type);
    }

    private GenericType getType(JavaClass javaClass, Map<String, JavaClass> enumClasses, List<String> enums, String type) {
        type = type.trim();
        // Check if this is an array
        if (type.endsWith("[]")) {
            GenericType t = getType(javaClass, enumClasses, enums, type.substring(0, type.length() - 2));
            return new GenericType(Array.newInstance(t.getRawClass(), 0).getClass(), t);
        }
        // Check if this is a generic
        int genericIndex = type.indexOf('<');
        if (genericIndex > 0) {
            if (!type.endsWith(">")) {
                throw new IllegalArgumentException("Can not load type: " + type);
            }
            GenericType base = getType(javaClass, enumClasses, enums, type.substring(0, genericIndex));
            if (base.getRawClass() == Object.class) {
                return base;
            }
            String[] params = splitParams(type.substring(genericIndex + 1, type.length() - 1));
            GenericType[] types = new GenericType[params.length];
            for (int i = 0; i < params.length; i++) {
                types[i] = getType(javaClass, enumClasses, enums, params[i]);
            }
            return new GenericType(base.getRawClass(), types);
        }
        // Primitive
        if (isPrimitive(type)) {
            return new GenericType(PRIMITIVEMAP.get(type));
        }
        // Extends
        if (type.startsWith("? extends ")) {
            String raw = type.substring("? extends ".length());
            return new GenericType(loadClass(raw), BoundType.Extends);
        }
        // Super
        if (type.startsWith("? super ")) {
            String raw = type.substring("? extends ".length());
            return new GenericType(loadClass(raw), BoundType.Super);
        }
        // Wildcard
        if (type.equals("?")) {
            return new GenericType(Object.class, BoundType.Extends);
        }
        if (loadClass(type).isEnum() && !isCamelCoreType(type)) {
            String enumClassName = type.substring(type.lastIndexOf('.') + 1);
            if (enumClassName.contains("$")) {
                enumClassName = enumClassName.substring(enumClassName.indexOf('$') + 1);
            }
            JavaClass enumClass = enumClasses.get(enumClassName);
            if (enumClass == null) {
                enumClass = javaClass.addNestedType().setPackagePrivate().setName(enumClassName).setEnum(true);
                enumClass.getJavaDoc().setText("Proxy enum for <code>" + type + "</code> enum.");
                enumClasses.put(enumClassName, enumClass);
                for (Object value : loadClass(type).getEnumConstants()) {
                    enumClass.addValue((((Enum<?>) value).name()).replace('.', '_').replace('-', '_'));
                }
            }
            type = javaClass.getPackage() + "." + javaClass.getName() + "$" + enumClassName;
            return new GenericType(generateDummyClass(type));
        }
        if (!isCamelCoreType(type)) {
            getLog().debug("Substituting java.lang.Object to " + type);
            return new GenericType(Object.class);
        }
        return new GenericType(loadClass(type));
    }

    private String[] splitParams(String string) {
        List<String> params = new ArrayList<>();
        int cur = 0;
        int start = 0;
        int opened = 0;
        while (true) {
            int nextComma = string.indexOf(',', cur);
            int nextOpen = string.indexOf('<', cur);
            int nextClose = string.indexOf('>', cur);
            if (nextComma < 0) {
                params.add(string.substring(start));
                return params.toArray(new String[0]);
            } else if ((nextOpen < 0 || nextComma < nextOpen) && (nextClose < 0 || nextComma < nextClose) && opened == 0) {
                params.add(string.substring(start, nextComma));
                start = cur = nextComma + 1;
            } else if (nextOpen < 0) {
                if (--opened < 0) {
                    throw new IllegalStateException();
                }
                cur = nextClose + 1;
            } else if (nextClose < 0 || nextOpen < nextClose) {
                ++opened;
                cur = nextOpen + 1;
            } else {
                if (--opened < 0) {
                    throw new IllegalStateException();
                }
                cur = nextClose + 1;
            }
        }
    }

    private boolean isCamelCoreType(String type) {
        return type.startsWith("java.") || type.matches("org\\.apache\\.camel\\.(spi\\.)?([A-Za-z]+)");
    }

    private Class generateDummyClass(String clazzName) {
        return getProjectClassLoader().generateDummyClass(clazzName);
    }

    private static String loadComponentJson(Map<File, Supplier<String>> jsonFiles, String componentName) {
        return loadJsonOfType(jsonFiles, componentName, "component");
    }

    private static String loadJsonOfType(Map<File, Supplier<String>> jsonFiles, String modelName, String type) {
        for (Map.Entry<File, Supplier<String>> entry : jsonFiles.entrySet()) {
            if (entry.getKey().getName().equals(modelName + PackageHelper.JSON_SUFIX)) {
                String json = entry.getValue().get();
                if (type.equals(PackageHelper.getSchemaKind(json))) {
                    return json;
                }
            }
        }
        return null;
    }

    private void findComponentNames(File dir, Set<String> componentNames) {
        File f = new File(dir, "classes/META-INF/services/org/apache/camel/component");

        if (f.exists() && f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File file : files) {
                    // skip directories as there may be a sub .resolver
                    // directory
                    if (file.isDirectory()) {
                        continue;
                    }
                    String name = file.getName();
                    if (name.charAt(0) != '.') {
                        componentNames.add(name);
                    }
                }
            }
        }
    }

    private boolean writeSourceIfChanged(JavaClass source, String filePath, String fileName, boolean innerClassesLast)
            throws MojoFailureException {
        return writeSourceIfChanged(source.printClass(innerClassesLast), filePath, fileName);
    }

    private boolean writeSourceIfChanged(String source, String filePath, String fileName) throws MojoFailureException {
        try {
            String header;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt")) {
                header = loadText(is);
            }
            String code = header + source;
            getLog().debug("Source code generated:\n" + code);

            return updateResource(sourcesOutputDir.toPath(), filePath + "/" + fileName, code);
        } catch (Exception e) {
            throw new MojoFailureException("IOError with file " + filePath + "/" + fileName, e);
        }
    }
}
