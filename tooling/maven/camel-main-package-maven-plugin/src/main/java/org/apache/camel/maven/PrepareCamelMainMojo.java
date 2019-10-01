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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.main.parser.ConfigurationModel;
import org.apache.camel.main.parser.MainConfigurationParser;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

import static org.apache.camel.maven.PrepareHelper.sanitizeDescription;
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
                String prefix;
                if (file.getName().contains("Hystrix")) {
                    prefix = "camel.hystrix.";
                } else if (file.getName().contains("Rest")) {
                    prefix = "camel.rest.";
                } else {
                    prefix = "camel.main.";
                }
                final String namePrefix = prefix;
                model.stream().forEach(m -> m.setName(namePrefix + m.getName()));
                data.addAll(model);
            } catch (Exception e) {
                throw new MojoFailureException("Error parsing file " + file + " due " + e.getMessage(), e);
            }
        }

        // include additional rest configuration from camel-api
        File restConfig = new File(buildDir, "../../camel-api/src/main/java/org/apache/camel/spi/RestConfiguration.java");
        try {
            List<ConfigurationModel> model = parser.parseConfigurationSource(restConfig);
            model.stream().forEach(m -> m.setName("camel.rest." + m.getName()));
            data.addAll(model);
        } catch (Exception e) {
            throw new MojoFailureException("Error parsing file " + restConfig + " due " + e.getMessage(), e);
        }

        // lets sort so they are always ordered (but camel.main in top)
        data.sort((o1, o2) -> {
            if (o1.getName().startsWith("camel.main.") && !o2.getName().startsWith("camel.main.")) {
                return -1;
            } else if (!o1.getName().startsWith("camel.main.") && o2.getName().startsWith("camel.main.")) {
                return 1;
            } else {
                return o1.getName().compareToIgnoreCase(o2.getName());
            }
        });

        if (!data.isEmpty()) {
            List list = new ArrayList();
            for (int i = 0; i < data.size(); i++) {
                ConfigurationModel row = data.get(i);
                String name = camelCaseToDash(row.getName());
                String javaType = row.getJavaType();
                String desc = sanitizeDescription(row.getDescription(), false);
                String sourceType = row.getSourceType();
                String defaultValue = row.getDefaultValue();

                Map p = new LinkedHashMap();
                p.put("name", name);
                p.put("type", javaType);
                p.put("sourceType", sourceType);
                p.put("description", desc);
                if (defaultValue != null) {
                    p.put("defaultValue", defaultValue);
                }
                if (row.isDeprecated()) {
                    p.put("deprecated", true);
                    p.put("deprecation", Collections.EMPTY_MAP);
                }
                list.add(p);
            }

            List groups = new ArrayList();
            Map group1 = new LinkedHashMap();
            group1.put("name", "camel.main");
            group1.put("description", "camel-main configurations.");
            group1.put("sourceType", "org.apache.camel.main.DefaultConfigurationProperties");
            Map group2 = new LinkedHashMap();
            group2.put("name", "camel.hystrix");
            group2.put("description", "camel-hystrix configurations.");
            group2.put("sourceType", "org.apache.camel.main.HystrixConfigurationProperties");
            Map group3 = new LinkedHashMap();
            group3.put("name", "camel.rest");
            group3.put("description", "camel-rest configurations.");
            group3.put("sourceType", "org.apache.camel.spi.RestConfiguration");
            groups.add(group1);
            groups.add(group2);
            groups.add(group3);

            Map map = new LinkedHashMap();
            map.put("groups", groups);
            map.put("properties", list);

            String json = Jsoner.serialize(map);
            json = Jsoner.prettyPrint(json);

            outFolder.mkdirs();
            File file = new File(outFolder, "camel-main-configuration-metadata.json");
            updateResource(file.toPath(), json.getBytes());
        }
    }

    private void updateResource(Path path, byte[] newdata) throws MojoFailureException {
        try {
            byte[] olddata = new byte[0];
            if (Files.exists(path) && Files.isReadable(path)) {
                olddata = Files.readAllBytes(path);
            }
            if (!Arrays.equals(olddata, newdata)) {
                Files.write(path, newdata, StandardOpenOption.WRITE,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }
            getLog().info("Created file: " + path);
        } catch (Throwable e) {
            throw new MojoFailureException("Cannot write to file " + path + " due " + e.getMessage(), e);
        }
    }

}
