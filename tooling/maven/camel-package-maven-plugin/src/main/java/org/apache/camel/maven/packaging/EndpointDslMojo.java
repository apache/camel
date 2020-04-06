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

import org.apache.camel.maven.packaging.dsl.component.ComponentsDslMetadataRegistry;
import org.apache.camel.maven.packaging.generics.GenericsUtil;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.ComponentModel.EndpointOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
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
import org.sonatype.plexus.build.incremental.BuildContext;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;

/**
 * Generate Endpoint DSL source files for Components.
 */
@Mojo(name = "generate-endpoint-dsl", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
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

    DynamicClassLoader projectClassLoader;

    @Override
    public void execute(MavenProject project, MavenProjectHelper projectHelper, BuildContext buildContext) throws MojoFailureException, MojoExecutionException {
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
        try {
            projectClassLoader = DynamicClassLoader.createDynamicClassLoader(project.getTestClasspathElements());
        } catch (org.apache.maven.artifact.DependencyResolutionRequiredException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        Path root = findCamelDirectory(baseDir, "core/camel-endpointdsl").toPath();
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
            files = Files.find(buildDir.toPath(), Integer.MAX_VALUE, (p, a) -> a.isRegularFile() && p.toFile().getName().endsWith(PackageHelper.JSON_SUFIX))
                .collect(Collectors.toMap(Path::toFile, s -> cache(() -> loadJson(s.toFile()))));
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        // generate component endpoint DSL files and write them
        executeComponent(files);
    }

    private static String loadJson(File file) {
        try {
            return PackageHelper.loadText(file);
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
            Map<String, List<ComponentModel>> grModels = allModels.stream().collect(Collectors.groupingBy(ComponentModel::getJavaType));
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

    private void createEndpointDsl(ComponentModel model, List<ComponentModel> aliases, String overrideComponentName) throws MojoFailureException {
        boolean updated = doCreateEndpointDsl(model, aliases, overrideComponentName);

        // Update components metadata
        getLog().debug("Load components EndpointFactories");
        List<File> endpointFactories = loadAllComponentsDslEndpointFactoriesAsFile();

        getLog().debug("Regenerate EndpointBuilderFactory");
        // make sure EndpointBuilderFactory is synced
        updated |= synchronizeEndpointBuilderFactoryInterface(endpointFactories);

        getLog().debug("Regenerate EndpointBuilders");
        // make sure EndpointBuilders is synced
        updated |= synchronizeEndpointBuildersInterface(endpointFactories);

        if (updated) {
            getLog().info("Updated EndpointDsl: " + model.getScheme());
        }
    }

    private ComponentsDslMetadataRegistry syncAndUpdateComponentsMetadataRegistry(final ComponentModel componentModel, final String className) {
        final ComponentsDslMetadataRegistry componentsDslMetadataRegistry =
                new ComponentsDslMetadataRegistry(sourcesOutputDir.toPath()
                        .resolve(componentsFactoriesPackageName.replace('.', '/')).toFile(),
                            componentsMetadata);
        componentsDslMetadataRegistry.addComponentToMetadataAndSyncMetadataFile(componentModel, className);

        getLog().debug("Update components metadata with " + className);

        return componentsDslMetadataRegistry;
    }

    @SuppressWarnings("checkstyle:methodlength")
    private boolean doCreateEndpointDsl(ComponentModel model, List<ComponentModel> aliases, String overrideComponentName) throws MojoFailureException {
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

        if (!realEndpointClass.getAnnotation(UriEndpoint.class).producerOnly() && !realEndpointClass.getAnnotation(UriEndpoint.class).consumerOnly()) {
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
                advancedConsumerClass.getJavaDoc().setText("Advanced builder for endpoint consumers for the " + model.getTitle() + " component.");

                consumerClass.addMethod().setName("advanced").setReturnType(loadClass(advancedConsumerClass.getCanonicalName())).setDefault()
                    .setBody("return (Advanced" + consumerName + ") this;");
                advancedConsumerClass.addMethod().setName("basic").setReturnType(loadClass(consumerClass.getCanonicalName())).setDefault()
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
                advancedProducerClass.getJavaDoc().setText("Advanced builder for endpoint producers for the " + model.getTitle() + " component.");

                producerClass.addMethod().setName("advanced").setReturnType(loadClass(advancedProducerClass.getCanonicalName())).setDefault()
                    .setBody("return (Advanced" + producerName + ") this;");
                advancedProducerClass.addMethod().setName("basic").setReturnType(loadClass(producerClass.getCanonicalName())).setDefault()
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
            advancedBuilderClass.getJavaDoc().setText("Advanced builder for endpoint for the " + model.getTitle() + " component.");

            builderClass.addMethod().setName("advanced").setReturnType(loadClass(advancedBuilderClass.getCanonicalName())).setDefault()
                .setBody("return (Advanced" + builderName + ") this;");
            advancedBuilderClass.addMethod().setName("basic").setReturnType(loadClass(builderClass.getCanonicalName())).setDefault().setBody("return (" + builderName + ") this;");
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
                Method fluent = target.addMethod().setDefault().setName(option.getName()).setReturnType(new GenericType(loadClass(target.getCanonicalName())))
                    .addParameter(isPrimitive(ogtype.toString()) ? ogtype : gtype, option.getName())
                    .setBody("doSetProperty(\"" + option.getName() + "\", " + option.getName() + ");", "return this;\n");

                if (option.isDeprecated()) {
                    fluent.addAnnotation(Deprecated.class);
                }
                if (!Strings.isEmpty(option.getDescription())) {
                    String desc = option.getDescription();
                    if (!desc.endsWith(".")) {
                        desc += ".";
                    }
                    desc += "\n";
                    desc += "\nThe option is a: <code>" + ogtype.toString().replace("<", "&lt;").replace(">", "&gt;") + "</code> type.";
                    desc += "\n";
                    // the Endpoint DSL currently requires to provide the entire
                    // context-path and not as individual options
                    // so lets only mark query parameters that are required as
                    // required
                    if ("parameter".equals(option.getKind()) && option.isRequired()) {
                        desc += "\nRequired: true";
                    }
                    // include default value (if any)
                    if (option.getDefaultValue() != null) {
                        desc += "\nDefault: " + option.getDefaultValue();
                    }
                    desc += "\nGroup: " + option.getGroup();
                    fluent.getJavaDoc().setFullText(desc);
                }

                if (ogtype.getRawClass() != String.class) {
                    fluent = target.addMethod().setDefault().setName(option.getName()).setReturnType(new GenericType(loadClass(target.getCanonicalName())))
                        .addParameter(new GenericType(String.class), option.getName())
                        .setBody("doSetProperty(\"" + option.getName() + "\", " + option.getName() + ");", "return this;\n");

                    if (option.isDeprecated()) {
                        fluent.addAnnotation(Deprecated.class);
                    }
                    if (!Strings.isEmpty(option.getDescription())) {
                        String desc = option.getDescription();
                        if (!desc.endsWith(".")) {
                            desc += ".";
                        }
                        desc += "\n";
                        desc += "\nThe option will be converted to a <code>" + ogtype.toString().replace("<", "&lt;").replace(">", "&gt;") + "</code> type.";
                        desc += "\n";
                        // the Endpoint DSL currently requires to provide the
                        // entire context-path and not as individual options
                        // so lets only mark query parameters that are required
                        // as required
                        if ("parameter".equals(option.getKind()) && option.isRequired()) {
                            desc += "\nRequired: true";
                        }
                        // include default value (if any)
                        if (option.getDefaultValue() != null) {
                            desc += "\nDefault: " + option.getDefaultValue();
                        }
                        desc += "\nGroup: " + option.getGroup();
                        fluent.getJavaDoc().setFullText(desc);
                    }
                }
            }
        }

        javaClass.removeImport("T");

        JavaClass dslClass = javaClass.addNestedType();
        dslClass.setName(getComponentNameFromType(componentClassName) + "Builders");
        dslClass.setClass(false);

        if (aliases.size() == 1) {
            Method method = javaClass.addMethod().setStatic().setName(camelCaseLower(model.getScheme())).addParameter(String.class, "path")
                .setReturnType(new GenericType(loadClass(builderClass.getCanonicalName())))
                .setBody("class " + builderName + "Impl extends AbstractEndpointBuilder implements " + builderName + ", Advanced" + builderName + " {",
                         "    public " + builderName + "Impl(String path) {", "        super(\"" + model.getScheme() + "\", path);", "    }", "}",
                         "return new " + builderName + "Impl(path);", "");

            if (model.isDeprecated()) {
                method.addAnnotation(Deprecated.class);
            }
            String desc = getMainDescription(model);
            method.getJavaDoc().setText(desc);

            dslClass.addMethod(method.copy()).setDefault().setBodyF("return %s.%s(%s);", javaClass.getName(), method.getName(), String.join(",", method.getParametersNames()));

        } else {
            for (ComponentModel componentModel : aliases) {
                Method method = javaClass.addMethod().setStatic().setName(camelCaseLower(componentModel.getScheme())).addParameter(String.class, "path")
                    .setReturnType(new GenericType(loadClass(builderClass.getCanonicalName())))
                    .setBody("return " + camelCaseLower(model.getScheme()) + "(\"" + componentModel.getScheme() + "\", path);\n");

                if (model.isDeprecated()) {
                    method.addAnnotation(Deprecated.class);
                }
                String desc = getMainDescription(componentModel);
                method.getJavaDoc().setText(desc);

                dslClass.addMethod(method.copy()).setDefault().setBodyF("return %s.%s(%s);", javaClass.getName(), method.getName(), String.join(",", method.getParametersNames()));
            }

            Method method = javaClass.addMethod().setStatic().setName(camelCaseLower(model.getScheme())).addParameter(String.class, "scheme").addParameter(String.class, "path")
                .setReturnType(new GenericType(loadClass(builderClass.getCanonicalName())))
                .setBody("class " + builderName + "Impl extends AbstractEndpointBuilder implements " + builderName + ", Advanced" + builderName + " {",
                         "    public " + builderName + "Impl(String scheme, String path) {", "        super(scheme, path);", "    }", "}",
                         "return new " + builderName + "Impl(scheme, path);\n");

            if (model.isDeprecated()) {
                method.addAnnotation(Deprecated.class);
            }

            String desc = model.getTitle() + " (" + model.getArtifactId() + ")";
            desc += "\n" + model.getDescription();
            desc += "\n";
            desc += "\nCategory: " + model.getLabel();
            desc += "\nSince: " + model.getFirstVersionShort();
            desc += "\nMaven coordinates: " + project.getGroupId() + ":" + project.getArtifactId();
            method.getJavaDoc().setText(desc);

            dslClass.addMethod(method.copy()).setDefault().setBodyF("return %s.%s(%s);", javaClass.getName(), method.getName(), String.join(",", method.getParametersNames()));
        }

        return writeSourceIfChanged(javaClass, componentsFactoriesPackageName.replace('.', '/'), builderName + "Factory.java", false);
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
            .setBody("return new org.apache.camel.support.ExpressionAdapter() {", "    List<org.apache.camel.Expression> expressions = Stream.of(endpoints)",
                     "        .map(org.apache.camel.builder.EndpointProducerBuilder::expr)", "        .collect(Collectors.toList());", "", "    @Override",
                     "    public Object evaluate(org.apache.camel.Exchange exchange) {",
                     "        return expressions.stream().map(e -> e.evaluate(exchange, Object.class)).collect(Collectors.toList());", "    }", "};");

        for (File factory : factories) {
            String factoryName = Strings.before(factory.getName(), ".");
            String endpointsName = factoryName.replace("EndpointBuilderFactory", "Builders");
            javaClass.implementInterface(componentsFactoriesPackageName + "." + factoryName + "." + endpointsName);
        }

        return writeSourceIfChanged("//CHECKSTYLE:OFF\n" + javaClass.printClass() + "\n//CHECKSTYLE:ON", endpointFactoriesPackageName.replace('.', '/'), "EndpointBuilderFactory.java");
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

        return writeSourceIfChanged("//CHECKSTYLE:OFF\n" + javaClass.printClass() + "\n//CHECKSTYLE:ON", endpointFactoriesPackageName.replace(".", "/"), "EndpointBuilders.java");
    }

    private List<File> loadAllComponentsDslEndpointFactoriesAsFile() {
        final File allComponentsDslEndpointFactory = new File(sourcesOutputDir, componentsFactoriesPackageName.replace('.', '/'));
        final File[] files = allComponentsDslEndpointFactory.listFiles();

        if (files == null) {
            return Collections.emptyList();
        }

        // load components
        return Arrays.stream(files).filter(file -> file.isFile() && file.getName().endsWith(".java") && file.exists()).sorted().collect(Collectors.toList());
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
                    desc += "\nThe value can be one of: " + wrapEnumValues(option.getEnums());
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
        int pos = type.lastIndexOf(".");
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

    private Field findField(Class<?> realComponentClass, Class<?> realEndpointClass, EndpointOptionModel option) throws NoSuchFieldException {
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

    private Class<?> loadClass(String loadClassName) {
        Class<?> optionClass;
        String org = loadClassName;
        while (true) {
            try {
                optionClass = getProjectClassLoader().loadClass(loadClassName);
                break;
            } catch (ClassNotFoundException e) {
                int dotIndex = loadClassName.lastIndexOf('.');
                if (dotIndex == -1) {
                    throw new IllegalArgumentException(org);
                } else {
                    loadClassName = loadClassName.substring(0, dotIndex) + "$" + loadClassName.substring(dotIndex + 1);
                }
            }
        }
        return optionClass;
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

    private DynamicClassLoader getProjectClassLoader() {
        return projectClassLoader;
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

    private boolean writeSourceIfChanged(JavaClass source, String filePath, String fileName, boolean innerClassesLast) throws MojoFailureException {
        return writeSourceIfChanged(source.printClass(innerClassesLast), filePath, fileName);
    }

    private boolean writeSourceIfChanged(String source, String filePath, String fileName) throws MojoFailureException {
        try {
            String header;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt")) {
                header = PackageHelper.loadText(is);
            }
            String code = header + source;
            getLog().debug("Source code generated:\n" + code);

            return updateResource(sourcesOutputDir.toPath(), filePath + "/" + fileName, code);
        } catch (Exception e) {
            throw new MojoFailureException("IOError with file " + filePath + "/" + fileName, e);
        }
    }
}
