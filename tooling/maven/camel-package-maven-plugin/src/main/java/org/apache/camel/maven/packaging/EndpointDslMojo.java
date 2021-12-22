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
import java.io.LineNumberReader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Generated;

import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.ComponentModel.EndpointOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.JavadocHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.tooling.util.srcgen.GenericType;
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

    /**
     * The catalog directory where the component json files are
     */
    @Parameter(defaultValue = "${project.build.directory}/../../../catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/components")
    protected File jsonDir;

    private transient String licenseHeader;

    @Override
    public void execute(MavenProject project, MavenProjectHelper projectHelper, BuildContext buildContext)
            throws MojoFailureException, MojoExecutionException {
        buildDir = new File(project.getBuild().getDirectory());
        baseDir = project.getBasedir();
        endpointFactoriesPackageName = "org.apache.camel.builder.endpoint";
        componentsFactoriesPackageName = "org.apache.camel.builder.endpoint.dsl";
        super.execute(project, projectHelper, buildContext);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File camelDir = findCamelDirectory(baseDir, "dsl/camel-endpointdsl");
        if (camelDir == null) {
            getLog().debug("No dsl/camel-endpointdsl folder found, skipping execution");
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

        List<ComponentModel> models = new ArrayList<>();

        for (File file : jsonDir.listFiles()) {
            BaseModel<?> model = JsonMapper.generateModel(file.toPath());
            models.add((ComponentModel) model);
        }

        // generate component endpoint DSL files and write them
        executeComponent(models);
    }

    private void executeComponent(List<ComponentModel> allModels) throws MojoFailureException {
        if (!allModels.isEmpty()) {
            getLog().debug("Found " + allModels.size() + " components");

            // Group the models by implementing classes
            Map<String, List<ComponentModel>> grModels
                    = allModels.stream().collect(Collectors.groupingBy(ComponentModel::getJavaType));

            // load license header
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt")) {
                this.licenseHeader = loadText(is);
            } catch (Exception e) {
                throw new MojoFailureException("Error loading license-header-java.txt file", e);
            }

            for (List<ComponentModel> compModels : grModels.values()) {
                // if there are alias then we need to sort scheme according to the alternative schemes position
                if (compModels.size() > 1) {
                    compModels.sort((o1, o2) -> {
                        String s1 = o1.getScheme();
                        String s2 = o2.getScheme();
                        String as = o1.getAlternativeSchemes();
                        int i1 = as.indexOf(s1);
                        int i2 = as.indexOf(s2);
                        return Integer.compare(i1, i2);
                    });
                }

                ComponentModel model = compModels.get(0); // master component
                List<String> aliases = compModels.stream().map(ComponentModel::getScheme).collect(Collectors.toList());

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

        final JavaClass javaClass = new JavaClass(getProjectClassLoader());
        javaClass.setPackage(componentsFactoriesPackageName);
        javaClass.setName(builderName + "Factory");
        javaClass.setClass(false);
        javaClass.addImport("java.util.*");
        javaClass.addImport("java.util.concurrent.*");
        javaClass.addImport("java.util.function.*");
        javaClass.addImport("java.util.stream.*");
        javaClass.addImport("org.apache.camel.builder.EndpointConsumerBuilder");
        javaClass.addImport("org.apache.camel.builder.EndpointProducerBuilder");
        javaClass.addImport("org.apache.camel.builder.endpoint.AbstractEndpointBuilder");

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

        if (!model.isProducerOnly() && !model.isConsumerOnly()) {
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
        if (model.isProducerOnly()) {
            builderClass.implementInterface("EndpointProducerBuilder");
        } else if (model.isConsumerOnly()) {
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
            if (model.isProducerOnly()) {
                advancedBuilderClass.implementInterface("EndpointProducerBuilder");
            } else if (model.isConsumerOnly()) {
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
                            "\nThe option is a: <code>" + option.getJavaType().replace("<", "&lt;").replace(">", "&gt;")
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
                            "\nThe option is a: <code>" + option.getJavaType().replace("<", "&lt;").replace(">", "&gt;")
                                                                     + "</code> type.");
                    desc = JavadocHelper.xmlEncode(desc);

                    Method fluent = target.addMethod().setDefault().setName(option.getName())
                            .setReturnType(new GenericType(loadClass(target.getCanonicalName())))
                            .addParameter(optionJavaType(option), option.getName())
                            .setBody("doSetProperty(\"" + option.getName() + "\", " + option.getName() + ");",
                                    "return this;\n");
                    if (option.isDeprecated()) {
                        fluent.addAnnotation(Deprecated.class);
                    }
                    String text = desc;
                    text += "\n\n@param " + option.getName() + " the value to set";
                    text += "\n@return the dsl builder\n";
                    fluent.getJavaDoc().setText(text);

                    if (!"String".equals(optionJavaType(option))) {
                        // regular option by String parameter variant
                        desc = baseDesc.replace("@@REPLACE_ME@@",
                                "\nThe option will be converted to a <code>"
                                                                  + option.getJavaType().replace("<", "&lt;").replace(">",
                                                                          "&gt;")
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

        String adv = hasAdvanced ? ", Advanced" + builderName : "";
        Method method = javaClass.addMethod().setStatic().setName("endpointBuilder")
                .addParameter(String.class, "componentName")
                .addParameter(String.class, "path")
                .setReturnType(new GenericType(loadClass(builderClass.getCanonicalName())))
                .setBody("class " + builderName + "Impl extends AbstractEndpointBuilder implements " + builderName + adv + " {",
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

    private static String optionJavaType(EndpointOptionModel option) {
        String answer = option.getJavaType();
        if (answer.startsWith("java.lang.")) {
            return answer.substring(10);
        } else if (answer.startsWith("java.util.concurrent.")) {
            return answer.substring(21);
        } else if (answer.startsWith("java.util.function.")) {
            return answer.substring(19);
        } else if (answer.startsWith("java.util.stream.")) {
            return answer.substring(17);
        } else if (answer.startsWith("java.util.")) {
            return answer.substring(10);
        }

        return answer;
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
                        "",
                        "    private List<org.apache.camel.Expression> expressions = null;",
                        "",
                        "    @Override",
                        "    public Object evaluate(org.apache.camel.Exchange exchange) {",
                        "        return expressions.stream().map(e -> e.evaluate(exchange, Object.class)).collect(Collectors.toList());",
                        "    }",
                        "",
                        "    @Override",
                        "    public void init(org.apache.camel.CamelContext context) {",
                        "        super.init(context);",
                        "        expressions = Stream.of(endpoints)",
                        "                .map(epb -> epb.expr(context))",
                        "                .collect(Collectors.toList());",
                        "    }",
                        "};");

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
        desc += "\nMaven coordinates: " + model.getGroupId() + ":" + model.getArtifactId();

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

    private Class generateDummyClass(String clazzName) {
        return getProjectClassLoader().generateDummyClass(clazzName);
    }

    private boolean writeSourceIfChanged(JavaClass source, String filePath, String fileName, boolean innerClassesLast)
            throws MojoFailureException {
        return writeSourceIfChanged(source.printClass(innerClassesLast), filePath, fileName);
    }

    private boolean writeSourceIfChanged(String source, String filePath, String fileName) throws MojoFailureException {
        try {
            String code = licenseHeader + source;
            getLog().debug("Source code generated:\n" + code);

            return updateResource(sourcesOutputDir.toPath(), filePath + "/" + fileName, code);
        } catch (Exception e) {
            throw new MojoFailureException("IOError with file " + filePath + "/" + fileName, e);
        }
    }
}
