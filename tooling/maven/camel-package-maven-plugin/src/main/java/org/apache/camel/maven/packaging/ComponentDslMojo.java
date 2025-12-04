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

import static org.apache.camel.tooling.util.PackageHelper.findCamelDirectory;

import java.io.File;
import java.io.IOError;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.camel.maven.packaging.dsl.DslHelper;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.JavadocHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

/**
 * Generate Endpoint DSL source files for Components.
 */
@Mojo(
        name = "generate-component-dsl",
        threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class ComponentDslMojo extends AbstractGeneratorMojo {

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
     * The package where to the main DSL component package is
     */
    @Parameter(property = "camel.pmp.package-name", defaultValue = "org.apache.camel.builder.component")
    protected String componentsDslPackageName;

    /**
     * The package where to generate component DSL specific factories
     */
    @Parameter(property = "camel.pmp.factories-package-name", defaultValue = "org.apache.camel.builder.component.dsl")
    protected String componentsDslFactoriesPackageName;

    /**
     * The catalog directory where the component json files are
     */
    @Parameter(
            property = "camel.pmp.json-directory",
            defaultValue =
                    "${project.basedir}/../../catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/components")
    protected File jsonDir;

    @Inject
    public ComponentDslMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    @Override
    public void execute(MavenProject project) throws MojoFailureException, MojoExecutionException {
        buildDir = new File(project.getBuild().getDirectory());
        baseDir = project.getBasedir();
        componentsDslPackageName = "org.apache.camel.builder.component";
        componentsDslFactoriesPackageName = "org.apache.camel.builder.component.dsl";
        super.execute(project);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        File camelDir = findCamelDirectory(baseDir, "dsl/camel-componentdsl");
        if (camelDir == null) {
            getLog().debug("No dsl/camel-componentdsl folder found, skipping execution");
            return;
        }

        if (jsonDir == null) {
            jsonDir = findCamelDirectory(
                    baseDir, "catalog/camel-catalog/src/generated/resources/org/apache/camel/catalog/components");

            if (jsonDir == null) {
                getLog().debug("No json directory folder found, skipping execution");
                return;
            }
        }

        Path root = camelDir.toPath();
        if (sourcesOutputDir == null) {
            sourcesOutputDir = root.resolve("src/generated/java").toFile();
        }
        if (outputResourcesDir == null) {
            outputResourcesDir = root.resolve("src/generated/resources").toFile();
        }
        if (componentsMetadata == null) {
            componentsMetadata =
                    outputResourcesDir.toPath().resolve("metadata.json").toFile();
        }

        List<ComponentModel> models = new ArrayList<>();

        File[] files = jsonDir.listFiles();
        if (files != null) {
            for (File file : files) {
                BaseModel<?> model = JsonMapper.generateModel(file.toPath());
                models.add((ComponentModel) model);
            }
        } else {
            throw new IllegalStateException("Error listing directory: " + jsonDir);
        }
        models.sort((o1, o2) -> o1.getScheme().compareToIgnoreCase(o2.getScheme()));

        executeComponent(models);
    }

    private void executeComponent(List<ComponentModel> allModels) throws MojoFailureException {
        if (allModels.isEmpty()) {
            return;
        }
        if (getLog().isDebugEnabled()) {
            getLog().debug("Found " + allModels.size() + " components");
        }

        for (ComponentModel model : allModels) {
            String componentName = capitalize(toCamelCaseLower(model.getScheme()));
            String packageName = componentsDslFactoriesPackageName;
            String className = componentName + "ComponentBuilderFactory";
            Map<String, Object> ctx = new HashMap<>();
            ctx.put("generatorClass", getClass().getName());
            ctx.put("dslPackage", componentsDslPackageName);
            ctx.put("packageName", packageName);
            ctx.put("className", className);
            ctx.put("model", model);
            ctx.put("mojo", this);
            ctx.put(
                    "configurationOption",
                    findConfiguration(model.getComponentOptions()).orElse(null));

            String source = velocity("velocity/component-builder.vm", ctx);

            writeSourceIfChanged(source, packageName, className);
        }

        String packageName = componentsDslPackageName;
        String className = "ComponentsBuilderFactory";
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("generatorClass", getClass().getName());
        ctx.put("dslFactoriesPackage", componentsDslFactoriesPackageName);
        ctx.put("packageName", packageName);
        ctx.put("className", className);
        ctx.put("models", allModels);
        ctx.put("mojo", this);

        String source = velocity("velocity/component-builder-factory.vm", ctx);

        writeSourceIfChanged(source, packageName, className);
    }

    private boolean writeSourceIfChanged(String code, String packageName, String className)
            throws MojoFailureException {
        String fileName = packageName.replace('.', '/') + "/" + className + ".java";
        try {
            if (getLog().isDebugEnabled()) {
                getLog().debug("Source code generated:\n" + code);
            }

            return updateResource(sourcesOutputDir.toPath(), fileName, code);
        } catch (IOError e) {
            throw new MojoFailureException("IOError with file " + fileName, e);
        }
    }

    public String capitalize(String str) {
        return Strings.capitalize(str);
    }

    public String uncapitalize(String str) {
        return StringUtils.uncapitalize(str);
    }

    public String xmlEncode(String str) {
        return JavadocHelper.xmlEncode(str);
    }

    public String javadoc(String indent, String doc) {
        StringBuilder sb = new StringBuilder(doc.length() * 2);
        sb.append("/**\n");
        int len = 78 - indent.length();
        String rem = xmlEncode(doc);
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
                sb.append(indent).append(" * ").append(s).append("\n");
                rem = l;
            } else {
                sb.append(indent).append(" * ").append(rem).append("\n");
                rem = "";
            }
        }
        sb.append(indent).append(" */");
        return sb.toString();
    }

    public String getMainDescriptionWithoutPathOptions(ComponentModel model) {
        return DslHelper.getMainDescriptionWithoutPathOptions(model);
    }

    public String toCamelCaseLower(String str) {
        return DslHelper.toCamelCaseLower(str);
    }

    public static Optional<ComponentModel.ComponentOptionModel> findConfiguration(
            Collection<ComponentModel.ComponentOptionModel> options) {
        return options.stream().filter(o -> o.getConfigurationField() != null).findFirst();
    }
}
