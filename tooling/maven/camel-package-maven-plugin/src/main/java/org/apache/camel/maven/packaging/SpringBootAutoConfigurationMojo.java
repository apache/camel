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
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.maven.packaging.model.ComponentOptionModel;
import org.apache.camel.maven.packaging.model.DataFormatModel;
import org.apache.camel.maven.packaging.model.DataFormatOptionModel;
import org.apache.camel.maven.packaging.model.EndpointOptionModel;
import org.apache.camel.maven.packaging.model.LanguageModel;
import org.apache.camel.maven.packaging.model.LanguageOptionModel;
import org.apache.camel.maven.packaging.model.OtherModel;
import org.apache.camel.maven.packaging.model.OtherOptionModel;
import org.apache.camel.maven.packaging.srcgen.Annotation;
import org.apache.camel.maven.packaging.srcgen.GenericType;
import org.apache.camel.maven.packaging.srcgen.JavaClass;
import org.apache.camel.maven.packaging.srcgen.Method;
import org.apache.camel.maven.packaging.srcgen.Property;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jboss.forge.roaster.model.util.Strings;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;

import static org.apache.camel.maven.packaging.AbstractGeneratorMojo.updateResource;
import static org.apache.camel.maven.packaging.JSonSchemaHelper.getPropertyDefaultValue;
import static org.apache.camel.maven.packaging.JSonSchemaHelper.getPropertyDescriptionValue;
import static org.apache.camel.maven.packaging.JSonSchemaHelper.getPropertyJavaType;
import static org.apache.camel.maven.packaging.JSonSchemaHelper.getPropertyType;
import static org.apache.camel.maven.packaging.JSonSchemaHelper.getSafeValue;
import static org.apache.camel.maven.packaging.JSonSchemaHelper.parseJsonSchema;
import static org.apache.camel.maven.packaging.PackageHelper.loadText;

/**
 * Generate Spring Boot auto configuration files for Camel components and data formats.
 */
@Mojo(name = "prepare-spring-boot-auto-configuration", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PROCESS_CLASSES)
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
     * Classes to include when adding {@link NestedConfigurationProperty} annotations.
     */
    private static final Pattern INCLUDE_INNER_PATTERN = Pattern.compile("org\\.apache\\.camel\\..*");

    /**
     * Whether to enable adding @NestedConfigurationProperty annotations to options.
     * This is disabled as the generated options likely is not configurable as plain POJOs
     * and there is also no documentation for each of the generated options.
     */
    private static final boolean ADD_NESTED_CONFIGURATION_PROPERTY = false;

    private static final Map<String, String> PRIMITIVEMAP;
    private static final Map<Type, Type> PRIMITIVE_CLASSES;

    static {
        PRIMITIVE_CLASSES = new HashMap<>();
        PRIMITIVE_CLASSES.put(boolean.class, Boolean.class);
        PRIMITIVE_CLASSES.put(char.class, Character.class);
        PRIMITIVE_CLASSES.put(long.class, Long.class);
        PRIMITIVE_CLASSES.put(int.class, Integer.class);
        PRIMITIVE_CLASSES.put(byte.class, Byte.class);
        PRIMITIVE_CLASSES.put(short.class, Short.class);
        PRIMITIVE_CLASSES.put(double.class, Double.class);
        PRIMITIVE_CLASSES.put(float.class, Float.class);
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

    private static final String[] IGNORE_MODULES = {/* Non-standard -> */ "camel-grape"};

    /**
     * The output directory for generated component schema file
     *
     */
    @Parameter(defaultValue = "${project.build.directory}/classes")
    protected File classesDir;

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The project build directory
     *
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    /**
     * The base directory
     *
     */
    @Parameter(defaultValue = "${basedir}")
    protected File baseDir;

    DynamicClassLoader projectClassLoader;

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

        executeAll();
    }

    private void executeAll() throws MojoExecutionException, MojoFailureException {
        Map<File, Supplier<String>> files =
                PackageHelper.findJsonFiles(buildDir, p -> p.isDirectory() || p.getName().endsWith(".json"))
                        .stream().collect(Collectors.toMap(Function.identity(), s -> cache(() -> loadJson(s))));

        executeModels(files);
        executeComponent(files);
        executeDataFormat(files);
        executeLanguage(files);
    }

    private static String loadJson(File file) {
        try (InputStream is = new FileInputStream(file)) {
            return loadText(is);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    private static <T> Supplier<T> cache(Supplier<T> supplier) {
        return new Supplier<T>() {
            T value;
            @Override
            public T get() {
                if (value == null) {
                    value = supplier.get();
                }
                return value;
            }
        };
    }

    private void executeModels(Map<File, Supplier<String>> files) throws MojoExecutionException, MojoFailureException {
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
        JavaClass commonClass = new JavaClass(getProjectClassLoader());
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
            Property prop = commonClass.addProperty(type, option.getName());
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

        writeSourceIfChanged(commonClass, packageName.replaceAll("\\.", "\\/") + "/" + commonName + ".java", true);

        Class commonClazz = generateDummyClass(commonClass.getCanonicalName());

        // Config class
        if (generatedNestedConfig) {
            JavaClass configClass = new JavaClass(getProjectClassLoader());
            configClass.setPackage(packageName);
            configClass.setName(configName);
            configClass.extendSuperType(commonClass);
            configClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
            configClass.addAnnotation(loadClass("org.springframework.boot.context.properties.ConfigurationProperties")).setStringValue("prefix", propertiesPrefix);
            configClass.addImport(Map.class);
            configClass.addImport(HashMap.class);
            configClass.removeImport(commonClass);

            configClass.addField()
                .setName("enabled")
                .setType(boolean.class)
                .setPrivate()
                .setLiteralInitializer("true")
                .getJavaDoc().setFullText("Enable the component");
            configClass.addField()
                .setName("configurations")
                .setType(loadType("java.util.Map<java.lang.String, " + packageName + "." + commonName + ">"))
                .setPrivate()
                .setLiteralInitializer("new HashMap<>()")
                .getJavaDoc().setFullText("Define additional configuration definitions");

            Method method;

            method = configClass.addMethod();
            method.setName("getConfigurations");
            method.setReturnType(loadType("java.util.Map<java.lang.String, " + packageName + "." + commonName + ">"));
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

            String fileName = packageName.replaceAll("\\.", "\\/") + "/" + configName + ".java";
            writeSourceIfChanged(configClass, fileName, true);
        }
    }

    private void createRestConfigurationSource(String packageName, OtherModel model, String propertiesPrefix) throws MojoFailureException {
        final int pos = model.getJavaType().lastIndexOf(".");
        final String className = model.getJavaType().substring(pos + 1) + "Properties";

        generateDummyClass(packageName + "." + className);

        // Common base class
        JavaClass javaClass = new JavaClass(getProjectClassLoader());
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

            // to avoid ugly names such as c-o-r-s
            if ("enableCORS".equalsIgnoreCase(name)) {
                name = "enableCors";
            }

            // generate inner class for non-primitive options
            Property prop = javaClass.addProperty(type, name);
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

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + className + ".java";
        writeSourceIfChanged(javaClass, fileName, true);
    }

    private void createRestModuleAutoConfigurationSource(String packageName, OtherModel model) throws MojoFailureException {
        final JavaClass javaClass = new JavaClass(getProjectClassLoader());
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
        javaClass.addImport("org.apache.camel.support.IntrospectionSupport");
        javaClass.addImport("org.apache.camel.spring.boot.util.CamelPropertiesHelper");
        javaClass.addImport("org.apache.camel.CamelContext");
        javaClass.addImport("org.apache.camel.model.rest.RestConstants");
        javaClass.addImport("org.apache.camel.spi.RestConfiguration");

        javaClass.addField()
            .setName("camelContext")
            .setType(loadClass("org.apache.camel.CamelContext"))
            .setPrivate()
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setName("config")
            .setType(loadClass(packageName + "." + configType))
            .setPrivate()
            .addAnnotation(Autowired.class);

        Method method;

        // Configuration
        method = javaClass.addMethod();
        method.setName("configure" + model.getShortJavaType());
        method.setPublic();
        method.addThrows(Exception.class);
        method.setReturnType(loadClass("org.apache.camel.spi.RestConfiguration"));
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
            + "    definition.setApiProperties(new HashMap<>(CollectionHelper.flattenKeysInMap(config.getApiProperty(), \".\")));\n"
            + "}\n"
            + "if (config.getComponentProperty() != null) {\n"
            + "    definition.setComponentProperties(new HashMap<>(CollectionHelper.flattenKeysInMap(config.getComponentProperty(), \".\")));\n"
            + "}\n"
            + "if (config.getConsumerProperty() != null) {\n"
            + "    definition.setConsumerProperties(new HashMap<>(CollectionHelper.flattenKeysInMap(config.getConsumerProperty(), \".\")));\n"
            + "}\n"
            + "if (config.getDataFormatProperty() != null) {\n"
            + "    definition.setDataFormatProperties(new HashMap<>(CollectionHelper.flattenKeysInMap(config.getDataFormatProperty(), \".\")));\n"
            + "}\n"
            + "if (config.getEndpointProperty() != null) {\n"
            + "    definition.setEndpointProperties(new HashMap<>(CollectionHelper.flattenKeysInMap(config.getEndpointProperty(), \".\")));\n"
            + "}\n"
            + "if (config.getCorsHeaders() != null) {\n"
            + "    Map<String, Object> map = CollectionHelper.flattenKeysInMap(config.getCorsHeaders(), \".\");\n"
            + "    Map<String, String> target = new HashMap<>();\n"
            + "    map.forEach((k, v) -> target.put(k, v.toString()));\n"
            + "    definition.setCorsHeaders(target);\n"
            + "}\n"
            + "return definition;"
        );

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        writeSourceIfChanged(javaClass, fileName, true);
        writeComponentSpringFactorySource(packageName, name);
    }

    private void executeComponent(Map<File, Supplier<String>> jsonFiles) throws MojoExecutionException, MojoFailureException {
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

    private void executeDataFormat(Map<File, Supplier<String>> jsonFiles) throws MojoExecutionException, MojoFailureException {
        // find the data format names
        List<String> dataFormatNames = findDataFormatNames();

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

    private void executeLanguage(Map<File, Supplier<String>> jsonFiles) throws MojoExecutionException, MojoFailureException {
        // find the language names
        List<String> languageNames = findLanguageNames();

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
        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Component", "ComponentConfiguration");

        final JavaClass javaClass = new JavaClass(getProjectClassLoader());
        javaClass.setPackage(packageName);
        javaClass.setName(name);
        javaClass.extendSuperType("ComponentConfigurationPropertiesCommon");
        javaClass.addImport("org.apache.camel.spring.boot.ComponentConfigurationPropertiesCommon");

        // add bogus field for enabled so spring boot tooling can get the javadoc as description in its metadata
        Property bogus = javaClass.addProperty("java.lang.Boolean", "enabled");
        String scheme = overrideComponentName != null ? overrideComponentName : model.getScheme();
        bogus.getField().getJavaDoc().setText("Whether to enable auto configuration of the " + scheme + " component. This is enabled by default.");
        bogus.removeAccessor();
        bogus.removeMutator();

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        if (!Strings.isBlank(model.getDescription())) {
            doc = model.getDescription() + "\n\n" + doc;
        }
        javaClass.getJavaDoc().setText(doc);

        String prefix = "camel.component." + (overrideComponentName != null ? overrideComponentName : model.getScheme());
        // make sure prefix is in lower case
        prefix = prefix.toLowerCase(Locale.US);
        javaClass.addAnnotation(Generated.class.getName()).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
        javaClass.addAnnotation("org.springframework.boot.context.properties.ConfigurationProperties").setStringValue("prefix", prefix);

        Set<JavaClass> nestedTypes = new LinkedHashSet<>();
        for (ComponentOptionModel option : model.getComponentOptions()) {

            if (skipComponentOption(model, option)) {
                // some component options should be skipped
                continue;
            }

            String type = option.getJavaType();

            // generate inner class for non-primitive options
            type = getSimpleJavaType(type);
            JavaClass javaClassSource = readJavaType(type);
            boolean isNestedProperty = isNestedProperty(nestedTypes, javaClassSource)
                || "org.apache.camel.converter.jaxp.XmlConverter".equals(type);
            if (isNestedProperty) {
                type = packageName + "." + name + "$" + option.getShortJavaType() + INNER_TYPE_SUFFIX;
            }

            // spring-boot auto configuration does not support complex types (unless they are enum, nested)
            // and if so then we should use a String type so spring-boot and its tooling support that
            // as Camel will be able to convert the string value into a lookup of the bean in the registry anyway
            // and therefore there is no problem, eg camel.component.jdbc.data-source = myDataSource
            // where the type would have been javax.sql.DataSource
            boolean complex = isComplexType(option) && !isNestedProperty && Strings.isBlank(option.getEnums());
            if (complex) {
                // force to use a string type
                type = "java.lang.String";
            }

            if (type.equals(packageName + "." + name + "$" + option.getShortJavaType() + INNER_TYPE_SUFFIX)) {
                Class configClass = generateDummyClass(type);
            }

            Property prop = javaClass.addProperty(type, option.getName());
            if (ADD_NESTED_CONFIGURATION_PROPERTY) {
                if (!type.endsWith(INNER_TYPE_SUFFIX)
                    && type.indexOf('[') == -1
                    && INCLUDE_INNER_PATTERN.matcher(type).matches()
                    && Strings.isBlank(option.getEnums())
                    && (javaClassSource == null || (javaClassSource.isClass() && !javaClassSource.isAbstract()))) {
                    // add nested configuration annotation for complex properties
                    prop.getField().addAnnotation(NestedConfigurationProperty.class);
                }
            }
            if ("true".equals(option.getDeprecated())) {
                prop.getField().addAnnotation(Deprecated.class);
                prop.getAccessor().addAnnotation(Deprecated.class);
                prop.getMutator().addAnnotation(Deprecated.class);
                // DeprecatedConfigurationProperty must be on getter when deprecated
                prop.getAccessor().addAnnotation(DeprecatedConfigurationProperty.class);
            }
            if (!Strings.isBlank(option.getDescription())) {
                String desc = option.getDescription();
                if (complex) {
                    if (!desc.endsWith(".")) {
                        desc = desc + ".";
                    }
                    desc = desc + " The option is a " + option.getJavaType() + " type.";
                }
                prop.getField().getJavaDoc().setFullText(desc);
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

        createComponentConfigurationSourceInnerClass(javaClass, nestedTypes, model);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        writeSourceIfChanged(javaClass, fileName, true);
    }

    private void createComponentConfigurationSourceInnerClass(JavaClass javaClass, Set<JavaClass> nestedTypes, ComponentModel model) throws MojoFailureException {
        // add inner classes for nested AutoConfiguration options
        for (JavaClass nestedType : nestedTypes) {

            final JavaClass innerClass = javaClass.addNestedType()
                    .setPublic()
                    .setStatic(true)
                    .setName(nestedType.getName() + INNER_TYPE_SUFFIX);
            // add source class name as a static field
            innerClass.addField()
                .setPublic()
                .setStatic(true)
                .setFinal(true)
                .setType(Class.class)
                .setName("CAMEL_NESTED_CLASS")
                .setLiteralInitializer(nestedType.getCanonicalName() + ".class");

            // parse option type
            for (Property sourceProp : getProperties(nestedType)) {

                GenericType propType = sourceProp.getType();

                // skip these types
                boolean ignore = sourceProp.hasAnnotation(XmlTransient.class);
                if (ignore || propType.getRawClass().getName().equals("org.apache.camel.CamelContext")) {
                    continue;
                }

                String wt = PRIMITIVEMAP.get(propType.toString());
                GenericType ptype = wt != null ? loadType(wt) : propType;
                final Property prop = innerClass.addProperty(ptype, sourceProp.getName());
                if (sourceProp.getField() != null) {
                    prop.getField().getJavaDoc().setText(sourceProp.getField().getJavaDoc().getText());
                    prop.getField().setLiteralInitializer(sourceProp.getField().getLiteralInitializer());
                }

                ComponentOptionModel com = model.getComponentOptions().stream()
                        .filter(o -> o.getName().equals(sourceProp.getName()))
                        .findFirst().orElse(null);
                EndpointOptionModel eom = Stream.concat(model.getEndpointOptions().stream(), model.getEndpointPathOptions().stream())
                        .filter(o -> o.getName().equals(sourceProp.getName()))
                        .findFirst().orElse(null);
                String deprecationNote = null;
                if (eom != null) {
                    prop.getField().getJavaDoc().setText(eom.getDescription());
                    prop.getField().setLiteralInitializer(asLiteralDefault(sourceProp.getType(), eom.getDefaultValue()));
                    deprecationNote = eom.getDeprecationNote();
                } else if (com != null) {
                    prop.getField().getJavaDoc().setText(com.getDescription());
                    prop.getField().setLiteralInitializer(asLiteralDefault(sourceProp.getType(), com.getDefaultValue()));
                    deprecationNote = com.getDeprecationNote();
                }

                boolean anEnum;
                Class<?> optionClass;
                if (!propType.getRawClass().isArray()) {
                    optionClass = propType.getRawClass();
                    anEnum = optionClass.isEnum();
                } else {
                    optionClass = null;
                    anEnum = false;
                }

                // add nested configuration annotation for complex properties
                if (ADD_NESTED_CONFIGURATION_PROPERTY) {
                    if (INCLUDE_INNER_PATTERN.matcher(propType.toString()).matches()
                        && !propType.getRawClass().isArray()
                        && !anEnum
                        && optionClass != null
                        && !optionClass.isInterface()
                        && !optionClass.isAnnotation()
                        && !Modifier.isAbstract(optionClass.getModifiers())) {
                        prop.getField().addAnnotation(NestedConfigurationProperty.class);
                    }
                }
                if (sourceProp.hasAnnotation(Deprecated.class)) {
                    if (deprecationNote != null && !deprecationNote.isEmpty()) {
                        String jd = prop.getField().getJavaDoc().getText();
                        if (jd != null) {
                            jd += "\n\n";
                        } else {
                            jd = "";
                        }
                        jd = "@deprecated " + deprecationNote;
                        prop.getField().getJavaDoc().setText(jd);
                    }
                    prop.getField().addAnnotation(Deprecated.class);
                    prop.getAccessor().addAnnotation(Deprecated.class);
                    prop.getMutator().addAnnotation(Deprecated.class);
                    // DeprecatedConfigurationProperty must be on getter when deprecated
                    prop.getAccessor().addAnnotation(DeprecatedConfigurationProperty.class);
                }

                // find description for the nested type on its field/setter javadoc or via Camel annotations
                String description = null;
                final Method mutator = sourceProp.getMutator();
                if (mutator.hasJavaDoc()) {
                    description = mutator.getJavaDoc().getFullText();
                } else if (sourceProp.hasField()) {
                    description = sourceProp.getField().getJavaDoc().getFullText();
                }
                if (Strings.isBlank(description) && sourceProp.hasAnnotation(UriPath.class)) {
                    description = sourceProp.getAnnotation(UriPath.class).getStringValue("description");
                }
                if (Strings.isBlank(description) && sourceProp.hasAnnotation(UriParam.class)) {
                    description = sourceProp.getAnnotation(UriParam.class).getStringValue("description");
                }
                if (Strings.isBlank(description) && sourceProp.hasAnnotation(Metadata.class)) {
                    description = sourceProp.getAnnotation(Metadata.class).getStringValue("description");
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
                                // favour description from the model
                                description = getPropertyDescriptionValue(rows, optionName);
                                if (description != null) {
                                    prop.getField().getJavaDoc().setFullText(description);
                                }
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
                            prop.setType(loadType(wrapperType));
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
    }

    private boolean isComplexType(ComponentOptionModel option) {
        // all the object types are complex
        return "object".equals(option.getType());
    }

    private boolean isComplexType(DataFormatOptionModel option) {
        // all the object types are complex
        return "object".equals(option.getType());
    }

    private boolean isComplexType(LanguageOptionModel option) {
        // all the object types are complex
        return "object".equals(option.getType());
    }

    // get properties for nested type and super types, only properties with setters are supported!!!
    private List<Property> getProperties(JavaClass nestedType) {
        final List<Property> properties = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        do {
            for (Property propertySource : nestedType.getProperties()) {
                // NOTE: fields with no setters are skipped
                if (propertySource.isMutable() && !names.contains(propertySource.getName())) {
                    properties.add(propertySource);
                    names.add(propertySource.getName());
                }
            }
            nestedType = readJavaType(nestedType.getSuperType());
        } while (nestedType != null);
        return properties;
    }

    private String asLiteralDefault(GenericType type, String defaultValue) {
        if (defaultValue != null && !defaultValue.isEmpty()) {
            if (type.getRawClass() == String.class) {
                return Annotation.quote(defaultValue);
            } else if (type.getRawClass().isEnum()) {
                return type.getRawClass().getSimpleName() + "." + defaultValue;
            } else if (type.getRawClass() == boolean.class || type.getRawClass() == Boolean.class) {
                return defaultValue;
            } else if (type.getRawClass() == int.class || type.getRawClass() == Integer.class) {
                return defaultValue;
            } else if (type.getRawClass() == long.class || type.getRawClass() == Long.class) {
                return defaultValue + "L";
            } else if (type.getRawClass() == float.class || type.getRawClass() == Float.class) {
                return defaultValue + "f";
            } else if (type.getRawClass() == double.class || type.getRawClass() == Double.class) {
                return defaultValue;
            } else if (type.getRawClass() == Class.class) {
                return defaultValue + ".class";
            } else {
//                throw new UnsupportedOperationException("Unsupported default value for type: "
//                        + type.toString() + ": " + defaultValue);
                return null;
            }
        }
        return null;
    }

    private GenericType loadType(String type) throws MojoFailureException {
        try {
            return GenericType.parse(type, getProjectClassLoader());
        } catch (ClassNotFoundException e) {
            throw new MojoFailureException("Unable to load type", e);
        }
    }

    // try loading class, looking for inner classes if needed
    private Class<?> loadClass(String loadClassName) throws MojoFailureException {
        Class<?> optionClass;
        while (true) {
            try {
                optionClass = getProjectClassLoader().loadClass(loadClassName);
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

    protected DynamicClassLoader getProjectClassLoader() {
        if (projectClassLoader == null) {
            final List<?> classpathElements;
            try {
                classpathElements = project.getTestClasspathElements();
            } catch (org.apache.maven.artifact.DependencyResolutionRequiredException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
            final URL[] urls = new URL[classpathElements.size()];
            int i = 0;
            for (Iterator<?> it = classpathElements.iterator(); it.hasNext(); i++) {
                try {
                    urls[i] = new File((String) it.next()).toURI().toURL();
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e.getMessage(), e);
                }
            }
            final ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            projectClassLoader = new DynamicClassLoader(urls, tccl != null ? tccl : getClass().getClassLoader());
        }
        return projectClassLoader;
    }

    private String getSimpleJavaType(String type) {
        // remove <?> as generic type as Roaster (Eclipse JDT) cannot use that
//        type = type.replaceAll("\\<\\?\\>", "");
        // use wrapper types for primitive types so a null mean that the option has not been configured
        String wrapper = PRIMITIVEMAP.get(type);
        if (wrapper != null) {
            type = wrapper;
        }
        return type;
    }

    // it's a nested property if the source exists and it's not an abstract class in this project, e.g. endpoint configuration
    private boolean isNestedProperty(Set<JavaClass> nestedTypes, JavaClass type) {
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
    private JavaClass readJavaType(String type) {
        if (!type.startsWith("java.lang.")) {
            final String fileName = type.replaceAll("[\\[\\]]", "").replaceAll("\\.", "\\/") + ".java";
            Path sourcePath = project.getCompileSourceRoots().stream()
                    .map(Paths::get)
                    .map(p -> p.resolve(fileName))
                    .filter(Files::isRegularFile)
                    .findFirst().orElse(null);
            if (sourcePath == null) {
                return null;
            }
            String sourceCode;
            try (InputStream is = Files.newInputStream(sourcePath)) {
                sourceCode = loadText(is);
            } catch (IOException e) {
                throw new RuntimeException("Unable to load source code", e);
            }
            try {
                Class<?> clazz = getProjectClassLoader().loadClass(type);
                JavaClass nestedType = new JavaClass(getProjectClassLoader())
                        .setPackage(clazz.getPackage().getName())
                        .setName(clazz.getSimpleName())
                        .setEnum(clazz.isEnum())
                        .setClass(!clazz.isInterface())
                        .setAbstract((clazz.getModifiers() & Modifier.ABSTRACT) != 0)
                        .setStatic((clazz.getModifiers() & Modifier.STATIC) != 0)
                        .extendSuperType(clazz.getGenericSuperclass() != null ? new GenericType(clazz.getGenericSuperclass()).toString() : null);

                List<java.lang.reflect.Method> publicMethods = Stream.of(clazz.getDeclaredMethods())
                        .filter(m -> Modifier.isPublic(m.getModifiers()))
                        .collect(Collectors.toList());
                List<java.lang.reflect.Method> allSetters = publicMethods.stream()
                        .filter(m -> m.getReturnType() == void.class || m.getReturnType() == clazz)
                        .filter(m -> m.getParameterCount() == 1)
                        .filter(m -> m.getName().matches("set[A-Z][a-zA-Z0-9]*"))
                        .collect(Collectors.toList());
                List<java.lang.reflect.Method> allGetters = publicMethods.stream()
                        .filter(m -> m.getReturnType() != void.class)
                        .filter(m -> m.getParameterCount() == 0)
                        .filter(m -> m.getName().matches("(get|is)[A-Z][a-zA-Z0-9]*"))
                        .collect(Collectors.toList());
                allSetters.stream()
                        .sorted(Comparator.comparing(m -> getSetterPosition(sourceCode, m)))
                        .map(m -> Strings.uncapitalize(m.getName().substring(3)))
                        .forEach(fn -> {
                            Class<?> ft;
                            Type wft;
                            boolean isBoolean;
                            java.lang.reflect.Field field = Stream.of(clazz.getDeclaredFields())
                                    .filter(f -> f.getName().equals(fn))
                                    .findAny().orElse(null);
                            List<java.lang.reflect.Method> setters = allSetters.stream()
                                    .filter(m -> m.getName().equals("set" + Strings.capitalize(fn)))
                                    .collect(Collectors.toList());
                            List<java.lang.reflect.Method> getters = allGetters.stream()
                                    .filter(m -> m.getName().equals("get" + Strings.capitalize(fn))
                                            || m.getName().equals("is" + Strings.capitalize(fn)))
                                    .collect(Collectors.toList());
                            java.lang.reflect.Method mutator;
                            java.lang.reflect.Method accessor;
                            if (setters.size() == 1) {
                                mutator = setters.get(0);
                                ft = mutator.getParameterTypes()[0];
                                wft = PRIMITIVE_CLASSES.getOrDefault(ft, ft);
                                isBoolean = ft == boolean.class || ft == Boolean.class;
                                accessor = allGetters.stream()
                                        .filter(m -> m.getName().equals("get" + Strings.capitalize(fn))
                                                || isBoolean && m.getName().equals("is" + Strings.capitalize(fn)))
                                        .filter(m -> PRIMITIVE_CLASSES.getOrDefault(m.getReturnType(), m.getReturnType()) == wft)
                                        .findAny().orElse(null);
                            } else if (field != null) {
                                ft = field.getType();
                                wft = PRIMITIVE_CLASSES.getOrDefault(ft, ft);
                                isBoolean = ft == boolean.class || ft == Boolean.class;
                                mutator = allSetters.stream()
                                        .filter(m -> m.getName().equals("set" + Strings.capitalize(fn)))
                                        .filter(m -> PRIMITIVE_CLASSES.getOrDefault(m.getParameterTypes()[0], m.getParameterTypes()[0]) == wft)
                                        .findAny().orElse(null);
                                accessor = allGetters.stream()
                                        .filter(m -> m.getName().equals("get" + Strings.capitalize(fn))
                                                || isBoolean && m.getName().equals("is" + Strings.capitalize(fn)))
                                        .filter(m -> PRIMITIVE_CLASSES.getOrDefault(m.getReturnType(), m.getReturnType()) == wft)
                                        .findAny().orElse(null);
                            } else {
                                if (getters.size() == 1) {
                                    ft = getters.get(0).getReturnType();
                                } else {
                                    throw new IllegalStateException("Unable to determine type for property " + fn);
                                }
                                wft = PRIMITIVE_CLASSES.getOrDefault(ft, ft);
                                mutator = setters.stream()
                                        .filter(m -> PRIMITIVE_CLASSES.getOrDefault(m.getParameterTypes()[0], m.getParameterTypes()[0]) == wft)
                                        .findAny().orElse(null);
                                accessor = getters.stream()
                                        .filter(m -> PRIMITIVE_CLASSES.getOrDefault(m.getReturnType(), m.getReturnType()) == wft)
                                        .findAny().orElse(null);
                            }
                            if (mutator == null) {
                                throw new IllegalStateException("Could not find mutator for property " + fn);
                            }
                            Property property = nestedType.addProperty(new GenericType(wft), fn);
                            property.getMutator().getJavaDoc().setText(getSetterJavaDoc(sourceCode, fn));
                            for (java.lang.annotation.Annotation ann : mutator.getAnnotations()) {
                                addAnnotation(ac -> property.getMutator().addAnnotation(ac), ann);
                            }
                            if (accessor != null) {
                                for (java.lang.annotation.Annotation ann : accessor.getAnnotations()) {
                                    addAnnotation(ac -> property.getAccessor().addAnnotation(ac), ann);
                                }
                            } else {
                                property.removeAccessor();
                            }
                            if (field != null) {
                                for (java.lang.annotation.Annotation ann : field.getAnnotations()) {
                                    addAnnotation(ac -> property.getField().addAnnotation(ac), ann);
                                }
                            } else {
                                property.removeField();
                            }
                        });
                return nestedType;
            } catch (ClassNotFoundException e) {
                return null;
            }
        }
        return null;
    }

    private int getSetterPosition(String sourceCode, java.lang.reflect.Method m) {
        int i0 = sourceCode.indexOf("void " + m.getName() + "(");
        int i1 = sourceCode.indexOf(m.getDeclaringClass().getSimpleName() + " " + m.getName() + "(");
        int l = sourceCode.length();
        return Math.min(i0 > 0 ? i0 : l, i1 > 0 ? i1 : l);
    }

    private String getSetterJavaDoc(String sourceCode, String name) {
        int idx = sourceCode.indexOf("public void set" + Strings.capitalize(name) + "(");
        if (idx > 0) {
            sourceCode = sourceCode.substring(0, idx);
            idx = sourceCode.lastIndexOf("/**");
            if (idx > 0) {
                sourceCode = sourceCode.substring(idx + 3);
                idx = sourceCode.indexOf("*/");
                if (idx > 0) {
                    sourceCode = sourceCode.substring(0, idx);
                    List<String> lines = Stream.of(sourceCode.split("\n"))
                            .map(String::trim)
                            .map(s -> s.startsWith("*") ? s.substring(1) : s)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.toList());
                    int lastLine = 0;
                    while (lastLine < lines.size()) {
                        if (lines.get(lastLine).startsWith("@")) {
                            break;
                        }
                        lastLine++;
                    }
                    sourceCode = lines.subList(0, lastLine).stream()
                            .map(s -> s.replaceAll("  ", " "))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .collect(Collectors.joining(" "));
                    return sourceCode;
                }

            }
        }
        return null;
    }

    private void addAnnotation(Function<Class<? extends java.lang.annotation.Annotation>, Annotation> creator, java.lang.annotation.Annotation ann) {
        Class<? extends java.lang.annotation.Annotation> ac = ann.annotationType();
        Annotation a = creator.apply(ac);
        for (java.lang.reflect.Method m : ac.getMethods()) {
            if ("equals".equals(m.getName()) || "toString".equals(m.getName()) || "hashCode".equals(m.getName())) {
                continue;
            }
            String n = m.getName();
            try {
                Object v = m.invoke(ann);
                if (v != null) {
                    a.setLiteralValue(n, v.toString());
                }
            } catch (Exception e) {
                throw new RuntimeException("Unable to retrieve annotation value " + n + " on " + ac.getName());
            }
        }
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
        final JavaClass javaClass = new JavaClass(getProjectClassLoader());

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("DataFormat", "DataFormatConfiguration");
        javaClass.setPackage(packageName).setName(name);
        javaClass.extendSuperType("DataFormatConfigurationPropertiesCommon");
        javaClass.addImport("org.apache.camel.spring.boot.DataFormatConfigurationPropertiesCommon");

        // add bogus field for enabled so spring boot tooling can get the javadoc as description in its metadata
        Property bogus = javaClass.addProperty("java.lang.Boolean", "enabled");
        bogus.getField().getJavaDoc().setText("Whether to enable auto configuration of the " + model.getName() + " data format. This is enabled by default.");
        bogus.removeAccessor();
        bogus.removeMutator();

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

            // spring-boot auto configuration does not support complex types (unless they are enum, nested)
            // and if so then we should use a String type so spring-boot and its tooling support that
            // as Camel will be able to convert the string value into a lookup of the bean in the registry anyway
            // and therefore there is no problem, eg camel.component.jdbc.data-source = myDataSource
            // where the type would have been javax.sql.DataSource
            boolean complex = isComplexType(option) && Strings.isBlank(option.getEnumValues());
            if (complex) {
                // force to use a string type
                type = "java.lang.String";
            }

            Property prop = javaClass.addProperty(type, option.getName());
            if ("true".equals(option.getDeprecated())) {
                prop.getField().addAnnotation(Deprecated.class);
                prop.getAccessor().addAnnotation(Deprecated.class);
                prop.getMutator().addAnnotation(Deprecated.class);
                // DeprecatedConfigurationProperty must be on getter when deprecated
                prop.getAccessor().addAnnotation(DeprecatedConfigurationProperty.class);
            }
            if (!Strings.isBlank(option.getDescription())) {
                String desc = option.getDescription();
                if (complex) {
                    if (!desc.endsWith(".")) {
                        desc = desc + ".";
                    }
                    desc = desc + " The option is a " + option.getJavaType() + " type.";
                }
                prop.getField().getJavaDoc().setFullText(desc);
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

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        writeSourceIfChanged(javaClass, fileName, true);
    }

    private void createLanguageConfigurationSource(String packageName, LanguageModel model, String overrideLanguageName) throws MojoFailureException {
        final JavaClass javaClass = new JavaClass(getProjectClassLoader());

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Language", "LanguageConfiguration");
        javaClass.setPackage(packageName).setName(name);
        javaClass.extendSuperType("LanguageConfigurationPropertiesCommon");
        javaClass.addImport("org.apache.camel.spring.boot.LanguageConfigurationPropertiesCommon");

        // add bogus field for enabled so spring boot tooling can get the javadoc as description in its metadata
        Property bogus = javaClass.addProperty("java.lang.Boolean", "enabled");
        bogus.getField().getJavaDoc().setText("Whether to enable auto configuration of the " + model.getName() + " language. This is enabled by default.");
        bogus.removeAccessor();
        bogus.removeMutator();

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

            // spring-boot auto configuration does not support complex types (unless they are enum, nested)
            // and if so then we should use a String type so spring-boot and its tooling support that
            // as Camel will be able to convert the string value into a lookup of the bean in the registry anyway
            // and therefore there is no problem, eg camel.component.jdbc.data-source = myDataSource
            // where the type would have been javax.sql.DataSource
            boolean complex = isComplexType(option) && Strings.isBlank(option.getEnumValues());
            if (complex) {
                // force to use a string type
                type = "java.lang.String";
            }

            Property prop = javaClass.addProperty(type, option.getName());
            if ("true".equals(option.getDeprecated())) {
                prop.getField().addAnnotation(Deprecated.class);
                prop.getAccessor().addAnnotation(Deprecated.class);
                prop.getMutator().addAnnotation(Deprecated.class);
                // DeprecatedConfigurationProperty must be on getter when deprecated
                prop.getAccessor().addAnnotation(DeprecatedConfigurationProperty.class);
            }
            if (!Strings.isBlank(option.getDescription())) {
                String desc = option.getDescription();
                if (complex) {
                    if (!desc.endsWith(".")) {
                        desc = desc + ".";
                    }
                    desc = desc + " The option is a " + option.getJavaType() + " type.";
                }
                prop.getField().getJavaDoc().setFullText(desc);
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

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        writeSourceIfChanged(javaClass, fileName, true);
    }

    static class DynamicClassLoader extends URLClassLoader {
        public DynamicClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent);
        }

        public Class defineClass(String name, byte[] data) {
            return super.defineClass(name, data, 0, data.length);
        }
    }

    private void createComponentAutoConfigurationSource(
        String packageName, ComponentModel model, List<String> componentAliases, String overrideComponentName) throws MojoFailureException {

        final String name = model.getJavaType().substring(model.getJavaType().lastIndexOf(".") + 1).replace("Component", "ComponentAutoConfiguration");
        final String configurationName = name.replace("ComponentAutoConfiguration", "ComponentConfiguration");
        final String componentName = (overrideComponentName != null ? overrideComponentName : model.getScheme()).toLowerCase(Locale.US);

        Class configClass = generateDummyClass(packageName + "." + configurationName);

        final JavaClass javaClass = new JavaClass(getProjectClassLoader());

        javaClass.setPackage(packageName);
        javaClass.setName(name);
        javaClass.getJavaDoc().setFullText("Generated by camel-package-maven-plugin - do not edit this file!");
        javaClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
        javaClass.addAnnotation(Configuration.class);
        javaClass.addAnnotation(Conditional.class).setLiteralValue(
            "{ConditionalOnCamelContextAndAutoConfigurationBeans.class,\n        " + name + ".GroupConditions.class}");
        javaClass.addAnnotation(AutoConfigureAfter.class).setLiteralValue("CamelAutoConfiguration.class");
        javaClass.addAnnotation(EnableConfigurationProperties.class).setLiteralValue(
            "{ComponentConfigurationProperties.class,\n        " + configurationName + ".class}"
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
        javaClass.addImport("org.apache.camel.support.IntrospectionSupport");
        javaClass.addImport("org.apache.camel.util.ObjectHelper");
        javaClass.addImport("org.apache.camel.spi.HasId");
        javaClass.addImport(model.getJavaType());

        javaClass.addField()
            .setPrivate()
            .setStatic(true)
            .setFinal(true)
            .setName("LOGGER")
            .setType(loadClass("org.slf4j.Logger"))
            .setLiteralInitializer("LoggerFactory\n            .getLogger(" + name + ".class)");
        javaClass.addField()
            .setPrivate()
            .setName("applicationContext")
            .setType(ApplicationContext.class)
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("camelContext")
            .setType(loadClass("org.apache.camel.CamelContext"))
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("configuration")
            .setType(configClass)
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("customizers")
            .setType(loadType("java.util.List<org.apache.camel.spi.ComponentCustomizer<" + model.getJavaType() + ">>"))
            .addAnnotation(Autowired.class)
            .setLiteralValue("required", "false");

        javaClass.addNestedType()
                .setName("GroupConditions")
                .setStatic(true)
                .setPackagePrivate()
                .extendSuperType("GroupCondition")
                .addMethod()
                .setName("GroupConditions")
                .setConstructor(true)
                .setPublic()
                .setBody("super(\"camel.component\", \"camel.component." + componentName + "\");");

        // add method for auto configure
        String body = createComponentBody(model.getShortJavaType(), componentName);
        String methodName = "configure" + model.getShortJavaType();

        Method method = javaClass.addMethod()
            .setName(methodName)
            .setPublic()
            .setBody(body)
            .setReturnType(loadType(model.getJavaType()))
            .addThrows(Exception.class);

        // Determine all the aliases
        String[] springBeanAliases = componentAliases.stream().map(alias -> alias + "-component").toArray(size -> new String[size]);

        method.addAnnotation(Lazy.class);
        method.addAnnotation(Bean.class).setStringArrayValue("name", springBeanAliases);
        method.addAnnotation(ConditionalOnMissingBean.class).setLiteralValue(model.getShortJavaType() + ".class");

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        writeSourceIfChanged(javaClass, fileName, false);
    }

    private Class generateDummyClass(String clazzName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC,
                clazzName.replace('.', '/'),
                null,
                "java/lang/Object",
                null);
        cw.visitEnd();
        return getProjectClassLoader().defineClass(clazzName, cw.toByteArray());
    }

    private void createDataFormatAutoConfigurationSource(
        String packageName, DataFormatModel model, List<String> dataFormatAliases, String overrideDataFormatName) throws MojoFailureException {

        final String name = model.getJavaType().substring(model.getJavaType().lastIndexOf(".") + 1).replace("DataFormat", "DataFormatAutoConfiguration");
        final String configurationName = name.replace("DataFormatAutoConfiguration", "DataFormatConfiguration");
        final String dataformatName = (overrideDataFormatName != null ? overrideDataFormatName : model.getName()).toLowerCase(Locale.US);

        Class configClass = generateDummyClass(packageName + "." + configurationName);

        final JavaClass javaClass = new JavaClass(getProjectClassLoader());

        javaClass.setPackage(packageName);
        javaClass.setName(name);
        javaClass.getJavaDoc().setFullText("Generated by camel-package-maven-plugin - do not edit this file!");
        javaClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
        javaClass.addAnnotation(Configuration.class);
        javaClass.addAnnotation(Conditional.class).setLiteralValue(
            "{ConditionalOnCamelContextAndAutoConfigurationBeans.class,\n        " + name + ".GroupConditions.class}");
        javaClass.addAnnotation(AutoConfigureAfter.class).setStringValue("name", "org.apache.camel.spring.boot.CamelAutoConfiguration");
        javaClass.addAnnotation(EnableConfigurationProperties.class).setLiteralValue(
            "{DataFormatConfigurationProperties.class,\n        " + configurationName + ".class}"
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
        javaClass.addImport("org.apache.camel.support.IntrospectionSupport");
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
            .setType(loadType("org.slf4j.Logger"))
            .setLiteralInitializer("LoggerFactory\n            .getLogger(" + name + ".class)");
        javaClass.addField()
            .setPrivate()
            .setName("applicationContext")
            .setType(ApplicationContext.class)
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("camelContext")
            .setType(loadType("org.apache.camel.CamelContext"))
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("configuration")
            .setType(configClass)
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("customizers")
            .setType(loadType("java.util.List<org.apache.camel.spi.DataFormatCustomizer<" + model.getJavaType() + ">>"))
            .addAnnotation(Autowired.class)
            .setLiteralValue("required", "false");

        JavaClass groupConditions = javaClass.addNestedType()
                .setName("GroupConditions")
                .setStatic(true)
                .setPackagePrivate()
                .extendSuperType("GroupCondition");
        groupConditions.addMethod()
                .setName("GroupConditions")
                .setConstructor(true)
                .setPublic()
                .setBody("super(\"camel.dataformat\", \"camel.dataformat." + dataformatName + "\");");

        String body = createDataFormatBody(model.getShortJavaType(), dataformatName);
        String methodName = "configure" + model.getShortJavaType() + "Factory";

        Method method = javaClass.addMethod()
            .setName(methodName)
            .setPublic()
            .setBody(body)
            .setReturnType(loadType("org.apache.camel.spi.DataFormatFactory"))
            .addThrows(Exception.class);

        // Determine all the aliases
        // adding the '-dataformat' suffix to prevent collision with component names
        String[] springBeanAliases = dataFormatAliases.stream().map(alias -> alias + "-dataformat-factory").toArray(String[]::new);

        method.addAnnotation(Bean.class).setStringArrayValue("name", springBeanAliases);
        method.addAnnotation(ConditionalOnMissingBean.class).setLiteralValue("value", model.getShortJavaType() + ".class");

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        writeSourceIfChanged(javaClass, fileName, false);
    }

    private void createLanguageAutoConfigurationSource(
        String packageName, LanguageModel model, List<String> languageAliases, String overrideLanguageName) throws MojoFailureException {

        final String name = model.getJavaType().substring(model.getJavaType().lastIndexOf(".") + 1).replace("Language", "LanguageAutoConfiguration");
        final String configurationName = name.replace("LanguageAutoConfiguration", "LanguageConfiguration");
        final String languageName = (overrideLanguageName != null ? overrideLanguageName : model.getName()).toLowerCase(Locale.US);

        Class configClass = generateDummyClass(packageName + "." + configurationName);

        final JavaClass javaClass = new JavaClass(getProjectClassLoader());

        javaClass.setPackage(packageName);
        javaClass.setName(name);
        javaClass.getJavaDoc().setFullText("Generated by camel-package-maven-plugin - do not edit this file!");
        javaClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
        javaClass.addAnnotation(Configuration.class);
        javaClass.addAnnotation(Conditional.class).setLiteralValue(
            "{ConditionalOnCamelContextAndAutoConfigurationBeans.class,\n        " + name + ".GroupConditions.class}");
        javaClass.addAnnotation(AutoConfigureAfter.class).setLiteralValue("CamelAutoConfiguration.class");
        javaClass.addAnnotation(EnableConfigurationProperties.class).setLiteralValue(
            "{LanguageConfigurationProperties.class,\n        " + configurationName + ".class}"
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
        javaClass.addImport("org.apache.camel.support.IntrospectionSupport");
        javaClass.addImport("org.apache.camel.util.ObjectHelper");
        javaClass.addImport("org.apache.camel.spi.HasId");
        javaClass.addImport("org.apache.camel.spi.LanguageCustomizer");
        javaClass.addImport(model.getJavaType());

        javaClass.addField()
            .setPrivate()
            .setStatic(true)
            .setFinal(true)
            .setName("LOGGER")
            .setType(loadType("org.slf4j.Logger"))
            .setLiteralInitializer("LoggerFactory\n            .getLogger(" + name + ".class)");
        javaClass.addField()
            .setPrivate()
            .setName("applicationContext")
            .setType(ApplicationContext.class)
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("camelContext")
            .setType(loadType("org.apache.camel.CamelContext"))
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("configuration")
            .setType(configClass)
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("customizers")
            .setType(loadType("java.util.List<org.apache.camel.spi.LanguageCustomizer<" + model.getJavaType() + ">>"))
            .addAnnotation(Autowired.class)
            .setLiteralValue("required", "false");

        javaClass.addNestedType()
                .setName("GroupConditions")
                .setStatic(true)
                .setPackagePrivate()
                .extendSuperType("GroupCondition")
                .addMethod()
                .setName("GroupConditions")
                .setConstructor(true)
                .setPublic()
                .setBody("super(\"camel.component\", \"camel.component." + languageName + "\");");

        String body = createLanguageBody(model.getShortJavaType(), languageName);
        String methodName = "configure" + model.getShortJavaType();

        Method method = javaClass.addMethod()
            .setName(methodName)
            .setPublic()
            .setBody(body)
            .setReturnType(loadType(model.getJavaType()))
            .addThrows(Exception.class);

        // Determine all the aliases
        // adding the '-language' suffix to prevent collision with component names
        String[] springBeanAliases = languageAliases.stream().map(alias -> alias + "-language").toArray(String[]::new);

        method.addAnnotation(Bean.class).setStringArrayValue("name", springBeanAliases);
        method.addAnnotation(Scope.class).setLiteralValue("ConfigurableBeanFactory.SCOPE_PROTOTYPE");
        method.addAnnotation(ConditionalOnMissingBean.class).setLiteralValue("value", model.getShortJavaType() + ".class");

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        writeSourceIfChanged(javaClass, fileName, false);
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
        sb.append("Map<String, Object> parameters = new HashMap<>();\n");
        sb.append("IntrospectionSupport.getProperties(configuration, parameters, null,\n" +
                  "        false);\n");
        sb.append("for (Map.Entry<String, Object> entry : parameters.entrySet()) {\n");
        sb.append("    Object value = entry.getValue();\n");
        sb.append("    Class<?> paramClass = value.getClass();\n");
        sb.append("    if (paramClass.getName().endsWith(\"NestedConfiguration\")) {\n");
        sb.append("        Class nestedClass = null;\n");
        sb.append("        try {\n");
        sb.append("            nestedClass = (Class) paramClass.getDeclaredField(\n" +
                  "                    \"CAMEL_NESTED_CLASS\").get(null);\n");
        sb.append("            HashMap<String, Object> nestedParameters = new HashMap<>();\n");
        sb.append("            IntrospectionSupport.getProperties(value, nestedParameters,\n" +
                  "                    null, false);\n");
        sb.append("            Object nestedProperty = nestedClass.newInstance();\n");
        sb.append("            CamelPropertiesHelper.setCamelProperties(camelContext,\n" +
                  "                    nestedProperty, nestedParameters, false);\n");
        sb.append("            entry.setValue(nestedProperty);\n");
        sb.append("        } catch (NoSuchFieldException e) {\n");
//        sb.append("            // ignore, class must not be a nested configuration class after all\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("CamelPropertiesHelper.setCamelProperties(camelContext, component,\n");
        sb.append("        parameters, false);\n");
        sb.append("if (ObjectHelper.isNotEmpty(customizers)) {\n");
        sb.append("    for (ComponentCustomizer<").append(shortJavaType).append("> customizer : customizers) {\n");
        sb.append("        boolean useCustomizer = (customizer instanceof HasId)\n");
        sb.append("                ? HierarchicalPropertiesEvaluator.evaluate(\n");
        sb.append("                        applicationContext.getEnvironment(),\n");
        sb.append("                        \"camel.component.customizer\",\n");
        sb.append("                        \"camel.component.").append(name).append(".customizer\",\n");
        sb.append("                        ((HasId) customizer).getId())\n");
        sb.append("                : HierarchicalPropertiesEvaluator.evaluate(\n");
        sb.append("                        applicationContext.getEnvironment(),\n");
        sb.append("                        \"camel.component.customizer\",\n");
        sb.append("                        \"camel.component.").append(name).append(".customizer\");\n");
        sb.append("        if (useCustomizer) {\n");
        sb.append("            LOGGER.debug(\"Configure component {}, with customizer {}\",\n");
        sb.append("                    component, customizer);\n");
        sb.append("            customizer.customize(component);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("return component;");

        return sb.toString();
    }

    private static String createDataFormatBody(String shortJavaType, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("return new DataFormatFactory() {\n");
        sb.append("    @Override\n");
        sb.append("    public DataFormat newInstance() {\n");
        sb.append("        ").append(shortJavaType).append(" dataformat = new ").append(shortJavaType).append("();").append("\n");
        sb.append("        if (CamelContextAware.class\n" +
                  "                .isAssignableFrom(").append(shortJavaType).append(".class)) {\n");
        sb.append("            CamelContextAware contextAware = CamelContextAware.class\n" +
                  "                    .cast(dataformat);\n");
        sb.append("            if (contextAware != null) {\n");
        sb.append("                contextAware.setCamelContext(camelContext);\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        try {\n");
        sb.append("            Map<String, Object> parameters = new HashMap<>();\n");
        sb.append("            IntrospectionSupport.getProperties(configuration,\n" +
                  "                    parameters, null, false);\n");
        sb.append("            CamelPropertiesHelper.setCamelProperties(camelContext,\n" +
                  "                    dataformat, parameters, false);\n");
        sb.append("        } catch (Exception e) {\n");
        sb.append("            throw new RuntimeCamelException(e);\n");
        sb.append("        }\n");
        sb.append("        if (ObjectHelper.isNotEmpty(customizers)) {\n");
        sb.append("            for (DataFormatCustomizer<").append(shortJavaType).append("> customizer : customizers) {\n");
        sb.append("                boolean useCustomizer = (customizer instanceof HasId)\n");
        sb.append("                        ? HierarchicalPropertiesEvaluator.evaluate(\n");
        sb.append("                                applicationContext.getEnvironment(),\n");
        sb.append("                                \"camel.dataformat.customizer\",\n");
        sb.append("                                \"camel.dataformat.").append(name).append(".customizer\",\n");
        sb.append("                                ((HasId) customizer).getId())\n");
        sb.append("                        : HierarchicalPropertiesEvaluator.evaluate(\n" +
                  "                                applicationContext.getEnvironment(),\n");
        sb.append("                                \"camel.dataformat.customizer\",\n");
        sb.append("                                \"camel.dataformat.").append(name).append(".customizer\");\n");
        sb.append("                if (useCustomizer) {\n");
        sb.append("                    LOGGER.debug(\n" +
                  "                            \"Configure dataformat {}, with customizer {}\",\n" +
                  "                            dataformat, customizer);\n");
        sb.append("                    customizer.customize(dataformat);\n");
        sb.append("                }\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("        return dataformat;\n");
        sb.append("    }\n");
        sb.append("};\n");

        return sb.toString();
    }

    private static String createLanguageBody(String shortJavaType, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append(shortJavaType).append(" language = new ").append(shortJavaType).append("();").append("\n");
        sb.append("if (CamelContextAware.class.isAssignableFrom(").append(shortJavaType).append(".class)) {\n");
        sb.append("    CamelContextAware contextAware = CamelContextAware.class\n" +
                  "            .cast(language);\n");
        sb.append("    if (contextAware != null) {\n");
        sb.append("        contextAware.setCamelContext(camelContext);\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("Map<String, Object> parameters = new HashMap<>();\n");
        sb.append("IntrospectionSupport.getProperties(configuration, parameters, null,\n" +
                  "        false);\n");
        sb.append("CamelPropertiesHelper.setCamelProperties(camelContext, language,\n" +
                  "        parameters, false);\n");
        sb.append("if (ObjectHelper.isNotEmpty(customizers)) {\n");
        sb.append("    for (LanguageCustomizer<").append(shortJavaType).append("> customizer : customizers) {\n");
        sb.append("        boolean useCustomizer = (customizer instanceof HasId)\n");
        sb.append("                ? HierarchicalPropertiesEvaluator.evaluate(\n");
        sb.append("                        applicationContext.getEnvironment(),\n");
        sb.append("                        \"camel.language.customizer\",\n");
        sb.append("                        \"camel.language.").append(name).append(".customizer\",\n");
        sb.append("                        ((HasId) customizer).getId())\n");
        sb.append("                : HierarchicalPropertiesEvaluator.evaluate(\n");
        sb.append("                        applicationContext.getEnvironment(),\n");
        sb.append("                        \"camel.language.customizer\",\n");
        sb.append("                        \"camel.language.").append(name).append(".customizer\");\n");
        sb.append("        if (useCustomizer) {\n");
        sb.append("            LOGGER.debug(\"Configure language {}, with customizer {}\",\n" +
                  "                    language, customizer);\n");
        sb.append("            customizer.customize(language);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("return language;");

        return sb.toString();
    }

    private static void sortImports(JavaClass importer) {
        // sort imports
        // do nothing, as imports are sorted automatically when displayed
    }

    private static String loadModelJson(Map<File, Supplier<String>> jsonFiles, String modelName) {
        return loadJsonOfType(jsonFiles, modelName, "model");
    }

    private static String loadComponentJson(Map<File, Supplier<String>> jsonFiles, String componentName) {
        return loadJsonOfType(jsonFiles, componentName, "component");
    }

    private static String loadDataFormatJson(Map<File, Supplier<String>> jsonFiles, String dataFormatName) {
        return loadJsonOfType(jsonFiles, dataFormatName, "dataformat");
    }

    private static String loadLanguageJson(Map<File, Supplier<String>> jsonFiles, String languageName) {
        return loadJsonOfType(jsonFiles, languageName, "language");
    }

    private static String loadJsonOfType(Map<File, Supplier<String>> jsonFiles, String modelName, String type) {
        for (Map.Entry<File, Supplier<String>> entry : jsonFiles.entrySet()) {
            if (entry.getKey().getName().equals(modelName + ".json")) {
                String json = entry.getValue().get();
                if (json.contains("\"kind\": \"" + type + "\"")) {
                    return json;
                }
            }
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

    private void findComponentNames(File dir, Set<String> componentNames) {
        File f = new File(dir, "classes/META-INF/services/org/apache/camel/component");

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

    private List<String> findDataFormatNames() {
        List<String> dataFormatNames = new ArrayList<>();
        File f = new File(project.getBasedir(), "target/classes");
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
        return dataFormatNames;
    }

    private List<String> findLanguageNames() {
        List<String> languageNames = new ArrayList<>();
        File f = new File(project.getBasedir(), "target/classes");
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
        return languageNames;
    }

    private void writeSourceIfChanged(JavaClass source, String fileName, boolean innerClassesLast) throws MojoFailureException {
        writeSourceIfChanged(source.printClass(innerClassesLast), fileName);
    }

    private void writeSourceIfChanged(String source, String fileName) throws MojoFailureException {

        File target = new File(SpringBootHelper.starterSrcDir(baseDir, project.getArtifactId()), fileName);

        deleteFileOnMainArtifact(target);

        try {
            String header;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt")) {
                header = loadText(is);
            }
            String code = header + source;
            getLog().debug("Source code generated:\n" + code);

            updateResource(null, target.toPath(), code);
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

}
