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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.support.component.ApiMethodArg;
import org.apache.camel.support.component.ApiMethodParser;
import org.apache.camel.support.component.ArgumentSubstitutionParser;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.commons.lang3.ClassUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.velocity.VelocityContext;

/**
 * Base Mojo class for ApiMethod generators.
 */
public abstract class AbstractApiMethodGeneratorMojo extends AbstractApiMethodBaseMojo {

    private static final Map<Class<?>, String> PRIMITIVE_VALUES;

    @Parameter(required = true, property = PREFIX + "proxyClass")
    protected String proxyClass;

    @Parameter
    protected String classPrefix;

    @Parameter
    protected String apiName;

    @Parameter
    protected String apiDescription;

    @Parameter
    protected boolean consumerOnly;

    @Parameter
    protected boolean producerOnly;

    /**
     * Method alias patterns for all APIs.
     */
    @Parameter
    protected List<ApiMethodAlias> aliases = Collections.emptyList();

    /**
     * Names of options that can be set to null value if not specified.
     */
    @Parameter
    protected String[] nullableOptions;

    // cached fields
    private Class<?> proxyType;

    private Pattern propertyNamePattern;
    private Pattern propertyTypePattern;

    @Override
    public void executeInternal() throws MojoExecutionException {

        setCompileSourceRoots();

        // load proxy class and get enumeration file to generate
        final Class<?> proxyType = getProxyType();

        // parse pattern for excluded endpoint properties
        if (excludeConfigNames != null) {
            propertyNamePattern = Pattern.compile(excludeConfigNames);
        }
        if (excludeConfigTypes != null) {
            propertyTypePattern = Pattern.compile(excludeConfigTypes);
        }

        // create parser
        ApiMethodParser<?> parser = createAdapterParser(proxyType);

        List<String> signatures = new ArrayList<>();
        Map<String, Map<String, String>> parameters = new HashMap<>();
        List<SignatureModel> data = getSignatureList();
        for (SignatureModel model : data) {
            // we get the api description via the method signature (not ideal but that's the way of the old parser API)
            if (model.getApiDescription() != null) {
                this.apiDescription = model.getApiDescription();
            }
            signatures.add(model.getSignature());
            String method = StringHelper.before(model.getSignature(), "(");
            if (method != null && method.contains(" ")) {
                method = StringHelper.after(method, " ");
            }
            if (method != null) {
                parameters.put(method, model.getParameterDescriptions());
            }
            parser.getDescriptions().put(method, model.getMethodDescription());
            parser.addSignatureArguments(model.getSignature(), model.getParameterTypes());
        }
        parser.setSignatures(signatures);
        parser.setParameters(parameters);
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

    protected ApiMethodParser<?> createAdapterParser(Class<?> proxyType) {
        return new ArgumentSubstitutionParser<>(proxyType, getArgumentSubstitutions());
    }

    public abstract List<SignatureModel> getSignatureList() throws MojoExecutionException;

    public Class<?> getProxyType() throws MojoExecutionException {
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

    public File getApiMethodFile() {
        final StringBuilder fileName = new StringBuilder();
        fileName.append(outPackage.replace(".", Matcher.quoteReplacement(File.separator))).append(File.separator);
        fileName.append(getEnumName()).append(".java");
        return new File(generatedSrcDir, fileName.toString());
    }

    private String getEnumName() {
        String proxyClassWithCanonicalName = getProxyClassWithCanonicalName(proxyClass);
        String prefix = classPrefix != null ? classPrefix : "";
        return prefix + proxyClassWithCanonicalName.substring(proxyClassWithCanonicalName.lastIndexOf('.') + 1) + "ApiMethod";
    }

    private VelocityContext getApiTestContext(List<ApiMethodParser.ApiMethodModel> models) throws MojoExecutionException {
        VelocityContext context = getCommonContext(models);
        context.put("testName", getUnitTestName());
        context.put("scheme", scheme);
        context.put("componentPackage", componentPackage);
        context.put("componentName", componentName);
        context.put("enumName", getEnumName());
        return context;
    }

    private String getTestFilePath() {
        final StringBuilder fileName = new StringBuilder();
        fileName.append(componentPackage.replace(".", Matcher.quoteReplacement(File.separator))).append(File.separator);
        fileName.append(getUnitTestName()).append(".java");
        return fileName.toString();
    }

    private String getUnitTestName() {
        String proxyClassWithCanonicalName = getProxyClassWithCanonicalName(proxyClass);
        String prefix = classPrefix != null ? classPrefix : "";
        return prefix + proxyClassWithCanonicalName.substring(proxyClassWithCanonicalName.lastIndexOf('.') + 1)
               + "IT";
    }

    private VelocityContext getEndpointContext(List<ApiMethodParser.ApiMethodModel> models) throws MojoExecutionException {
        VelocityContext context = getCommonContext(models);
        context.put("apiName", apiName);
        context.put("apiDescription", apiDescription);
        context.put("consumerOnly", consumerOnly);
        context.put("producerOnly", producerOnly);
        context.put("configName", getConfigName());
        context.put("componentName", componentName);
        context.put("componentPackage", componentPackage);
        context.put("nullableOptions", nullableOptions);

        // generate parameter names and types for configuration, sorted by parameter name
        Map<String, ApiMethodArg> parameters = new TreeMap<>();
        for (ApiMethodParser.ApiMethodModel model : models) {
            for (ApiMethodArg argument : model.getArguments()) {
                final String name = argument.getName();
                final Class<?> type = argument.getType();
                final String typeName = type.getCanonicalName();
                if (!parameters.containsKey(name)
                        && (propertyNamePattern == null || !propertyNamePattern.matcher(name).matches())
                        && (propertyTypePattern == null || !propertyTypePattern.matcher(typeName).matches())) {
                    parameters.put(name, argument);
                }
            }
        }

        // add custom parameters
        if (extraOptions != null && extraOptions.length > 0) {
            for (ExtraOption option : extraOptions) {
                final String name = option.getName();
                final String argWithTypes = option.getType().replace(" ", "");
                final int rawEnd = argWithTypes.indexOf('<');
                String typeArgs = null;
                Class<?> argType;
                try {
                    if (rawEnd != -1) {
                        argType = getProjectClassLoader().loadClass(argWithTypes.substring(0, rawEnd));
                        typeArgs = argWithTypes.substring(rawEnd + 1, argWithTypes.lastIndexOf('>'));
                    } else {
                        argType = getProjectClassLoader().loadClass(argWithTypes);
                    }
                } catch (ClassNotFoundException e) {
                    throw new MojoExecutionException(
                            String.format("Error loading extra option [%s %s] : %s",
                                    argWithTypes, name, e.getMessage()),
                            e);
                }
                parameters.put(name, new ApiMethodArg(name, argType, typeArgs, argWithTypes, option.getDescription()));
            }
        }

        context.put("parameters", parameters);
        return context;
    }

    private File getConfigurationFile() {
        final StringBuilder fileName = new StringBuilder();
        // endpoint configuration goes in component package
        fileName.append(componentPackage.replace(".", Matcher.quoteReplacement(File.separator))).append(File.separator);
        fileName.append(getConfigName()).append(".java");
        return new File(generatedSrcDir, fileName.toString());
    }

    private String getConfigName() {
        String proxyClassWithCanonicalName = getProxyClassWithCanonicalName(proxyClass);
        String prefix = classPrefix != null ? classPrefix : "";
        return prefix + proxyClassWithCanonicalName.substring(proxyClassWithCanonicalName.lastIndexOf('.') + 1)
               + "EndpointConfiguration";
    }

    private String getProxyClassWithCanonicalName(String proxyClass) {
        return proxyClass.replace("$", "");
    }

    private VelocityContext getCommonContext(List<ApiMethodParser.ApiMethodModel> models) throws MojoExecutionException {
        VelocityContext context = new VelocityContext();
        context.put("models", models);
        context.put("proxyType", getProxyType());
        context.put("proxyTypeLink", getProxyType().getName().replace('$', '.'));
        context.put("helper", this);
        return context;
    }

    public ArgumentSubstitutionParser.Substitution[] getArgumentSubstitutions() {
        ArgumentSubstitutionParser.Substitution[] subs = new ArgumentSubstitutionParser.Substitution[substitutions.length];
        for (int i = 0; i < substitutions.length; i++) {
            final Substitution substitution = substitutions[i];
            subs[i] = new ArgumentSubstitutionParser.Substitution(
                    substitution.getMethod(),
                    substitution.getArgName(), substitution.getArgType(),
                    substitution.getReplacement(), substitution.isReplaceWithType());
        }
        return subs;
    }

    public static String getType(Class<?> clazz) {
        if (clazz.isArray()) {
            if (clazz.getComponentType().isPrimitive()) {
                return getCanonicalName(clazz) + ".class";
            } else {
                // create a zero length array and get the class from the instance
                return "new " + getCanonicalName(clazz).replaceAll("\\[\\]", "[0]") + ".getClass()";
            }
        } else {
            return getCanonicalName(clazz) + ".class";
        }
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public String getAliases() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        if (!aliases.isEmpty()) {
            StringJoiner sj = new StringJoiner(", ");
            aliases.forEach(a -> sj.add("\"" + a.getMethodPattern() + "=" + a.getMethodAlias() + "\""));
            sb.append(sj);
        }
        sb.append("}");
        return sb.toString();
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public static String getApiMethodsForParam(List<ApiMethodParser.ApiMethodModel> models, ApiMethodArg argument) {
        StringBuilder sb = new StringBuilder();

        // avoid duplicate methods as we only want them listed once
        Set<String> names = new HashSet<>();

        String key = argument.getName();
        // if the given argument does not belong to any method with the same argument name,
        // then it mean it should belong to all methods; this is typically extra options that has been declared in the
        // pom.xml file
        boolean noneMatch = models.stream().noneMatch(m -> m.getArguments().stream().noneMatch(a -> a.getName().equals(key)));

        models.forEach(p -> {
            ApiMethodArg match = p.getArguments().stream().filter(a -> a.getName().equals(key)).findFirst().orElse(null);
            if (match != null && names.add(p.getName())) {
                // favour desc from the matched argument list
                String desc = match.getDescription();
                if (desc == null) {
                    desc = argument.getDescription();
                }
                sb.append("@ApiMethod(methodName = \"").append(p.getName()).append("\"");
                if (ObjectHelper.isNotEmpty(desc)) {
                    sb.append(", description=\"").append(desc).append("\"");
                }
                sb.append(")");
                sb.append(", ");
            } else if (noneMatch) {
                // favour desc from argument
                String desc = argument.getDescription();
                sb.append("@ApiMethod(methodName = \"").append(p.getName()).append("\"");
                if (ObjectHelper.isNotEmpty(desc)) {
                    sb.append(", description=\"").append(desc).append("\"");
                }
                sb.append(")");
                sb.append(", ");
            }
        });
        String answer = sb.toString();
        if (answer.endsWith(", ")) {
            answer = answer.substring(0, answer.length() - 2);
        }
        return "{" + answer + "}";
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public static String getTestName(ApiMethodParser.ApiMethodModel model) {
        final StringBuilder builder = new StringBuilder();
        final String name = model.getMethod().getName();
        builder.append(Character.toUpperCase(name.charAt(0)));
        builder.append(name, 1, name.length());
        // find overloaded method suffix from unique name
        final String uniqueName = model.getUniqueName();
        if (uniqueName.length() > name.length()) {
            builder.append(uniqueName, name.length(), uniqueName.length());
        }
        return builder.toString();
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public static boolean isVoidType(Class<?> resultType) {
        return resultType == Void.TYPE;
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public String getExchangePropertyPrefix() {
        // exchange property prefix
        return "Camel" + componentName + ".";
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public static String getResultDeclaration(Class<?> resultType) {
        if (resultType.isPrimitive()) {
            return ClassUtils.primitiveToWrapper(resultType).getSimpleName();
        } else {
            return getCanonicalName(resultType);
        }
    }

    static {
        PRIMITIVE_VALUES = new HashMap<>();
        PRIMITIVE_VALUES.put(Boolean.TYPE, "Boolean.FALSE");
        PRIMITIVE_VALUES.put(Byte.TYPE, "(byte) 0");
        PRIMITIVE_VALUES.put(Character.TYPE, "(char) 0");
        PRIMITIVE_VALUES.put(Short.TYPE, "(short) 0");
        PRIMITIVE_VALUES.put(Integer.TYPE, "0");
        PRIMITIVE_VALUES.put(Long.TYPE, "0L");
        PRIMITIVE_VALUES.put(Float.TYPE, "0.0f");
        PRIMITIVE_VALUES.put(Double.TYPE, "0.0d");
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public boolean hasDoc(ApiMethodArg argument) {
        return argument.getDescription() != null && !argument.getDescription().isEmpty();
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public String getDoc(ApiMethodArg argument) {
        if (argument.getDescription() == null || argument.getDescription().isEmpty()) {
            return "";
        }
        return argument.getDescription();
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public String getApiName(String apiName) {
        if (apiName == null || apiName.isEmpty()) {
            return "DEFAULT";
        }
        return apiName;
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public String getApiDescription(String apiDescription) {
        if (apiDescription == null) {
            return "";
        }
        return apiDescription;
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public boolean isOptionalParameter(ApiMethodArg argument) {
        String name = argument.getName();
        if (nullableOptions != null) {
            for (String nu : nullableOptions) {
                if (name.equalsIgnoreCase(nu)) {
                    return true;
                }
            }
        }
        return false;
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public String getCanonicalName(ApiMethodArg argument) {
        // replace primitives with wrapper classes (as that makes them option and avoid boolean because false by default)
        final Class<?> type = argument.getType();
        if (type.isPrimitive()) {
            return getCanonicalName(ClassUtils.primitiveToWrapper(type));
        }
        String fqn = argument.getRawTypeArgs();
        // the type may use $ for classloader, so replace it back with dot
        fqn = fqn.replace('$', '.');
        return fqn;
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public String getApiMethods(List<ApiMethodParser.ApiMethodModel> models) {
        models.sort((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));

        // avoid duplicate methods as we only want them listed once
        Set<String> names = new HashSet<>();

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        for (int i = 0; i < models.size(); i++) {
            ApiMethodParser.ApiMethodModel model = models.get(i);
            String name = model.getName();
            if (names.add(name)) {
                sb.append("@ApiMethod(methodName = \"").append(model.getName()).append("\"");
                String desc = model.getDescription();
                if (ObjectHelper.isNotEmpty(desc)) {
                    sb.append(", description=\"").append(desc).append("\"");
                }
                List<String> signatures = getSignatures(models, name);
                if (!signatures.isEmpty()) {
                    sb.append(", signatures={");
                    StringJoiner sj = new StringJoiner(", ");
                    signatures.forEach(s -> sj.add("\"" + s + "\""));
                    sb.append(sj);
                    sb.append("}");
                }
                sb.append(")");
                if (i < models.size() - 1) {
                    sb.append(", ");
                }
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private List<String> getSignatures(List<ApiMethodParser.ApiMethodModel> models, String methodName) {
        List<String> list = new ArrayList<>();
        for (ApiMethodParser.ApiMethodModel model : models) {
            if (model.getName().equals(methodName)) {
                if (model.getSignature() != null) {
                    list.add(model.getSignature());
                }
            }
        }
        return list;
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public static String getDefaultArgValue(Class<?> aClass) {
        if (aClass.isPrimitive()) {
            // lookup default primitive value string
            return PRIMITIVE_VALUES.get(aClass);
        } else {
            // return type cast null string
            return "null";
        }
    }

    /*
     * This is used when configuring the plugin instead of directly, which is why it reports as unused
     * without the annotation
     */
    @SuppressWarnings("unused")
    public static String getBeanPropertySuffix(String parameter) {
        // capitalize first character
        StringBuilder builder = new StringBuilder();
        builder.append(Character.toUpperCase(parameter.charAt(0)));
        builder.append(parameter, 1, parameter.length());
        return builder.toString();
    }
}
