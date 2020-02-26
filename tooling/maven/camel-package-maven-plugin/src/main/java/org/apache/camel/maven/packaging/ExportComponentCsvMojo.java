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
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.apache.camel.tooling.model.BaseArtifactModel;
import org.apache.camel.tooling.model.ComponentModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Exports the list of components, languages, data formats and others to a CSV file.
 */
@Mojo(name = "export-component-csv", threadSafe = true)
public class ExportComponentCsvMojo extends AbstractMojo {

    /**
     * The maven project.
     */
    @Parameter(property = "project", required = true, readonly = true)
    protected MavenProject project;

    /**
     * The base directory of Camel catalog
     */
    @Parameter(defaultValue = "${project.build.directory}/classes/org/apache/camel/catalog")
    protected File catalogDir;

    /**
     * The base directory of Quarkus catalog
     */
    @Parameter(property = "camel.quarkus.catalog.dir")
    protected File quarkusCatalogDir;

    /**
     * The website doc for components
     */
    @Parameter(defaultValue = "${project.build.directory}/camel-components.csv")
    protected File outputFile;

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the
     *             threads it generated failed.
     * @throws MojoFailureException something bad happened...
     */
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try (Writer out = Files.newBufferedWriter(outputFile.toPath(), StandardCharsets.UTF_8)) {
            out.write("Name\tScheme\tartifactId\tKind\tDeprecated\tLabel\tGroup\tQuarkus\n");
            for (Kind kind : Kind.values()) {
                executeComponentsList(out, kind, this);
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Could not write to " + outputFile);
        }
    }

    static void executeComponentsList(Writer out, Kind kind, ExportComponentCsvMojo mojo) throws MojoExecutionException, MojoFailureException {
        final List<BaseArtifactModel<?>> models = new ArrayList<>();
        {
            Set<File> componentFiles = new TreeSet<>();
            final File dir = mojo.catalogDir.toPath().resolve(kind.name()).toFile();
            File[] files = dir.listFiles();
            componentFiles.addAll(Arrays.asList(files));
            for (File file : componentFiles) {
                String json;
                try {
                    json = PackageHelper.loadText(file);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                final BaseArtifactModel<?> model = kind.parse(json);

                if (model instanceof ComponentModel) {
                    ComponentModel componentModel = (ComponentModel) model;
                    // filter out alternative schemas which reuses documentation
                    boolean add = true;
                    if (!Strings.isNullOrEmpty(componentModel.getAlternativeSchemes())) {
                        String first = componentModel.getAlternativeSchemes().split(",")[0];
                        if (!componentModel.getScheme().equals(first)) {
                            add = false;
                        }
                    }
                    if (add) {
                        componentModel.setName(componentModel.getScheme());
                        models.add(model);
                    }
                } else {
                    models.add(model);
                }
            }
        }

        final Set<String> quarkusComponentFiles = new HashSet<>();
        if (mojo.quarkusCatalogDir != null && Files.exists(mojo.quarkusCatalogDir.toPath())) {
            final File dir = mojo.quarkusCatalogDir.toPath().resolve(kind.name()).toFile();
            if (dir != null && dir.isDirectory()) {
                File[] files = dir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        try {
                            quarkusComponentFiles.add(PackageHelper.loadText(file));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }

        try {

            // sort the models
            models.sort((m1, m2) -> m1.getTitle().compareToIgnoreCase(m2.getTitle()));

            for (BaseArtifactModel<?> model : models) {
                final String name = model.getName();
                final String label = model.getLabel();
                out.write(model.getTitle());
                out.write('\t');
                out.write(name);
                out.write('\t');
                out.write(model.getArtifactId());
                out.write('\t');
                out.write(model.getKind());
                out.write('\t');
                out.write(String.valueOf(model.isDeprecated()));
                out.write('\t');
                out.write(model.getLabel() != null ? model.getLabel() : "");
                out.write('\t');
                out.write(primaryGroup(kind, label, name));
                out.write('\t');
                out.write(isSupportedOnQuarkus(quarkusComponentFiles, kind.getNameFieldName(), name));
                out.write('\n');
            }
        } catch (IOException e) {
            throw new MojoFailureException("Error due " + e.getMessage(), e);
        }
    }

    static String isSupportedOnQuarkus(Set<String> quarkusComponentFiles, String fieldName, String name) {
        if (quarkusComponentFiles.isEmpty()) {
            return "";
        }
        final Optional<String> findFirst = quarkusComponentFiles.stream()
                .filter(json -> json.contains("\"" + fieldName + "\": \"" + name + "\""))
                .findFirst();
        return String.valueOf(findFirst.isPresent());
    }

    static String primaryGroup(Kind kind, String rawLabels, String name) {
        if (kind != Kind.components) {
            return kind.name();
        }
        if (name.startsWith("aws")) {
            return "aws";
        }
        if (name.startsWith("azure")) {
            return "azure";
        }
        if (name.startsWith("google")) {
            return "google";
        }
        if (name.startsWith("spring")) {
            return "spring";
        }
        if (name.startsWith("kubernetes") || name.startsWith("openshift") || name.startsWith("openstack") || name.startsWith("digitalocean")) {
            return "cloud";
        }
        if (rawLabels != null) {
            final Set<String> labels = new HashSet<>(Arrays.asList(rawLabels.split(",")));
            if (labels.contains("core")) {
                return "core";
            } else if (labels.contains("file") || labels.contains("document")) {
                return "file";
            } else if (labels.contains("http") || labels.contains("websocket")) {
                return "http";
            } else if (labels.contains("messaging")) {
                return "messaging";
            } else if (labels.contains("database") || labels.contains("nosql") || labels.contains("sql") || labels.contains("bigdata")) {
                return "database";
            } else if (labels.contains("clustering")) {
                return "clustering";
            } else if (labels.contains("monitoring")) {
                return "monitoring";
            } else if (labels.contains("api")) {
                return "api";
            } else if (labels.contains("cache")) {
                return "cache";
            } else if (labels.size() == 1) {
                return labels.iterator().next();
            }
        }
        return "";
    }

    enum Kind {
        components() {
            public String getNameFieldName() {
                return "scheme";
            }
            @Override
            public BaseArtifactModel<?> parse(String json) {
                return JsonMapper.generateComponentModel(json);
            }
        },
        languages() {
            @Override
            public BaseArtifactModel<?> parse(String json) {
                return JsonMapper.generateLanguageModel(json);
            }
        },
        dataformats() {
            @Override
            public BaseArtifactModel<?> parse(String json) {
                return JsonMapper.generateDataFormatModel(json);
            }
        },
        others() {
            @Override
            public BaseArtifactModel<?> parse(String json) {
                return JsonMapper.generateOtherModel(json);
            }
        };
        public String getNameFieldName() {
            return "name";
        }
        public abstract BaseArtifactModel<?> parse(String json);
    }

}
