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
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;

import javax.inject.Inject;

import org.apache.camel.maven.packaging.generics.PackagePluginUtils;
import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.DevConsoleModel.DevConsoleOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
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

import static org.apache.camel.maven.packaging.MojoHelper.annotationValue;

/**
 * Factory for generating metadata for @DevConsole.
 *
 * This mojo will only generate json metadata with details of the dev consoles. The general spi-generator will generate
 * the marker files
 */
@Mojo(name = "generate-dev-console", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyCollection = ResolutionScope.COMPILE,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateDevConsoleMojo extends AbstractGeneratorMojo {

    public static final DotName DEV_CONSOLE_ANNOTATION = DotName.createSimple("org.apache.camel.spi.annotations.DevConsole");

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
        private final List<DevConsoleOptionModel> options = new ArrayList<>();

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

        public List<DevConsoleOptionModel> getOptions() {
            return options;
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
            boolean deprecated
                    = a.target().asClass().hasAnnotation(Deprecated.class) || project.getName().contains("(deprecated)");
            model.setClassName(currentClass);
            model.setDeprecated(deprecated);
            model.setGroup(annotationValue(a, "group"));
            model.setName(annotationValue(a, "name"));
            model.setDisplayName(annotationValue(a, "displayName"));
            model.setDescription(annotationValue(a, "description"));
            // skip default registry
            boolean skip = "default-registry".equals(model.getName());
            if (!skip) {
                extractQueryOptions(model);
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
                    boolean updated = updateResource(resourcesOutputDir.toPath(),
                            "META-INF/org/apache/camel/dev-console/" + fn,
                            json + NL);
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
        if (!model.getOptions().isEmpty()) {
            root.put("options", JsonMapper.asJsonObject(model.getOptions()));
        }
        return root;
    }

    private void extractQueryOptions(DevConsoleModel model) {
        Class<?> clazz;
        try {
            clazz = loadClass(model.getClassName());
        } catch (NoClassDefFoundError e) {
            getLog().debug("Cannot load class: " + model.getClassName());
            return;
        }
        for (Field field : clazz.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !Modifier.isFinal(field.getModifiers())
                    || field.getType() != String.class) {
                continue;
            }
            if (!field.isAnnotationPresent(Metadata.class)) {
                continue;
            }
            Metadata metadata = field.getAnnotation(Metadata.class);
            String labels = metadata.label();
            if (labels == null || !labels.contains("query")) {
                continue;
            }
            String optionName;
            try {
                field.setAccessible(true);
                optionName = (String) field.get(null);
            } catch (IllegalAccessException e) {
                getLog().debug("Cannot read field value: " + field.getName());
                continue;
            }
            DevConsoleOptionModel o = new DevConsoleOptionModel();
            o.setName(optionName);
            o.setKind("option");
            o.setGroup("query");
            o.setLabel(labels);
            o.setRequired(metadata.required());
            o.setDescription(metadata.description());
            o.setDeprecated(field.isAnnotationPresent(Deprecated.class));
            if (!metadata.deprecationNote().isEmpty()) {
                o.setDeprecationNote(metadata.deprecationNote());
            }
            if (!metadata.displayName().isEmpty()) {
                o.setDisplayName(metadata.displayName());
            } else {
                o.setDisplayName(Strings.asTitle(optionName));
            }
            String javaType = metadata.javaType();
            if (!javaType.isEmpty()) {
                o.setJavaType(javaType);
                o.setType(javaTypeToType(javaType));
            } else {
                o.setJavaType("java.lang.String");
                o.setType("string");
            }
            if (!metadata.defaultValue().isEmpty()) {
                o.setDefaultValue(resolveDefaultValue(metadata.defaultValue(), o.getType()));
            }
            if (!metadata.enums().isEmpty()) {
                o.setEnums(List.of(metadata.enums().split(",")));
            }
            model.getOptions().add(o);
        }
        model.getOptions().sort(Comparator.comparing(BaseOptionModel::getName));
    }

    private static String javaTypeToType(String javaType) {
        return switch (javaType) {
            case "java.lang.String" -> "string";
            case "java.lang.Integer", "int" -> "integer";
            case "java.lang.Long", "long" -> "integer";
            case "java.lang.Boolean", "boolean" -> "boolean";
            case "java.lang.Double", "double", "java.lang.Float", "float" -> "number";
            default -> "object";
        };
    }

    private static Object resolveDefaultValue(String defaultValue, String type) {
        if (defaultValue == null || defaultValue.isEmpty()) {
            return null;
        }
        return switch (type) {
            case "boolean" -> Boolean.parseBoolean(defaultValue);
            case "integer" -> {
                try {
                    yield Long.parseLong(defaultValue);
                } catch (NumberFormatException e) {
                    yield defaultValue;
                }
            }
            case "number" -> {
                try {
                    yield Double.parseDouble(defaultValue);
                } catch (NumberFormatException e) {
                    yield defaultValue;
                }
            }
            default -> defaultValue;
        };
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
