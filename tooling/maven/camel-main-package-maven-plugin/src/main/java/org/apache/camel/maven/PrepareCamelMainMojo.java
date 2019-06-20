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
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.main.parser.ConfigurationModel;
import org.apache.camel.main.parser.MainConfigurationParser;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import static org.apache.camel.util.StringHelper.camelCaseToDash;

/**
 * Prepares camel-main by generating Camel Main configuration metadata for tooling support.
 */
@Mojo(name = "prepare-main", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class PrepareCamelMainMojo extends AbstractMojo {

    /**
     * The output directory for generated spring boot tooling file
     */
    @Parameter(readonly = true, defaultValue = "${project.build.directory}/../src/main/resources/META-INF/")
    protected File outFolder;

    /**
     * The build directory
     */
    @Parameter(readonly = true, defaultValue = "${project.build.directory}/")
    protected File buildDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        final List<ConfigurationModel> data = new ArrayList<>();

        MainConfigurationParser parser = new MainConfigurationParser();

        // scan for configuration files
        File[] files = new File(buildDir, "../src/main/java/org/apache/camel/main").listFiles(f ->
                f.isFile() && f.getName().endsWith("Properties.java"));

        for (File file : files) {
            getLog().info("Parsing Camel Main configuration file: " + file);
            try {
                List<ConfigurationModel> model = parser.parseConfigurationSource(file);
                // compute prefix for name
                String prefix = "camel.main.";
                if (file.getName().contains("Hystrix")) {
                    prefix += "hystrix.";
                } else if (file.getName().contains("Rest")) {
                    prefix += "rest.";
                }
                final String namePrefix = prefix;
                model.stream().forEach(m -> m.setName(namePrefix + m.getName()));
                data.addAll(model);
            } catch (Exception e) {
                throw new MojoFailureException("Error parsing file " + file + " due " + e.getMessage(), e);
            }
        }

        // lets sort so they are always ordered
        data.sort((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));

        if (!data.isEmpty()) {
            StringBuilder sb = new StringBuilder();

            sb.append("{\n");
            sb.append("  \"properties\": [\n");
            for (int i = 0; i < data.size(); i++) {
                ConfigurationModel row = data.get(i);
                String name = camelCaseToDash(row.getName());
                String javaType = springBootJavaType(row.getJavaType());
                String desc = row.getDescription();
                String defaultValue = row.getDefaultValue();
                sb.append("    {\n");
                sb.append("      \"name\": \"" + name + "\",\n");
                sb.append("      \"type\": \"" + javaType + "\",\n");
                sb.append("      \"description\": \"" + desc + "\"");
                if (defaultValue != null) {
                    sb.append(",\n");
                    if (springBootDefaultValueQuotes(javaType)) {
                        sb.append("      \"defaultValue\": \"" + defaultValue + "\"\n");
                    } else {
                        sb.append("      \"defaultValue\": " + defaultValue + "\n");
                    }
                } else {
                    sb.append("\n");
                }
                if (i < data.size() - 1) {
                    sb.append("    },\n");
                } else {
                    sb.append("    }\n");
                }
            }
            sb.append("  ]\n");
            sb.append("}\n");

            outFolder.mkdirs();
            File file = new File(outFolder, "camel-main-configuration-metadata.json");
            try {
                FileOutputStream fos = new FileOutputStream(file, false);
                fos.write(sb.toString().getBytes());
                fos.close();
                getLog().info("Created file: " + file);
            } catch (Throwable e) {
                throw new MojoFailureException("Cannot write to file " + file + " due " + e.getMessage(), e);
            }
        }
    }

    private static String springBootJavaType(String javaType) {
        if ("boolean".equalsIgnoreCase(javaType)) {
            return "java.lang.Boolean";
        } else if ("int".equalsIgnoreCase(javaType)) {
            return "java.lang.Integer";
        } else if ("long".equalsIgnoreCase(javaType)) {
            return "java.lang.Long";
        } else if ("string".equalsIgnoreCase(javaType)) {
            return "java.lang.String";
        }
        return javaType;
    }

    private static boolean springBootDefaultValueQuotes(String javaType) {
        if ("java.lang.Boolean".equalsIgnoreCase(javaType)) {
            return false;
        } else if ("java.lang.Integer".equalsIgnoreCase(javaType)) {
            return false;
        } else if ("java.lang.Long".equalsIgnoreCase(javaType)) {
            return false;
        }
        return true;
    }
}
