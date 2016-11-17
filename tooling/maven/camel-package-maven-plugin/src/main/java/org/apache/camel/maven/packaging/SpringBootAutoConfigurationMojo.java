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

import org.apache.camel.maven.packaging.model.ComponentModel;
import org.apache.camel.maven.packaging.model.ComponentOptionModel;
import org.apache.camel.maven.packaging.model.DataFormatModel;
import org.apache.camel.maven.packaging.model.DataFormatOptionModel;
import org.apache.camel.maven.packaging.model.EndpointOptionModel;
import org.apache.camel.maven.packaging.model.LanguageModel;
import org.apache.camel.maven.packaging.model.LanguageOptionModel;
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
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.Import;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.MethodSource;
import org.jboss.forge.roaster.model.source.PropertySource;
import org.jboss.forge.roaster.model.util.Strings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.apache.camel.maven.packaging.JSonSchemaHelper.getSafeValue;
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

    private static final String[] IGNORE_MODULES = {/* Non-standard -> */ "camel-grape"};

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
            Map<String, List<ComponentModel>> grModels = allModels.stream().collect(Collectors.groupingBy(ComponentModel::getJavaType));
            for (String componentClass : grModels.keySet()) {
                List<ComponentModel> compModels = grModels.get(componentClass);
                ComponentModel model = compModels.get(0); // They should be equivalent
                List<String> aliases = compModels.stream().map(ComponentModel::getScheme).sorted().collect(Collectors.toList());

                boolean hasOptions = !model.getComponentOptions().isEmpty();

                // use springboot as sub package name so the code is not in normal
                // package so the Spring Boot JARs can be optional at runtime
                int pos = model.getJavaType().lastIndexOf(".");
                String pkg = model.getJavaType().substring(0, pos) + ".springboot";

                String overrideComponentName = null;
                if (aliases.size() > 1) {
                    // determine component name when there are multiple ones
                    overrideComponentName = model.getArtifactId().replace("camel-", "");
                }

                if (hasOptions) {
                    createComponentConfigurationSource(pkg, model, overrideComponentName);
                }
                createComponentAutoConfigurationSource(pkg, model, aliases, hasOptions);
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
                String json = loadDataFormaatJson(jsonFiles, dataFormatName);
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

                boolean hasOptions = !model.getDataFormatOptions().isEmpty();

                // use springboot as sub package name so the code is not in normal
                // package so the Spring Boot JARs can be optional at runtime
                int pos = model.getJavaType().lastIndexOf(".");
                String pkg = model.getJavaType().substring(0, pos) + ".springboot";

                String overrideDataformatName = null;
                if (aliases.size() > 1) {
                    // determine component name when there are multiple ones
                    overrideDataformatName = model.getArtifactId().replace("camel-", "");
                }

                if (hasOptions) {
                    createDataFormatConfigurationSource(pkg, model, overrideDataformatName);
                }
                createDataFormatAutoConfigurationSource(pkg, model, aliases, hasOptions);
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


                boolean hasOptions = !model.getLanguageOptions().isEmpty();

                // use springboot as sub package name so the code is not in normal
                // package so the Spring Boot JARs can be optional at runtime
                int pos = model.getJavaType().lastIndexOf(".");
                String pkg = model.getJavaType().substring(0, pos) + ".springboot";

                String overrideLanguageName = null;
                if (aliases.size() > 1) {
                    // determine language name when there are multiple ones
                    overrideLanguageName = model.getArtifactId().replace("camel-", "");
                }

                if (hasOptions) {
                    createLanguageConfigurationSource(pkg, model, overrideLanguageName);
                }
                createLanguageAutoConfigurationSource(pkg, model, aliases, hasOptions);
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

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        if (!Strings.isBlank(model.getDescription())) {
            doc = model.getDescription() + "\n\n" + doc;
        }
        javaClass.getJavaDoc().setFullText(doc);

        String prefix = "camel.component." + (overrideComponentName != null ? overrideComponentName : model.getScheme());
        // make sure prefix is in lower case
        prefix = prefix.toLowerCase(Locale.US);
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
                && Strings.isBlank(option.getEnumValues())
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
                } else if (!Strings.isBlank(option.getEnumValues())) {
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

                String defaultValue = null;
                if (sourceProp.hasAnnotation(UriParam.class)) {
                    defaultValue = sourceProp.getAnnotation(UriParam.class).getStringValue("defaultValue");
                } else if (sourceProp.hasAnnotation(UriPath.class)) {
                    defaultValue = sourceProp.getAnnotation(UriPath.class).getStringValue("defaultValue");
                }
                if (!Strings.isBlank(defaultValue)) {
                    if ("java.lang.String".equals(optionType)) {
                        prop.getField().setStringInitializer(defaultValue);
                    } else if ("integer".equals(optionType) || "boolean".equals(optionType)) {
                        prop.getField().setLiteralInitializer(defaultValue);
                    } else if (anEnum) {
                        String enumShortName = optionClass.getSimpleName();
                        prop.getField().setLiteralInitializer(enumShortName + "." + defaultValue);
                        javaClass.addImport(model.getJavaType());
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

        writeSourceIfChanged(javaClass, fileName);
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

        writeSourceIfChanged(javaClass, fileName);
    }

    private void createComponentAutoConfigurationSource(String packageName, ComponentModel model, List<String> componentAliases, boolean hasOptions) throws MojoFailureException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Component", "ComponentAutoConfiguration");
        javaClass.setPackage(packageName).setName(name);

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        javaClass.getJavaDoc().setFullText(doc);

        javaClass.addAnnotation(Configuration.class);
        javaClass.addAnnotation(ConditionalOnBean.class).setStringValue("type", "org.apache.camel.springboot.CamelAutoConfiguration");

        String configurationName = name.replace("ComponentAutoConfiguration", "ComponentConfiguration");
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

        // Determine all the aliases
        String[] springBeanAliases = componentAliases.stream().map(alias -> alias + "-component").toArray(size -> new String[size]);

        method.addAnnotation(Bean.class).setStringArrayValue("name", springBeanAliases);
        method.addAnnotation(ConditionalOnClass.class).setLiteralValue("value", "CamelContext.class");
        method.addAnnotation(ConditionalOnMissingBean.class).setLiteralValue("value", model.getShortJavaType() + ".class");

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";

        writeSourceIfChanged(javaClass, fileName);
    }

    private void createDataFormatAutoConfigurationSource(String packageName, DataFormatModel model, List<String> dataFormatAliases, boolean hasOptions) throws MojoFailureException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("DataFormat", "DataFormatAutoConfiguration");
        javaClass.setPackage(packageName).setName(name);

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        javaClass.getJavaDoc().setFullText(doc);

        javaClass.addAnnotation(Configuration.class);
        javaClass.addAnnotation(ConditionalOnBean.class).setStringValue("type", "org.apache.camel.springboot.CamelAutoConfiguration");

        String configurationName = name.replace("DataFormatAutoConfiguration", "DataFormatConfiguration");
        if (hasOptions) {
            AnnotationSource<JavaClassSource> ann = javaClass.addAnnotation(EnableConfigurationProperties.class);
            ann.setLiteralValue("value", configurationName + ".class");

            javaClass.addImport("java.util.HashMap");
            javaClass.addImport("java.util.Map");
            javaClass.addImport("org.apache.camel.util.IntrospectionSupport");
        }

        javaClass.addImport("org.apache.camel.CamelContextAware");
        javaClass.addImport(model.getJavaType());
        javaClass.addImport("org.apache.camel.CamelContext");

        String body = createDataFormatBody(model.getShortJavaType(), hasOptions);
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


        // Determine all the aliases
        // adding the '-dataformat' suffix to prevent collision with component names
        String[] springBeanAliases = dataFormatAliases.stream().map(alias -> alias + "-dataformat").toArray(size -> new String[size]);

        method.addAnnotation(Bean.class).setStringArrayValue("name", springBeanAliases);
        method.addAnnotation(ConditionalOnClass.class).setLiteralValue("value", "CamelContext.class");
        method.addAnnotation(ConditionalOnMissingBean.class).setLiteralValue("value", model.getShortJavaType() + ".class");

        sortImports(javaClass);

        String fileName = packageName.replaceAll("\\.", "\\/") + "/" + name + ".java";

        writeSourceIfChanged(javaClass, fileName);
    }

    private void createLanguageAutoConfigurationSource(String packageName, LanguageModel model, List<String> languageAliases, boolean hasOptions) throws MojoFailureException {
        final JavaClassSource javaClass = Roaster.create(JavaClassSource.class);

        int pos = model.getJavaType().lastIndexOf(".");
        String name = model.getJavaType().substring(pos + 1);
        name = name.replace("Language", "LanguageAutoConfiguration");
        javaClass.setPackage(packageName).setName(name);

        String doc = "Generated by camel-package-maven-plugin - do not edit this file!";
        javaClass.getJavaDoc().setFullText(doc);

        javaClass.addAnnotation(Configuration.class);
        javaClass.addAnnotation(ConditionalOnBean.class).setStringValue("type", "org.apache.camel.springboot.CamelAutoConfiguration");

        String configurationName = name.replace("LanguageAutoConfiguration", "LanguageConfiguration");
        if (hasOptions) {
            AnnotationSource<JavaClassSource> ann = javaClass.addAnnotation(EnableConfigurationProperties.class);
            ann.setLiteralValue("value", configurationName + ".class");

            javaClass.addImport("java.util.HashMap");
            javaClass.addImport("java.util.Map");
            javaClass.addImport("org.apache.camel.util.IntrospectionSupport");
        }

        javaClass.addImport("org.apache.camel.CamelContextAware");
        javaClass.addImport(model.getJavaType());
        javaClass.addImport("org.apache.camel.CamelContext");

        String body = createLanguageBody(model.getShortJavaType(), hasOptions);
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

    private static String createComponentBody(String shortJavaType, boolean hasOptions) {
        StringBuilder sb = new StringBuilder();
        sb.append(shortJavaType).append(" component = new ").append(shortJavaType).append("();").append("\n");
        sb.append("component.setCamelContext(camelContext);\n");
        sb.append("\n");
        if (hasOptions) {
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
            sb.append("            IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), nestedProperty, nestedParameters);\n");
            sb.append("            entry.setValue(nestedProperty);\n");
            sb.append("        } catch (NoSuchFieldException e) {\n");
            sb.append("            // ignore, class must not be a nested configuration class after all\n");
            sb.append("        }\n");
            sb.append("    }\n");
            sb.append("}\n");
            sb.append("IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), component, parameters);\n");
        }
        sb.append("\n");
        sb.append("return component;");
        return sb.toString();
    }

    private static String createDataFormatBody(String shortJavaType, boolean hasOptions) {
        StringBuilder sb = new StringBuilder();
        sb.append(shortJavaType).append(" dataformat = new ").append(shortJavaType).append("();").append("\n");
        sb.append("if (dataformat instanceof CamelContextAware) {\n");
        sb.append("    ((CamelContextAware) dataformat).setCamelContext(camelContext);\n");
        sb.append("}\n");
        if (hasOptions) {
            sb.append("\n");
            sb.append("Map<String, Object> parameters = new HashMap<>();\n");
            sb.append("IntrospectionSupport.getProperties(configuration, parameters, null, false);\n");
            sb.append("\n");
            sb.append("IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), dataformat, parameters);\n");
        }
        sb.append("\n");
        sb.append("return dataformat;");
        return sb.toString();
    }

    private static String createLanguageBody(String shortJavaType, boolean hasOptions) {
        StringBuilder sb = new StringBuilder();
        sb.append(shortJavaType).append(" language = new ").append(shortJavaType).append("();").append("\n");
        sb.append("if (language instanceof CamelContextAware) {\n");
        sb.append("    ((CamelContextAware) language).setCamelContext(camelContext);\n");
        sb.append("}\n");
        if (hasOptions) {
            sb.append("\n");
            sb.append("Map<String, Object> parameters = new HashMap<>();\n");
            sb.append("IntrospectionSupport.getProperties(configuration, parameters, null, false);\n");
            sb.append("\n");
            sb.append("IntrospectionSupport.setProperties(camelContext, camelContext.getTypeConverter(), language, parameters);\n");
        }
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

    private void writeSourceIfChanged(JavaClassSource source, String fileName) throws MojoFailureException {

        File target = new File(SpringBootHelper.starterSrcDir(baseDir, project.getArtifactId()), fileName);

        deleteFileOnMainArtifact(target);

        try {
            InputStream is = getClass().getClassLoader().getResourceAsStream("license-header-java.txt");
            String header = loadText(is);
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
