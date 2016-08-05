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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.maven.packaging.model.ComponentOptionModel;
import org.apache.camel.maven.packaging.model.DataFormatModel;
import org.apache.camel.maven.packaging.model.DataFormatOptionModel;
import org.apache.camel.maven.packaging.model.EndpointOptionModel;
import org.apache.camel.maven.packaging.model.LanguageModel;
import org.apache.camel.maven.packaging.model.LanguageOptionModel;
import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.Import;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.PropertySource;
import org.jboss.forge.roaster.model.util.Strings;
import org.sonatype.plexus.build.incremental.BuildContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.apache.camel.maven.packaging.JSonSchemaHelper.getSafeValue;
import static org.apache.camel.maven.packaging.PackageHelper.loadText;

/**
 * Generate Spring Boot auto configuration files for Camel components and data formats.
 *
 * @goal prepare-spring-boot-auto-configuration
 */
public class SpringBootAutoConfigurationMojo extends AbstractMojo {

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
     * The source directory
     *
     * @parameter default-value="${basedir}/src/main/java"
     */
    protected File srcDir;

    /**
     * The resources directory
     *
     * @parameter default-value="${basedir}/src/main/resources"
     */
    protected File resourcesDir;

    /**
     * build context to check changed files and mark them for refresh (used for
     * m2e compatibility)
     *
     * @component
     * @readonly
     */
    private BuildContext buildContext;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        executeComponent();
        executeDataFormat();
        executeLanguage();
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
            Map<String, List<ComponentModel>> grModels = allModels.stream().collect(Collectors.groupingBy(m -> m.getJavaType()));
            for (String componentClass : grModels.keySet()) {
                List<ComponentModel> compModels = grModels.get(componentClass);
                ComponentModel model = compModels.get(0); // They should be equivalent
                List<String> aliases = compModels.stream().map(m -> m.getScheme()).sorted().collect(Collectors.toList());

                // only create source code if the component has options that can be used in auto configuration
                if (!model.getComponentOptions().isEmpty()) {

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
                    createComponentAutoConfigurationSource(pkg, model, aliases);
                    createComponentSpringFactorySource(pkg, model);
                }
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
                String json = loadDataFormaatJson(jsonFiles, dataFormatName);
                if (json != null) {
                    DataFormatModel model = generateDataFormatModel(dataFormatName, json);
                    allModels.add(model);
                }
            }

            // Group the models by implementing classes
            Map<String, List<DataFormatModel>> grModels = allModels.stream().collect(Collectors.groupingBy(m -> m.getJavaType()));
            for (String dataFormatClass : grModels.keySet()) {
                List<DataFormatModel> dfModels = grModels.get(dataFormatClass);
                DataFormatModel model = dfModels.get(0); // They should be equivalent
                List<String> aliases = dfModels.stream().map(m -> m.getName()).sorted().collect(Collectors.toList());

                // only create source code if the data format has options that can be used in auto configuration
                if (!model.getDataFormatOptions().isEmpty()) {

                    // use springboot as sub package name so the code is not in normal
                    // package so the Spring Boot JARs can be optional at runtime
                    int pos = model.getJavaType().lastIndexOf(".");
                    String pkg = model.getJavaType().substring(0, pos) + ".springboot";

                    String overrideDataformatName = null;
                    if (aliases.size() > 1) {
                        // determine component name when there are multiple ones
                        overrideDataformatName = model.getArtifactId().replace("camel-", "");
                    }

                    createDataFormatConfigurationSource(pkg, model, overrideDataformatName);
                    createDataFormatAutoConfigurationSource(pkg, model, aliases);
                    createDataFormatSpringFactorySource(pkg, model);
                }
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
            Map<String, List<LanguageModel>> grModels = allModels.stream().collect(Collectors.groupingBy(m -> m.getJavaType()));
            for (String languageClass : grModels.keySet()) {
                List<LanguageModel> dfModels = grModels.get(languageClass);
                LanguageModel model = dfModels.get(0); // They should be equivalent
                List<String> aliases = dfModels.stream().map(m -> m.getName()).sorted().collect(Collectors.toList());

                // only create source code if the language has options that can be used in auto configuration
                if (!model.getLanguageOptions().isEmpty()) {

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
                    createLanguageAutoConfigurationSource(pkg, model, aliases);
                    createLanguageSpringFactorySource(pkg, model);
                }
            }
        }
    }

    private void createComponentConfigurationSource(String packageName, ComponentModel model, String overrideComponentName) throws MojoFailureException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Component", "ComponentConfiguration");
        javaClass.setPackage(packageName).setName(name);

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        if (!Strings.isBlank(model.getDescription())) {
            doc = model.getDescription() + "\n\n" + doc;
        }
        javaClass.getJavaDoc().setFullText(doc);

        String prefix = "camel.component." + (overrideComponentName != null ? overrideComponentName : model.getScheme());
        // make sure prefix is in lower case
        prefix = prefix.toLowerCase(Locale.US);
        javaClass.addAnnotation("org.springframework.boot.context.properties.ConfigurationProperties").setStringValue("prefix", prefix);

        for (ComponentOptionModel option : model.getComponentOptions()) {

            if (skipComponentOption(model, option)) {
                // some component options should be skipped
                continue;
            }

            // remove <?> as generic type as Roaster (Eclipse JDT) cannot use that
            String type = option.getJavaType();
            type = type.replaceAll("\\<\\?\\>", "");
            // use wrapper types for primitive types so a null mean that the option has not been configured
            if ("boolean".equals(type)) {
                type = "java.lang.Boolean";
            } else if ("int".equals(type) || "integer".equals(type)) {
                type = "java.lang.Integer";
            } else if ("byte".equals(type)) {
                type = "java.lang.Byte";
            } else if ("short".equals(type)) {
                type = "java.lang.Short";
            } else if ("double".equals(type)) {
                type = "java.lang.Double";
            } else if ("float".equals(type)) {
                type = "java.lang.Float";
            }

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
        File target = new File(srcDir, fileName);

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt");
            String header = loadText(is);
            String code = sourceToString(javaClass);
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

    // CHECKSTYLE:OFF
    private static boolean skipComponentOption(ComponentModel model, ComponentOptionModel option) {
        if ("netty4-http".equals(model.getScheme()) || "netty-http".equals(model.getScheme())) {
            String name = option.getName();
            if (name.equals("textline") || name.equals("delimiter") ||  name.equals("autoAppendDelimiter") || name.equals("decoderMaxLineLength")
                || name.equals("encoding") || name.equals("allowDefaultCodec") ||  name.equals("udpConnectionlessSending") || name.equals("networkInterface")
                || name.equals("clientMode") || name.equals("reconnect") ||  name.equals("reconnectInterval") || name.equals("useByteBuf")
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

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        if (!Strings.isBlank(model.getDescription())) {
            doc = model.getDescription() + "\n\n" + doc;
        }
        javaClass.getJavaDoc().setFullText(doc);

        String prefix = "camel.dataformat." + (overrideDataFormatName != null ? overrideDataFormatName : model.getName());
        // make sure prefix is in lower case
        prefix = prefix.toLowerCase(Locale.US);
        javaClass.addAnnotation("org.springframework.boot.context.properties.ConfigurationProperties").setStringValue("prefix", prefix);

        for (DataFormatOptionModel option : model.getDataFormatOptions()) {
            // skip option with name id in data format as we do not need that
            if ("id".equals(option.getName())) {
                continue;
            }
            // remove <?> as generic type as Roaster (Eclipse JDT) cannot use that
            String type = option.getJavaType();
            type = type.replaceAll("\\<\\?\\>", "");
            // use wrapper types for primitive types so a null mean that the option has not been configured
            if ("boolean".equals(type)) {
                type = "java.lang.Boolean";
            } else if ("int".equals(type) || "integer".equals(type)) {
                type = "java.lang.Integer";
            } else if ("byte".equals(type)) {
                type = "java.lang.Byte";
            } else if ("short".equals(type)) {
                type = "java.lang.Short";
            } else if ("double".equals(type)) {
                type = "java.lang.Double";
            } else if ("float".equals(type)) {
                type = "java.lang.Float";
            }

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
                if ("java.lang.String".equals(option.getType())) {
                    prop.getField().setStringInitializer(option.getDefaultValue());
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
        File target = new File(srcDir, fileName);

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt");
            String header = loadText(is);
            String code = sourceToString(javaClass);
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

    private void createLanguageConfigurationSource(String packageName, LanguageModel model, String overrideLanguageName) throws MojoFailureException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Language", "LanguageConfiguration");
        javaClass.setPackage(packageName).setName(name);

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        if (!Strings.isBlank(model.getDescription())) {
            doc = model.getDescription() + "\n\n" + doc;
        }
        javaClass.getJavaDoc().setFullText(doc);

        String prefix = "camel.language." + (overrideLanguageName != null ? overrideLanguageName : model.getName());
        // make sure prefix is in lower case
        prefix = prefix.toLowerCase(Locale.US);
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

            // remove <?> as generic type as Roaster (Eclipse JDT) cannot use that
            String type = option.getJavaType();
            type = type.replaceAll("\\<\\?\\>", "");
            // use wrapper types for primitive types so a null mean that the option has not been configured
            if ("boolean".equals(type)) {
                type = "java.lang.Boolean";
            } else if ("int".equals(type) || "integer".equals(type)) {
                type = "java.lang.Integer";
            } else if ("byte".equals(type)) {
                type = "java.lang.Byte";
            } else if ("short".equals(type)) {
                type = "java.lang.Short";
            } else if ("double".equals(type)) {
                type = "java.lang.Double";
            } else if ("float".equals(type)) {
                type = "java.lang.Float";
            }

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
                if ("java.lang.String".equals(option.getType())) {
                    prop.getField().setStringInitializer(option.getDefaultValue());
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
        File target = new File(srcDir, fileName);

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt");
            String header = loadText(is);
            String code = sourceToString(javaClass);
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

    private void createComponentAutoConfigurationSource(String packageName, ComponentModel model, List<String> componentAliases) throws MojoFailureException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Component", "ComponentAutoConfiguration");
        javaClass.setPackage(packageName).setName(name);

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        javaClass.getJavaDoc().setFullText(doc);

        javaClass.addAnnotation(Configuration.class);

        String configurationName = name.replace("ComponentAutoConfiguration", "ComponentConfiguration");
        AnnotationSource<JavaClassSource> ann = javaClass.addAnnotation(EnableConfigurationProperties.class);
        ann.setLiteralValue("value", configurationName + ".class");

        // add method for auto configure

        javaClass.addImport("java.util.HashMap");
        javaClass.addImport("java.util.Map");
        javaClass.addImport(model.getJavaType());
        javaClass.addImport("org.apache.camel.CamelContext");
        javaClass.addImport("org.apache.camel.util.IntrospectionSupport");

        String body = createComponentBody(model.getShortJavaType());
        String methodName = "configure" + model.getShortJavaType();

        MethodSource<JavaClassSource> method = javaClass.addMethod()
                .setName(methodName)
                .setPublic()
                .setBody(body)
                .setReturnType(model.getShortJavaType())
                .addThrows(Exception.class);

        method.addParameter("CamelContext", "camelContext");
        method.addParameter(configurationName, "configuration");

        // Determine all the aliases
        String[] springBeanAliases = componentAliases.stream().map(alias -> alias + "-component").toArray(size -> new String[size]);

        method.addAnnotation(Bean.class).setStringArrayValue("name", springBeanAliases);
        method.addAnnotation(ConditionalOnClass.class).setLiteralValue("value", "CamelContext.class");
        method.addAnnotation(ConditionalOnMissingBean.class).setLiteralValue("value", model.getShortJavaType() + ".class");

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        File target = new File(srcDir, fileName);

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt");
            String header = loadText(is);
            String code = sourceToString(javaClass);
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

    private void createDataFormatAutoConfigurationSource(String packageName, DataFormatModel model, List<String> dataFormatAliases) throws MojoFailureException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("DataFormat", "DataFormatAutoConfiguration");
        javaClass.setPackage(packageName).setName(name);

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        javaClass.getJavaDoc().setFullText(doc);

        javaClass.addAnnotation(Configuration.class);

        String configurationName = name.replace("DataFormatAutoConfiguration", "DataFormatConfiguration");
        AnnotationSource<JavaClassSource> ann = javaClass.addAnnotation(EnableConfigurationProperties.class);
        ann.setLiteralValue("value", configurationName + ".class");

        // add method for auto configure

        javaClass.addImport("java.util.HashMap");
        javaClass.addImport("java.util.Map");
        javaClass.addImport(model.getJavaType());
        javaClass.addImport("org.apache.camel.CamelContext");
        javaClass.addImport("org.apache.camel.CamelContextAware");
        javaClass.addImport("org.apache.camel.util.IntrospectionSupport");

        String body = createDataFormatBody(model.getShortJavaType());
        String methodName = "configure" + model.getShortJavaType();

        MethodSource<JavaClassSource> method = javaClass.addMethod()
                .setName(methodName)
                .setPublic()
                .setBody(body)
                .setReturnType(model.getShortJavaType())
                .addThrows(Exception.class);

        method.addParameter("CamelContext", "camelContext");
        method.addParameter(configurationName, "configuration");


        // Determine all the aliases
        // adding the '-dataformat' suffix to prevent collision with component names
        String[] springBeanAliases = dataFormatAliases.stream().map(alias -> alias + "-dataformat").toArray(size -> new String[size]);

        method.addAnnotation(Bean.class).setStringArrayValue("name", springBeanAliases);
        method.addAnnotation(ConditionalOnClass.class).setLiteralValue("value", "CamelContext.class");
        method.addAnnotation(ConditionalOnMissingBean.class).setLiteralValue("value", model.getShortJavaType() + ".class");

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        File target = new File(srcDir, fileName);

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt");
            String header = loadText(is);
            String code = sourceToString(javaClass);
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

    private void createLanguageAutoConfigurationSource(String packageName, LanguageModel model, List<String> languageAliases) throws MojoFailureException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Language", "LanguageAutoConfiguration");
        javaClass.setPackage(packageName).setName(name);

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        javaClass.getJavaDoc().setFullText(doc);

        javaClass.addAnnotation(Configuration.class);

        String configurationName = name.replace("LanguageAutoConfiguration", "LanguageConfiguration");
        AnnotationSource<JavaClassSource> ann = javaClass.addAnnotation(EnableConfigurationProperties.class);
        ann.setLiteralValue("value", configurationName + ".class");

        // add method for auto configure

        javaClass.addImport("java.util.HashMap");
        javaClass.addImport("java.util.Map");
        javaClass.addImport(model.getJavaType());
        javaClass.addImport("org.apache.camel.CamelContext");
        javaClass.addImport("org.apache.camel.CamelContextAware");
        javaClass.addImport("org.apache.camel.util.IntrospectionSupport");

        String body = createLanguageBody(model.getShortJavaType());
        String methodName = "configure" + model.getShortJavaType();

        MethodSource<JavaClassSource> method = javaClass.addMethod()
                .setName(methodName)
                .setPublic()
                .setBody(body)
                .setReturnType(model.getShortJavaType())
                .addThrows(Exception.class);

        method.addParameter("CamelContext", "camelContext");
        method.addParameter(configurationName, "configuration");


        // Determine all the aliases
        // adding the '-language' suffix to prevent collision with component names
        String[] springBeanAliases = languageAliases.stream().map(alias -> alias + "-language").toArray(size -> new String[size]);

        method.addAnnotation(Bean.class).setStringArrayValue("name", springBeanAliases);
        method.addAnnotation(ConditionalOnClass.class).setLiteralValue("value", "CamelContext.class");
        method.addAnnotation(ConditionalOnMissingBean.class).setLiteralValue("value", model.getShortJavaType() + ".class");

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";
        File target = new File(srcDir, fileName);

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt");
            String header = loadText(is);
            String code = sourceToString(javaClass);
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

    private void createComponentSpringFactorySource(String packageName, ComponentModel model) throws MojoFailureException {
        StringBuilder sb = new StringBuilder();
        sb.append("org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\\n");

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Component", "ComponentAutoConfiguration");
        String lineToAdd = packageName + "." + name + "\n";
        sb.append(lineToAdd);

        String fileName = "META-INF/spring.factories";
        File target = new File(resourcesDir, fileName);

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

    private void createDataFormatSpringFactorySource(String packageName, DataFormatModel model) throws MojoFailureException {
        StringBuilder sb = new StringBuilder();
        sb.append("org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\\n");

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("DataFormat", "DataFormatAutoConfiguration");
        String lineToAdd = packageName + "." + name + "\n";
        sb.append(lineToAdd);

        String fileName = "META-INF/spring.factories";
        File target = new File(resourcesDir, fileName);

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

    private void createLanguageSpringFactorySource(String packageName, LanguageModel model) throws MojoFailureException {
        StringBuilder sb = new StringBuilder();
        sb.append("org.springframework.boot.autoconfigure.EnableAutoConfiguration=\\\n");

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Language", "LanguageAutoConfiguration");
        String lineToAdd = packageName + "." + name + "\n";
        sb.append(lineToAdd);

        String fileName = "META-INF/spring.factories";
        File target = new File(resourcesDir, fileName);

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

    private static String createComponentBody(String shortJavaType) {
        StringBuilder sb = new StringBuilder();
        sb.append(shortJavaType).append(" component = new ").append(shortJavaType).append("();").append("\n");
        sb.append("component.setCamelContext(camelContext);\n");
        sb.append("\n");
        sb.append("Map<String, Object> parameters = new HashMap<>();\n");
        sb.append("IntrospectionSupport.getProperties(configuration, parameters, null, false);\n");
        sb.append("\n");
        sb.append("IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), component, parameters);\n");
        sb.append("\n");
        sb.append("return component;");
        return sb.toString();
    }

    private static String createDataFormatBody(String shortJavaType) {
        StringBuilder sb = new StringBuilder();
        sb.append(shortJavaType).append(" dataformat = new ").append(shortJavaType).append("();").append("\n");
        sb.append("if (dataformat instanceof CamelContextAware) {\n");
        sb.append("    ((CamelContextAware) dataformat).setCamelContext(camelContext);\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("Map<String, Object> parameters = new HashMap<>();\n");
        sb.append("IntrospectionSupport.getProperties(configuration, parameters, null, false);\n");
        sb.append("\n");
        sb.append("IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), dataformat, parameters);\n");
        sb.append("\n");
        sb.append("return dataformat;");
        return sb.toString();
    }

    private static String createLanguageBody(String shortJavaType) {
        StringBuilder sb = new StringBuilder();
        sb.append(shortJavaType).append(" language = new ").append(shortJavaType).append("();").append("\n");
        sb.append("if (language instanceof CamelContextAware) {\n");
        sb.append("    ((CamelContextAware) language).setCamelContext(camelContext);\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("Map<String, Object> parameters = new HashMap<>();\n");
        sb.append("IntrospectionSupport.getProperties(configuration, parameters, null, false);\n");
        sb.append("\n");
        sb.append("IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), language, parameters);\n");
        sb.append("\n");
        sb.append("return language;");
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
        String code = javaClass.toString();
        // convert tabs to 4 spaces
        code = code.replaceAll("\\t", "    ");
        return code;
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

    private static String loadDataFormaatJson(Set<File> jsonFiles, String dataFormatName) {
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

        ComponentModel component = new ComponentModel();
        component.setScheme(getSafeValue("scheme", rows));
        component.setSyntax(getSafeValue("syntax", rows));
        component.setAlternativeSyntax(getSafeValue("alternativeSyntax", rows));
        component.setTitle(getSafeValue("title", rows));
        component.setDescription(getSafeValue("description", rows));
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
            option.setKind(getSafeValue("kind", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDescription(getSafeValue("description", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setEnumValues(getSafeValue("enum", row));
            component.addComponentOption(option);
        }

        rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        for (Map<String, String> row : rows) {
            EndpointOptionModel option = new EndpointOptionModel();
            option.setName(getSafeValue("name", row));
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

        return component;
    }

    private static DataFormatModel generateDataFormatModel(String dataFormatName, String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("dataformat", json, false);

        DataFormatModel dataFormat = new DataFormatModel();
        dataFormat.setTitle(getSafeValue("title", rows));
        dataFormat.setName(getSafeValue("name", rows));
        dataFormat.setModelName(getSafeValue("modelName", rows));
        dataFormat.setDescription(getSafeValue("description", rows));
        dataFormat.setLabel(getSafeValue("label", rows));
        dataFormat.setDeprecated(getSafeValue("deprecated", rows));
        dataFormat.setJavaType(getSafeValue("javaType", rows));
        dataFormat.setGroupId(getSafeValue("groupId", rows));
        dataFormat.setArtifactId(getSafeValue("artifactId", rows));
        dataFormat.setVersion(getSafeValue("version", rows));

        rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        for (Map<String, String> row : rows) {
            DataFormatOptionModel option = new DataFormatOptionModel();
            option.setName(getSafeValue("name", row));
            option.setKind(getSafeValue("kind", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDescription(getSafeValue("description", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setEnumValues(getSafeValue("enum", row));
            dataFormat.addDataFormatOption(option);
        }

        return dataFormat;
    }

    private static LanguageModel generateLanguageModel(String languageName, String json) {
        List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("language", json, false);

        LanguageModel dataFormat = new LanguageModel();
        dataFormat.setTitle(getSafeValue("title", rows));
        dataFormat.setName(getSafeValue("name", rows));
        dataFormat.setModelName(getSafeValue("modelName", rows));
        dataFormat.setDescription(getSafeValue("description", rows));
        dataFormat.setLabel(getSafeValue("label", rows));
        dataFormat.setDeprecated(getSafeValue("deprecated", rows));
        dataFormat.setJavaType(getSafeValue("javaType", rows));
        dataFormat.setGroupId(getSafeValue("groupId", rows));
        dataFormat.setArtifactId(getSafeValue("artifactId", rows));
        dataFormat.setVersion(getSafeValue("version", rows));

        rows = JSonSchemaHelper.parseJsonSchema("properties", json, true);
        for (Map<String, String> row : rows) {
            LanguageOptionModel option = new LanguageOptionModel();
            option.setName(getSafeValue("name", row));
            option.setKind(getSafeValue("kind", row));
            option.setType(getSafeValue("type", row));
            option.setJavaType(getSafeValue("javaType", row));
            option.setDeprecated(getSafeValue("deprecated", row));
            option.setDescription(getSafeValue("description", row));
            option.setDefaultValue(getSafeValue("defaultValue", row));
            option.setEnumValues(getSafeValue("enum", row));
            dataFormat.addLanguageOption(option);
        }

        return dataFormat;
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

}
