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
import java.util.StringJoiner;

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
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;

import static org.apache.camel.maven.packaging.MojoHelper.annotationValue;

/**
 * Factory for generating code for Camel pojo beans that are intended for end user to use with Camel EIPs and
 * components.
 */
@Mojo(name = "generate-pojo-bean", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyCollection = ResolutionScope.COMPILE,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class GeneratePojoBeanMojo extends AbstractGeneratorMojo {

    public static final DotName METADATA = DotName.createSimple("org.apache.camel.spi.Metadata");

    /**
     * The project build directory
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    private static class BeanPojoModel {
        private String name;
        private String title;
        private String className;
        private String interfaceName;
        private String description;
        private boolean deprecated;

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

        public String getClassName() {
            return className;
        }

        public void setClassName(String className) {
            this.className = className;
        }

        public String getInterfaceName() {
            return interfaceName;
        }

        public void setInterfaceName(String interfaceName) {
            this.interfaceName = interfaceName;
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

    public GeneratePojoBeanMojo() {
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

        List<BeanPojoModel> models = new ArrayList<>();
        List<AnnotationInstance> annotations = index.getAnnotations(METADATA);
        annotations.forEach(a -> {
            // only @Metadata(label="bean") is selected
            String label = annotationValue(a, "label");
            if ("bean".equals(label)) {
                BeanPojoModel model = new BeanPojoModel();
                model.setName(a.target().asClass().simpleName());
                boolean deprecated = a.target().asClass().hasAnnotation(Deprecated.class);
                String title = annotationValue(a, "title");
                if (title == null) {
                    title = Strings.camelCaseToDash(model.getName());
                    title = Strings.camelDashToTitle(title);
                }
                model.setTitle(title);
                model.setClassName(a.target().asClass().name().toString());
                model.setDeprecated(deprecated);
                model.setDescription(annotationValue(a, "description"));
                for (DotName dn : a.target().asClass().interfaceNames()) {
                    if (dn.packagePrefix().startsWith("org.apache.camel")) {
                        model.setInterfaceName(dn.toString());
                        break;
                    }
                }
                // TODO: getter/setter for options ala EIP/components
                models.add(model);
            }
        });
        models.sort(Comparator.comparing(BeanPojoModel::getClassName));

        if (!models.isEmpty()) {
            try {
                StringJoiner names = new StringJoiner(" ");
                for (var model : models) {
                    names.add(model.getClassName());
                    JsonObject jo = asJsonObject(model);
                    String json = jo.toJson();
                    json = Jsoner.prettyPrint(json, 2);
                    String fn = sanitizeFileName(model.getName()) + PackageHelper.JSON_SUFIX;
                    boolean updated = updateResource(resourcesOutputDir.toPath(),
                            "META-INF/services/org/apache/camel/bean/" + fn,
                            json + NL);
                    if (updated) {
                        getLog().info("Updated bean json: " + model.getName());
                    }
                }

                // generate marker file
                File camelMetaDir = new File(resourcesOutputDir, "META-INF/services/org/apache/camel/");
                int count = models.size();
                String properties = createProperties(project, "beans", names.toString());
                updateResource(camelMetaDir.toPath(), "beans.properties", properties);
                getLog().info("Generated beans.properties containing " + count + " Camel "
                              + (count > 1 ? "beans: " : "bean: ") + names);
            } catch (Exception e) {
                throw new MojoExecutionException(e);
            }
        }
    }

    private JsonObject asJsonObject(BeanPojoModel model) {
        JsonObject jo = new JsonObject();
        // we need to know the maven GAV also
        jo.put("kind", "bean");
        jo.put("name", model.getName());
        jo.put("javaType", model.getClassName());
        if (model.getInterfaceName() != null) {
            jo.put("interfaceType", model.getInterfaceName());
        }
        jo.put("title", asTitle(model.getName()));
        if (model.getDescription() != null) {
            jo.put("description", model.getDescription());
        }
        jo.put("deprecated", model.isDeprecated());
        jo.put("groupId", project.getGroupId());
        jo.put("artifactId", project.getArtifactId());
        jo.put("version", project.getVersion());
        JsonObject root = new JsonObject();
        root.put("bean", jo);
        return root;
    }

    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[^A-Za-z0-9+-/]", "-");
    }

    private String asTitle(String name) {
        return Strings.asTitle(name);
    }

}
