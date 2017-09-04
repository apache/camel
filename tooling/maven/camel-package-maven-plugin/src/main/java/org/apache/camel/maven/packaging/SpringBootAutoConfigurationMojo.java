/**
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
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.Generated;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.maven.packaging.model.ComponentOptionModel;
import org.apache.camel.maven.packaging.model.DataFormatModel;
import org.apache.camel.maven.packaging.model.DataFormatOptionModel;
import org.apache.camel.maven.packaging.model.EndpointOptionModel;
import org.apache.camel.maven.packaging.model.LanguageModel;
import org.apache.camel.maven.packaging.model.LanguageOptionModel;
import org.apache.camel.maven.packaging.model.OtherModel;
import org.apache.camel.maven.packaging.model.OtherOptionModel;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.Type;
import org.jboss.forge.roaster.model.source.Import;
import org.jboss.forge.roaster.model.source.Importer;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.PropertySource;
import org.jboss.forge.roaster.model.util.Formatter;
import org.jboss.forge.roaster.model.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.bind.RelaxedPropertyResolver;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.apache.camel.maven.packaging.JSonSchemaHelper.getPropertyDefaultValue;
import static org.apache.camel.maven.packaging.JSonSchemaHelper.getPropertyJavaType;
import static org.apache.camel.maven.packaging.JSonSchemaHelper.getPropertyType;
import static org.apache.camel.maven.packaging.JSonSchemaHelper.getSafeValue;
import static org.apache.camel.maven.packaging.JSonSchemaHelper.parseJsonSchema;
import static org.apache.camel.maven.packaging.PackageHelper.loadText;

/**
 * Generate Spring Boot auto configuration files for Camel components and data formats.
 *
 * @goal prepare-spring-boot-auto-configuration
 * @requiresDependencyResolution compile+runtime
 */
public class SpringBootAutoConfigurationMojo extends AbstractMojo {

    /**
     * Useful to move configuration towards starters.
     * Warning: the spring.factories files sometimes are used also on the main artifacts.
     * Make sure it is not the case before enabling this property.
     */
    private static final boolean DELETE_FILES_ON_MAIN_ARTIFACTS = false;

    /**
     * Suffix used for generating inner classes for nested component properties, e.g. endpoint configuration.
     */
    private static final String INNER_TYPE_SUFFIX = "NestedConfiguration";

    /**
     * Classes to exclude when adding {@link NestedConfigurationProperty} annotations.
     */
    private static final Pattern EXCLUDE_INNER_PATTERN = Pattern.compile("^((java\\.)|(javax\\.)|(org\\.springframework\\.context\\.ApplicationContext)|(freemarker\\.template\\.Configuration)).*");

    private static final Map<String, String> PRIMITIVEMAP;

    static {
        PRIMITIVEMAP = new HashMap<>();
        PRIMITIVEMAP.put("boolean", "java.lang.Boolean");
        PRIMITIVEMAP.put("char", "java.lang.Character");
        PRIMITIVEMAP.put("long", "java.lang.Long");
        PRIMITIVEMAP.put("int", "java.lang.Integer");
        PRIMITIVEMAP.put("integer", "java.lang.Integer");
        PRIMITIVEMAP.put("byte", "java.lang.Byte");
        PRIMITIVEMAP.put("short", "java.lang.Short");
        PRIMITIVEMAP.put("double", "java.lang.Double");
        PRIMITIVEMAP.put("float", "java.lang.Float");
    }

    private static final List<String> JAVA_LANG_TYPES = Arrays.asList("Boolean", "Byte", "Character", "Class", "Double", "Float", "Integer", "Long", "Object", "Short", "String");

    private static final String[] IGNORE_MODULES = {/* Non-standard -> */ "camel-grape", "camel-connector"};

    /**
     * The output directory for generated component schema file
     *
     * @parameter default-value="${project.build.directory}/classes"
     */
    protected File classesDir;

    /**
     * The maven project.
     *
     * @parameter property="project"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The project build directory
     *
     * @parameter default-value="${project.build.directory}"
     */
    protected File buildDir;

    /**
     * The base directory
     *
     * @parameter default-value="${basedir}"
     */
    protected File baseDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // Do not generate code for ignored module
        if (Arrays.asList(IGNORE_MODULES).contains(project.getArtifactId())) {
            getLog().info("Component auto-configuration will not be created: component contained in the ignore list");
            return;
        }

        // Spring-boot configuration has been moved on starters
        File starterDir = SpringBootHelper.starterDir(baseDir, project.getArtifactId());
        if (!starterDir.exists() || !(new File(starterDir, "pom.xml").exists())) {
            // If the starter does not exist, no configuration can be created
            getLog().info("Component auto-configuration will not be created: the starter does not exist");
            return;
        }

        executeModels();
        executeComponent();
        executeDataFormat();
        executeLanguage();
    }

    private void executeModels() throws MojoExecutionException, MojoFailureException {
        final Set<File> files = PackageHelper.findJsonFiles(buildDir, p -> p.isDirectory() || p.getName().endsWith(".json"));

        String json;

        // Hystrix
        json = loadModelJson(files, "hystrixConfiguration");
        if (json != null) {
            OtherModel model = generateOtherModel(json);

            int pos = model.getJavaType().lastIndexOf(".");
            String pkg = model.getJavaType().substring(0, pos) + ".springboot";

            // Generate properties, auto-configuration happens in camel-hystrix-starter
            createOtherModelConfigurationSource(pkg, model, "camel.hystrix", true);
        }

        // Consul
        json = loadModelJson(files, "consulServiceDiscovery");
        if (json != null) {
            OtherModel model = generateOtherModel(json);

            int pos = model.getJavaType().lastIndexOf(".");
            String pkg = model.getJavaType().substring(0, pos) + ".springboot";

            // Generate properties, auto-configuration happens in camel-consul-starter
            createOtherModelConfigurationSource(pkg, model, "camel.cloud.consul.service-discovery", true);
        }

        // DNS
        json = loadModelJson(files, "dnsServiceDiscovery");
        if (json != null) {
            OtherModel model = generateOtherModel(json);

            int pos = model.getJavaType().lastIndexOf(".");
            String pkg = model.getJavaType().substring(0, pos) + ".springboot";

            // Generate properties, auto-configuration happens in camel-dns-starter
            createOtherModelConfigurationSource(pkg, model, "camel.cloud.dns.service-discovery", true);
        }

        // Etcd
        json = loadModelJson(files, "etcdServiceDiscovery");
        if (json != null) {
            OtherModel model = generateOtherModel(json);

            int pos = model.getJavaType().lastIndexOf(".");
            String pkg = model.getJavaType().substring(0, pos) + ".springboot";

            // Generate properties, auto-configuration happens in camel-etcd-starter
            createOtherModelConfigurationSource(pkg, model, "camel.cloud.etcd.service-discovery", true);
        }

        // Kubernetes
        json = loadModelJson(files, "kubernetesServiceDiscovery");
        if (json != null) {
            OtherModel model = generateOtherModel(json);

            int pos = model.getJavaType().lastIndexOf(".");
            String pkg = model.getJavaType().substring(0, pos) + ".springboot";

            // Generate properties, auto-configuration happens in camel-kubernetes-starter
            createOtherModelConfigurationSource(pkg, model, "camel.cloud.kubernetes.service-discovery", true);
        }

        // Ribbon
        json = loadModelJson(files, "ribbonLoadBalancer");
        if (json != null) {
            OtherModel model = generateOtherModel(json);

            int pos = model.getJavaType().lastIndexOf(".");
            String pkg = model.getJavaType().substring(0, pos) + ".springboot";

            // Generate properties, auto-configuration happens in camel-kubernetes-starter
            createOtherModelConfigurationSource(pkg, model, "camel.cloud.ribbon.load-balancer", true);
        }

        // Rest
        json = loadModelJson(files, "restConfiguration");
        if (json != null) {
            OtherModel model = generateOtherModel(json);

            int pos = model.getJavaType().lastIndexOf(".");
            String pkg = model.getJavaType().substring(0, pos) + ".springboot";

            // Generate properties, auto-configuration happens in camel-kubernetes-starter
            createRestConfigurationSource(pkg, model, "camel.rest");
            createRestModuleAutoConfigurationSource(pkg, model);
        }
    }

    private void createOtherModelConfigurationSource(String packageName, OtherModel model, String propertiesPrefix, boolean generatedNestedConfig) throws MojoFailureException {
        final int pos = model.getJavaType().lastIndexOf(".");
        final String commonName = model.getJavaType().substring(pos + 1) + (generatedNestedConfig ? "Common" : "Properties");
        final String configName = model.getJavaType().substring(pos + 1) + (generatedNestedConfig ? "Properties" : null);

        // Common base class
        JavaClassSource commonClass = Roaster.create(JavaClassSource.class);
        commonClass.setPackage(packageName);
        commonClass.setName(commonName);

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        if (!Strings.isBlank(model.getDescription())) {
            doc = model.getDescription() + "\n\n" + doc;
        }
        commonClass.getJavaDoc().setFullText(doc);
        commonClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());

        for (OtherOptionModel option : model.getOptions()) {
            String type = option.getJavaType();
            String name = option.getName();

            if ("id".equalsIgnoreCase(name) || "parent".equalsIgnoreCase(name) || "camelContext".equalsIgnoreCase(name)) {
                // Skip them as they should not be set via spring boot
                continue;
            }

            if ("java.util.List<org.apache.camel.model.PropertyDefinition>".equalsIgnoreCase(type)) {
                type = "java.util.Map<java.lang.String, java.lang.String>";
            }

            // generate inner class for non-primitive options
            PropertySource<JavaClassSource> prop = commonClass.addProperty(type, option.getName());
            if (!Strings.isBlank(option.getDescription())) {
                prop.getField().getJavaDoc().setFullText(option.getDescription());
            }
            if (!Strings.isBlank(option.getDefaultValue())) {
                if ("java.lang.String".equals(type)) {
                    prop.getField().setStringInitializer(option.getDefaultValue());
                } else if ("long".equals(type) || "java.lang.Long".equals(type)) {
                    // the value should be a Long number
                    String value = option.getDefaultValue() + "L";
                    prop.getField().setLiteralInitializer(value);
                } else if ("integer".equals(option.getType()) || "boolean".equals(option.getType())) {
                    prop.getField().setLiteralInitializer(option.getDefaultValue());
                } else if (!Strings.isBlank(option.getEnums())) {
                    String enumShortName = type.substring(type.lastIndexOf(".") + 1);
                    prop.getField().setLiteralInitializer(enumShortName + "." + option.getDefaultValue());
                    commonClass.addImport(model.getJavaType());
                }
            }
        }

        sortImports(commonClass);
        writeSourceIfChanged(commonClass, packageName.replaceAll("\\.", "\\/") + "/" + commonName + ".java");

        // Config class
        if (generatedNestedConfig) {
            JavaClassSource configClass = Roaster.create(JavaClassSource.class);
            configClass.setPackage(packageName);
            configClass.setName(configName);
            configClass.extendSuperType(commonClass);
            configClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
            configClass.addAnnotation("org.springframework.boot.context.properties.ConfigurationProperties").setStringValue("prefix", propertiesPrefix);
            configClass.addImport(Map.class);
            configClass.addImport(HashMap.class);
            configClass.removeImport(commonClass);

            configClass.addField()
                .setName("enabled")
                .setType(boolean.class)
                .setPrivate()
                .setLiteralInitializer("true")
                .getJavaDoc().setFullText("Enable the component");
            configClass.addField("Map<String, " + commonName + "> configurations = new HashMap<>()")
                .setPrivate()
                .getJavaDoc().setFullText("Define additional configuration definitions");

            MethodSource<JavaClassSource> method;

            method = configClass.addMethod();
            method.setName("getConfigurations");
            method.setReturnType("Map<String, " + commonName + ">");
            method.setPublic();
            method.setBody("return configurations;");

            method = configClass.addMethod();
            method.setName("isEnabled");
            method.setReturnType(boolean.class);
            method.setPublic();
            method.setBody("return enabled;");

            method = configClass.addMethod();
            method.setName("setEnabled");
            method.addParameter(boolean.class, "enabled");
            method.setPublic();
            method.setBody("this.enabled = enabled;");


            sortImports(configClass);
            writeSourceIfChanged(configClass, packageName.replaceAll("\\.", "\\/") + "/" + configName + ".java");
        }
    }

    private void createRestConfigurationSource(String packageName, OtherModel model, String propertiesPrefix) throws MojoFailureException {
        final int pos = model.getJavaType().lastIndexOf(".");
        final String className = model.getJavaType().substring(pos + 1) + "Properties";

        // Common base class
        JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
        javaClass.setPackage(packageName);
        javaClass.setName(className);
        javaClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
        javaClass.addAnnotation("org.springframework.boot.context.properties.ConfigurationProperties").setStringValue("prefix", propertiesPrefix);

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        if (!Strings.isBlank(model.getDescription())) {
            doc = model.getDescription() + "\n\n" + doc;
        }
        javaClass.getJavaDoc().setFullText(doc);

        for (OtherOptionModel option : model.getOptions()) {
            String type = option.getJavaType();
            String name = option.getName();

            if ("id".equalsIgnoreCase(name) || "parent".equalsIgnoreCase(name) || "camelContext".equalsIgnoreCase(name)) {
                // Skip them as they should not be set via spring boot
                continue;
            }

            if ("java.util.List<org.apache.camel.model.PropertyDefinition>".equalsIgnoreCase(type)) {
                type = "java.util.Map<java.lang.String, java.lang.String>";
            } else if ("java.util.List<org.apache.camel.model.rest.RestPropertyDefinition>".equalsIgnoreCase(type)) {
                type = "java.util.Map<java.lang.String, java.lang.Object>";
            }

            if ("enableCORS".equalsIgnoreCase(name)) {
                name = "enableCors";
            }

            // generate inner class for non-primitive options
            PropertySource<JavaClassSource> prop = javaClass.addProperty(type, name);
            if (!Strings.isBlank(option.getDescription())) {
                prop.getField().getJavaDoc().setFullText(option.getDescription());
            }
            if (!Strings.isBlank(option.getDefaultValue())) {
                if ("java.lang.String".equals(type)) {
                    prop.getField().setStringInitializer(option.getDefaultValue());
                } else if ("long".equals(type) || "java.lang.Long".equals(type)) {
                    // the value should be a Long number
                    String value = option.getDefaultValue() + "L";
                    prop.getField().setLiteralInitializer(value);
                } else if ("integer".equals(option.getType()) || "boolean".equals(option.getType())) {
                    prop.getField().setLiteralInitializer(option.getDefaultValue());
                } else if (!Strings.isBlank(option.getEnums())) {
                    String enumShortName = type.substring(type.lastIndexOf(".") + 1);
                    prop.getField().setLiteralInitializer(enumShortName + "." + option.getDefaultValue());
                    javaClass.addImport(model.getJavaType());
                }
            }
        }

        sortImports(javaClass);
        writeSourceIfChanged(javaClass, packageName.replaceAll("\\.", "\\/") + "/" + className + ".java");
    }

    private void createRestModuleAutoConfigurationSource(String packageName, OtherModel model) throws MojoFailureException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);
        final int pos = model.getJavaType().lastIndexOf(".");
        final String name = model.getJavaType().substring(pos + 1) + "AutoConfiguration";
        final String configType = model.getJavaType().substring(pos + 1) + "Properties";

        javaClass.setPackage(packageName);
        javaClass.setName(name);

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        javaClass.getJavaDoc().setFullText(doc);

        javaClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
        javaClass.addAnnotation(Configuration.class);
        javaClass.addAnnotation(ConditionalOnBean.class).setStringValue("type", "org.apache.camel.spring.boot.CamelAutoConfiguration");
        javaClass.addAnnotation(ConditionalOnProperty.class).setStringValue("name", "camel.rest.enabled").setLiteralValue("matchIfMissing", "true");
        javaClass.addAnnotation(AutoConfigureAfter.class).setStringValue("name", "org.apache.camel.spring.boot.CamelAutoConfiguration");
        javaClass.addAnnotation(EnableConfigurationProperties.class).setLiteralValue("value", configType + ".class");

        javaClass.addImport("java.util.Map");
        javaClass.addImport("java.util.HashMap");
        javaClass.addImport("org.apache.camel.util.CollectionHelper");
        javaClass.addImport("org.apache.camel.util.IntrospectionSupport");
        javaClass.addImport("org.apache.camel.spring.boot.util.CamelPropertiesHelper");
        javaClass.addImport("org.apache.camel.CamelContext");
        javaClass.addImport("org.apache.camel.model.rest.RestConstants");
        javaClass.addImport("org.apache.camel.spi.RestConfiguration");

        javaClass.addField()
            .setName("camelContext")
            .setType("org.apache.camel.CamelContext")
            .setPrivate()
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setName("config")
            .setType(configType)
            .setPrivate()
            .addAnnotation(Autowired.class);

        MethodSource<JavaClassSource> method;

        // Configuration
        method = javaClass.addMethod();
        method.setName("configure" + model.getShortJavaType());
        method.setPublic();
        method.addThrows(Exception.class);
        method.setReturnType("org.apache.camel.spi.RestConfiguration");
        method.addAnnotation(Lazy.class);
        method.addAnnotation(Bean.class).setLiteralValue("name", "RestConstants.DEFAULT_REST_CONFIGURATION_ID");
        method.addAnnotation(ConditionalOnClass.class).setLiteralValue("value", "CamelContext.class");
        method.addAnnotation(ConditionalOnMissingBean.class);
        method.setBody(""
            + "Map<String, Object> properties = new HashMap<>();\n"
            + "IntrospectionSupport.getProperties(config, properties, null, false);\n"
            + "// These options is configured specially further below, so remove them first\n"
            + "properties.remove(\"enableCors\");\n"
            + "properties.remove(\"apiProperty\");\n"
            + "properties.remove(\"componentProperty\");\n"
            + "properties.remove(\"consumerProperty\");\n"
            + "properties.remove(\"dataFormatProperty\");\n"
            + "properties.remove(\"endpointProperty\");\n"
            + "properties.remove(\"corsHeaders\");\n"
            + "\n"
            + "RestConfiguration definition = new RestConfiguration();\n"
            + "CamelPropertiesHelper.setCamelProperties(camelContext, definition, properties, true);\n"
            + "\n"
            + "// Workaround for spring-boot properties name as It would appear\n"
            + "// as enable-c-o-r-s if left uppercase in Configuration\n"
            + "definition.setEnableCORS(config.getEnableCors());\n"
            + "\n"
            + "if (config.getApiProperty() != null) {\n"
            + "    definition.setApiProperties(new HashMap<>(CollectionHelper.flatternKeysInMap(config.getApiProperty(), \".\")));\n"
            + "}\n"
            + "if (config.getComponentProperty() != null) {\n"
            + "    definition.setComponentProperties(new HashMap<>(CollectionHelper.flatternKeysInMap(config.getComponentProperty(), \".\")));\n"
            + "}\n"
            + "if (config.getConsumerProperty() != null) {\n"
            + "    definition.setConsumerProperties(new HashMap<>(CollectionHelper.flatternKeysInMap(config.getConsumerProperty(), \".\")));\n"
            + "}\n"
            + "if (config.getDataFormatProperty() != null) {\n"
            + "    definition.setDataFormatProperties(new HashMap<>(CollectionHelper.flatternKeysInMap(config.getDataFormatProperty(), \".\")));\n"
            + "}\n"
            + "if (config.getEndpointProperty() != null) {\n"
            + "    definition.setEndpointProperties(new HashMap<>(CollectionHelper.flatternKeysInMap(config.getEndpointProperty(), \".\")));\n"
            + "}\n"
            + "if (config.getCorsHeaders() != null) {\n"
            + "    Map<String, Object> map = CollectionHelper.flatternKeysInMap(config.getCorsHeaders(), \".\");\n"
            + "    Map<String, String> target = new HashMap<>();\n"
            + "    map.forEach((k, v) -> target.put(k, v.toString()));\n"
            + "    definition.setCorsHeaders(target);\n"
            + "}\n"
            + "return definition;"
        );

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        writeSourceIfChanged(javaClass, fileName);
        writeComponentSpringFactorySource(packageName, name);
    }

    private void executeComponent() throws MojoExecutionException, MojoFailureException {
        // find the component names
        List<String> componentNames = findComponentNames();

        final Set<File> jsonFiles = new TreeSet<File>();
        PackageHelper.findJsonFiles(buildDir, jsonFiles, new PackageHelper.CamelComponentsModelFilter());

        // create auto configuration for the components
        if (!componentNames.isEmpty()) {
            getLog().debug("Found " + componentNames.size() + " components");

            List<ComponentModel> allModels = new LinkedList<>();
            for (String componentName : componentNames) {
                String json = loadComponentJson(jsonFiles, componentName);
                if (json != null) {
                    ComponentModel model = generateComponentModel(componentName, json);
                    allModels.add(model);
                }
            }

            // Group the models by implementing classes
            Map<String, List<ComponentModel>> grModels = allModels.stream().collect(Collectors.groupingBy(ComponentModel::getJavaType));
            for (String componentClass : grModels.keySet()) {
                List<ComponentModel> compModels = grModels.get(componentClass);
                ComponentModel model = compModels.get(0); // They should be equivalent
                List<String> aliases = compModels.stream().map(ComponentModel::getScheme).sorted().collect(Collectors.toList());

                // resolvePropertyPlaceholders is an option which only make sense to use if the component has other options
                //boolean hasOptions = model.getComponentOptions().stream().anyMatch(o -> !o.getName().equals("resolvePropertyPlaceholders"));

                // use springboot as sub package name so the code is not in normal
                // package so the Spring Boot JARs can be optional at runtime
                int pos = model.getJavaType().lastIndexOf(".");
                String pkg = model.getJavaType().substring(0, pos) + ".springboot";

                String overrideComponentName = null;
                if (aliases.size() > 1) {
                    // determine component name when there are multiple ones
                    overrideComponentName = model.getArtifactId().replace("camel-", "");
                }

                createComponentConfigurationSource(pkg, model, overrideComponentName);
                createComponentAutoConfigurationSource(pkg, model, aliases, overrideComponentName);
                createComponentSpringFactorySource(pkg, model);
            }
        }
    }

    private void executeDataFormat() throws MojoExecutionException, MojoFailureException {
        // find the data format names
        List<String> dataFormatNames = findDataFormatNames();

        final Set<File> jsonFiles = new TreeSet<File>();
        // we can reuse the component model filter
        PackageHelper.findJsonFiles(buildDir, jsonFiles, new PackageHelper.CamelComponentsModelFilter());

        // create auto configuration for the data formats
        if (!dataFormatNames.isEmpty()) {
            getLog().debug("Found " + dataFormatNames.size() + " dataformats");

            List<DataFormatModel> allModels = new LinkedList<>();
            for (String dataFormatName : dataFormatNames) {
                String json = loadDataFormatJson(jsonFiles, dataFormatName);
                if (json != null) {
                    DataFormatModel model = generateDataFormatModel(dataFormatName, json);
                    allModels.add(model);
                }
            }

            // Group the models by implementing classes
            Map<String, List<DataFormatModel>> grModels = allModels.stream().collect(Collectors.groupingBy(DataFormatModel::getJavaType));
            for (String dataFormatClass : grModels.keySet()) {
                List<DataFormatModel> dfModels = grModels.get(dataFormatClass);
                DataFormatModel model = dfModels.get(0); // They should be equivalent
                List<String> aliases = dfModels.stream().map(DataFormatModel::getName).sorted().collect(Collectors.toList());

                // use springboot as sub package name so the code is not in normal
                // package so the Spring Boot JARs can be optional at runtime
                int pos = model.getJavaType().lastIndexOf(".");
                String pkg = model.getJavaType().substring(0, pos) + ".springboot";

                String overrideDataFormatName = null;
                if (aliases.size() > 1) {
                    // determine component name when there are multiple ones
                    overrideDataFormatName = model.getArtifactId().replace("camel-", "");
                }

                createDataFormatConfigurationSource(pkg, model, overrideDataFormatName);
                createDataFormatAutoConfigurationSource(pkg, model, aliases, overrideDataFormatName);
                createDataFormatSpringFactorySource(pkg, model);
            }
        }
    }

    private void executeLanguage() throws MojoExecutionException, MojoFailureException {
        // find the language names
        List<String> languageNames = findLanguageNames();

        final Set<File> jsonFiles = new TreeSet<File>();
        // we can reuse the component model filter
        PackageHelper.findJsonFiles(buildDir, jsonFiles, new PackageHelper.CamelComponentsModelFilter());

        // create auto configuration for the languages
        if (!languageNames.isEmpty()) {
            getLog().debug("Found " + languageNames.size() + " languages");

            List<LanguageModel> allModels = new LinkedList<>();
            for (String languageName : languageNames) {
                String json = loadLanguageJson(jsonFiles, languageName);
                if (json != null) {
                    LanguageModel model = generateLanguageModel(languageName, json);
                    allModels.add(model);
                }
            }

            // Group the models by implementing classes
            Map<String, List<LanguageModel>> grModels = allModels.stream().collect(Collectors.groupingBy(LanguageModel::getJavaType));
            for (String languageClass : grModels.keySet()) {
                List<LanguageModel> dfModels = grModels.get(languageClass);
                LanguageModel model = dfModels.get(0); // They should be equivalent
                List<String> aliases = dfModels.stream().map(LanguageModel::getName).sorted().collect(Collectors.toList());

                // use springboot as sub package name so the code is not in normal
                // package so the Spring Boot JARs can be optional at runtime
                int pos = model.getJavaType().lastIndexOf(".");
                String pkg = model.getJavaType().substring(0, pos) + ".springboot";

                String overrideLanguageName = null;
                if (aliases.size() > 1) {
                    // determine language name when there are multiple ones
                    overrideLanguageName = model.getArtifactId().replace("camel-", "");
                }

                createLanguageConfigurationSource(pkg, model, overrideLanguageName);
                createLanguageAutoConfigurationSource(pkg, model, aliases, overrideLanguageName);
                createLanguageSpringFactorySource(pkg, model);
            }
        }
    }

    private void createComponentConfigurationSource(String packageName, ComponentModel model, String overrideComponentName) throws MojoFailureException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Component", "ComponentConfiguration");
        javaClass.setPackage(packageName).setName(name);
        javaClass.extendSuperType(Roaster.create(JavaClassSource.class).setName("ComponentConfigurationPropertiesCommon"));
        javaClass.addImport("org.apache.camel.spring.boot.ComponentConfigurationPropertiesCommon");

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        if (!Strings.isBlank(model.getDescription())) {
            doc = model.getDescription() + "\n\n" + doc;
        }
        javaClass.getJavaDoc().setFullText(doc);

        String prefix = "camel.component." + (overrideComponentName != null ? overrideComponentName : model.getScheme());
        // make sure prefix is in lower case
        prefix = prefix.toLowerCase(Locale.US);
        javaClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
        javaClass.addAnnotation("org.springframework.boot.context.properties.ConfigurationProperties").setStringValue("prefix", prefix);

        Set<JavaClassSource> nestedTypes = new HashSet<>();
        for (ComponentOptionModel option : model.getComponentOptions()) {

            if (skipComponentOption(model, option)) {
                // some component options should be skipped
                continue;
            }

            String type = option.getJavaType();

            // generate inner class for non-primitive options
            type = getSimpleJavaType(type);
            JavaClassSource javaClassSource = readJavaType(type);
            if (isNestedProperty(nestedTypes, javaClassSource)) {
                type = option.getShortJavaType() + INNER_TYPE_SUFFIX;
            }

            PropertySource<JavaClassSource> prop = javaClass.addProperty(type, option.getName());
            if (!type.endsWith(INNER_TYPE_SUFFIX)
                && type.indexOf('[') == -1
                && !EXCLUDE_INNER_PATTERN.matcher(type).matches()
                && Strings.isBlank(option.getEnums())
                && (javaClassSource == null || (javaClassSource.isClass() && !javaClassSource.isAbstract()))) {
                // add nested configuration annotation for complex properties
                prop.getField().addAnnotation(NestedConfigurationProperty.class);
            }
            if ("true".equals(option.getDeprecated())) {
                prop.getField().addAnnotation(Deprecated.class);
                prop.getAccessor().addAnnotation(Deprecated.class);
                prop.getMutator().addAnnotation(Deprecated.class);
                // DeprecatedConfigurationProperty must be on getter when deprecated
                prop.getAccessor().addAnnotation(DeprecatedConfigurationProperty.class);
            }
            if (!Strings.isBlank(option.getDescription())) {
                prop.getField().getJavaDoc().setFullText(option.getDescription());
            }
            if (!Strings.isBlank(option.getDefaultValue())) {
                if ("java.lang.String".equals(option.getJavaType())) {
                    prop.getField().setStringInitializer(option.getDefaultValue());
                } else if ("long".equals(option.getJavaType()) || "java.lang.Long".equals(option.getJavaType())) {
                    // the value should be a Long number
                    String value = option.getDefaultValue() + "L";
                    prop.getField().setLiteralInitializer(value);
                } else if ("integer".equals(option.getType()) || "boolean".equals(option.getType())) {
                    prop.getField().setLiteralInitializer(option.getDefaultValue());
                } else if (!Strings.isBlank(option.getEnums())) {
                    String enumShortName = type.substring(type.lastIndexOf(".") + 1);
                    prop.getField().setLiteralInitializer(enumShortName + "." + option.getDefaultValue());
                    javaClass.addImport(model.getJavaType());
                }
            }
        }

        // add inner classes for nested AutoConfiguration options
        ClassLoader projectClassLoader = getProjectClassLoader();
        for (JavaClassSource nestedType : nestedTypes) {

            final JavaClassSource innerClass = javaClass.addNestedType("public static class " + nestedType.getName() + INNER_TYPE_SUFFIX);
            // add source class name as a static field
            innerClass.addField()
                .setPublic()
                .setStatic(true)
                .setFinal(true)
                .setType(Class.class)
                .setName("CAMEL_NESTED_CLASS")
                .setLiteralInitializer(nestedType.getCanonicalName() + ".class");

            // parse option type
            for (ResolvedProperty resolvedProperty : getProperties(nestedType)) {

                String optionType = resolvedProperty.propertyType;
                PropertySource<JavaClassSource> sourceProp = resolvedProperty.propertySource;

                Type<JavaClassSource> propType = sourceProp.getType();
                final PropertySource<JavaClassSource> prop = innerClass.addProperty(optionType, sourceProp.getName());

                boolean anEnum;
                Class optionClass;
                if (!propType.isArray()) {
                    optionClass = loadClass(projectClassLoader, optionType);
                    anEnum = optionClass.isEnum();
                } else {
                    optionClass = null;
                    anEnum = false;
                }

                // add nested configuration annotation for complex properties
                if (!EXCLUDE_INNER_PATTERN.matcher(optionType).matches()
                    && !propType.isArray()
                    && !anEnum
                    && optionClass != null
                    && !optionClass.isInterface()
                    && !optionClass.isAnnotation()
                    && !Modifier.isAbstract(optionClass.getModifiers())) {
                    prop.getField().addAnnotation(NestedConfigurationProperty.class);
                }
                if (sourceProp.hasAnnotation(Deprecated.class)) {
                    prop.getField().addAnnotation(Deprecated.class);
                    prop.getAccessor().addAnnotation(Deprecated.class);
                    prop.getMutator().addAnnotation(Deprecated.class);
                    // DeprecatedConfigurationProperty must be on getter when deprecated
                    prop.getAccessor().addAnnotation(DeprecatedConfigurationProperty.class);
                }

                String description = null;
                final MethodSource<JavaClassSource> mutator = sourceProp.getMutator();
                if (mutator.hasJavaDoc()) {
                    description = mutator.getJavaDoc().getFullText();
                } else if (sourceProp.hasField()) {
                    description = sourceProp.getField().getJavaDoc().getFullText();
                }
                if (!Strings.isBlank(description)) {
                    prop.getField().getJavaDoc().setFullText(description);
                }

                // try to see if the source is actually reusing a shared Camel configuration that that has @UriParam options
                // if so we can fetch the default value from the json file as it holds the correct value vs the annotation
                // as the annotation can refer to a constant field which we wont have accessible at this point
                if (sourceProp.hasAnnotation(UriParam.class) || sourceProp.hasAnnotation(UriPath.class)) {
                    String defaultValue = null;
                    String javaType = null;
                    String type = null;

                    String fileName = model.getJavaType();
                    fileName = fileName.substring(0, fileName.lastIndexOf("."));
                    fileName = fileName.replace('.', '/');
                    File jsonFile = new File(classesDir, fileName + "/" + model.getScheme() + ".json");
                    if (jsonFile.isFile() && jsonFile.exists()) {
                        try {
                            String json = FileUtils.readFileToString(jsonFile);
                            List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);

                            // grab name from annotation
                            String optionName;
                            if (sourceProp.hasAnnotation(UriParam.class)) {
                                optionName = sourceProp.getAnnotation(UriParam.class).getStringValue("name");
                            } else {
                                optionName = sourceProp.getAnnotation(UriPath.class).getStringValue("name");
                            }
                            if (optionName == null) {
                                optionName = sourceProp.hasField() ? sourceProp.getField().getName() : null;
                            }

                            if (optionName != null) {
                                javaType = getPropertyJavaType(rows, optionName);
                                type = getPropertyType(rows, optionName);
                                defaultValue = getPropertyDefaultValue(rows, optionName);
                            }
                        } catch (IOException e) {
                            // ignore
                        }
                    }

                    if (!Strings.isBlank(defaultValue)) {

                        // roaster can create the wrong type for some options so use the correct type we found in the json schema
                        String wrapperType = getSimpleJavaType(javaType);
                        if (wrapperType.startsWith("java.lang.")) {
                            // skip java.lang. as prefix for wrapper type
                            wrapperType = wrapperType.substring(10);
                            prop.setType(wrapperType);
                        }

                        if ("long".equals(javaType) || "java.lang.Long".equals(javaType)) {
                            // the value should be a Long number
                            String value = defaultValue + "L";
                            prop.getField().setLiteralInitializer(value);
                        } else if ("integer".equals(type) || "boolean".equals(type)) {
                            prop.getField().setLiteralInitializer(defaultValue);
                        } else if ("string".equals(type)) {
                            prop.getField().setStringInitializer(defaultValue);
                        } else if (anEnum) {
                            String enumShortName = optionClass.getSimpleName();
                            prop.getField().setLiteralInitializer(enumShortName + "." + defaultValue);
                            javaClass.addImport(model.getJavaType());
                        }
                    }
                }
            }
        }

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";

        writeSourceIfChanged(javaClass, fileName);
    }

    // resolved property type name and property source, Roaster doesn't resolve inner classes correctly
    private class ResolvedProperty {
        private String propertyType;
        private PropertySource<JavaClassSource> propertySource;

        ResolvedProperty(String propertyType, PropertySource<JavaClassSource> propertySource) {
            this.propertyType = propertyType;
            this.propertySource = propertySource;
        }
    }

    // get properties for nested type and super types, only properties with setters are supported!!!
    private List<ResolvedProperty> getProperties(JavaClassSource nestedType) {
        final List<ResolvedProperty> properties = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        do {
            for (PropertySource<JavaClassSource> propertySource : nestedType.getProperties()) {
                // NOTE: fields with no setters are skipped
                if (propertySource.isMutable() && !names.contains(propertySource.getName())) {
                    properties.add(new ResolvedProperty(getSimpleJavaType(resolveParamType(nestedType, propertySource.getType().getName())), propertySource));
                    names.add(propertySource.getName());
                }
            }
            nestedType = readJavaType(nestedType.getSuperType());
        } while (nestedType != null);
        return properties;
    }

    // try loading class, looking for inner classes if needed
    private Class loadClass(ClassLoader projectClassLoader, String loadClassName) throws MojoFailureException {
        Class optionClass;
        while (true) {
            try {
                optionClass = projectClassLoader.loadClass(loadClassName);
                break;
            } catch (ClassNotFoundException e) {
                int dotIndex = loadClassName.lastIndexOf('.');
                if (dotIndex == -1) {
                    throw new MojoFailureException(e.getMessage(), e);
                } else {
                    loadClassName = loadClassName.substring(0, dotIndex) + "$" + loadClassName.substring(dotIndex + 1);
                }
            }
        }
        return optionClass;
    }

    // Roaster doesn't resolve inner classes correctly
    private String resolveParamType(JavaClassSource nestedType, String type) {
        String result;
        int innerStart = type.indexOf('.');
        int arrayStart = type.indexOf('[');
        if (innerStart != -1) {
            result = nestedType.resolveType(type.substring(0, innerStart)) + type.substring(innerStart);
        } else {
            result = nestedType.resolveType(type);
        }
        return arrayStart == -1 ? result : result + type.substring(arrayStart);
    }

    protected ClassLoader getProjectClassLoader() throws MojoFailureException {
        final List classpathElements;
        try {
            classpathElements = project.getTestClasspathElements();
        } catch (org.apache.maven.artifact.DependencyResolutionRequiredException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
        final URL[] urls = new URL[classpathElements.size()];
        int i = 0;
        for (Iterator it = classpathElements.iterator(); it.hasNext(); i++) {
            try {
                urls[i] = new File((String) it.next()).toURI().toURL();
            } catch (MalformedURLException e) {
                throw new MojoFailureException(e.getMessage(), e);
            }
        }
        final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        return new URLClassLoader(urls, tccl != null ? tccl : getClass().getClassLoader());
    }

    private String getSimpleJavaType(String type) {
        // remove <?> as generic type as Roaster (Eclipse JDT) cannot use that
        type = type.replaceAll("\\<\\?\\>", "");
        // use wrapper types for primitive types so a null mean that the option has not been configured
        String wrapper = PRIMITIVEMAP.get(type);
        if (wrapper != null) {
            type = wrapper;
        }
        return type;
    }

    // it's a nested property if the source exists and it's not an abstract class in this project, e.g. endpoint configuration
    private boolean isNestedProperty(Set<JavaClassSource> nestedTypes, JavaClassSource type) {
        if (type != null) {
            // nested type MUST have some properties of it's own, besides those from super class
            if (type.isClass() && !type.isEnum() && !type.isAbstract() && !type.getProperties().isEmpty()) {
                nestedTypes.add(type);
            } else {
                type = null;
            }
        }
        return type != null;
    }

    // read java type from project, returns null if not found
    private JavaClassSource readJavaType(String type) {
        JavaClassSource nestedType = null;
        if (!type.startsWith("java.lang.")) {

            final String fileName = type.replaceAll("[\\[\\]]", "").replaceAll("\\.", "\\/") + ".java";
            for (Object sourceRoot : project.getCompileSourceRoots()) {

                File sourceFile = new File(sourceRoot.toString(), fileName);
                if (sourceFile.isFile()) {
                    try {
                        JavaType<?> classSource = Roaster.parse(sourceFile);
                        if (classSource instanceof JavaClassSource) {
                            nestedType = (JavaClassSource) classSource;
                            break;
                        }
                    } catch (FileNotFoundException e) {
                        throw new IllegalArgumentException("Missing source file " + type);
                    }
                }
            }
        }
        return nestedType;
    }

    // CHECKSTYLE:OFF
    private static boolean skipComponentOption(ComponentModel model, ComponentOptionModel option) {
        if ("netty4-http".equals(model.getScheme()) || "netty-http".equals(model.getScheme())) {
            String name = option.getName();
            if (name.equals("textline") || name.equals("delimiter") || name.equals("autoAppendDelimiter") || name.equals("decoderMaxLineLength")
                || name.equals("encoding") || name.equals("allowDefaultCodec") || name.equals("udpConnectionlessSending") || name.equals("networkInterface")
                || name.equals("clientMode") || name.equals("reconnect") || name.equals("reconnectInterval") || name.equals("useByteBuf")
                || name.equals("udpByteArrayCodec") || name.equals("broadcast")) {
                return true;
            }
        }
        return false;
    }
    // CHECKSTYLE:ON

    private void createDataFormatConfigurationSource(String packageName, DataFormatModel model, String overrideDataFormatName) throws MojoFailureException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("DataFormat", "DataFormatConfiguration");
        javaClass.setPackage(packageName).setName(name);
        javaClass.extendSuperType(Roaster.create(JavaClassSource.class).setName("DataFormatConfigurationPropertiesCommon"));
        javaClass.addImport("org.apache.camel.spring.boot.DataFormatConfigurationPropertiesCommon");

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        if (!Strings.isBlank(model.getDescription())) {
            doc = model.getDescription() + "\n\n" + doc;
        }
        javaClass.getJavaDoc().setFullText(doc);

        String prefix = "camel.dataformat." + (overrideDataFormatName != null ? overrideDataFormatName : model.getName());
        // make sure prefix is in lower case
        prefix = prefix.toLowerCase(Locale.US);
        javaClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
        javaClass.addAnnotation("org.springframework.boot.context.properties.ConfigurationProperties").setStringValue("prefix", prefix);

        for (DataFormatOptionModel option : model.getDataFormatOptions()) {
            // skip option with name id in data format as we do not need that
            if ("id".equals(option.getName())) {
                continue;
            }
            String type = option.getJavaType();
            type = getSimpleJavaType(type);

            PropertySource<JavaClassSource> prop = javaClass.addProperty(type, option.getName());
            if ("true".equals(option.getDeprecated())) {
                prop.getField().addAnnotation(Deprecated.class);
                prop.getAccessor().addAnnotation(Deprecated.class);
                prop.getMutator().addAnnotation(Deprecated.class);
                // DeprecatedConfigurationProperty must be on getter when deprecated
                prop.getAccessor().addAnnotation(DeprecatedConfigurationProperty.class);
            }
            if (!Strings.isBlank(option.getDescription())) {
                prop.getField().getJavaDoc().setFullText(option.getDescription());
            }
            if (!Strings.isBlank(option.getDefaultValue())) {
                if ("java.lang.String".equals(option.getJavaType())) {
                    prop.getField().setStringInitializer(option.getDefaultValue());
                } else if ("long".equals(option.getJavaType()) || "java.lang.Long".equals(option.getJavaType())) {
                    // the value should be a Long number
                    String value = option.getDefaultValue() + "L";
                    prop.getField().setLiteralInitializer(value);
                } else if ("integer".equals(option.getType()) || "boolean".equals(option.getType())) {
                    prop.getField().setLiteralInitializer(option.getDefaultValue());
                } else if (!Strings.isBlank(option.getEnumValues())) {
                    String enumShortName = type.substring(type.lastIndexOf(".") + 1);
                    prop.getField().setLiteralInitializer(enumShortName + "." + option.getDefaultValue());
                    javaClass.addImport(model.getJavaType());
                }
            }
        }

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";

        writeSourceIfChanged(javaClass, fileName);
    }

    private void createLanguageConfigurationSource(String packageName, LanguageModel model, String overrideLanguageName) throws MojoFailureException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Language", "LanguageConfiguration");
        javaClass.setPackage(packageName).setName(name);
        javaClass.extendSuperType(Roaster.create(JavaClassSource.class).setName("LanguageConfigurationPropertiesCommon"));
        javaClass.addImport("org.apache.camel.spring.boot.LanguageConfigurationPropertiesCommon");

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        if (!Strings.isBlank(model.getDescription())) {
            doc = model.getDescription() + "\n\n" + doc;
        }
        javaClass.getJavaDoc().setFullText(doc);

        String prefix = "camel.language." + (overrideLanguageName != null ? overrideLanguageName : model.getName());
        // make sure prefix is in lower case
        prefix = prefix.toLowerCase(Locale.US);
        javaClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
        javaClass.addAnnotation("org.springframework.boot.context.properties.ConfigurationProperties").setStringValue("prefix", prefix);

        for (LanguageOptionModel option : model.getLanguageOptions()) {
            // skip option with name id, or expression in language as we do not need that and skip resultType as they are not global options
            if ("id".equals(option.getName()) || "expression".equals(option.getName()) || "resultType".equals(option.getName())) {
                continue;
            }
            // CHECKSTYLE:OFF
            if ("bean".equals(model.getName())) {
                // and skip following as they are not global options
                if ("bean".equals(option.getName()) || "ref".equals(option.getName()) || "method".equals(option.getName()) || "beanType".equals(option.getName())) {
                    continue;
                }
            } else if ("tokenize".equals(model.getName())) {
                // and skip following as they are not global options
                if ("token".equals(option.getName()) || "endToken".equals(option.getName()) || "inheritNamespaceTagName".equals(option.getName())
                    || "headerName".equals(option.getName()) || "regex".equals(option.getName()) || "xml".equals(option.getName())
                    || "includeTokens".equals(option.getName()) || "group".equals(option.getName()) || "skipFirst".equals(option.getName())) {
                    continue;
                }
            } else if ("xtokenize".equals(model.getName())) {
                // and skip following as they are not global options
                if ("headerName".equals(option.getName()) || "group".equals(option.getName())) {
                    continue;
                }
            } else if ("xpath".equals(model.getName())) {
                // and skip following as they are not global options
                if ("headerName".equals(option.getName())) {
                    continue;
                }
            } else if ("xquery".equals(model.getName())) {
                // and skip following as they are not global options
                if ("headerName".equals(option.getName())) {
                    continue;
                }
            }
            // CHECKSTYLE:ON
            String type = option.getJavaType();
            type = getSimpleJavaType(type);

            PropertySource<JavaClassSource> prop = javaClass.addProperty(type, option.getName());
            if ("true".equals(option.getDeprecated())) {
                prop.getField().addAnnotation(Deprecated.class);
                prop.getAccessor().addAnnotation(Deprecated.class);
                prop.getMutator().addAnnotation(Deprecated.class);
                // DeprecatedConfigurationProperty must be on getter when deprecated
                prop.getAccessor().addAnnotation(DeprecatedConfigurationProperty.class);
            }
            if (!Strings.isBlank(option.getDescription())) {
                prop.getField().getJavaDoc().setFullText(option.getDescription());
            }
            if (!Strings.isBlank(option.getDefaultValue())) {
                if ("java.lang.String".equals(option.getJavaType())) {
                    prop.getField().setStringInitializer(option.getDefaultValue());
                } else if ("long".equals(option.getJavaType()) || "java.lang.Long".equals(option.getJavaType())) {
                    // the value should be a Long number
                    String value = option.getDefaultValue() + "L";
                    prop.getField().setLiteralInitializer(value);
                } else if ("integer".equals(option.getType()) || "boolean".equals(option.getType())) {
                    prop.getField().setLiteralInitializer(option.getDefaultValue());
                } else if (!Strings.isBlank(option.getEnumValues())) {
                    String enumShortName = type.substring(type.lastIndexOf(".") + 1);
                    prop.getField().setLiteralInitializer(enumShortName + "." + option.getDefaultValue());
                    javaClass.addImport(model.getJavaType());
                }
            }
        }

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";

        writeSourceIfChanged(javaClass, fileName);
    }

    private void createComponentAutoConfigurationSource(
        String packageName, ComponentModel model, List<String> componentAliases, String overrideComponentName) throws MojoFailureException {

        final String name = model.getJavaType().substring(model.getJavaType().lastIndexOf(".") + 1).replace("Component", "ComponentAutoConfiguration");
        final String configurationName = name.replace("ComponentAutoConfiguration", "ComponentConfiguration");
        final String componentName = (overrideComponentName != null ? overrideComponentName : model.getScheme()).toLowerCase(Locale.US);

        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        javaClass.setPackage(packageName);
        javaClass.setName(name);
        javaClass.getJavaDoc().setFullText("Generated by camel-package-maven-plugin - do not edit this file!");
        javaClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
        javaClass.addAnnotation(Configuration.class);
        javaClass.addAnnotation(Conditional.class).setLiteralValue(
            "{ ConditionalOnCamelContextAndAutoConfigurationBeans.class, " + name + ".GroupConditions.class }");
        javaClass.addAnnotation(AutoConfigureAfter.class).setLiteralValue("CamelAutoConfiguration.class");
        javaClass.addAnnotation(EnableConfigurationProperties.class).setLiteralValue(
            "{ ComponentConfigurationProperties.class, " + configurationName + ".class }"
        );

        javaClass.addImport(HashMap.class);
        javaClass.addImport(List.class);
        javaClass.addImport(Map.class);
        javaClass.addImport(ApplicationContext.class);
        javaClass.addImport(ConditionalOnBean.class);
        javaClass.addImport("org.slf4j.Logger");
        javaClass.addImport("org.slf4j.LoggerFactory");
        javaClass.addImport("org.apache.camel.CamelContext");
        javaClass.addImport("org.apache.camel.spi.ComponentCustomizer");
        javaClass.addImport("org.apache.camel.spring.boot.CamelAutoConfiguration");
        javaClass.addImport("org.apache.camel.spring.boot.ComponentConfigurationProperties");
        javaClass.addImport("org.apache.camel.spring.boot.util.CamelPropertiesHelper");
        javaClass.addImport("org.apache.camel.spring.boot.util.ConditionalOnCamelContextAndAutoConfigurationBeans");
        javaClass.addImport("org.apache.camel.spring.boot.util.GroupCondition");
        javaClass.addImport("org.apache.camel.spring.boot.util.HierarchicalPropertiesEvaluator");
        javaClass.addImport("org.apache.camel.util.IntrospectionSupport");
        javaClass.addImport("org.apache.camel.util.ObjectHelper");
        javaClass.addImport("org.apache.camel.spi.HasId");
        javaClass.addImport(model.getJavaType());

        javaClass.addField()
            .setPrivate()
            .setStatic(true)
            .setFinal(true)
            .setName("LOGGER")
            .setType("Logger")
            .setLiteralInitializer("LoggerFactory.getLogger(" + name + ".class)");
        javaClass.addField()
            .setPrivate()
            .setName("applicationContext")
            .setType("ApplicationContext")
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("camelContext")
            .setType("CamelContext")
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("configuration")
            .setType(configurationName)
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("customizers")
            .setType("List<ComponentCustomizer<" + model.getShortJavaType() + ">>")
            .addAnnotation(Autowired.class)
                .setLiteralValue("required", "false");

        javaClass.addNestedType(
            Roaster.create(JavaClassSource.class)
                .setName("GroupConditions")
                .setStatic(true)
                .setPackagePrivate()
                .extendSuperType(Roaster.create(JavaClassSource.class).setName("GroupCondition"))
                .addMethod()
                    .setName("GroupConditions")
                    .setConstructor(true)
                    .setPublic()
                    .setBody("super(\"camel.component\", \"camel.component." + componentName + "\");")
                    .getOrigin()
        );

        // add method for auto configure
        String body = createComponentBody(model.getShortJavaType(), componentName);
        String methodName = "configure" + model.getShortJavaType();

        MethodSource<JavaClassSource> method = javaClass.addMethod()
            .setName(methodName)
            .setPublic()
            .setBody(body)
            .setReturnType(model.getShortJavaType())
            .addThrows(Exception.class);

        // Determine all the aliases
        String[] springBeanAliases = componentAliases.stream().map(alias -> alias + "-component").toArray(size -> new String[size]);

        method.addAnnotation(Lazy.class);
        method.addAnnotation(Bean.class).setStringArrayValue("name", springBeanAliases);
        method.addAnnotation(ConditionalOnMissingBean.class).setLiteralValue(model.getShortJavaType() + ".class");

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        writeSourceIfChanged(javaClass, fileName);
    }

    private void createDataFormatAutoConfigurationSource(
        String packageName, DataFormatModel model, List<String> dataFormatAliases, String overrideDataFormatName) throws MojoFailureException {

        final String name = model.getJavaType().substring(model.getJavaType().lastIndexOf(".") + 1).replace("DataFormat", "DataFormatAutoConfiguration");
        final String configurationName = name.replace("DataFormatAutoConfiguration", "DataFormatConfiguration");
        final String dataformatName = (overrideDataFormatName != null ? overrideDataFormatName : model.getName()).toLowerCase(Locale.US);

        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        javaClass.setPackage(packageName);
        javaClass.setName(name);
        javaClass.getJavaDoc().setFullText("Generated by camel-package-maven-plugin - do not edit this file!");
        javaClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
        javaClass.addAnnotation(Configuration.class);
        javaClass.addAnnotation(Conditional.class).setLiteralValue(
            "{ ConditionalOnCamelContextAndAutoConfigurationBeans.class, " + name + ".GroupConditions.class }");
        javaClass.addAnnotation(AutoConfigureAfter.class).setStringValue("name", "org.apache.camel.spring.boot.CamelAutoConfiguration");
        javaClass.addAnnotation(EnableConfigurationProperties.class).setLiteralValue(
            "{ DataFormatConfigurationProperties.class, " + configurationName + ".class }"
        );

        javaClass.addImport(HashMap.class);
        javaClass.addImport(List.class);
        javaClass.addImport(Map.class);
        javaClass.addImport(ApplicationContext.class);
        javaClass.addImport(ConditionalOnBean.class);
        javaClass.addImport("org.slf4j.Logger");
        javaClass.addImport("org.slf4j.LoggerFactory");
        javaClass.addImport("org.apache.camel.CamelContext");
        javaClass.addImport("org.apache.camel.CamelContextAware");
        javaClass.addImport("org.apache.camel.spring.boot.CamelAutoConfiguration");
        javaClass.addImport("org.apache.camel.spring.boot.DataFormatConfigurationProperties");
        javaClass.addImport("org.apache.camel.spring.boot.util.CamelPropertiesHelper");
        javaClass.addImport("org.apache.camel.spring.boot.util.ConditionalOnCamelContextAndAutoConfigurationBeans");
        javaClass.addImport("org.apache.camel.spring.boot.util.GroupCondition");
        javaClass.addImport("org.apache.camel.spring.boot.util.HierarchicalPropertiesEvaluator");
        javaClass.addImport("org.apache.camel.util.IntrospectionSupport");
        javaClass.addImport("org.apache.camel.util.ObjectHelper");
        javaClass.addImport("org.apache.camel.RuntimeCamelException");
        javaClass.addImport("org.apache.camel.spi.DataFormat");
        javaClass.addImport("org.apache.camel.spi.DataFormatCustomizer");
        javaClass.addImport("org.apache.camel.spi.DataFormatFactory");
        javaClass.addImport("org.apache.camel.spi.HasId");
        javaClass.addImport(model.getJavaType());

        javaClass.addField()
            .setPrivate()
            .setStatic(true)
            .setFinal(true)
            .setName("LOGGER")
            .setType("Logger")
            .setLiteralInitializer("LoggerFactory.getLogger(" + name + ".class)");
        javaClass.addField()
            .setPrivate()
            .setName("applicationContext")
            .setType("ApplicationContext")
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("camelContext")
            .setType("CamelContext")
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("configuration")
            .setType(configurationName)
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("customizers")
            .setType("List<DataFormatCustomizer<" + model.getShortJavaType() + ">>")
            .addAnnotation(Autowired.class)
                .setLiteralValue("required", "false");

        javaClass.addNestedType(
            Roaster.create(JavaClassSource.class)
                .setName("GroupConditions")
                .setStatic(true)
                .setPackagePrivate()
                .extendSuperType(Roaster.create(JavaClassSource.class).setName("GroupCondition"))
                .addMethod()
                    .setName("GroupConditions")
                    .setConstructor(true)
                    .setPublic()
                    .setBody("super(\"camel.dataformat\", \"camel.dataformat." + dataformatName + "\");")
                    .getOrigin()
        );


        String body = createDataFormatBody(model.getShortJavaType(), dataformatName);
        String methodName = "configure" + model.getShortJavaType() + "Factory";

        MethodSource<JavaClassSource> method = javaClass.addMethod()
            .setName(methodName)
            .setPublic()
            .setBody(body)
            .setReturnType("org.apache.camel.spi.DataFormatFactory")
            .addThrows(Exception.class);

        // Determine all the aliases
        // adding the '-dataformat' suffix to prevent collision with component names
        String[] springBeanAliases = dataFormatAliases.stream().map(alias -> alias + "-dataformat-factory").toArray(size -> new String[size]);

        method.addAnnotation(Bean.class).setStringArrayValue("name", springBeanAliases);
        method.addAnnotation(ConditionalOnMissingBean.class).setLiteralValue("value", model.getShortJavaType() + ".class");

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        writeSourceIfChanged(javaClass, fileName);
    }

    private void createLanguageAutoConfigurationSource(
        String packageName, LanguageModel model, List<String> languageAliases, String overrideLanguageName) throws MojoFailureException {

        final String name = model.getJavaType().substring(model.getJavaType().lastIndexOf(".") + 1).replace("Language", "LanguageAutoConfiguration");
        final String configurationName = name.replace("LanguageAutoConfiguration", "LanguageConfiguration");
        final String languageName = (overrideLanguageName != null ? overrideLanguageName : model.getName()).toLowerCase(Locale.US);

        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        javaClass.setPackage(packageName);
        javaClass.setName(name);
        javaClass.getJavaDoc().setFullText("Generated by camel-package-maven-plugin - do not edit this file!");
        javaClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
        javaClass.addAnnotation(Configuration.class);
        javaClass.addAnnotation(Conditional.class).setLiteralValue(
            "{ ConditionalOnCamelContextAndAutoConfigurationBeans.class, " + name + ".GroupConditions.class }");
        javaClass.addAnnotation(AutoConfigureAfter.class).setLiteralValue("CamelAutoConfiguration.class");
        javaClass.addAnnotation(EnableConfigurationProperties.class).setLiteralValue(
            "{ LanguageConfigurationProperties.class, " + configurationName + ".class }"
        );

        javaClass.addImport(HashMap.class);
        javaClass.addImport(List.class);
        javaClass.addImport(Map.class);
        javaClass.addImport(ApplicationContext.class);
        javaClass.addImport(ConditionalOnBean.class);
        javaClass.addImport(ConfigurableBeanFactory.class);
        javaClass.addImport("org.slf4j.Logger");
        javaClass.addImport("org.slf4j.LoggerFactory");
        javaClass.addImport("org.apache.camel.CamelContext");
        javaClass.addImport("org.apache.camel.CamelContextAware");
        javaClass.addImport("org.apache.camel.spring.boot.CamelAutoConfiguration");
        javaClass.addImport("org.apache.camel.spring.boot.LanguageConfigurationProperties");
        javaClass.addImport("org.apache.camel.spring.boot.util.CamelPropertiesHelper");
        javaClass.addImport("org.apache.camel.spring.boot.util.ConditionalOnCamelContextAndAutoConfigurationBeans");
        javaClass.addImport("org.apache.camel.spring.boot.util.GroupCondition");
        javaClass.addImport("org.apache.camel.spring.boot.util.HierarchicalPropertiesEvaluator");
        javaClass.addImport("org.apache.camel.util.IntrospectionSupport");
        javaClass.addImport("org.apache.camel.util.ObjectHelper");
        javaClass.addImport("org.apache.camel.spi.HasId");
        javaClass.addImport("org.apache.camel.spi.LanguageCustomizer");
        javaClass.addImport(model.getJavaType());

        javaClass.addField()
            .setPrivate()
            .setStatic(true)
            .setFinal(true)
            .setName("LOGGER")
            .setType("Logger")
            .setLiteralInitializer("LoggerFactory.getLogger(" + name + ".class)");
        javaClass.addField()
            .setPrivate()
            .setName("applicationContext")
            .setType("ApplicationContext")
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("camelContext")
            .setType("CamelContext")
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("configuration")
            .setType(configurationName)
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("customizers")
            .setType("List<LanguageCustomizer<" + model.getShortJavaType() + ">>")
            .addAnnotation(Autowired.class)
                .setLiteralValue("required", "false");

        javaClass.addNestedType(
            Roaster.create(JavaClassSource.class)
                .setName("GroupConditions")
                .setStatic(true)
                .setPackagePrivate()
                .extendSuperType(Roaster.create(JavaClassSource.class).setName("GroupCondition"))
                .addMethod()
                    .setName("GroupConditions")
                    .setConstructor(true)
                    .setPublic()
                    .setBody("super(\"camel.component\", \"camel.component." + languageName + "\");")
                    .getOrigin()
        );

        String body = createLanguageBody(model.getShortJavaType(), languageName);
        String methodName = "configure" + model.getShortJavaType();

        MethodSource<JavaClassSource> method = javaClass.addMethod()
            .setName(methodName)
            .setPublic()
            .setBody(body)
            .setReturnType(model.getShortJavaType())
            .addThrows(Exception.class);

        // Determine all the aliases
        // adding the '-language' suffix to prevent collision with component names
        String[] springBeanAliases = languageAliases.stream().map(alias -> alias + "-language").toArray(size -> new String[size]);

        method.addAnnotation(Bean.class).setStringArrayValue("name", springBeanAliases);
        method.addAnnotation(Scope.class).setLiteralValue("ConfigurableBeanFactory.SCOPE_PROTOTYPE");
        method.addAnnotation(ConditionalOnMissingBean.class).setLiteralValue("value", model.getShortJavaType() + ".class");

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        writeSourceIfChanged(javaClass, fileName);
    }

    private void createComponentSpringFactorySource(String packageName, ComponentModel model) throws MojoFailureException {
        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Component", "ComponentAutoConfiguration");

        writeComponentSpringFactorySource(packageName, name);
    }

    private void createDataFormatSpringFactorySource(String packageName, DataFormatModel model) throws MojoFailureException {
        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("DataFormat", "DataFormatAutoConfiguration");

        writeComponentSpringFactorySource(packageName, name);
    }

    private void createLanguageSpringFactorySource(String packageName, LanguageModel model) throws MojoFailureException {
        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Language", "LanguageAutoConfiguration");

        writeComponentSpringFactorySource(packageName, name);
    }

    private static String createComponentBody(String shortJavaType, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append(shortJavaType).append(" component = new ").append(shortJavaType).append("();").append("\n");
        sb.append("component.setCamelContext(camelContext);\n");
        sb.append("\n");
        sb.append("Map<String, Object> parameters = new HashMap<>();\n");
        sb.append("IntrospectionSupport.getProperties(configuration, parameters, null, false);\n");
        sb.append("\n");
        sb.append("for (Map.Entry<String, Object> entry : parameters.entrySet()) {\n");
        sb.append("    Object value = entry.getValue();\n");
        sb.append("    Class<?> paramClass = value.getClass();\n");
        sb.append("    if (paramClass.getName().endsWith(\"NestedConfiguration\")) {\n");
        sb.append("        Class nestedClass = null;\n");
        sb.append("        try {\n");
        sb.append("            nestedClass = (Class) paramClass.getDeclaredField(\"CAMEL_NESTED_CLASS\").get(null);\n");
        sb.append("            HashMap<String, Object> nestedParameters = new HashMap<>();\n");
        sb.append("            IntrospectionSupport.getProperties(value, nestedParameters, null, false);\n");
        sb.append("            Object nestedProperty = nestedClass.newInstance();\n");
        sb.append("            CamelPropertiesHelper.setCamelProperties(camelContext, nestedProperty, nestedParameters, false);\n");
        sb.append("            entry.setValue(nestedProperty);\n");
        sb.append("        } catch (NoSuchFieldException e) {\n");
        sb.append("            // ignore, class must not be a nested configuration class after all\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("CamelPropertiesHelper.setCamelProperties(camelContext, component, parameters, false);\n");
        sb.append("\n");
        sb.append("if (ObjectHelper.isNotEmpty(customizers)) {\n");
        sb.append("    for (ComponentCustomizer<").append(shortJavaType).append("> customizer : customizers) {\n");
        sb.append("\n");
        sb.append("        boolean useCustomizer = (customizer instanceof HasId)");
        sb.append("            ? HierarchicalPropertiesEvaluator.evaluate(\n");
        sb.append("                applicationContext.getEnvironment(),\n");
        sb.append("               \"camel.component.customizer\",\n");
        sb.append("               \"camel.component.").append(name).append(".customizer\",\n");
        sb.append("               ((HasId)customizer).getId())\n");
        sb.append("            : HierarchicalPropertiesEvaluator.evaluate(\n");
        sb.append("                applicationContext.getEnvironment(),\n");
        sb.append("               \"camel.component.customizer\",\n");
        sb.append("               \"camel.component.").append(name).append(".customizer\");\n");
        sb.append("\n");
        sb.append("        if (useCustomizer) {\n");
        sb.append("            LOGGER.debug(\"Configure component {}, with customizer {}\", component, customizer);\n");
        sb.append("            customizer.customize(component);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("return component;");

        return sb.toString();
    }

    private static String createDataFormatBody(String shortJavaType, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("return new DataFormatFactory() {\n");
        sb.append("    @Override\n");
        sb.append("    public DataFormat newInstance() {\n");
        sb.append("        ").append(shortJavaType).append(" dataformat = new ").append(shortJavaType).append("();").append("\n");
        sb.append("        if (CamelContextAware.class.isAssignableFrom(").append(shortJavaType).append(".class)) {\n");
        sb.append("            CamelContextAware contextAware = CamelContextAware.class.cast(dataformat);\n");
        sb.append("            if (contextAware != null) {\n");
        sb.append("                contextAware.setCamelContext(camelContext);\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        try {\n");
        sb.append("            Map<String, Object> parameters = new HashMap<>();\n");
        sb.append("            IntrospectionSupport.getProperties(configuration, parameters, null, false);\n");
        sb.append("            CamelPropertiesHelper.setCamelProperties(camelContext, dataformat, parameters, false);\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            throw new RuntimeCamelException(e);\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("if (ObjectHelper.isNotEmpty(customizers)) {\n");
        sb.append("    for (DataFormatCustomizer<").append(shortJavaType).append("> customizer : customizers) {\n");
        sb.append("\n");
        sb.append("        boolean useCustomizer = (customizer instanceof HasId)");
        sb.append("            ? HierarchicalPropertiesEvaluator.evaluate(\n");
        sb.append("                applicationContext.getEnvironment(),\n");
        sb.append("               \"camel.dataformat.customizer\",\n");
        sb.append("               \"camel.dataformat.").append(name).append(".customizer\",\n");
        sb.append("               ((HasId)customizer).getId())\n");
        sb.append("            : HierarchicalPropertiesEvaluator.evaluate(\n");
        sb.append("                applicationContext.getEnvironment(),\n");
        sb.append("               \"camel.dataformat.customizer\",\n");
        sb.append("               \"camel.dataformat.").append(name).append(".customizer\");\n");
        sb.append("\n");
        sb.append("        if (useCustomizer) {\n");
        sb.append("            LOGGER.debug(\"Configure dataformat {}, with customizer {}\", dataformat, customizer);\n");
        sb.append("            customizer.customize(dataformat);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("        return dataformat;\n");
        sb.append("    }\n");
        sb.append("};\n");

        return sb.toString();
    }

    private static String createLanguageBody(String shortJavaType, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append(shortJavaType).append(" language = new ").append(shortJavaType).append("();").append("\n");
        sb.append("if (CamelContextAware.class.isAssignableFrom(").append(shortJavaType).append(".class)) {\n");
        sb.append("    CamelContextAware contextAware = CamelContextAware.class.cast(language);\n");
        sb.append("    if (contextAware != null) {\n");
        sb.append("        contextAware.setCamelContext(camelContext);\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("Map<String, Object> parameters = new HashMap<>();\n");
        sb.append("IntrospectionSupport.getProperties(configuration, parameters, null, false);\n");
        sb.append("CamelPropertiesHelper.setCamelProperties(camelContext, language, parameters, false);\n");
        sb.append("\n");
        sb.append("\n");
        sb.append("if (ObjectHelper.isNotEmpty(customizers)) {\n");
        sb.append("    for (LanguageCustomizer<").append(shortJavaType).append("> customizer : customizers) {\n");
        sb.append("\n");
        sb.append("        boolean useCustomizer = (customizer instanceof HasId)");
        sb.append("            ? HierarchicalPropertiesEvaluator.evaluate(\n");
        sb.append("                applicationContext.getEnvironment(),\n");
        sb.append("               \"camel.language.customizer\",\n");
        sb.append("               \"camel.language.").append(name).append(".customizer\",\n");
        sb.append("               ((HasId)customizer).getId())\n");
        sb.append("            : HierarchicalPropertiesEvaluator.evaluate(\n");
        sb.append("                applicationContext.getEnvironment(),\n");
        sb.append("               \"camel.language.customizer\",\n");
        sb.append("               \"camel.language.").append(name).append(".customizer\");\n");
        sb.append("\n");
        sb.append("        if (useCustomizer) {\n");
        sb.append("            LOGGER.debug(\"Configure language {}, with customizer {}\", language, customizer);\n");
        sb.append("            customizer.customize(language);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("return language;");

        return sb.toString();
    }

    private static void sortImports(Importer importer) {
        // sort imports
        List<Import> imports = importer.getImports();

        // sort imports
        List<String> names = new ArrayList<>();
        for (Import imp : imports) {
            names.add(imp.getQualifiedName());
        }
        // sort
        Collections.sort(names, (s1, s2) -> {
            // java comes first
            if (s1.startsWith("java.")) {
                s1 = "___" + s1;
            }
            if (s2.startsWith("java.")) {
                s2 = "___" + s2;
            }
            // then javax comes next
            if (s1.startsWith("javax.")) {
                s1 = "__" + s1;
            }
            if (s2.startsWith("javax.")) {
                s2 = "__" + s2;
            }
            // org.w3c is for some odd reason also before others
            if (s1.startsWith("org.w3c.")) {
                s1 = "_" + s1;
            }
            if (s2.startsWith("org.w3c.")) {
                s2 = "_" + s2;
            }
            return s1.compareTo(s2);
        });

        // remove all imports first
        for (String name : names) {
            importer.removeImport(name);
        }
        // and add them back in correct order
        for (String name : names) {
            importer.addImport(name);
        }
    }

    private static String loadModelJson(Set<File> jsonFiles, String modelName) {
        try {
            for (File file : jsonFiles) {
                if (file.getName().equals(modelName + ".json")) {
                    String json = loadText(new FileInputStream(file));
                    boolean isModel = json.contains("\"kind\": \"model\"");
                    if (isModel) {
                        return json;
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private static String loadComponentJson(Set<File> jsonFiles, String componentName) {
        try {
            for (File file : jsonFiles) {
                if (file.getName().equals(componentName + ".json")) {
                    String json = loadText(new FileInputStream(file));
                    boolean isComponent = json.contains("\"kind\": \"component\"");
                    if (isComponent) {
                        return json;
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private static String loadDataFormatJson(Set<File> jsonFiles, String dataFormatName) {
        try {
            for (File file : jsonFiles) {
                if (file.getName().equals(dataFormatName + ".json")) {
                    String json = loadText(new FileInputStream(file));
                    boolean isDataFormat = json.contains("\"kind\": \"dataformat\"");
                    if (isDataFormat) {
                        return json;
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private static String loadLanguageJson(Set<File> jsonFiles, String languageName) {
        try {
            for (File file : jsonFiles) {
                if (file.getName().equals(languageName + ".json")) {
                    String json = loadText(new FileInputStream(file));
                    boolean isLanguage = json.contains("\"kind\": \"language\"");
                    if (isLanguage) {
                        return json;
                    }
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private static ComponentModel generateComponentModel(String componentName, String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);

        ComponentModel component = new ComponentModel(true);
        component.setScheme(getSafeValue("scheme", rows));
        component.setSyntax(getSafeValue("syntax", rows));
        component.setAlternativeSyntax(getSafeValue("alternativeSyntax", rows));
        component.setTitle(getSafeValue("title", rows));
        component.setDescription(getSafeValue("description", rows));
        component.setFirstVersion(JSonSchemaHelper.getSafeValue("firstVersion", rows));
        component.setLabel(getSafeValue("label", rows));
        component.setDeprecated(getSafeValue("deprecated", rows));
        component.setDeprecationNote(getSafeValue("deprecationNote", rows));
        component.setConsumerOnly(getSafeValue("consumerOnly", rows));
        component.setProducerOnly(getSafeValue("producerOnly", rows));
        component.setJavaType(getSafeValue("javaType", rows));
        component.setGroupId(getSafeValue("groupId", rows));
        component.setArtifactId(getSafeValue("artifactId", rows));
        component.setVersion(getSafeValue("version", rows));

        rows = JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);
        for (Map<String, String> row : rows) {
            ComponentOptionModel option = new ComponentOptionModel();
            option.setName(getSafeValue("name", row));
            option.setDisplayName(getSafeValue("displayName", row));
            option.setKind(getSafeValue("kind", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDeprecationNote(getSafeValue("deprecationNote", row));
            option.setDescription(getSafeValue("description", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setEnums(getSafeValue("enum", row));
            component.addComponentOption(option);
        }

        rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        for (Map<String, String> row : rows) {
            EndpointOptionModel option = new EndpointOptionModel();
            option.setName(getSafeValue("name", row));
            option.setDisplayName(getSafeValue("displayName", row));
            option.setKind(getSafeValue("kind", row));
            option.setGroup(getSafeValue("group", row));
            option.setRequired(getSafeValue("required", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setEnums(getSafeValue("enum", row));
            option.setPrefix(getSafeValue("prefix", row));
            option.setMultiValue(getSafeValue("multiValue", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDeprecationNote(getSafeValue("deprecationNote", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setDescription(getSafeValue("description", row));
            option.setEnumValues(getSafeValue("enum", row));
            component.addEndpointOption(option);
        }

        return component;
    }

    private static DataFormatModel generateDataFormatModel(String dataFormatName, String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("dataformat", json, false);

        DataFormatModel dataFormat = new DataFormatModel();
        dataFormat.setTitle(getSafeValue("title", rows));
        dataFormat.setName(getSafeValue("name", rows));
        dataFormat.setModelName(getSafeValue("modelName", rows));
        dataFormat.setDescription(getSafeValue("description", rows));
        dataFormat.setFirstVersion(JSonSchemaHelper.getSafeValue("firstVersion", rows));
        dataFormat.setLabel(getSafeValue("label", rows));
        dataFormat.setDeprecated(getSafeValue("deprecated", rows));
        dataFormat.setDeprecationNote(getSafeValue("deprecationNote", rows));
        dataFormat.setJavaType(getSafeValue("javaType", rows));
        dataFormat.setGroupId(getSafeValue("groupId", rows));
        dataFormat.setArtifactId(getSafeValue("artifactId", rows));
        dataFormat.setVersion(getSafeValue("version", rows));

        rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        for (Map<String, String> row : rows) {
            DataFormatOptionModel option = new DataFormatOptionModel();
            option.setName(getSafeValue("name", row));
            option.setDisplayName(getSafeValue("displayName", row));
            option.setKind(getSafeValue("kind", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDeprecationNote(getSafeValue("deprecationNote", row));
            option.setDescription(getSafeValue("description", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setEnumValues(getSafeValue("enum", row));
            dataFormat.addDataFormatOption(option);
        }

        return dataFormat;
    }

    private static LanguageModel generateLanguageModel(String languageName, String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("language", json, false);

        LanguageModel language = new LanguageModel();
        language.setTitle(getSafeValue("title", rows));
        language.setName(getSafeValue("name", rows));
        language.setModelName(getSafeValue("modelName", rows));
        language.setDescription(getSafeValue("description", rows));
        language.setFirstVersion(JSonSchemaHelper.getSafeValue("firstVersion", rows));
        language.setLabel(getSafeValue("label", rows));
        language.setDeprecated(getSafeValue("deprecated", rows));
        language.setDeprecationNote(getSafeValue("deprecationNote", rows));
        language.setJavaType(getSafeValue("javaType", rows));
        language.setGroupId(getSafeValue("groupId", rows));
        language.setArtifactId(getSafeValue("artifactId", rows));
        language.setVersion(getSafeValue("version", rows));

        rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        for (Map<String, String> row : rows) {
            LanguageOptionModel option = new LanguageOptionModel();
            option.setName(getSafeValue("name", row));
            option.setDisplayName(getSafeValue("displayName", row));
            option.setKind(getSafeValue("kind", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDeprecationNote(getSafeValue("deprecationNote", row));
            option.setDescription(getSafeValue("description", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setEnumValues(getSafeValue("enum", row));
            language.addLanguageOption(option);
        }

        return language;
    }

    private OtherModel generateOtherModel(String json) {
        List<Map<String, String>> rows = parseJsonSchema("model", json, false);

        OtherModel model = new OtherModel();
        model.setName(getSafeValue("name", rows));
        model.setTitle(getSafeValue("title", rows));
        model.setDescription(getSafeValue("description", rows));
        model.setJavaType(getSafeValue("javaType", rows));
        model.setLabel(getSafeValue("label", rows));
        model.setDeprecated(getSafeValue("deprecated", rows));
        model.setDeprecationNote(getSafeValue("deprecationNote", rows));

        rows = parseJsonSchema("properties", json, true);
        for (Map<String, String> row : rows) {
            OtherOptionModel option = new OtherOptionModel();
            option.setName(getSafeValue("name", row));
            option.setDisplayName(getSafeValue("displayName", row));
            option.setKind(getSafeValue("kind", row));
            option.setGroup(getSafeValue("group", row));
            option.setRequired(getSafeValue("required", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setEnums(getSafeValue("enum", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDeprecationNote(getSafeValue("deprecationNote", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setDescription(getSafeValue("description", row));
            option.setEnums(getSafeValue("enums", row));

            model.addOptionModel(option);
        }

        return model;
    }

    private List<String> findComponentNames() {
        List<String> componentNames = new ArrayList<String>();
        for (Resource r : project.getBuild().getResources()) {
            File f = new File(r.getDirectory());
            if (!f.exists()) {
                f = new File(project.getBasedir(), r.getDirectory());
            }
            f = new File(f, "META-INF/services/org/apache/camel/component");

            if (f.exists() && f.isDirectory()) {
                File[] files = f.listFiles();
                if (files != null) {
                    for (File file : files) {
                        // skip directories as there may be a sub .resolver directory
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
        return componentNames;
    }

    private List<String> findDataFormatNames() {
        List<String> dataFormatNames = new ArrayList<String>();
        for (Resource r : project.getBuild().getResources()) {
            File f = new File(r.getDirectory());
            if (!f.exists()) {
                f = new File(project.getBasedir(), r.getDirectory());
            }
            f = new File(f, "META-INF/services/org/apache/camel/dataformat");

            if (f.exists() && f.isDirectory()) {
                File[] files = f.listFiles();
                if (files != null) {
                    for (File file : files) {
                        // skip directories as there may be a sub .resolver directory
                        if (file.isDirectory()) {
                            continue;
                        }
                        String name = file.getName();
                        if (name.charAt(0) != '.') {
                            dataFormatNames.add(name);
                        }
                    }
                }
            }
        }
        return dataFormatNames;
    }

    private List<String> findLanguageNames() {
        List<String> languageNames = new ArrayList<String>();
        for (Resource r : project.getBuild().getResources()) {
            File f = new File(r.getDirectory());
            if (!f.exists()) {
                f = new File(project.getBasedir(), r.getDirectory());
            }
            f = new File(f, "META-INF/services/org/apache/camel/language");

            if (f.exists() && f.isDirectory()) {
                File[] files = f.listFiles();
                if (files != null) {
                    for (File file : files) {
                        // skip directories as there may be a sub .resolver directory
                        if (file.isDirectory()) {
                            continue;
                        }
                        String name = file.getName();
                        if (name.charAt(0) != '.') {
                            languageNames.add(name);
                        }
                    }
                }
            }
        }
        return languageNames;
    }

    private void writeSourceIfChanged(JavaClassSource source, String fileName) throws MojoFailureException {
        writeSourceIfChanged(source.toString(), fileName);
    }

    private void writeSourceIfChanged(String source, String fileName) throws MojoFailureException {

        source = Formatter.format(source);
        source = source.replaceAll("\\t", "    ");

        File target = new File(SpringBootHelper.starterSrcDir(baseDir, project.getArtifactId()), fileName);

        deleteFileOnMainArtifact(target);

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt");
            String header = loadText(is);
            String code = source;
            code = header + code;
            getLog().debug("Source code generated:\n" + code);

            if (target.exists()) {
                String existing = FileUtils.readFileToString(target);
                if (!code.equals(existing)) {
                    FileUtils.write(target, code, false);
                    getLog().info("Updated existing file: " + target);
                } else {
                    getLog().debug("No changes to existing file: " + target);
                }
            } else {
                FileUtils.write(target, code);
                getLog().info("Created file: " + target);
            }
        } catch (Exception e) {
            throw new MojoFailureException("IOError with file " + target, e);
        }
    }

    private void writeComponentSpringFactorySource(String packageName, String name) throws MojoFailureException {
        StringBuilder sb = new StringBuilder();
        sb.append("org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\\n");

        String lineToAdd = packageName + "." + name + "\n";
        sb.append(lineToAdd);

        String fileName = "META-INF/spring.factories";
        File target = new File(SpringBootHelper.starterResourceDir(baseDir, project.getArtifactId()), fileName);

        deleteFileOnMainArtifact(target);

        if (target.exists()) {
            try {
                // is the auto configuration already in the file
                boolean found = false;
                List<String> lines = FileUtils.readLines(target);
                for (String line : lines) {
                    if (line.contains(name)) {
                        found = true;
                        break;
                    }
                }

                if (found) {
                    getLog().debug("No changes to existing file: " + target);
                } else {
                    // find last non empty line, so we can add our new line after that
                    int lastLine = 0;
                    for (int i = lines.size() - 1; i >= 0; i--) {
                        String line = lines.get(i);
                        if (!line.trim().isEmpty()) {
                            // adjust existing line so its being continued
                            line = line + ",\\";
                            lines.set(i, line);
                            lastLine = i;
                            break;
                        }
                    }
                    lines.add(lastLine + 1, lineToAdd);

                    StringBuilder code = new StringBuilder();
                    for (String line : lines) {
                        code.append(line).append("\n");
                    }

                    // update
                    FileUtils.write(target, code.toString(), false);
                    getLog().info("Updated existing file: " + target);
                }
            } catch (Exception e) {
                throw new MojoFailureException("IOError with file " + target, e);
            }
        } else {
            // create new file
            try {
                InputStream is = getClass().getClassLoader().getResourceAsStream("license-header.txt");
                String header = loadText(is);
                String code = sb.toString();
                // add empty new line after header
                code = header + "\n" + code;
                getLog().debug("Source code generated:\n" + code);

                FileUtils.write(target, code);
                getLog().info("Created file: " + target);
            } catch (Exception e) {
                throw new MojoFailureException("IOError with file " + target, e);
            }
        }
    }

    /*
    private void writeAdditionalSpringMetaData(String prefix, String type, String name) throws MojoFailureException {
        String fullQualifiedName = prefix + "." + type + "." + name + "." + "enabled";
        String fileName = "META-INF/additional-spring-configuration-metadata.json";
        File target = new File(SpringBootHelper.starterResourceDir(baseDir, project.getArtifactId()), fileName);

        deleteFileOnMainArtifact(target);

        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Map<String, Object> map = null;
            List<Map<String, Object>> properties = null;

            if (target.exists()) {
                BufferedReader br = new BufferedReader(new FileReader(target));
                map = gson.fromJson(br, Map.class);

                properties = (List<Map<String, Object>>)map.get("properties");
                if (properties != null && properties.stream().anyMatch(m -> fullQualifiedName.equals(m.get("name")))) {
                    getLog().debug("No changes to existing file: " + target);
                    return;
                }
            }

            Map<String, Object> meta = new HashMap();
            meta.put("name", fullQualifiedName);
            meta.put("type", "java.lang.Boolean");
            meta.put("defaultValue", true);
            meta.put("description", "Enable " + name + " " + type);

            if (properties == null) {
                properties = new ArrayList<>(1);
            }

            if (map == null) {
                map = new HashMap();
            }

            properties.add(meta);
            map.put("properties", properties);

            FileUtils.write(target, gson.toJson(map));
        } catch (Exception e) {
            throw new MojoFailureException("IOError with file " + target, e);
        }
    }
    */

    private void deleteFileOnMainArtifact(File starterFile) {
        if (!DELETE_FILES_ON_MAIN_ARTIFACTS) {
            return;
        }

        String relativePath = SpringBootHelper.starterDir(baseDir, project.getArtifactId()).toPath().relativize(starterFile.toPath()).toString();
        File mainArtifactFile = new File(baseDir, relativePath);
        if (mainArtifactFile.exists()) {
            boolean deleted = mainArtifactFile.delete();
            if (!deleted) {
                throw new IllegalStateException("Cannot delete file " + mainArtifactFile);
            }
        }
    }

    private JavaClassSource createConditionType(JavaClassSource parentClass, String prefix, String type) {
        parentClass.addImport(ConditionMessage.class);
        parentClass.addImport(ConditionContext.class);
        parentClass.addImport(ConditionOutcome.class);
        parentClass.addImport(RelaxedPropertyResolver.class);
        parentClass.addImport(AnnotatedTypeMetadata.class);
        parentClass.addImport(SpringBootCondition.class);

        JavaClassSource condition = Roaster.create(JavaClassSource.class);
        condition.setName("Condition");
        condition.extendSuperType(SpringBootCondition.class);
        condition.setPublic();
        condition.setStatic(true);

        condition.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());

        String fullQualifiedType = prefix.endsWith(".") ? prefix +  type : prefix + "." + type;

        MethodSource<JavaClassSource> isEnabled = condition.addMethod();
        isEnabled.setName("isEnabled");
        isEnabled.setPrivate();
        isEnabled.addParameter(ConditionContext.class, "context");
        isEnabled.addParameter(String.class, "prefix");
        isEnabled.addParameter(boolean.class, "defaultValue");
        isEnabled.setReturnType(boolean.class);
        isEnabled.setBody(new StringBuilder()
            .append("RelaxedPropertyResolver resolver = new RelaxedPropertyResolver(context.getEnvironment(), prefix);\n")
            .append("return resolver.getProperty(\"enabled\", Boolean.class, defaultValue);")
            .toString()
        );

        MethodSource<JavaClassSource> matchMethod = condition.getMethod("getMatchOutcome", ConditionContext.class, AnnotatedTypeMetadata.class);
        matchMethod.setBody(new StringBuilder()
            .append("boolean groupEnabled = isEnabled(conditionContext, \"").append(prefix).append(".\", true);\n")
            .append("ConditionMessage.Builder message = ConditionMessage.forCondition(\"").append(fullQualifiedType).append("\");\n")
            .append("if (isEnabled(conditionContext, \"").append(fullQualifiedType).append(".\", groupEnabled)) {\n")
            .append("    return ConditionOutcome.match(message.because(\"enabled\"));\n")
            .append("}\n")
            .append("return ConditionOutcome.noMatch(message.because(\"not enabled\"));\n")
            .toString()
        );

        return condition;
    }

}
