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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.catalog.JSonSchemaHelper;
import org.apache.camel.util.IOHelper;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import static org.apache.camel.util.StringHelper.camelCaseToDash;

/**
 * Pre scans your project and builds spring boot tooling metafiles which fools tools to
 * offer code completion for editing properties files.
 */
@Mojo(name = "spring-boot-tooling", defaultPhase = LifecyclePhase.PROCESS_CLASSES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class SpringBootToolingMojo extends AbstractMainMojo {

    /**
     * The output directory for generated spring boot tooling file
     */
    @Parameter(readonly = true, defaultValue = "${project.build.directory}/../src/main/resources/META-INF/")
    protected File outFolder;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        // perform common tasks
        super.execute();

        // load camel-main metadata
        String mainJson = loadCamelMainConfigurationMetadata();
        if (mainJson == null) {
            getLog().warn("Cannot load camel-main-configuration-metadata.json from within the camel-main JAR from the classpath."
                    + " Not possible to build spring boot configuration file for this project");
            return;
        }

        List<String[]> componentData = new ArrayList<>();
        for (String componentName : camelComponentsOnClasspath) {
            String json = catalog.componentJSonSchema(componentName);
            if (json == null) {
                getLog().debug("Cannot find component JSon metadata for component: " + componentName);
                continue;
            }

            List<Map<String, String>> rows = JSonSchemaHelper.parseJsonSchema("componentProperties", json, true);
            Set<String> names = JSonSchemaHelper.getNames(rows);
            for (String name : names) {
                Map<String, String> row = JSonSchemaHelper.getRow(rows, name);
                String javaType = springBootJavaType(safeJavaType(row.get("javaType")));
                String desc = row.get("description");
                String defaultValue = row.get("defaultValue");
                // we want to use dash in the name
                String dash = camelCaseToDash(name);
                String key = "camel.component." + componentName + "." + dash;
                componentData.add(new String[]{key, javaType, desc, defaultValue});
            }
        }

        if (!componentData.isEmpty()) {
            StringBuilder sb = new StringBuilder();

            for (int i = 0; i < componentData.size(); i++) {
                String[] row = componentData.get(i);
                String name = row[0];
                String javaType = row[1];
                String desc = row[2];
                String defaultValue = row[3];
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
                if (i < componentData.size() - 1) {
                    sb.append("    },\n");
                } else {
                    sb.append("    }\n");
                }
            }
            sb.append("  ]\n");
            sb.append("}\n");

            // okay then add the components into the main json at the end so they get merged together
            int pos = mainJson.lastIndexOf("    }");
            String newJson = mainJson.substring(0, pos);
            newJson = newJson + "    },\n";
            newJson = newJson + sb.toString();

            outFolder.mkdirs();
            File file = new File(outFolder, "spring-configuration-metadata.json");
            try {
                FileOutputStream fos = new FileOutputStream(file, false);
                fos.write(newJson.getBytes());
                IOHelper.close(fos);
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

    protected String loadCamelMainConfigurationMetadata() throws MojoFailureException {
        try {
            InputStream is = classLoader.getResourceAsStream("META-INF/camel-main-configuration-metadata.json");
            String text = IOHelper.loadText(is);
            IOHelper.close(is);
            return text;
        } catch (Throwable e) {
            throw new MojoFailureException("Error during discovering camel-main from classpath due " + e.getMessage(), e);
        }
    }
}
