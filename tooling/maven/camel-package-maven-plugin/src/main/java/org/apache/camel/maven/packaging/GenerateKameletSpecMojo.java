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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;

import org.apache.camel.maven.packaging.generics.PackagePluginUtils;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.AnnotationValue;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;

import static org.apache.camel.maven.packaging.MojoHelper.annotationArrayValue;
import static org.apache.camel.maven.packaging.MojoHelper.annotationValue;

/**
 * Factory for generating code for @KameletSpec.
 */
@Mojo(name = "generate-kamelet-spec", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyCollection = ResolutionScope.COMPILE,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateKameletSpecMojo extends AbstractGeneratorMojo {

    public static final DotName KAMELET_SPEC_ANNOTATION = DotName.createSimple("org.apache.camel.spi.KameletSpec");
    public static final DotName KAMELET_ICON_ANNOTATION = DotName.createSimple("org.apache.camel.spi.KameletIcon");

    /**
     * The project build directory
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    private static class KameletSpecModel {
        private String className;
        private String type;
        private String title;
        private String name;
        private String description;
        private String provider;
        private String supportLevel;
        private String namespace;
        private String[] dependencies;
        private String icon;
        private boolean deprecated;

        private final List<KameletPropertyModel> properties = new ArrayList<>();

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getSupportLevel() {
            return supportLevel;
        }

        public void setSupportLevel(String supportLevel) {
            this.supportLevel = supportLevel;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String[] getDependencies() {
            return dependencies;
        }

        public void setDependencies(String[] dependencies) {
            this.dependencies = dependencies;
        }

        public String getIcon() {
            return icon;
        }

        public void setIcon(String icon) {
            this.icon = icon;
        }

        public List<KameletPropertyModel> getProperties() {
            return properties;
        }

        public void addProperty(KameletPropertyModel property) {
            this.properties.add(property);
        }

        public boolean isDeprecated() {
            return deprecated;
        }

        public void setDeprecated(boolean deprecated) {
            this.deprecated = deprecated;
        }

        private static class KameletPropertyModel {
            private String title;
            private String type;
            private String name;
            private String description;
            private boolean required = false;
            private String defaultValue;
            private String example;
            private String[] enumeration;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public String getTitle() {
                return title;
            }

            public void setTitle(String title) {
                this.title = title;
            }

            public String getDescription() {
                return description;
            }

            public void setDescription(String description) {
                this.description = description;
            }

            public boolean isRequired() {
                return required;
            }

            public void setRequired(String required) {
                if (required == null) {
                    return;
                }

                this.required = Boolean.parseBoolean(required);
            }

            public String getDefaultValue() {
                return defaultValue;
            }

            public void setDefaultValue(String defaultValue) {
                this.defaultValue = defaultValue;
            }

            public String getExample() {
                return example;
            }

            public void setExample(String example) {
                this.example = example;
            }

            public String[] getEnumeration() {
                return enumeration;
            }

            public void setEnumeration(String[] enumeration) {
                this.enumeration = enumeration;
            }
        }
    }

    public GenerateKameletSpecMojo() {
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

        List<KameletSpecModel> models = new ArrayList<>();
        List<AnnotationInstance> annotations = index.getAnnotations(KAMELET_SPEC_ANNOTATION);
        annotations.forEach(a -> {
            KameletSpecModel model = new KameletSpecModel();

            String currentClass = a.target().asClass().name().toString();
            boolean deprecated
                    = a.target().asClass().hasAnnotation(Deprecated.class) || project.getName().contains("(deprecated)");
            model.setClassName(currentClass);
            model.setDeprecated(deprecated);
            model.setType(annotationValue(a, "type"));
            model.setName(annotationValue(a, "name"));
            model.setTitle(annotationValue(a, "title"));
            model.setDescription(annotationValue(a, "description"));
            model.setProvider(annotationValue(a, "provider", "Apache Software Foundation"));
            model.setSupportLevel(annotationValue(a, "supportLevel", "Preview"));
            model.setNamespace(annotationValue(a, "namespace"));
            model.setDependencies(annotationArrayValue(a, "dependencies"));

            AnnotationValue[] properties = (AnnotationValue[]) Optional.ofNullable(a.value("properties"))
                    .map(AnnotationValue::value)
                    .orElse(null);

            if (properties != null) {
                for (AnnotationValue pa : properties) {
                    KameletSpecModel.KameletPropertyModel propertyModel = new KameletSpecModel.KameletPropertyModel();
                    propertyModel.setName(annotationValue(pa.asNested(), "name"));
                    propertyModel.setTitle(annotationValue(pa.asNested(), "title"));
                    propertyModel.setDescription(annotationValue(pa.asNested(), "description"));
                    propertyModel.setType(annotationValue(pa.asNested(), "type", "string"));
                    propertyModel.setRequired(annotationValue(pa.asNested(), "required"));
                    propertyModel.setDefaultValue(annotationValue(pa.asNested(), "defaultValue"));
                    propertyModel.setEnumeration(annotationArrayValue(pa.asNested(), "enumeration"));
                    propertyModel.setExample(annotationValue(pa.asNested(), "example"));
                    model.addProperty(propertyModel);
                }
            }

            AnnotationInstance iconAnnotation = a.target().asClass().annotation(KAMELET_ICON_ANNOTATION);
            if (iconAnnotation != null) {
                model.setIcon(annotationValue(iconAnnotation, "data"));
            }

            models.add(model);
        });
        models.sort(Comparator.comparing(KameletSpecModel::getName));

        if (!models.isEmpty()) {
            try {
                StringJoiner names = new StringJoiner(" ");
                for (var model : models) {
                    names.add(model.getName());

                    JsonObject jo = asJsonObject(model);
                    String json = jo.toJson();
                    json = Jsoner.prettyPrint(json, 2);
                    String fn = sanitizeFileName(model.getName()) + PackageHelper.JSON_SUFIX;
                    boolean updated = updateResource(resourcesOutputDir.toPath(),
                            "META-INF/services/org/apache/camel/kamelet/" + fn,
                            json + NL);
                    if (updated) {
                        getLog().info("Updated kamelet json: " + model.getName());
                    }
                }

                // generate marker file
                File camelMetaDir = new File(resourcesOutputDir, "META-INF/services/org/apache/camel/");
                int count = models.size();
                String properties = createProperties(project, "kamelets", names.toString());
                updateResource(camelMetaDir.toPath(), "kamelet.properties", properties);
                getLog().info("Generated kamelet.properties containing " + count + " Camel "
                              + (count > 1 ? "kamelets: " : "kamelet: ") + names);
            } catch (Exception e) {
                throw new MojoExecutionException(e);
            }
        }
    }

    private JsonObject asJsonObject(KameletSpecModel model) {
        JsonObject jo = new JsonObject();
        // we need to know the maven GAV also
        jo.put("kind", "kamelet");
        jo.put("type", model.getType());
        jo.put("name", model.getName());
        if (model.getTitle() != null) {
            jo.put("title", model.getTitle());
        } else {
            jo.put("title", asTitle(model.getName()));
        }
        if (model.getDescription() != null) {
            jo.put("description", model.getDescription());
        }
        jo.put("provider", model.getProvider());
        jo.put("supportLevel", model.getSupportLevel());
        if (model.getNamespace() != null) {
            jo.put("namespace", model.getNamespace());
        }

        if (model.getDependencies() != null) {
            jo.put("dependencies", String.join(",", model.getDependencies()));
        }

        if (model.getIcon() != null) {
            jo.put("icon", model.getIcon());
        }

        jo.put("deprecated", model.isDeprecated());
        jo.put("javaType", model.getClassName());

        if (!model.getProperties().isEmpty()) {
            JsonArray propertyArray = new JsonArray();

            for (KameletSpecModel.KameletPropertyModel propertyModel : model.getProperties()) {
                JsonObject po = new JsonObject();
                po.put("name", propertyModel.getName());
                if (propertyModel.getTitle() != null) {
                    po.put("title", propertyModel.getTitle());
                } else {
                    po.put("title", asTitle(propertyModel.getName()));
                }
                if (propertyModel.getDescription() != null) {
                    po.put("description", propertyModel.getDescription());
                }
                po.put("type", propertyModel.getType());
                po.put("required", propertyModel.isRequired());
                if (propertyModel.getDefaultValue() != null) {
                    po.put("defaultValue", propertyModel.getDefaultValue());
                }
                if (propertyModel.getExample() != null) {
                    po.put("example", propertyModel.getExample());
                }
                if (propertyModel.getEnumeration() != null) {
                    po.put("enumeration", String.join(",", propertyModel.getEnumeration()));
                }

                propertyArray.add(po);
            }

            jo.put("properties", propertyArray);
        }
        jo.put("groupId", project.getGroupId());
        jo.put("artifactId", project.getArtifactId());
        jo.put("version", project.getVersion());
        JsonObject root = new JsonObject();
        root.put("kamelet", jo);
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
