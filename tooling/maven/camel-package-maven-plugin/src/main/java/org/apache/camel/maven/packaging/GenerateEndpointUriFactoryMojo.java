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
import java.io.IOError;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import static org.apache.camel.tooling.util.PackageHelper.loadText;

/**
 * Abstract class for endpoint uri factory generator.
 */
@Mojo(name = "generate-endpoint-uri-factory", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyCollection = ResolutionScope.COMPILE,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateEndpointUriFactoryMojo extends AbstractGeneratorMojo {

    /**
     * The project build directory
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File sourcesOutputDir;
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    public GenerateEndpointUriFactoryMojo() {
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if ("pom".equals(project.getPackaging())) {
            return;
        }

        buildDir = new File(project.getBuild().getDirectory());

        if (sourcesOutputDir == null) {
            sourcesOutputDir = new File(project.getBasedir(), "src/generated/java");
        }
        if (resourcesOutputDir == null) {
            resourcesOutputDir = new File(project.getBasedir(), "src/generated/resources");
        }

        Map<File, Supplier<String>> files;
        try {
            files = Files
                    .find(buildDir.toPath(), Integer.MAX_VALUE,
                            (p, a) -> a.isRegularFile() && p.toFile().getName().endsWith(PackageHelper.JSON_SUFIX))
                    .collect(Collectors.toMap(Path::toFile, s -> cache(() -> loadJson(s.toFile()))));
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        executeComponent(files);
    }

    private void executeComponent(Map<File, Supplier<String>> jsonFiles) throws MojoExecutionException, MojoFailureException {
        // find the component names
        Set<String> componentNames = new TreeSet<>();
        findComponentNames(buildDir, componentNames);

        // create auto configuration for the components
        if (!componentNames.isEmpty()) {
            getLog().debug("Found " + componentNames.size() + " components");

            List<ComponentModel> allModels = new LinkedList<>();
            for (String componentName : componentNames) {
                String json = loadComponentJson(jsonFiles, componentName);
                if (json != null) {
                    ComponentModel model = JsonMapper.generateComponentModel(json);
                    allModels.add(model);
                }
            }

            // Group the models by implementing classes
            Map<String, List<ComponentModel>> grModels
                    = allModels.stream().collect(Collectors.groupingBy(ComponentModel::getJavaType));
            for (String componentClass : grModels.keySet()) {
                List<ComponentModel> compModels = grModels.get(componentClass);
                for (ComponentModel model : compModels) {
                    // if more than one, we have a component class with multiple components aliases
                    try {
                        createEndpointUriFactory(model);
                    } catch (IOException e) {
                        throw new MojoExecutionException("Error generating source code", e);
                    }
                }
            }
        }
    }

    protected void createEndpointUriFactory(ComponentModel model) throws IOException {
        getLog().debug("Generating endpoint-uri-factory: " + model.getScheme());

        String fqn = model.getJavaType();
        generateEndpointUriFactory(fqn, fqn, model, sourcesOutputDir);

        int pos = fqn.lastIndexOf('.');
        String pn = fqn.substring(0, pos);
        String cn = fqn.substring(pos + 1) + "EndpointUriFactory";
        // remove component from name
        cn = cn.replace("Component", "");
        fqn = pn + "." + cn;

        String pval = model.getScheme() + "-endpoint";
        updateResource(resourcesOutputDir.toPath(),
                "META-INF/services/org/apache/camel/urifactory/" + pval,
                "# " + GENERATED_MSG + NL + "class=" + fqn + NL);

        // META-INF/services/org/apache/camel/configurer/
        if (model.getAlternativeSchemes() != null) {
            String[] schemes = model.getAlternativeSchemes().split(",");
            for (String alt : schemes) {
                pval = alt + "-endpoint";
                updateResource(resourcesOutputDir.toPath(),
                        "META-INF/services/org/apache/camel/urifactory/" + pval,
                        "# " + GENERATED_MSG + NL + "class=" + fqn + NL);
            }
        }
    }

    @Deprecated
    private void generateEndpointUriFactory(
            String fqn, String targetFqn, ComponentModel model, File outputDir)
            throws IOException {

        int pos = targetFqn.lastIndexOf('.');
        String pn = targetFqn.substring(0, pos);
        String cn = targetFqn.substring(pos + 1) + "EndpointUriFactory";
        // remove component from name
        cn = cn.replace("Component", "");
        String en = fqn;
        String pfqn = fqn;
        String psn = "org.apache.camel.support.component.EndpointUriFactorySupport";

        StringWriter sw = new StringWriter();
        EndpointUriFactoryGenerator.generateEndpointUriFactory(pn, cn, en, pfqn, psn, model, sw);

        String source = sw.toString();

        String fileName = pn.replace('.', '/') + "/" + cn + ".java";
        outputDir.mkdirs();
        boolean updated = updateResource(buildContext, outputDir.toPath().resolve(fileName), source);
        if (updated) {
            getLog().info("Updated " + fileName);
        }
    }

    protected static String loadJson(File file) {
        try {
            return loadText(file);
        } catch (IOException e) {
            throw new IOError(e);
        }
    }

    protected static String loadComponentJson(Map<File, Supplier<String>> jsonFiles, String componentName) {
        return loadJsonOfType(jsonFiles, componentName, "component");
    }

    protected static String loadJsonOfType(Map<File, Supplier<String>> jsonFiles, String modelName, String type) {
        for (Map.Entry<File, Supplier<String>> entry : jsonFiles.entrySet()) {
            if (entry.getKey().getName().equals(modelName + ".json")) {
                String json = entry.getValue().get();
                if (json.contains("\"kind\": \"" + type + "\"")) {
                    return json;
                }
            }
        }
        return null;
    }

    protected void findComponentNames(File dir, Set<String> componentNames) {
        File f = new File(dir, "classes/META-INF/services/org/apache/camel/component");

        if (f.exists() && f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File file : files) {
                    // skip directories as there may be a sub .resolver
                    // directory
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

}
