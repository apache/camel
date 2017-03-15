/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
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
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.maven.connector.model.ComponentModel;
import org.apache.camel.maven.connector.model.ComponentOptionModel;
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
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.apache.camel.maven.connector.FileHelper.loadText;
import static org.apache.camel.maven.connector.StringHelper.getSafeValue;

/**
 * Generate Spring Boot auto configuration files for Camel connectors.
 */
@Mojo(name = "prepare-spring-boot-auto-configuration",
    defaultPhase = LifecyclePhase.PACKAGE,
    requiresProject = true, threadSafe = true)
public class SpringBootAutoConfigurationMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.outputDirectory}", required = true)
    private File classesDirectory;

    @Parameter(defaultValue = "${basedir}", required = true)
    private File baseDir;

    @Parameter(defaultValue = "true")
    private boolean includeLicenseHeader;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            executeConnector();
        } catch (Exception e) {
            throw new MojoFailureException("Error generating Spring-Boot auto configuration for connector", e);
        }
    }

    private void executeConnector() throws Exception {

        String javaType = null;
        String connectorScheme = null;

        File file = new File(classesDirectory, "camel-connector.json");
        if (file.exists()) {
            ObjectMapper mapper = new ObjectMapper();
            Map dto = mapper.readValue(file, Map.class);

            javaType = (String) dto.get("javaType");
            connectorScheme = (String) dto.get("scheme");
        }

        // find the component dependency and get its .json file
        file = new File(classesDirectory, "camel-component-schema.json");
        if (file.exists() && javaType != null && connectorScheme != null) {
            String json = loadText(new FileInputStream(file));
            ComponentModel model = generateComponentModel(json);

            // resolvePropertyPlaceholders is an option which only make sense to use if the component has other options
            boolean hasOptions = model.getComponentOptions().stream().anyMatch(o -> !o.getName().equals("resolvePropertyPlaceholders"));

            // use springboot as sub package name so the code is not in normal
            // package so the Spring Boot JARs can be optional at runtime
            int pos = javaType.lastIndexOf(".");
            String pkg = javaType.substring(0, pos) + ".springboot";

            getLog().info("Generating Spring Boot AutoConfiguration for Connector: " + model.getScheme());

            if (hasOptions) {
                createConnectorConfigurationSource(pkg, model, javaType, connectorScheme);
            }
            createConnectorAutoConfigurationSource(pkg, model, hasOptions, javaType, connectorScheme);
            createConnectorSpringFactorySource(pkg, model);
        } else {
            getLog().warn("Cannot generate Spring Boot AutoConfiguration as camel-component-schema.json file missing");
        }
    }

    private void createConnectorSpringFactorySource(String packageName, ComponentModel model) throws MojoFailureException {
        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Component", "ConnectorAutoConfiguration");

        writeComponentSpringFactorySource(packageName, name);
    }

    private void writeComponentSpringFactorySource(String packageName, String name) throws MojoFailureException {
        StringBuilder sb = new StringBuilder();
        sb.append("org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\\n");

        String lineToAdd = packageName + "." + name + "\n";
        sb.append(lineToAdd);

        String fileName = "src/main/resources/META-INF/spring.factories";
        File target = new File(baseDir, fileName);

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

    private void createConnectorConfigurationSource(String packageName, ComponentModel model, String javaType, String connectorScheme) throws MojoFailureException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = javaType.lastIndexOf(".");
        String name = javaType.substring(pos + 1);
        name = name.replace("Component", "ConnectorConfiguration");
        javaClass.setPackage(packageName).setName(name);

        String doc = "Generated by camel-connector-maven-plugin - do not edit this file!";
        if (!Strings.isBlank(model.getDescription())) {
            doc = model.getDescription() + "\n\n" + doc;
        }
        // replace Component with Connector
        doc = doc.replaceAll("Component", "Connector");
        doc = doc.replaceAll("component", "connector");
        javaClass.getJavaDoc().setFullText(doc);

        String prefix = "camel.connector." + model.getScheme();
        // make sure prefix is in lower case
        prefix = connectorScheme.toLowerCase(Locale.US);
        javaClass.addAnnotation("org.springframework.boot.context.properties.ConfigurationProperties").setStringValue("prefix", prefix);

        for (ComponentOptionModel option : model.getComponentOptions()) {
            String type = option.getJavaType();
            PropertySource<JavaClassSource> prop = javaClass.addProperty(type, option.getName());

            // TODO: only include the global options so we can configure them

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


        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";

        writeSourceIfChanged(javaClass, fileName);
    }

    private void createConnectorAutoConfigurationSource(String packageName, ComponentModel model, boolean hasOptions,
                                                        String javaType, String connectorScheme) throws MojoFailureException {

        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = javaType.lastIndexOf(".");
        String name = javaType.substring(pos + 1);
        name = name.replace("Component", "ConnectorAutoConfiguration");

        javaClass.setPackage(packageName).setName(name);

        String doc = "Generated by camel-connector-maven-plugin - do not edit this file!";
        javaClass.getJavaDoc().setFullText(doc);

        javaClass.addAnnotation(Configuration.class);
        javaClass.addAnnotation(ConditionalOnBean.class).setStringValue("type", "org.apache.camel.spring.boot.CamelAutoConfiguration");
        javaClass.addAnnotation(AutoConfigureAfter.class).setStringValue("name", "org.apache.camel.spring.boot.CamelAutoConfiguration");

        String configurationName = name.replace("ConnectorAutoConfiguration", "ConnectorConfiguration");
        if (hasOptions) {
            AnnotationSource<JavaClassSource> ann = javaClass.addAnnotation(EnableConfigurationProperties.class);
            ann.setLiteralValue("value", configurationName + ".class");

            javaClass.addImport("java.util.HashMap");
            javaClass.addImport("java.util.Map");
            javaClass.addImport("org.apache.camel.util.IntrospectionSupport");
        }

        javaClass.addImport(model.getJavaType());
        javaClass.addImport("org.apache.camel.CamelContext");

        // add method for auto configure
        String body = createComponentBody(model.getShortJavaType(), hasOptions);
        String methodName = "configure" + model.getShortJavaType();

        MethodSource<JavaClassSource> method = javaClass.addMethod()
            .setName(methodName)
            .setPublic()
            .setBody(body)
            .setReturnType(model.getShortJavaType())
            .addThrows(Exception.class);

        method.addParameter("CamelContext", "camelContext");

        if (hasOptions) {
            method.addParameter(configurationName, "configuration");
        }

        method.addAnnotation(Bean.class).setStringValue("name", connectorScheme.toLowerCase(Locale.US) + "-connector");
        method.addAnnotation(ConditionalOnClass.class).setLiteralValue("value", "CamelContext.class");
        method.addAnnotation(ConditionalOnMissingBean.class).setLiteralValue("value", model.getShortJavaType() + ".class");

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        writeSourceIfChanged(javaClass, fileName);
    }

    private void writeSourceIfChanged(JavaClassSource source, String fileName) throws MojoFailureException {
        File target = new File(".", "src/main/java/" + fileName);

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

    private static String createComponentBody(String shortJavaType, boolean hasOptions) {
        StringBuilder sb = new StringBuilder();
        sb.append(shortJavaType).append(" connector = new ").append(shortJavaType).append("();").append("\n");
        sb.append("connector.setCamelContext(camelContext);\n");
        sb.append("\n");
        if (hasOptions) {
            sb.append("Map<String, Object> parameters = new HashMap<>();\n");
            sb.append("IntrospectionSupport.getProperties(configuration, parameters, null, false);\n");
            sb.append("IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), connector, parameters);\n");
        }
        sb.append("\n");
        sb.append("return connector;");
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

        ComponentModel component = new ComponentModel(true);
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

        return component;
    }

}
