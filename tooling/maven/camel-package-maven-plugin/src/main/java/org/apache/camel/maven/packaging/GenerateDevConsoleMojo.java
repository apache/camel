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

import static org.apache.camel.maven.packaging.MojoHelper.annotationValue;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

import javax.inject.Inject;

import org.apache.camel.maven.packaging.generics.PackagePluginUtils;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;

/**
 * Factory for generating metadata for @DevConsole.
 *
 * This mojo will only generate json metadata with details of the dev consoles. The general spi-generator will generate
 * the marker files
 */
@Mojo(
        name = "generate-dev-console",
        threadSafe = true,
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
        requiresDependencyCollection = ResolutionScope.COMPILE,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateDevConsoleMojo extends AbstractGeneratorMojo {

    public static final DotName DEV_CONSOLE_ANNOTATION =
            DotName.createSimple("org.apache.camel.spi.annotations.DevConsole");

    /**
     * The project build directory
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    @Inject
    public GenerateDevConsoleMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    private static class DevConsoleModel {
        private String className;
        private String group;
        private String name;
        private String displayName;
        private String description;
        private boolean deprecated;

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getGroup() {
            return group;
        }

        public void setGroup(String group) {
            this.group = group;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isDeprecated() {
            return deprecated;
        }

        public void setDeprecated(boolean deprecated) {
            this.deprecated = deprecated;
        }
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if ("pom".equals(project.getPackaging())) {
            return;
        }

        buildDir = new File(project.getBuild().getDirectory());

        if (resourcesOutputDir == null) {
            resourcesOutputDir = new File(project.getBasedir(), "src/generated/resources");
        }

        Index index = PackagePluginUtils.readJandexIndexIgnoreMissing(project, getLog());
        if (index == null) {
            return;
        }

        List<DevConsoleModel> models = new ArrayList<>();
        List<AnnotationInstance> annotations = index.getAnnotations(DEV_CONSOLE_ANNOTATION);
        annotations.forEach(a -> {
            DevConsoleModel model = new DevConsoleModel();

            String currentClass = a.target().asClass().name().toString();
            boolean deprecated = a.target().asClass().hasAnnotation(Deprecated.class)
                    || project.getName().contains("(deprecated)");
            model.setClassName(currentClass);
            model.setDeprecated(deprecated);
            model.setGroup(annotationValue(a, "group"));
            model.setName(annotationValue(a, "name"));
            model.setDisplayName(annotationValue(a, "displayName"));
            model.setDescription(annotationValue(a, "description"));
            // skip default registry
            boolean skip = "default-registry".equals(model.getName());
            if (!skip) {
                models.add(model);
            }
        });
        models.sort(Comparator.comparing(DevConsoleModel::getName));

        // remove default-registry as it's special

        if (!models.isEmpty()) {
            try {
                StringJoiner ids = new StringJoiner(" ");
                for (var model : models) {
                    ids.add(model.getName());

                    JsonObject jo = asJsonObject(model);
                    String json = jo.toJson();
                    json = Jsoner.prettyPrint(json, 2);
                    String fn = sanitizeFileName(model.getName()) + PackageHelper.JSON_SUFIX;
                    boolean updated = updateResource(
                            resourcesOutputDir.toPath(), "META-INF/org/apache/camel/dev-console/" + fn, json + NL);
                    if (updated) {
                        getLog().info("Updated dev-console json: " + model.getName());
                    }
                }

                // generate marker file
                File camelMetaDir = new File(resourcesOutputDir, "META-INF/services/org/apache/camel/");
                int count = models.size();
                String properties = createProperties(project, "dev-consoles", ids.toString());
                updateResource(camelMetaDir.toPath(), "dev-consoles.properties", properties);
                getLog().info("Generated dev-consoles.properties containing " + count + " Camel "
                        + (count > 1 ? "consoles: " : "console: ") + ids);
            } catch (Exception e) {
                throw new MojoExecutionException(e);
            }
        }
    }

    private JsonObject asJsonObject(DevConsoleModel model) {
        JsonObject jo = new JsonObject();
        // we need to know the maven GAV also
        jo.put("kind", "console");
        if (model.group != null) {
            jo.put("group", model.getGroup());
        } else {
            jo.put("group", "camel");
        }
        jo.put("name", model.getName());
        if (model.getDisplayName() != null) {
            jo.put("title", asTitle(model.getDisplayName()));
        } else {
            jo.put("title", asTitle(model.getName()));
        }
        jo.put("description", model.getDescription());
        jo.put("deprecated", model.isDeprecated());
        jo.put("javaType", model.getClassName());
        jo.put("groupId", project.getGroupId());
        jo.put("artifactId", project.getArtifactId());
        jo.put("version", project.getVersion());
        JsonObject root = new JsonObject();
        root.put("console", jo);
        return root;
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9+-/]", "-");
    }

    private String asTitle(String name) {
        name = Strings.camelDashToTitle(name);
        String part = Strings.after(name, ":");
        if (part != null) {
            part = Strings.capitalize(part);
            name = Strings.before(name, ":") + " (" + part + ")";
        }
        return name;
    }
}
