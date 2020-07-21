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
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;

import org.apache.maven.plugin.MojoExecutionException;
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

    /**
     * List of API names, proxies and code generation settings.
     */
    @Parameter(required = true)
    protected ApiProxy[] apis;

    /**
     * Common Javadoc code generation settings.
     */
    @Parameter
    protected FromJavadoc fromJavadoc = new FromJavadoc();

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
                    throw new MojoExecutionException("Missing one of fromSignatureFile or fromJavadoc for "
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
    }

    private AbstractApiMethodGeneratorMojo getApiMethodGenerator(ApiProxy api) {
        AbstractApiMethodGeneratorMojo apiMethodGenerator = null;

        final File signatureFile = api.getFromSignatureFile();
        if (signatureFile != null) {

            final FileApiMethodGeneratorMojo fileMojo = new FileApiMethodGeneratorMojo();
            fileMojo.signatureFile = signatureFile;
            apiMethodGenerator = fileMojo;

        } else {

            final FromJavadoc apiFromJavadoc = api.getFromJavadoc();
            if (apiFromJavadoc != null) {
                final JavadocApiMethodGeneratorMojo javadocMojo = new JavadocApiMethodGeneratorMojo();
                javadocMojo.excludePackages = apiFromJavadoc.getExcludePackages() != null
                        ? apiFromJavadoc.getExcludePackages() : fromJavadoc.getExcludePackages();
                javadocMojo.excludeClasses = apiFromJavadoc.getExcludeClasses() != null
                        ? apiFromJavadoc.getExcludeClasses() : fromJavadoc.getExcludeClasses();
                javadocMojo.includeMethods = apiFromJavadoc.getIncludeMethods() != null
                        ? apiFromJavadoc.getIncludeMethods() : fromJavadoc.getIncludeMethods();
                javadocMojo.excludeMethods = apiFromJavadoc.getExcludeMethods() != null
                        ? apiFromJavadoc.getExcludeMethods() : fromJavadoc.getExcludeMethods();
                javadocMojo.includeStaticMethods = apiFromJavadoc.getIncludeStaticMethods() != null
                        ? apiFromJavadoc.getIncludeStaticMethods() : fromJavadoc.getIncludeStaticMethods();

                apiMethodGenerator = javadocMojo;
            }
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

    public static String getApiMethod(String proxyClass, String classPrefix) {
        String proxyClassWithCanonicalName = getProxyClassWithCanonicalName(proxyClass);
        String prefix = classPrefix != null ? classPrefix : "";
        return prefix + proxyClassWithCanonicalName.substring(proxyClassWithCanonicalName.lastIndexOf('.') + 1) + "ApiMethod";
    }

    public static String getEndpointConfig(String proxyClass, String classPrefix) {
        String proxyClassWithCanonicalName = getProxyClassWithCanonicalName(proxyClass);
        String prefix = classPrefix != null ? classPrefix : "";
        return prefix + proxyClassWithCanonicalName.substring(proxyClassWithCanonicalName.lastIndexOf('.') + 1) + "EndpointConfiguration";
    }

    private static String getProxyClassWithCanonicalName(String proxyClass) {
        return proxyClass.replace("$", "");
    }

    public static String getEnumConstant(String enumValue) {
        if (enumValue == null || enumValue.isEmpty()) {
            return "DEFAULT";
        }
        StringBuilder builder = new StringBuilder();
        if (!Character.isJavaIdentifierStart(enumValue.charAt(0))) {
            builder.append('_');
        }
        for (char c : enumValue.toCharArray()) {
            char upperCase = Character.toUpperCase(c);
            if (!Character.isJavaIdentifierPart(upperCase)) {
                builder.append('_');
            } else {
                builder.append(upperCase);
            }
        }
        return builder.toString();
    }

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
}
