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
package org.apache.camel.maven;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.camel.util.component.ApiMethodParser;
import org.apache.camel.util.component.ArgumentSubstitutionParser;
import org.apache.commons.lang.ClassUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.velocity.VelocityContext;

/**
 * Base Mojo class for ApiMethod generators.
 */
public abstract class AbstractApiMethodGeneratorMojo extends AbstractGeneratorMojo {

    @Parameter(required = true, property = PREFIX + "proxyClass")
    protected String proxyClass;

    @Parameter(property = PREFIX + "substitutions")
    protected Substitution[] substitutions = new Substitution[0];

    // cached fields
    private Class<?> proxyType;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        // load proxy class and get enumeration file to generate
        final Class proxyType = getProxyType();

        // create parser
        ApiMethodParser parser = createAdapterParser(proxyType);
        parser.setSignatures(getSignatureList());
        parser.setClassLoader(getProjectClassLoader());

        // parse signatures
        final List<ApiMethodParser.ApiMethodModel> models = parser.parse();

        // generate enumeration from model
        mergeTemplate(getApiMethodContext(models), getApiMethodFile(), "/api-method-enum.vm");

        // generate EndpointConfiguration for this Api
        mergeTemplate(getEndpointContext(models), getConfigurationFile(), "/api-endpoint-config.vm");

        // generate junit test if it doesn't already exist under test source directory
        // i.e. it may have been generated then moved there and populated with test values
        final String testFilePath = getTestFilePath();
        if (!new File(project.getBuild().getTestSourceDirectory(), testFilePath).exists()) {
            mergeTemplate(getApiTestContext(models), new File(generatedTestDir, testFilePath), "/api-route-test.vm");
        }
    }

    protected ApiMethodParser createAdapterParser(Class proxyType) {
        return new ArgumentSubstitutionParser(proxyType, getArgumentSubstitutions()){};
    }

    public abstract List<String> getSignatureList() throws MojoExecutionException;

    public Class getProxyType() throws MojoExecutionException {
        if (proxyType == null) {
            // load proxy class from Project runtime dependencies
            try {
                proxyType = getProjectClassLoader().loadClass(proxyClass);
            } catch (ClassNotFoundException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        }
        return proxyType;
    }

    private VelocityContext getApiMethodContext(List<ApiMethodParser.ApiMethodModel> models) throws MojoExecutionException {
        VelocityContext context = getCommonContext(models);
        context.put("enumName", getEnumName());
        return context;
    }

    public File getApiMethodFile() throws MojoExecutionException {
        final StringBuilder fileName = new StringBuilder();
        fileName.append(outPackage.replaceAll("\\.", File.separator)).append(File.separator);
        fileName.append(getEnumName()).append(".java");
        return new File(generatedSrcDir, fileName.toString());
    }

    private String getEnumName() throws MojoExecutionException {
        return getProxyType().getSimpleName() + "ApiMethod";
    }

    private VelocityContext getApiTestContext(List<ApiMethodParser.ApiMethodModel> models) throws MojoExecutionException {
        VelocityContext context = getCommonContext(models);
        context.put("testName", getUnitTestName());
        context.put("scheme", scheme);
        return context;
    }

    private String getTestFilePath() throws MojoExecutionException {
        final StringBuilder fileName = new StringBuilder();
        fileName.append(outPackage.replaceAll("\\.", File.separator)).append(File.separator);
        fileName.append(getUnitTestName()).append(".java");
        return fileName.toString();
    }

    private String getUnitTestName() throws MojoExecutionException {
        return getProxyType().getSimpleName() + "IntegrationTest";
    }

    private VelocityContext getEndpointContext(List<ApiMethodParser.ApiMethodModel> models) throws MojoExecutionException {
        VelocityContext context = getCommonContext(models);
        context.put("configName", getConfigName());
        context.put("componentName", componentName);
        context.put("componentPackage", componentPackage);

        // generate parameter names and types for configuration, sorted by parameter name
        Map<String, Class<?>> parameters = new TreeMap<String, Class<?>>();
        for (ApiMethodParser.ApiMethodModel model : models) {
            for (ApiMethodParser.Argument argument : model.getArguments()) {
                if (!parameters.containsKey(argument.getName())) {
                    Class<?> type = argument.getType();
                    if (type.isPrimitive()) {
                        // replace primitives with wrapper classes
                        type = ClassUtils.primitiveToWrapper(type);
                    }
                    parameters.put(argument.getName(), type);
                }
            }
        }
        context.put("parameters", parameters);
        return context;
    }

    private File getConfigurationFile() throws MojoExecutionException {
        final StringBuilder fileName = new StringBuilder();
        // endpoint configuration goes in component package
        fileName.append(componentPackage.replaceAll("\\.", File.separator)).append(File.separator);
        fileName.append(getConfigName()).append(".java");
        return new File(generatedSrcDir, fileName.toString());
    }

    private String getConfigName() throws MojoExecutionException {
        return getProxyType().getSimpleName() + "EndpointConfiguration";
    }

    private VelocityContext getCommonContext(List<ApiMethodParser.ApiMethodModel> models) throws MojoExecutionException {
        VelocityContext context = new VelocityContext();
        context.put("models", models);
        context.put("proxyType", getProxyType());
        context.put("helper", this);
        return context;
    }

    public ArgumentSubstitutionParser.Substitution[] getArgumentSubstitutions() {
        ArgumentSubstitutionParser.Substitution[] subs = new ArgumentSubstitutionParser.Substitution[substitutions.length];
        for (int i = 0; i < substitutions.length; i++) {
            subs[i] = new ArgumentSubstitutionParser.Substitution(substitutions[i].getMethod(),
                    substitutions[i].getArgName(), substitutions[i].getArgType(), substitutions[i].getReplacement());
        }
        return subs;
    }

    public static String getType(Class<?> clazz) {
        if (clazz.isArray()) {
            // create a zero length array and get the class from the instance
            return "new " + getCanonicalName(clazz).replaceAll("\\[\\]", "[0]") + ".getClass()";
        } else {
            return getCanonicalName(clazz) + ".class";
        }
    }

    public static String getTestName(ApiMethodParser.ApiMethodModel model) {
        final StringBuilder builder = new StringBuilder();
        final String name = model.getMethod().getName();
        builder.append(Character.toUpperCase(name.charAt(0)));
        builder.append(name.substring(1));
        // find overloaded method suffix from unique name
        final String uniqueName = model.getUniqueName();
        if (uniqueName.length() > name.length()) {
            builder.append(uniqueName.substring(name.length()));
        }
        return builder.toString();
    }

    public static boolean isVoidType(Class<?> resultType) {
        return resultType == Void.TYPE;
    }

    public String getExchangePropertyPrefix() {
        // exchange property prefix
        return "Camel" + componentName + ".";
    }

    public static String getResultDeclaration(Class<?> resultType) {
        if (resultType.isPrimitive()) {
            return ClassUtils.primitiveToWrapper(resultType).getSimpleName();
        } else {
            return getCanonicalName(resultType);
        }
    }

    private static final Map<Class<?>, String> PRIMITIVE_VALUES;

    static {
        PRIMITIVE_VALUES = new HashMap<Class<?>, String>();
        PRIMITIVE_VALUES.put(Boolean.TYPE, "Boolean.FALSE");
        PRIMITIVE_VALUES.put(Byte.TYPE, "(byte) 0");
        PRIMITIVE_VALUES.put(Character.TYPE, "(char) 0");
        PRIMITIVE_VALUES.put(Short.TYPE, "(short) 0");
        PRIMITIVE_VALUES.put(Integer.TYPE, "0");
        PRIMITIVE_VALUES.put(Long.TYPE, "0L");
        PRIMITIVE_VALUES.put(Float.TYPE, "0.0f");
        PRIMITIVE_VALUES.put(Double.TYPE, "0.0d");
    }

    public static String getDefaultArgValue(Class<?> aClass) {
        if (aClass.isPrimitive()) {
            // lookup default primitive value string
            return PRIMITIVE_VALUES.get(aClass);
        } else {
            // return type cast null string
            return "null";
        }
    }

    public static String getBeanPropertySuffix(String parameter) {
        // capitalize first character
        StringBuilder builder = new StringBuilder();
        builder.append(Character.toUpperCase(parameter.charAt(0)));
        builder.append(parameter.substring(1));
        return builder.toString();
    }

    public static String getCanonicalName(Class<?> type) {
        // remove java.lang prefix for default Java package
        String canonicalName = type.getCanonicalName();
        final int pkgEnd = canonicalName.lastIndexOf('.');
        if (pkgEnd > 0 && canonicalName.substring(0, pkgEnd).equals("java.lang")) {
            canonicalName = canonicalName.substring(pkgEnd + 1);
        }
        return canonicalName;
    }
}
