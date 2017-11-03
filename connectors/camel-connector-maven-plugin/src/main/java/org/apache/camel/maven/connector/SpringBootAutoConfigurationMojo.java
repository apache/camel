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
package org.apache.camel.maven.connector;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Generated;
import javax.annotation.PostConstruct;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.catalog.JSonSchemaHelper;
import org.apache.camel.maven.connector.model.ComponentModel;
import org.apache.camel.maven.connector.model.ComponentOptionModel;
import org.apache.camel.maven.connector.model.ConnectorOptionModel;
import org.apache.camel.maven.connector.model.EndpointOptionModel;
import org.apache.camel.maven.connector.model.OptionModel;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.Import;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.PropertySource;
import org.jboss.forge.roaster.model.util.Formatter;
import org.jboss.forge.roaster.model.util.Strings;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import static org.apache.camel.maven.connector.util.FileHelper.loadText;
import static org.apache.camel.maven.connector.util.StringHelper.getSafeValue;
import static org.apache.camel.maven.connector.util.StringHelper.getShortJavaType;

/**
 * Generate Spring Boot auto configuration files for Camel connectors.
 */
@Mojo(name = "prepare-spring-boot-auto-configuration",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresProject = true, threadSafe = true)
public class SpringBootAutoConfigurationMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File classesDirectory;

    @Parameter(defaultValue = "true")
    private boolean includeLicenseHeader;

    @Parameter(defaultValue = "camel.connector")
    private String configurationPrefix;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            executeConnector();
        } catch (Exception e) {
            throw new MojoFailureException("Error generating Spring-Boot auto configuration for connector", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void executeConnector() throws Exception {

        String javaType = null;
        String connectorScheme = null;
        List<String> componentOptions = Collections.emptyList();
        List<String> endpointOptions = Collections.emptyList();

        File file = new File(classesDirectory, "camel-connector.json");
        if (file.exists()) {
            ObjectMapper mapper = new ObjectMapper();
            Map dto = mapper.readValue(file, Map.class);

            javaType = (String) dto.get("javaType");
            connectorScheme = (String) dto.get("scheme");
            componentOptions = (List) dto.get("componentOptions");
            endpointOptions = (List) dto.get("endpointOptions");
        }

        // find the component dependency and get its .json file
        file = new File(classesDirectory, "camel-connector-schema.json");
        if (file.exists() && javaType != null && connectorScheme != null) {
            String json = loadText(new FileInputStream(file));
            ComponentModel model = generateComponentModel(json);

            // resolvePropertyPlaceholders is an option which only make sense to use if the component has other options
            boolean hasComponentOptions = model.getComponentOptions().stream().anyMatch(o -> !o.getName().equals("resolvePropertyPlaceholders"));
            boolean hasConnectorOptions = !model.getConnectorOptions().isEmpty();

            // use springboot as sub package name so the code is not in normal
            // package so the Spring Boot JARs can be optional at runtime
            int pos = javaType.lastIndexOf(".");
            String pkg = javaType.substring(0, pos) + ".springboot";

            // we only create spring boot auto configuration if there is options to configure
            if (hasComponentOptions || hasConnectorOptions) {
                getLog().info("Generating Spring Boot AutoConfiguration for Connector: " + model.getScheme());

                createConnectorConfigurationSource(pkg, model, javaType, connectorScheme, componentOptions, endpointOptions);
                createConnectorAutoConfigurationSource(pkg, hasComponentOptions || hasConnectorOptions, javaType, connectorScheme);
                createConnectorSpringFactorySource(pkg, javaType);
            }
        }
    }

    private void createConnectorSpringFactorySource(String packageName, String javaType) throws MojoFailureException {
        int pos = javaType.lastIndexOf(".");
        String name = javaType.substring(pos + 1);
        name = name.replace("Component", "ConnectorAutoConfiguration");

        writeComponentSpringFactorySource(packageName, name);
    }

    private void writeComponentSpringFactorySource(String packageName, String name) throws MojoFailureException {
        StringBuilder sb = new StringBuilder();
        sb.append("org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\\n");

        String lineToAdd = packageName + "." + name + "\n";
        sb.append(lineToAdd);

        // project root folder
        File root = classesDirectory.getParentFile().getParentFile();
        String fileName = "src/main/resources/META-INF/spring.factories";
        File target = new File(root, fileName);

        // create new file
        try {
            String header = "";
            if (includeLicenseHeader) {
                InputStream is = getClass().getClassLoader().getResourceAsStream("license-header.txt");
                header = loadText(is);
            }
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



    private void createConnectorConfigurationSource(String packageName, ComponentModel model, String javaType,
                                                     String connectorScheme, List<String> componentOptions, List<String> endpointOptions) throws MojoFailureException {

        final int pos = javaType.lastIndexOf(".");
        final String commonName = javaType.substring(pos + 1).replace("Component", "ConnectorConfigurationCommon");
        final String configName = javaType.substring(pos + 1).replace("Component", "ConnectorConfiguration");

        // Common base class
        JavaClassSource commonClass = Roaster.create(JavaClassSource.class);
        commonClass.setPackage(packageName);
        commonClass.setName(commonName);

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        if (!Strings.isBlank(model.getDescription())) {
            doc = model.getDescription() + "\n\n" + doc;
        }
        // replace Component with Connector
        doc = doc.replaceAll("Component", "Connector");
        doc = doc.replaceAll("component", "connector");
        commonClass.getJavaDoc().setFullText(doc);
        commonClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());

        // compute the configuration prefix to use with spring boot configuration
        String prefix = "";
        if (!"false".equalsIgnoreCase(configurationPrefix)) {
            // make sure prefix is in lower case
            prefix = configurationPrefix.toLowerCase(Locale.US);
            if (!prefix.endsWith(".")) {
                prefix += ".";
            }
        }
        prefix += connectorScheme.toLowerCase(Locale.US);

        for (OptionModel option : model.getComponentOptions()) {
            boolean isComponentOption = componentOptions != null && componentOptions.stream().anyMatch(o -> o.equals(option.getName()));
            boolean isEndpointOption = endpointOptions != null && endpointOptions.stream().anyMatch(o -> o.equals(option.getName()));

            // only include the options that has been explicit configured in the
            // componentOptions section of camel-connector.json file and exclude
            // those configured on endpointOptions in the same file
            if (isComponentOption && !isEndpointOption) {
                addProperty(commonClass, model, option);
            }
        }

        for (OptionModel option : model.getEndpointOptions()) {
            if (endpointOptions != null && endpointOptions.stream().anyMatch(o -> o.equals(option.getName()))) {
                addProperty(commonClass, model, option);
            }
        }

        for (OptionModel option : model.getConnectorOptions()) {
            addProperty(commonClass, model, option);
        }

        sortImports(commonClass);
        writeSourceIfChanged(commonClass, packageName.replaceAll("\\.", "\\/") + "/" + commonName + ".java");

        // Config class
        JavaClassSource configClass = Roaster.create(JavaClassSource.class);
        configClass.setPackage(packageName);
        configClass.setName(configName);
        configClass.extendSuperType(commonClass);
        configClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
        configClass.addAnnotation(ConfigurationProperties.class).setStringValue("prefix", prefix);
        configClass.addImport(Map.class);
        configClass.addImport(HashMap.class);
        configClass.removeImport(commonClass);

        configClass.addField("Map<String, " + commonName + "> configurations = new HashMap<>()")
            .setPrivate()
            .getJavaDoc().setFullText("Define additional configuration definitions");

        MethodSource<JavaClassSource> method;

        method = configClass.addMethod();
        method.setName("getConfigurations");
        method.setReturnType("Map<String, " + commonName + ">");
        method.setPublic();
        method.setBody("return configurations;");


        sortImports(configClass);
        writeSourceIfChanged(configClass, packageName.replaceAll("\\.", "\\/") + "/" + configName + ".java");
    }

    private void createConnectorAutoConfigurationSource(String packageName, boolean hasOptions,
                                                        String javaType, String connectorScheme) throws MojoFailureException {

        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = javaType.lastIndexOf(".");
        String name = javaType.substring(pos + 1);
        name = name.replace("Component", "ConnectorAutoConfiguration");
        final String configNameCommon = javaType.substring(pos + 1).replace("Component", "ConnectorConfigurationCommon");
        final String configName = javaType.substring(pos + 1).replace("Component", "ConnectorConfiguration");

        // add method for auto configure
        final String shortJavaType = getShortJavaType(javaType);
        // must be named -component because camel-spring-boot uses that to lookup components
        final String beanName = connectorScheme + "-component";

        javaClass.setPackage(packageName).setName(name);

        String doc = "Generated by camel-connector-maven-plugin - do not edit this file!";
        javaClass.getJavaDoc().setFullText(doc);

        javaClass.addAnnotation(Generated.class).setStringValue("value", SpringBootAutoConfigurationMojo.class.getName());
        javaClass.addAnnotation(Configuration.class);
        javaClass.addAnnotation(ConditionalOnBean.class).setStringValue("type", "org.apache.camel.spring.boot.CamelAutoConfiguration");
        javaClass.addAnnotation(AutoConfigureAfter.class).setStringValue("name", "org.apache.camel.spring.boot.CamelAutoConfiguration");

        String configurationName = name.replace("ConnectorAutoConfiguration", "ConnectorConfiguration");
        if (hasOptions) {
            AnnotationSource<JavaClassSource> ann = javaClass.addAnnotation(EnableConfigurationProperties.class);
            ann.setLiteralValue("value", configurationName + ".class");

            javaClass.addImport(HashMap.class);
            javaClass.addImport(Map.class);
            javaClass.addImport("org.apache.camel.spring.boot.util.CamelPropertiesHelper");
        }

        javaClass.addImport(javaType);
        javaClass.addImport(ApplicationContext.class);
        javaClass.addImport(BeanCreationException.class);
        javaClass.addImport(List.class);
        javaClass.addImport("org.slf4j.Logger");
        javaClass.addImport("org.slf4j.LoggerFactory");
        javaClass.addImport("org.apache.camel.CamelContext");
        javaClass.addImport("org.apache.camel.component.connector.ConnectorCustomizer");
        javaClass.addImport("org.apache.camel.spi.HasId");
        javaClass.addImport("org.apache.camel.spring.boot.util.HierarchicalPropertiesEvaluator");
        javaClass.addImport("org.apache.camel.util.ObjectHelper");
        javaClass.addImport("org.apache.camel.util.IntrospectionSupport");

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
            .setName("camelContext")
            .setType("org.apache.camel.CamelContext")
            .setPrivate()
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setName("configuration")
            .setType(configName)
            .setPrivate()
            .addAnnotation(Autowired.class);
        javaClass.addField()
            .setPrivate()
            .setName("customizers")
            .setType("List<ConnectorCustomizer<" + shortJavaType + ">>")
            .addAnnotation(Autowired.class)
            .setLiteralValue("required", "false");

        MethodSource<JavaClassSource> configureMethod = javaClass.addMethod()
            .setName("configure" + shortJavaType)
            .setPublic()
            .setBody(createComponentBody(shortJavaType, hasOptions, connectorScheme.toLowerCase(Locale.US)))
            .setReturnType(shortJavaType)
            .addThrows(Exception.class);

        configureMethod.addAnnotation(Lazy.class);
        configureMethod.addAnnotation(Bean.class).setStringValue("name", beanName);
        configureMethod.addAnnotation(ConditionalOnClass.class).setLiteralValue("value", "CamelContext.class");
        configureMethod.addAnnotation(ConditionalOnMissingBean.class);

        MethodSource<JavaClassSource> postProcessMethod = javaClass.addMethod()
            .setName("postConstruct" + shortJavaType)
            .setPublic()
            .setBody(createPostConstructBody(shortJavaType, configNameCommon, connectorScheme.toLowerCase(Locale.US)));


        postProcessMethod.addAnnotation(PostConstruct.class);

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        writeSourceIfChanged(javaClass, fileName);
    }

    private void writeSourceIfChanged(JavaClassSource source, String fileName) throws MojoFailureException {
        // project root folder
        File root = classesDirectory.getParentFile().getParentFile();
        File target = new File(root, "src/main/java/" + fileName);

        try {
            String header = "";
            if (includeLicenseHeader) {
                InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt");
                header = loadText(is);
            }
            String code = sourceToString(source);
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

    private static String createComponentBody(String shortJavaType, boolean hasOptions, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append(shortJavaType).append(" connector = new ").append(shortJavaType).append("();").append("\n");
        sb.append("connector.setCamelContext(camelContext);\n");
        sb.append("\n");
        if (hasOptions) {
            sb.append("Map<String, Object> parameters = new HashMap<>();\n");
            sb.append("IntrospectionSupport.getProperties(configuration, parameters, null, false);\n");
            sb.append("CamelPropertiesHelper.setCamelProperties(camelContext, connector, parameters, false);\n");
            sb.append("connector.setOptions(parameters);\n");
        }
        sb.append("if (ObjectHelper.isNotEmpty(customizers)) {\n");
        sb.append("    for (ConnectorCustomizer<").append(shortJavaType).append("> customizer : customizers) {\n");
        sb.append("\n");
        sb.append("        boolean useCustomizer = (customizer instanceof HasId)");
        sb.append("            ? HierarchicalPropertiesEvaluator.evaluate(\n");
        sb.append("                applicationContext.getEnvironment(),\n");
        sb.append("               \"camel.connector.customizer\",\n");
        sb.append("               \"camel.connector.").append(name).append(".customizer\",\n");
        sb.append("               ((HasId)customizer).getId())\n");
        sb.append("            : HierarchicalPropertiesEvaluator.evaluate(\n");
        sb.append("                applicationContext.getEnvironment(),\n");
        sb.append("               \"camel.connector.customizer\",\n");
        sb.append("               \"camel.connector.").append(name).append(".customizer\");\n");
        sb.append("\n");
        sb.append("        if (useCustomizer) {\n");
        sb.append("            LOGGER.debug(\"Configure connector {}, with customizer {}\", connector, customizer);\n");
        sb.append("            customizer.customize(connector);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("return connector;");
        return sb.toString();
    }

    private static String createPostConstructBody(String shortJavaType, String commonConfigurationName, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append("Map<String, Object> parameters = new HashMap<>();\n");
        sb.append("\n");
        sb.append("for (Map.Entry<String, " + commonConfigurationName + "> entry : configuration.getConfigurations().entrySet()) {\n");
        sb.append("parameters.clear();\n");
        sb.append("\n");
        sb.append(shortJavaType).append(" connector = new ").append(shortJavaType).append("(").append("entry.getKey()").append(");\n");
        sb.append("connector.setCamelContext(camelContext);\n");
        sb.append("\n");
        sb.append("try {\n");
        sb.append("IntrospectionSupport.getProperties(entry.getValue(), parameters, null, false);\n");
        sb.append("CamelPropertiesHelper.setCamelProperties(camelContext, connector, parameters, false);\n");
        sb.append("connector.setOptions(parameters);\n");
        sb.append("if (ObjectHelper.isNotEmpty(customizers)) {\n");
        sb.append("    for (ConnectorCustomizer<").append(shortJavaType).append("> customizer : customizers) {\n");
        sb.append("\n");
        sb.append("        boolean useCustomizer = (customizer instanceof HasId)");
        sb.append("            ? HierarchicalPropertiesEvaluator.evaluate(\n");
        sb.append("                applicationContext.getEnvironment(),\n");
        sb.append("               \"camel.connector.customizer\",\n");
        sb.append("               \"camel.connector.").append(name).append(".\" + entry.getKey() + \".customizer\",\n");
        sb.append("               ((HasId)customizer).getId())\n");
        sb.append("            : HierarchicalPropertiesEvaluator.evaluate(\n");
        sb.append("                applicationContext.getEnvironment(),\n");
        sb.append("               \"camel.connector.customizer\",\n");
        sb.append("               \"camel.connector.").append(name).append(".\" + entry.getKey() + \".customizer\");\n");
        sb.append("\n");
        sb.append("        if (useCustomizer) {\n");
        sb.append("            LOGGER.debug(\"Configure connector {}, with customizer {}\", connector, customizer);\n");
        sb.append("            customizer.customize(connector);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("camelContext.addComponent(entry.getKey(), connector);\n");
        sb.append("} catch (Exception e) {\n");
        sb.append("throw new BeanCreationException(entry.getKey(), e.getMessage(), e);\n");
        sb.append("}\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static void sortImports(JavaClassSource javaClass) {
        // sort imports
        List<Import> imports = javaClass.getImports();

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
            javaClass.removeImport(name);
        }
        // and add them back in correct order
        for (String name : names) {
            javaClass.addImport(name);
        }
    }

    private static String sourceToString(JavaClassSource javaClass) {
        String code = Formatter.format(javaClass);
        // convert tabs to 4 spaces
        code = code.replaceAll("\\t", "    ");
        return code;
    }

    private static ComponentModel generateComponentModel(String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("component", json, false);

        ComponentModel component = new ComponentModel();
        component.setScheme(getSafeValue("scheme", rows));
        component.setSyntax(getSafeValue("syntax", rows));
        component.setAlternativeSyntax(getSafeValue("alternativeSyntax", rows));
        component.setTitle(getSafeValue("title", rows));
        component.setDescription(getSafeValue("description", rows));
        component.setFirstVersion(getSafeValue("firstVersion", rows));
        component.setLabel(getSafeValue("label", rows));
        component.setDeprecated(getSafeValue("deprecated", rows));
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
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setDescription(getSafeValue("description", row));
            option.setEnumValues(getSafeValue("enum", row));
            component.addEndpointOption(option);
        }

        rows = JSonSchemaHelper.parseJsonSchema("connectorProperties", json, true);
        for (Map<String, String> row : rows) {
            ConnectorOptionModel option = new ConnectorOptionModel();
            option.setName(getSafeValue("name", row));
            option.setDisplayName(getSafeValue("displayName", row));
            option.setKind(getSafeValue("kind", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDescription(getSafeValue("description", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setEnums(getSafeValue("enum", row));
            component.addConnectorOption(option);
        }

        return component;
    }


    private void addProperty(JavaClassSource clazz, ComponentModel model, OptionModel option) {
        String type = option.getJavaType();
        PropertySource<JavaClassSource> prop = clazz.addProperty(type, option.getName());

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
                clazz.addImport(model.getJavaType());
            }
        }
    }

}
