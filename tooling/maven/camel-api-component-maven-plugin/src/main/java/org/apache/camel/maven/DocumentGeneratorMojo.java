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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.camel.impl.engine.DefaultBeanIntrospection;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.component.ApiCollection;
import org.apache.camel.support.component.ApiMethod;
import org.apache.camel.support.component.ApiMethodHelper;
import org.apache.camel.support.component.ApiName;
import org.apache.commons.lang.ClassUtils;
import org.apache.maven.doxia.siterenderer.RenderingContext;
import org.apache.maven.doxia.siterenderer.sink.SiteRendererSink;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.reporting.MavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.velocity.VelocityContext;
import org.codehaus.doxia.sink.Sink;
import org.codehaus.plexus.util.StringUtils;

/**
 * Generates documentation for API Component.
 */
@Mojo(name = "document", requiresDependencyResolution = ResolutionScope.COMPILE, requiresProject = true,
        defaultPhase = LifecyclePhase.SITE, threadSafe = true)
public class DocumentGeneratorMojo extends AbstractGeneratorMojo implements MavenReport {

    // document output directory
    @Parameter(property = PREFIX + "reportOutputDirectory",
        defaultValue = "${project.reporting.outputDirectory}/cameldocs")
    private File reportOutputDirectory;

    // name of destination directory
    @Parameter(property = PREFIX + "destDir", defaultValue = "cameldocs")
    private String destDir;

    /**
     * The name of the Camel report to be displayed in the Maven Generated Reports page
     * (i.e. <code>project-reports.html</code>).
     */
    @Parameter(property = "name")
    private String name;

    /**
     * The description of the Camel report to be displayed in the Maven Generated Reports page
     * (i.e. <code>project-reports.html</code>).
     */
    @Parameter(property = "description")
    private String description;

    private ApiCollection collection;

    @Override
    public void executeInternal() throws MavenReportException {
        RenderingContext context = new RenderingContext(reportOutputDirectory, getOutputName() + ".html");
        SiteRendererSink sink = new SiteRendererSink(context);
        Locale locale = Locale.getDefault();
        generate(sink, locale);
    }

    private void loadApiCollection() throws MavenReportException {
        try {
            final Class<?> collectionClass = getProjectClassLoader().loadClass(
                    outPackage + "." + componentName + "ApiCollection");
            final Method getCollection = collectionClass.getMethod("getCollection");
            this.collection = (ApiCollection) getCollection.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            throw new MavenReportException(e.getMessage(), e);
        }
    }

    private VelocityContext getDocumentContext() throws MavenReportException {
        final VelocityContext context = new VelocityContext();
        context.put("helper", this);

        // project GAV
        context.put("groupId", project.getGroupId());
        context.put("artifactId", project.getArtifactId());
        context.put("version", project.getVersion());

        // component URI format
        // look for single API, no endpoint-prefix
        @SuppressWarnings("unchecked")
        final Set<String> apiNames = new TreeSet<String>(collection.getApiNames());
        context.put("apiNames", apiNames);
        String suffix;
        if (apiNames.size() == 1 && apiNames.contains("")) {
            suffix = "://endpoint?[options]";
        } else {
            suffix = "://endpoint-prefix/endpoint?[options]";
        }
        context.put("uriFormat", scheme + suffix);

        // API helpers
        final Map<String, ApiMethodHelper> apiHelpers = new TreeMap<>();
        for (Object element : collection.getApiHelpers().entrySet()) {
            Map.Entry entry = (Map.Entry) element;
            apiHelpers.put(((ApiName) entry.getKey()).getName(), (ApiMethodHelper) entry.getValue());
        }
        context.put("apiHelpers", apiHelpers);

        // API methods and endpoint configurations
        final Map<String, Class<? extends ApiMethod>> apiMethods = new TreeMap<>();
        final Map<String, Class<?>> apiConfigs = new TreeMap<>();
        for (Object element : collection.getApiMethods().entrySet()) {
            Map.Entry entry = (Map.Entry) element;
            final String name = ((ApiName) entry.getValue()).getName();

            @SuppressWarnings("unchecked")
            Class<? extends ApiMethod> apiMethod = (Class<? extends ApiMethod>) entry.getKey();
            apiMethods.put(name, apiMethod);

            Class<?> configClass;
            try {
                configClass = getProjectClassLoader().loadClass(getEndpointConfigName(apiMethod));
            } catch (ClassNotFoundException e) {
                throw new MavenReportException(e.getMessage(), e);
            }
            apiConfigs.put(name, configClass);
        }
        context.put("apiMethods", apiMethods);
        context.put("apiConfigs", apiConfigs);

        // API component properties
        context.put("scheme", this.scheme);
        context.put("componentName", this.componentName);
        Class<?> configClass;
        try {
            configClass = getProjectClassLoader().loadClass(getComponentConfig());
        } catch (ClassNotFoundException e) {
            throw new MavenReportException(e.getMessage(), e);
        }
        context.put("componentConfig", configClass);
        // get declared and derived fields for component config
        // use get/set methods instead of fields, since this class could inherit others, that have private fields
        // so getDeclaredFields() won't work, like it does for generated endpoint config classes!!!
        final Map<String, String> configFields = new TreeMap<>();
        do {
            BeanIntrospection.ClassInfo classInfo = new DefaultBeanIntrospection().cacheClass(configClass);
            for (BeanIntrospection.MethodInfo method : classInfo.methods) {
                if (method.isSetter) {
                    configFields.put(method.getterOrSetterShorthandName, getCanonicalName(method.method.getParameterTypes()[0]));
                }
            }
            configClass = configClass.getSuperclass();
        } while (configClass != null && !configClass.equals(Object.class));
        context.put("componentConfigFields", configFields);

        return context;
    }

    private String getComponentConfig() {
        StringBuilder builder = new StringBuilder(componentPackage);
        builder.append(".").append(componentName).append("Configuration");
        return builder.toString();
    }

    private String getEndpointConfigName(Class<? extends ApiMethod> apiMethod) {
        final String simpleName = apiMethod.getSimpleName();
        StringBuilder builder = new StringBuilder(componentPackage);
        builder.append(".");
        builder.append(simpleName, 0, simpleName.indexOf("ApiMethod"));
        builder.append("EndpointConfiguration");
        return builder.toString();
    }

    private File getDocumentFile() {
        return new File(getReportOutputDirectory(), getDocumentName() + ".html");
    }

    private String getDocumentName() {
        return this.componentName + "Component";
    }

    @Override
    public void generate(Sink sink, Locale locale) throws MavenReportException {
        // load APICollection
        loadApiCollection();

        try {
            mergeTemplate(getDocumentContext(), getDocumentFile(), "/api-document.vm");
        } catch (MojoExecutionException e) {
            throw new MavenReportException(e.getMessage(), e);
        }
    }

    @Override
    public String getOutputName() {
        return this.destDir + "/" + getDocumentName();
    }

    @Override
    public String getCategoryName() {
        return CATEGORY_PROJECT_REPORTS;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName(Locale locale) {
        if (StringUtils.isEmpty(name)) {
            return getBundle(locale).getString("report.cameldoc.name");
        }
        return name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getDescription(Locale locale) {
        if (StringUtils.isEmpty(description)) {
            return getBundle(locale).getString("report.cameldoc.description");
        }
        return description;
    }

    @Override
    public File getReportOutputDirectory() {
        return reportOutputDirectory;
    }

    @Override
    public void setReportOutputDirectory(File reportOutputDirectory) {
        updateReportOutputDirectory(reportOutputDirectory);
    }

    private void updateReportOutputDirectory(File reportOutputDirectory) {
        // append destDir if needed
        if (this.destDir != null && reportOutputDirectory != null
                && !reportOutputDirectory.getAbsolutePath().endsWith(destDir)) {
            this.reportOutputDirectory = new File(reportOutputDirectory, destDir);
        } else {
            this.reportOutputDirectory = reportOutputDirectory;
        }
    }

    public String getDestDir() {
        return destDir;
    }

    public void setDestDir(String destDir) {
        this.destDir = destDir;
        updateReportOutputDirectory(this.reportOutputDirectory);
    }

    @Override
    public boolean isExternalReport() {
        return true;
    }

    @Override
    public boolean canGenerateReport() {
        // TODO check for class availability??
        return true;
    }

    private ResourceBundle getBundle(Locale locale) {
        return ResourceBundle.getBundle("cameldoc-report", locale, getClass().getClassLoader());
    }

    public static List<EndpointInfo> getEndpoints(Class<? extends ApiMethod> apiMethod,
                                                  ApiMethodHelper<?> helper, Class<?> endpointConfig) {
        // get list of valid options
        final Set<String> validOptions = new HashSet<>();
        for (Field field : endpointConfig.getDeclaredFields()) {
            validOptions.add(field.getName());
        }

        // create method name map
        final Map<String, List<ApiMethod>> methodMap = new TreeMap<>();
        for (ApiMethod method : apiMethod.getEnumConstants()) {
            String methodName = method.getName();
            List<ApiMethod> apiMethods = methodMap.get(methodName);
            if (apiMethods == null) {
                apiMethods = new ArrayList<>();
                methodMap.put(methodName, apiMethods);
            }
            apiMethods.add(method);
        }

        // create method name to alias name map
        final Map<String, Set<String>> aliasMap = new TreeMap<>();
        final Map<String, Set<String>> aliasToMethodMap = helper.getAliases();
        for (Map.Entry<String, Set<String>> entry : aliasToMethodMap.entrySet()) {
            final String alias = entry.getKey();
            for (String method : entry.getValue()) {
                Set<String> aliases = aliasMap.get(method);
                if (aliases == null) {
                    aliases = new TreeSet<>();
                    aliasMap.put(method, aliases);
                }
                aliases.add(alias);
            }
        }

        // create options map and return type map
        final Map<String, Set<String>> optionMap = new TreeMap<>();
        final Map<String, Set<String>> returnType = new TreeMap<>();
        for (Map.Entry<String, List<ApiMethod>> entry : methodMap.entrySet()) {
            final String name = entry.getKey();
            final List<ApiMethod> apiMethods = entry.getValue();

            // count the number of times, every valid option shows up across methods
            // and also collect return types
            final Map<String, Integer> optionCount = new TreeMap<>();
            final TreeSet<String> resultTypes = new TreeSet<>();
            returnType.put(name, resultTypes);

            for (ApiMethod method : apiMethods) {
                for (String arg : method.getArgNames()) {

                    if (validOptions.contains(arg)) {
                        Integer count = optionCount.get(arg);
                        if (count == null) {
                            count = 1;
                        } else {
                            count += 1;
                        }
                        optionCount.put(arg, count);
                    }
                }

                // wrap primitive result types
                Class<?> resultType = method.getResultType();
                if (resultType.isPrimitive()) {
                    resultType = ClassUtils.primitiveToWrapper(resultType);
                }
                resultTypes.add(getCanonicalName(resultType));
            }

            // collect method options
            final TreeSet<String> options = new TreeSet<>();
            optionMap.put(name, options);
            final Set<String> mandatory = new TreeSet<>();

            // generate optional and mandatory lists for overloaded methods
            int nMethods = apiMethods.size();
            for (ApiMethod method : apiMethods) {
                final Set<String> optional = new TreeSet<>();

                for (String arg : method.getArgNames()) {
                    if (validOptions.contains(arg)) {

                        final Integer count = optionCount.get(arg);
                        if (count == nMethods) {
                            mandatory.add(arg);
                        } else {
                            optional.add(arg);
                        }
                    }
                }

                if (!optional.isEmpty()) {
                    options.add(optional.toString());
                }
            }

            if (!mandatory.isEmpty()) {
                // strip [] from mandatory options
                final String mandatoryOptions = mandatory.toString();
                options.add(mandatoryOptions.substring(1, mandatoryOptions.length() - 1));
            }
        }

        // create endpoint data
        final List<EndpointInfo> infos = new ArrayList<>();
        for (Map.Entry<String, List<ApiMethod>> methodEntry : methodMap.entrySet()) {
            final String endpoint = methodEntry.getKey();

            // set endpoint name
            EndpointInfo info = new EndpointInfo();
            info.endpoint = endpoint;
            info.aliases = convertSetToString(aliasMap.get(endpoint));
            info.options = convertSetToString(optionMap.get(endpoint));
            final Set<String> resultTypes = returnType.get(endpoint);
            // get rid of void results
            resultTypes.remove("void");
            info.resultTypes = convertSetToString(resultTypes);

            infos.add(info);
        }

        return infos;
    }

    private static String convertSetToString(Set<String> values) {
        if (values != null && !values.isEmpty()) {
            final String result = values.toString();
            return result.substring(1, result.length() - 1);
        } else {
            return "";
        }
    }

    public static String getCanonicalName(Field field) {
        final Type fieldType = field.getGenericType();
        if (fieldType instanceof ParameterizedType) {
            return getCanonicalName((ParameterizedType) fieldType);
        } else {
            return getCanonicalName(field.getType());
        }
    }

    private static String getCanonicalName(ParameterizedType fieldType) {
        final Type[] typeArguments = fieldType.getActualTypeArguments();

        final int nArguments = typeArguments.length;
        if (nArguments > 0) {
            final StringBuilder result = new StringBuilder(getCanonicalName((Class<?>) fieldType.getRawType()));
            result.append("&lt;");
            int i = 0;
            for (Type typeArg : typeArguments) {
                if (typeArg instanceof ParameterizedType) {
                    result.append(getCanonicalName((ParameterizedType) typeArg));
                } else {
                    result.append(getCanonicalName((Class<?>) typeArg));
                }
                if (++i < nArguments) {
                    result.append(',');
                }
            }
            result.append("&gt;");
            return result.toString();
        }

        return getCanonicalName((Class<?>) fieldType.getRawType());
    }

    public static class EndpointInfo {
        private String endpoint;
        private String aliases;
        private String options;
        private String resultTypes;

        public String getEndpoint() {
            return endpoint;
        }

        public String getAliases() {
            return aliases;
        }

        public String getOptions() {
            return options;
        }

        public String getResultTypes() {
            return resultTypes;
        }
    }
}
