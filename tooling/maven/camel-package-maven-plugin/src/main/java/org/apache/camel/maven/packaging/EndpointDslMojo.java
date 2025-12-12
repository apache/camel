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
package org.apache.camel.maven.packaging;

import java.io.File;
import java.io.FileFilter;
import java.io.IOError;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.camel.maven.packaging.dsl.DslHelper;
import org.apache.camel.maven.packaging.generics.JavadocUtil;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.ComponentModel.EndpointOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.JavadocHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.commons.text.CaseUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;

/**
 * Generate Endpoint DSL source files for Components.
 */
@Mojo(name = "generate-endpoint-dsl", threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
      defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class EndpointDslMojo extends AbstractGeneratorMojo {
    /**
     * The project build directory
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    /**
     * The base directory
     */
    @Parameter(defaultValue = "${project.basedir}")
    protected File baseDir;

    /**
     * The package where to generate component Endpoint factories
     */
    @Parameter(defaultValue = "org.apache.camel.builder.endpoint")
    protected String endpointFactoriesPackageName;

    /**
     * The package where to generate component specific Endpoint factories
     */
    @Parameter(defaultValue = "org.apache.camel.builder.endpoint.dsl")
    protected String componentsFactoriesPackageName;

    /**
     * The output directory
     */
    @Parameter
    protected File sourcesOutputDir;

    /**
     * Component Metadata file
     */
    @Parameter
    protected File componentsMetadata;

    /**
     * Components DSL Metadata
     */
    @Parameter
    protected File outputResourcesDir;

    /**
     * The catalog directory where the component json files are
     */
    @Parameter(defaultValue = "${project.basedir}/../../catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/components")
    protected File jsonDir;

    @Inject
    public EndpointDslMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    @Override
    public void execute(MavenProject project) throws MojoFailureException, MojoExecutionException {
        buildDir = new File(project.getBuild().getDirectory());
        baseDir = project.getBasedir();
        endpointFactoriesPackageName = "org.apache.camel.builder.endpoint";
        componentsFactoriesPackageName = "org.apache.camel.builder.endpoint.dsl";
        super.execute(project);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File camelDir = findCamelDirectory(baseDir, "dsl/camel-endpointdsl");
        if (camelDir == null) {
            getLog().debug("No dsl/camel-endpointdsl folder found, skipping execution");
            return;
        }
        Path root = camelDir.toPath();
        if (sourcesOutputDir == null) {
            sourcesOutputDir = root.resolve("src/generated/java").toFile();
        }
        if (outputResourcesDir == null) {
            outputResourcesDir = root.resolve("src/generated/resources").toFile();
        }
        if (componentsMetadata == null) {
            componentsMetadata = outputResourcesDir.toPath().resolve("metadata.json").toFile();
        }

        List<ComponentModel> models = new ArrayList<>();

        for (File file : jsonDir.listFiles()) {
            BaseModel<?> model = JsonMapper.generateModel(file.toPath());
            models.add((ComponentModel) model);
        }

        // generate component endpoint DSL files and write them
        executeComponent(models);
    }

    private void executeComponent(List<ComponentModel> allModels) throws MojoFailureException {
        if (allModels.isEmpty()) {
            return;
        }
        if (getLog().isDebugEnabled()) {
            getLog().debug("Found " + allModels.size() + " components");
        }

        // Group the models by implementing classes
        Map<String, List<ComponentModel>> models
                = allModels.stream().collect(Collectors.groupingBy(ComponentModel::getJavaType,
                        // order components by name
                        HashMap::new,
                        // if there are alias then we need to sort scheme according to the alternative schemes position
                        Collectors.collectingAndThen(Collectors.toList(), l -> {
                            l.sort(Comparator.comparingInt(o -> o.getAlternativeSchemes().indexOf(o.getScheme())));
                            return l;
                        })));

        // Update each component DSL
        for (List<ComponentModel> aliases : models.values()) {
            ComponentModel model = aliases.get(0); // master component
            if (doCreateEndpointDsl(model, aliases)) {
                getLog().info("Updated EndpointDsl: " + model.getScheme());
            }
        }

        // Update components metadata
        getLog().debug("Load components EndpointFactories");
        final List<File> endpointFactories = loadAllComponentsDslEndpointFactoriesAsFile();

        getLog().debug("Regenerate EndpointBuilderFactory");
        // make sure EndpointBuilderFactory is synced
        if (synchronizeEndpointBuilderFactoryInterface(endpointFactories)) {
            getLog().info("UpdatedEndpointBuilderFactory ");
        }

        getLog().debug("Regenerate EndpointBuilders");
        // make sure EndpointBuilders is synced
        if (synchronizeEndpointBuildersInterface(endpointFactories)) {
            getLog().info("Updated EndpointBuilders");
        }

        getLog().debug("Regenerate StaticEndpointBuilders");
        // make sure StaticEndpointBuilders is synced
        if (synchronizeEndpointBuildersStaticClass(allModels, models)) {
            getLog().info("Updated StaticEndpointBuilders");
        }
    }

    private boolean doCreateEndpointDsl(ComponentModel model, List<ComponentModel> aliases)
            throws MojoFailureException {
        String componentName = getComponentNameFromType(model.getJavaType());

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("generatorClass", getClass().getName());
        ctx.put("componentName", componentName);
        ctx.put("package", componentsFactoriesPackageName);
        ctx.put("model", model);
        ctx.put("aliases", aliases);
        ctx.put("mojo", this);
        String source = velocity("velocity/endpoint-builder.vm", ctx);

        return writeSourceIfChanged(source, componentsFactoriesPackageName.replace('.', '/'),
                componentName + "EndpointBuilderFactory.java");
    }

    public String headerNameMethodName(String headerName) {
        final String name;
        if (headerName.chars().anyMatch(c -> c == ':' || c == '-' || c == '.' || c == '_')) {
            name = CaseUtils.toCamelCase(headerName, false, ':', '-', '.', '_');
        } else {
            name = headerName.substring(0, 1).toLowerCase() + headerName.substring(1);
        }
        return name;
    }

    public String createBaseDescription(BaseOptionModel option, String kind, boolean ignoreMultiValue, String optionDoc) {
        String baseDesc = option.getDescription();
        if (Strings.isEmpty(baseDesc)) {
            return baseDesc;

        }

        // must xml encode description as in some rare cases it contains & chars which is invalid javadoc
        baseDesc = JavadocHelper.xmlEncode(baseDesc);

        StringBuilder baseDescBuilder = new StringBuilder(baseDesc);
        if (!baseDesc.endsWith(".")) {
            baseDescBuilder.append(".");
        }
        if (option.isSupportFileReference()) {
            baseDescBuilder.append("\n");
            baseDescBuilder.append(
                    "\nThis option can also be loaded from an existing file, by prefixing with file: or classpath: followed by the location of the file.");
        }
        baseDescBuilder.append("\n").append(optionDoc);
        if (option.isMultiValue()) {
            baseDescBuilder.append("\nThe option is multivalued, and you can use the ")
                    .append(option.getName())
                    .append("(String, Object) method to add a value (call the method multiple times to set more values).");
        }
        baseDescBuilder.append("\n");
        // the Endpoint DSL currently requires to provide the entire
        // context-path and not as individual options
        // so lets only mark query parameters that are required as
        // required
        if (kind.equals(option.getKind()) && option.isRequired()) {
            baseDescBuilder.append("\nRequired: true");
        }
        // include default value (if any)
        if (option.getDefaultValue() != null) {
            // must xml encode default value so its valid as javadoc
            String value = JavadocHelper.xmlEncode(option.getDefaultValue().toString());
            baseDescBuilder.append("\nDefault: ").append(value);
        }
        baseDescBuilder.append("\nGroup: ").append(option.getGroup());

        return baseDescBuilder.toString();
    }

    public String optionJavaType(EndpointOptionModel option) {
        String answer = option.getJavaType();
        if (answer.startsWith("java.lang.")) {
            return answer.substring(10);
        } else if (answer.startsWith("java.util.concurrent.")) {
            return answer.substring(21);
        } else if (answer.startsWith("java.util.function.")) {
            return answer.substring(19);
        } else if (answer.startsWith("java.util.stream.")) {
            return answer.substring(17);
        } else if (answer.startsWith("java.util.")) {
            return answer.substring(10);
        }

        return answer;
    }

    private boolean synchronizeEndpointBuilderFactoryInterface(List<File> factories) throws MojoFailureException {
        List<String> factoryNames = factories.stream()
                .map(factory -> {
                    String factoryName = Strings.before(factory.getName(), ".");
                    String endpointsName = factoryName.replace("EndpointBuilderFactory", "Builders");
                    return componentsFactoriesPackageName + "." + factoryName + "." + endpointsName;
                }).toList();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("generatorClass", getClass().getName());
        ctx.put("package", endpointFactoriesPackageName);
        ctx.put("className", "EndpointBuilderFactory");
        ctx.put("factories", factoryNames);
        ctx.put("mojo", this);
        String source = velocity("velocity/endpoint-builder-factory.vm", ctx);

        return writeSourceIfChanged(source,
                endpointFactoriesPackageName.replace('.', '/'), "EndpointBuilderFactory.java");
    }

    private boolean synchronizeEndpointBuildersInterface(List<File> factories) throws MojoFailureException {
        List<String> factoryNames = factories.stream()
                .map(factory -> {
                    String factoryName = Strings.before(factory.getName(), ".");
                    return componentsFactoriesPackageName + "." + factoryName;
                }).toList();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("generatorClass", getClass().getName());
        ctx.put("package", endpointFactoriesPackageName);
        ctx.put("className", "EndpointBuilders");
        ctx.put("factories", factoryNames);
        ctx.put("mojo", this);
        String source = velocity("velocity/endpoint-builders.vm", ctx);

        return writeSourceIfChanged(source,
                endpointFactoriesPackageName.replace(".", "/"), "EndpointBuilders.java");
    }

    private boolean synchronizeEndpointBuildersStaticClass(
            List<ComponentModel> allModels, Map<String, List<ComponentModel>> models)
            throws MojoFailureException {
        List<ComponentModel> sortedModels = new ArrayList<>(allModels);
        sortedModels.sort(Comparator.comparing(ComponentModel::getScheme));

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("generatorClass", getClass().getName());
        ctx.put("package", endpointFactoriesPackageName);
        ctx.put("dslPackage", componentsFactoriesPackageName);
        ctx.put("className", "StaticEndpointBuilders");
        ctx.put("models", models);
        ctx.put("sortedModels", sortedModels);
        ctx.put("mojo", this);
        String source = velocity("velocity/endpoint-static-builders.vm", ctx);

        return writeSourceIfChanged(source,
                endpointFactoriesPackageName.replace(".", "/"), "StaticEndpointBuilders.java");
    }

    private List<File> loadAllComponentsDslEndpointFactoriesAsFile() {
        final File allComponentsDslEndpointFactory
                = new File(sourcesOutputDir, componentsFactoriesPackageName.replace('.', '/'));
        FileFilter fileFilter = file -> file.isFile() && file.getName().endsWith(".java");
        final File[] files = allComponentsDslEndpointFactory.listFiles(fileFilter);

        if (files == null) {
            return Collections.emptyList();
        }

        // load components
        return Arrays.stream(files).sorted().toList();
    }

    public String camelCaseLower(String s) {
        int i;
        while (s != null && (i = s.indexOf('-')) > 0) {
            s = camelCaseAtIndex(s, i);
        }
        while (s != null && (i = s.indexOf('+')) > 0) {
            s = camelCaseAtIndex(s, i);
        }
        if (s != null) {
            s = s.substring(0, 1).toLowerCase() + s.substring(1);
            s = DslHelper.sanitizeText(s);
        }
        return s;
    }

    private static String camelCaseAtIndex(String s, int i) {
        return s.substring(0, i) + Strings.capitalize(s.substring(i + 1));
    }

    public String getComponentNameFromType(String type) {
        int pos = type.lastIndexOf('.');
        String name = type.substring(pos + 1).replace("Component", "");
        return switch (type) {
            case "org.apache.camel.component.atmosphere.websocket.WebsocketComponent" -> "AtmosphereWebsocket";
            case "org.apache.camel.component.zookeepermaster.MasterComponent" -> "ZooKeeperMaster";
            case "org.apache.camel.component.elasticsearch.ElasticsearchComponent" -> "ElasticsearchRest";
            case "org.apache.camel.component.activemq.ActiveMQComponent" -> "ActiveMQ";
            case "org.apache.camel.component.activemq6.ActiveMQComponent" -> "ActiveMQ6";
            default -> name;
        };
    }

    private boolean writeSourceIfChanged(String code, String filePath, String fileName) throws MojoFailureException {
        try {
            if (getLog().isDebugEnabled()) {
                getLog().debug("Source code generated:\n" + code);
            }

            return updateResource(sourcesOutputDir.toPath(), filePath + "/" + fileName, code);
        } catch (IOError e) {
            throw new MojoFailureException("IOError with file " + filePath + "/" + fileName, e);
        }
    }

    public String getMainDescription(ComponentModel model) {
        return JavadocUtil.getMainDescription(model, true);
    }

    public String getMainDescription(ComponentModel model, boolean withPathParameterDetails) {
        return JavadocUtil.getMainDescription(model, withPathParameterDetails);
    }

    public String pathParameterJavaDoc(ComponentModel model) {
        return JavadocUtil.pathParameterJavaDoc(model);
    }

    public String xmlEncode(String str) {
        return JavadocHelper.xmlEncode(str);
    }

    public String javadoc(String indent, String doc) {
        StringBuilder sb = new StringBuilder(doc.length() * 2);
        List<String> lines = formatJavadocOrCommentStringAsList(doc, indent);
        if (!lines.isEmpty()) {
            sb.append("/**\n");
            for (String line : lines) {
                sb.append(indent).append(" * ").append(line).append("\n");
            }
            sb.append(indent).append(" */");
        }
        return sb.toString();
    }

    private List<String> formatJavadocOrCommentStringAsList(String text, String indent) {
        List<String> lines = new ArrayList<>();
        int len = 78 - indent.length();

        String rem = text;

        if (rem != null) {
            while (!rem.isEmpty()) {
                int idx = rem.length() >= len ? rem.substring(0, len).lastIndexOf(' ') : -1;
                int idx2 = rem.indexOf('\n');
                if (idx2 >= 0 && (idx < 0 || idx2 < idx || idx2 < len)) {
                    idx = idx2;
                }
                if (idx >= 0) {
                    String s = rem.substring(0, idx);
                    while (s.endsWith(" ")) {
                        s = s.substring(0, s.length() - 1);
                    }
                    String l = rem.substring(idx + 1);
                    while (l.startsWith(" ")) {
                        l = l.substring(1);
                    }
                    lines.add(s);
                    rem = l;
                } else {
                    lines.add(rem);
                    rem = "";
                }
            }
        }

        return lines;
    }
}
