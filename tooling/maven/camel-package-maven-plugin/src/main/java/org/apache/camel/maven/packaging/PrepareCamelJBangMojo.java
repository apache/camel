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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
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

/**
 * Prepares camel-jbang by generating Camel CLI configuration metadata for tooling support.
 */
@Mojo(name = "prepare-jbang", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class PrepareCamelJBangMojo extends AbstractGeneratorMojo {

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File outFolder;

    /**
     * The directory with the Camel CLI configuration classes (the *Constants.java files), which are in camel-jbang-run.
     */
    @Parameter(defaultValue = "${project.basedir}/../" + CONFIGURATION_DIR)
    protected File configurationDir;

    private static final String CONFIGURATION_DIR
            = "camel-jbang-run/src/main/java/org/apache/camel/dsl/jbang/run/common";

    @Inject
    public PrepareCamelJBangMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    /**
     * Reads the Camel CLI configuration of a compiled configuration class: its static fields with {@code @Metadata}.
     * The compiler has resolved the values (a description written as string literals joined with {@code +} is one
     * text), so the source is not parsed.
     */
    public static List<JBangModel.JBangOptionModel> parseConfigurationClass(Class<?> clazz) {
        final List<JBangModel.JBangOptionModel> answer = new ArrayList<>();
        for (Field f : clazz.getDeclaredFields()) {
            Metadata as = f.getAnnotation(Metadata.class);
            if (as == null || !Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) {
                continue;
            }
            String name;
            try {
                f.trySetAccessible();
                name = (String) f.get(null);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read " + clazz.getName() + "." + f.getName(), e);
            }
            String javaType = text(as.javaType());
            String defaultValue = text(as.defaultValue());
            String security = text(as.security());
            boolean secret = as.secret() || "secret".equals(security);
            boolean deprecated = clazz.isAnnotationPresent(Deprecated.class) || f.isAnnotationPresent(Deprecated.class);
            JBangModel.JBangOptionModel model = new JBangModel.JBangOptionModel();
            model.setName(name);
            model.setJavaType(javaType);
            model.setDescription(JavadocHelper.sanitizeDescription(text(as.description()), false));
            model.setLabel(text(as.label()));
            model.setSourceType(null);
            model.setDeprecated(deprecated);
            model.setSecret(secret);
            model.setSecurity(security);
            model.setRequired(as.required());
            String enumText = text(as.enums());
            List<String> enums = enumText != null ? Arrays.asList(enumText.split(",")) : null;
            model.setEnums(enums);
            String type = MojoHelper.getType(javaType, enums != null && !enums.isEmpty(), false);
            model.setType(type);
            model.setDefaultValue(asDefaultValue(type, defaultValue));
            answer.add(model);
        }
        return answer;
    }

    // an annotation attribute that is not set is an empty text
    private static String text(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /** The class of a configuration source file: its path under src/main/java. */
    static String className(File file) {
        String path = file.getPath().replace(File.separatorChar, '/');
        int root = path.indexOf("src/main/java/");
        String relative = root >= 0 ? path.substring(root + "src/main/java/".length()) : file.getName();
        return relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
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
        configurationDir = new File(project.getBasedir().getParentFile(), CONFIGURATION_DIR);
        super.execute(project);
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // scan for configuration files
        File[] files = configurationDir.listFiles(f -> f.isFile() && f.getName().endsWith("Constants.java"));
        if (files == null || files.length == 0) {
            throw new MojoFailureException("No Camel CLI configuration (*Constants.java) found in " + configurationDir);
        }

        final List<JBangModel.JBangOptionModel> data = new ArrayList<>();

        for (File file : files) {
            // the compiled class of the source (camel-jbang-run is a dependency of the project)
            String className = className(file);
            getLog().info("Reading Camel CLI configuration class: " + className);
            try {
                data.addAll(parseConfigurationClass(getProjectClassLoader().loadClass(className)));
            } catch (Exception | LinkageError e) {
                throw new MojoFailureException("Error reading class " + className + " due " + e.getMessage(), e);
            }
        }

        // lets sort so they are always ordered
        data.sort((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));

        if (!data.isEmpty()) {
            JBangModel model = new JBangModel();
            model.getOptions().addAll(data);
            model.getGroups().add(new JBangModel.JBangGroupModel(
                    "camel.jbang", "Camel CLI configurations", null));
            String json = JsonMapper.createJsonSchema(model);

            updateResource(outFolder.toPath(), "META-INF/camel-jbang-configuration-metadata.json", json);
        }
    }

}
