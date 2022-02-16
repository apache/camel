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
package org.apache.camel.maven;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.stream.Stream;

import org.apache.camel.util.StringHelper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.velocity.VelocityContext;

/**
 * Generates Camel Component based on a collection of APIs.
 */
@Mojo(name = "fromApis", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, requiresProject = true,
      defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true)
public class ApiComponentGeneratorMojo extends AbstractApiMethodBaseMojo {

    protected static final String DEFAULT_EXCLUDE_PACKAGES = "javax?\\.lang.*";

    /** The Constant CACHE_PROPERTIES_FILENAME. */
    private static final String CACHE_PROPERTIES_FILENAME = "camel-api-component-maven-plugin-cache.properties";

    /**
     * List of API names, proxies and code generation settings.
     */
    @Parameter(required = true)
    protected ApiProxy[] apis;

    /**
     * Common Javasource code generation settings.
     */
    @Parameter
    protected FromJavasource fromJavasource = new FromJavasource();

    /**
     * Projects cache directory.
     *
     * <p>
     * This file is a hash cache of the files in the project source. It can be preserved in source code such that it
     * ensures builds are always fast by not unnecessarily writing files constantly. It can also be added to gitignore
     * in case startup is not necessary. It further can be redirected to another location.
     *
     * <p>
     * When stored in the repository, the cache if run on cross platforms will display the files multiple times due to
     * line ending differences on the platform.
     *
     * @since 2.9.0
     */
    @Parameter(defaultValue = "${project.build.directory}")
    private File cachedir;

    /**
     * Names of options that can be set to null value if not specified.
     */
    @Parameter
    private String[] nullableOptions;

    /**
     * Method alias patterns for all APIs.
     */
    @Parameter
    private List<ApiMethodAlias> aliases = Collections.emptyList();

    @Override
    public void executeInternal() throws MojoExecutionException {
        if (apis == null || apis.length == 0) {
            throw new MojoExecutionException("One or more API proxies are required");
        }

        // fix apiName for single API use-case since Maven configurator sets empty parameters as null!!!
        if (apis.length == 1 && apis[0].getApiName() == null) {
            apis[0].setApiName("");
        }

        String newHash = new HashHelper()
                .hash("ApiComponentGeneratorMojo")
                .hash("apis", apis)
                .hash("fromJavasource", fromJavasource)
                .hash("nullableOptions", nullableOptions)
                .hash("aliases", aliases)
                .hash("substitutions", substitutions)
                .hash("excludeConfigNames", excludeConfigNames)
                .hash("excludeConfigTypes", excludeConfigTypes)
                .hash("extraOptions", extraOptions)
                .toString();
        Instant newDate;
        try (Stream<File> stream = Stream.of(this.generatedSrcDir, generatedTestDir)) {
            newDate = stream
                    .map(File::toPath)
                    .flatMap(this::walk)
                    .filter(Files::isRegularFile)
                    .map(this::lastModified)
                    .max(Comparator.naturalOrder())
                    .orElse(Instant.now());
        }

        List<String> cache = readCacheFile();
        String prevHash = cache.stream().filter(s -> s.startsWith("hash=")).findFirst()
                .map(s -> s.substring("hash=".length())).orElse(null);
        Instant prevDate = cache.stream().filter(s -> s.startsWith("date=")).findFirst()
                .map(s -> s.substring("date=".length()))
                .map(Instant::parse)
                .orElse(Instant.ofEpochSecond(0));

        if (Objects.equals(prevHash, newHash) && !newDate.isAfter(prevDate)) {
            getLog().info("Skipping api generation, everything is up to date.");
            setCompileSourceRoots();
            return;
        }

        // generate API methods for each API proxy
        for (ApiProxy api : apis) {
            // validate API configuration
            api.validate();

            // create the appropriate code generator if signatureFile or fromJavaDoc are specified
            // this way users can skip generating API classes for duplicate proxy class references
            final AbstractApiMethodGeneratorMojo apiMethodGenerator = getApiMethodGenerator(api);

            if (apiMethodGenerator != null) {
                // configure API method properties and generate Proxy classes
                configureMethodGenerator(apiMethodGenerator, api);
                try {
                    apiMethodGenerator.setProjectClassLoader(getProjectClassLoader()); // supply pre-constructed ClassLoader
                    apiMethodGenerator.executeInternal(); // Call internal execute method
                } catch (Exception e) {
                    final String msg = "Error generating source for " + api.getProxyClass() + ": " + e.getMessage();
                    throw new MojoExecutionException(msg, e);
                }
            } else {
                // make sure the proxy class is being generated elsewhere
                final String proxyClass = api.getProxyClass();
                boolean found = false;
                for (ApiProxy other : apis) {
                    if (other != api && proxyClass.equals(other.getProxyClass())) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    throw new MojoExecutionException(
                            "Missing one of fromSignatureFile or fromJavadoc for "
                                                     + proxyClass);
                }
            }

            // set common aliases if needed
            if (!aliases.isEmpty() && api.getAliases().isEmpty()) {
                api.setAliases(aliases);
            }

            // set common nullable options if needed
            if (api.getNullableOptions() == null) {
                api.setNullableOptions(nullableOptions);
            }
        }

        // generate ApiCollection
        mergeTemplate(getApiContext(), getApiCollectionFile(), "/api-collection.vm");

        // generate ApiName
        mergeTemplate(getApiContext(), getApiNameFile(), "/api-name-enum.vm");

        try (Stream<File> stream = Stream.of(this.generatedSrcDir, generatedTestDir)) {
            newDate = stream
                    .map(File::toPath)
                    .flatMap(this::walk)
                    .filter(Files::isRegularFile)
                    .map(this::lastModified)
                    .max(Comparator.naturalOrder())
                    .orElse(Instant.now());
        }
        writeCacheFile(Arrays.asList(
                "# ApiComponentGenerator cache file",
                "hash=" + newHash,
                "date=" + newDate.toString()));
    }

    private Instant lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            return Instant.now();
        }
    }

    private Stream<Path> walk(Path p) {
        try {
            return Files.walk(p, Integer.MAX_VALUE);
        } catch (IOException e) {
            return Stream.empty();
        }
    }

    private void configureMethodGenerator(AbstractApiMethodGeneratorMojo mojo, ApiProxy apiProxy) {

        // set AbstractGeneratorMojo properties
        mojo.componentName = componentName;
        mojo.scheme = scheme;
        mojo.outPackage = outPackage;
        mojo.componentPackage = componentPackage;
        mojo.project = project;

        // set AbstractSourceGeneratorMojo properties
        mojo.generatedSrcDir = generatedSrcDir;
        mojo.generatedTestDir = generatedTestDir;
        mojo.addCompileSourceRoots = addCompileSourceRoots;

        // set AbstractAPIMethodBaseMojo properties
        mojo.substitutions = apiProxy.getSubstitutions().length != 0
                ? apiProxy.getSubstitutions() : substitutions;
        mojo.excludeConfigNames = apiProxy.getExcludeConfigNames() != null
                ? apiProxy.getExcludeConfigNames() : excludeConfigNames;
        mojo.excludeConfigTypes = apiProxy.getExcludeConfigTypes() != null
                ? apiProxy.getExcludeConfigTypes() : excludeConfigTypes;
        mojo.extraOptions = apiProxy.getExtraOptions() != null
                ? apiProxy.getExtraOptions() : extraOptions;

        // set AbstractAPIMethodGeneratorMojo properties
        mojo.proxyClass = apiProxy.getProxyClass();
        mojo.classPrefix = apiProxy.getClassPrefix();
        mojo.apiName = apiProxy.getApiName();
        mojo.apiDescription = apiProxy.getApiDescription();
        mojo.consumerOnly = apiProxy.isConsumerOnly();
        mojo.producerOnly = apiProxy.isProducerOnly();
    }

    private AbstractApiMethodGeneratorMojo getApiMethodGenerator(ApiProxy api) {
        AbstractApiMethodGeneratorMojo apiMethodGenerator = null;

        final FromJavasource apiFromJavasource = api.getFromJavasource();
        if (apiFromJavasource != null) {
            final JavaSourceApiMethodGeneratorMojo mojo = new JavaSourceApiMethodGeneratorMojo();
            mojo.excludePackages = apiFromJavasource.getExcludePackages() != null
                    ? apiFromJavasource.getExcludePackages() : fromJavasource.getExcludePackages();
            mojo.excludeClasses = apiFromJavasource.getExcludeClasses() != null
                    ? apiFromJavasource.getExcludeClasses() : fromJavasource.getExcludeClasses();
            mojo.includeMethods = apiFromJavasource.getIncludeMethods() != null
                    ? apiFromJavasource.getIncludeMethods() : fromJavasource.getIncludeMethods();
            mojo.excludeMethods = apiFromJavasource.getExcludeMethods() != null
                    ? apiFromJavasource.getExcludeMethods() : fromJavasource.getExcludeMethods();
            mojo.includeStaticMethods = apiFromJavasource.getIncludeStaticMethods() != null
                    ? apiFromJavasource.getIncludeStaticMethods() : fromJavasource.getIncludeStaticMethods();
            mojo.aliases = api.getAliases().isEmpty() ? aliases : api.getAliases();
            mojo.nullableOptions = api.getNullableOptions() != null ? api.getNullableOptions() : nullableOptions;
            apiMethodGenerator = mojo;
        }

        return apiMethodGenerator;
    }

    private VelocityContext getApiContext() {
        final VelocityContext context = new VelocityContext();
        context.put("componentName", componentName);
        context.put("componentPackage", componentPackage);
        context.put("apis", apis);
        context.put("helper", getClass());
        context.put("collectionName", getApiCollectionName());
        context.put("apiNameEnum", getApiNameEnum());
        return context;
    }

    private String getApiCollectionName() {
        return componentName + "ApiCollection";
    }

    private String getApiNameEnum() {
        return componentName + "ApiName";
    }

    private File getApiCollectionFile() {
        final StringBuilder fileName = getFileBuilder();
        fileName.append(getApiCollectionName()).append(".java");
        return new File(generatedSrcDir, fileName.toString());
    }

    private File getApiNameFile() {
        final StringBuilder fileName = getFileBuilder();
        fileName.append(getApiNameEnum()).append(".java");
        return new File(generatedSrcDir, fileName.toString());
    }

    private StringBuilder getFileBuilder() {
        final StringBuilder fileName = new StringBuilder();
        fileName.append(outPackage.replace(".", Matcher.quoteReplacement(File.separator))).append(File.separator);
        return fileName;
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public static String getApiMethod(String proxyClass, String classPrefix) {
        String proxyClassWithCanonicalName = getProxyClassWithCanonicalName(proxyClass);
        String prefix = classPrefix != null ? classPrefix : "";
        return prefix + proxyClassWithCanonicalName.substring(proxyClassWithCanonicalName.lastIndexOf('.') + 1) + "ApiMethod";
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public static String getEndpointConfig(String proxyClass, String classPrefix) {
        String proxyClassWithCanonicalName = getProxyClassWithCanonicalName(proxyClass);
        String prefix = classPrefix != null ? classPrefix : "";
        return prefix + proxyClassWithCanonicalName.substring(proxyClassWithCanonicalName.lastIndexOf('.') + 1)
               + "EndpointConfiguration";
    }

    private static String getProxyClassWithCanonicalName(String proxyClass) {
        return proxyClass.replace("$", "");
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public static String getEnumConstant(String enumValue) {
        if (enumValue == null || enumValue.isEmpty()) {
            return "DEFAULT";
        }
        String value = StringHelper.camelCaseToDash(enumValue);
        // replace dash with underscore and upper case
        value = value.replace('-', '_');
        value = value.toUpperCase(Locale.ENGLISH);
        return value;
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public static String getNullableOptionValues(String[] nullableOptions) {
        if (nullableOptions == null || nullableOptions.length == 0) {
            return "";
        }

        final StringBuilder builder = new StringBuilder();
        final int nOptions = nullableOptions.length;
        int i = 0;
        for (String option : nullableOptions) {
            builder.append('"').append(option).append('"');
            if (++i < nOptions) {
                builder.append(", ");
            }
        }
        return builder.toString();
    }

    /**
     * Store file hash cache.
     */
    private void writeCacheFile(List<String> cache) {
        if (this.cachedir != null) {
            File cacheFile = new File(this.cachedir, CACHE_PROPERTIES_FILENAME);
            try (OutputStream out = new FileOutputStream(cacheFile)) {
                Files.write(cacheFile.toPath(), cache);
            } catch (IOException e) {
                getLog().warn("Cannot store file hash cache properties file", e);
            }
        }
    }

    /**
     * Read file hash cache file.
     */
    private List<String> readCacheFile() {
        Log log = getLog();
        if (this.cachedir == null) {
            return Collections.emptyList();
        }
        if (!this.cachedir.exists()) {
            if (!this.cachedir.mkdirs()) {
                log.warn("Unable to create cache directory '" + this.cachedir + "'.");
            }
        } else if (!this.cachedir.isDirectory()) {
            log.warn("Something strange here as the '" + this.cachedir
                     + "' supposedly cache directory is not a directory.");
            return Collections.emptyList();
        }

        File cacheFile = new File(this.cachedir, CACHE_PROPERTIES_FILENAME);
        if (!cacheFile.exists()) {
            return Collections.emptyList();
        }

        try {
            return Files.readAllLines(cacheFile.toPath());
        } catch (IOException e) {
            log.warn("Cannot load cache file", e);
            return Collections.emptyList();
        }
    }

}
