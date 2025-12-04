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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.JBangModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.JavadocHelper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.StaticCapable;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;

/**
 * Prepares camel-jbang by generating Camel JBang configuration metadata for tooling support.
 */
@Mojo(
        name = "prepare-jbang",
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        threadSafe = true,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class PrepareCamelJBangMojo extends AbstractGeneratorMojo {

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File outFolder;

    @Inject
    public PrepareCamelJBangMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    /**
     * Parses the Camel JBang configuration java source file.
     */
    public static List<JBangModel.JBangOptionModel> parseConfigurationSource(String fileName) throws IOException {
        return parseConfigurationSource(new File(fileName));
    }

    /**
     * Parses the Camel JBang configuration java source file.
     */
    public static List<JBangModel.JBangOptionModel> parseConfigurationSource(File file) throws IOException {
        final List<JBangModel.JBangOptionModel> answer = new ArrayList<>();

        JavaClassSource clazz = (JavaClassSource) Roaster.parse(file);
        List<FieldSource<JavaClassSource>> fields = clazz.getFields();
        fields = fields.stream().filter(StaticCapable::isStatic).toList();
        fields.forEach(f -> {
            AnnotationSource<?> as = f.getAnnotation(Metadata.class);
            if (as != null) {
                String name = f.getStringInitializer();
                String javaType = as.getStringValue("javaType");
                String defaultValue = as.getStringValue("defaultValue");
                String desc = as.getStringValue("description");
                String label = as.getStringValue("label");
                boolean secret = "true".equals(as.getStringValue("secret"));
                boolean required = "true".equals(as.getStringValue("required"));
                boolean deprecated =
                        clazz.getAnnotation(Deprecated.class) != null || f.getAnnotation(Deprecated.class) != null;
                JBangModel.JBangOptionModel model = new JBangModel.JBangOptionModel();
                model.setName(name);
                model.setJavaType(javaType);
                model.setDescription(JavadocHelper.sanitizeDescription(desc, false));
                model.setLabel(label);
                model.setSourceType(null);
                model.setDeprecated(deprecated);
                model.setSecret(secret);
                model.setRequired(required);
                List<String> enums = null;
                String text = as.getStringValue("enums");
                if (text != null) {
                    enums = Arrays.asList(text.split(","));
                }
                model.setEnums(enums);
                String type = MojoHelper.getType(javaType, enums != null && !enums.isEmpty(), false);
                model.setType(type);
                model.setDefaultValue(asDefaultValue(type, defaultValue));
                answer.add(model);
            }
        });

        return answer;
    }

    private static Object asDefaultValue(String type, String defaultValue) {
        if (defaultValue != null) {
            if ("boolean".equals(type)) {
                return Boolean.parseBoolean(defaultValue);
            } else if ("integer".equals(type)) {
                return Integer.parseInt(defaultValue);
            }
        }
        if (defaultValue == null && "boolean".equals(type)) {
            return "false";
        }
        return defaultValue;
    }

    @Override
    public void execute(MavenProject project) throws MojoFailureException, MojoExecutionException {
        outFolder = new File(project.getBasedir(), "src/generated/resources");
        super.execute(project);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // scan for configuration files
        File[] files = new File(project.getBasedir(), "src/main/java/org/apache/camel/dsl/jbang/core/common")
                .listFiles(f -> f.isFile() && f.getName().endsWith("Constants.java"));
        if (files == null || files.length == 0) {
            return;
        }

        final List<JBangModel.JBangOptionModel> data = new ArrayList<>();

        for (File file : files) {
            getLog().info("Parsing Camel JBang configuration file: " + file);
            try {
                List<JBangModel.JBangOptionModel> model = parseConfigurationSource(file);
                data.addAll(model);
            } catch (Exception e) {
                throw new MojoFailureException("Error parsing file " + file + " due " + e.getMessage(), e);
            }
        }

        // lets sort so they are always ordered
        data.sort((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));

        if (!data.isEmpty()) {
            JBangModel model = new JBangModel();
            model.getOptions().addAll(data);
            model.getGroups().add(new JBangModel.JBangGroupModel("camel.jbang", "Camel JBang configurations", null));
            String json = JsonMapper.createJsonSchema(model);

            updateResource(outFolder.toPath(), "META-INF/camel-jbang-configuration-metadata.json", json);
        }
    }
}
