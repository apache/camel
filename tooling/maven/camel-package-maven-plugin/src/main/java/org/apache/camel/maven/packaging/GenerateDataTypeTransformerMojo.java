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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import javax.inject.Inject;

import org.apache.camel.maven.packaging.generics.PackagePluginUtils;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.tooling.model.BodyTypeModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.model.TransformerModel;
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
 * Factory for generating code for @DataTypeTransformer.
 */
@Mojo(name = "generate-data-type-transformer", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyCollection = ResolutionScope.COMPILE,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateDataTypeTransformerMojo extends AbstractGeneratorMojo {

    public static final DotName DATA_TYPE_ANNOTATION = DotName.createSimple("org.apache.camel.spi.DataTypeTransformer");

    /**
     * The project build directory
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    @Inject
    public GenerateDataTypeTransformerMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    static class DataTypeTransformerModel {
        private String className;
        private String name;
        private String from;
        private String to;
        private String description;
        private boolean deprecated;
        private Map<String, BodyTypeModel> bodyTypes = new LinkedHashMap<>();

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

        public String getFrom() {
            return from;
        }

        public void setFrom(String from) {
            this.from = from;
        }

        public String getTo() {
            return to;
        }

        public void setTo(String to) {
            this.to = to;
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

        public Map<String, BodyTypeModel> getBodyTypes() {
            return bodyTypes;
        }

        public void setBodyTypes(Map<String, BodyTypeModel> bodyTypes) {
            this.bodyTypes = bodyTypes;
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

        Index index = PackagePluginUtils.readJandexIndexIgnoreMissing(project);
        if (index == null) {
            return;
        }

        List<DataTypeTransformerModel> models = new ArrayList<>();
        List<AnnotationInstance> annotations = index.getAnnotations(DATA_TYPE_ANNOTATION);
        annotations.forEach(a -> {
            DataTypeTransformerModel model = new DataTypeTransformerModel();

            String currentClass = a.target().asClass().name().toString();
            boolean deprecated
                    = a.target().asClass().hasAnnotation(Deprecated.class) || project.getName().contains("(deprecated)");
            model.setClassName(currentClass);
            model.setDeprecated(deprecated);
            readAnnotation(a, model);
            // the body the transformer needs and the body it leaves (CAMEL-25928)
            Class<?> clazz = loadClass(currentClass);
            TransformerModel transformer = new TransformerModel();
            transformer.setName(model.getName());
            BodyTypesHelper.addBodyTypes(transformer, clazz.getAnnotation(BodyTypes.class), clazz);
            model.setBodyTypes(transformer.getBodyTypes());
            models.add(model);
        });
        models.sort(Comparator.comparing(DataTypeTransformerModel::getName));

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
                            "META-INF/services/org/apache/camel/transformer/" + fn,
                            json + NL);
                    if (updated) {
                        getLog().info("Updated transformer json: " + model.getName());
                    }
                }

                // generate marker file
                File camelMetaDir = new File(resourcesOutputDir, "META-INF/services/org/apache/camel/");
                int count = models.size();
                String properties = createProperties(project, "transformers", names.toString());
                updateResource(camelMetaDir.toPath(), "transformer.properties", properties);
                getLog().info("Generated transformer.properties containing " + count + " Camel "
                              + (count > 1 ? "transformers: " : "transformer: ") + names);
            } catch (Exception e) {
                throw new MojoExecutionException(e);
            }
        }
    }

    /** The name, the data types and the description of a transformer, from its @DataTypeTransformer. */
    static void readAnnotation(AnnotationInstance a, DataTypeTransformerModel model) {
        model.setName(annotationValue(a, "name"));
        model.setFrom(annotationValue(a, "fromType"));
        model.setTo(annotationValue(a, "toType"));
        model.setDescription(annotationValue(a, "description"));
    }

    private JsonObject asJsonObject(DataTypeTransformerModel model) {
        JsonObject jo = new JsonObject();
        // we need to know the maven GAV also
        jo.put("kind", "transformer");
        jo.put("name", model.getName());
        jo.put("title", asTitle(model.getName()));
        if (model.getDescription() != null) {
            jo.put("description", model.getDescription());
        }
        jo.put("deprecated", model.isDeprecated());
        jo.put("javaType", model.getClassName());
        if (model.getFrom() != null) {
            jo.put("from", model.getFrom());
        }
        if (model.getTo() != null) {
            jo.put("to", model.getTo());
        }
        jo.put("groupId", project.getGroupId());
        jo.put("artifactId", project.getArtifactId());
        jo.put("version", project.getVersion());
        JsonObject root = new JsonObject();
        root.put("transformer", jo);
        if (!model.getBodyTypes().isEmpty()) {
            root.put("bodyTypes", JsonMapper.asJsonObjectBodyTypes(model.getBodyTypes()));
        }
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
