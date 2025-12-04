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
import static org.apache.camel.maven.packaging.MojoHelper.getType;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.camel.maven.packaging.generics.PackagePluginUtils;
import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.JsonMapper;
import org.apache.camel.tooling.util.PackageHelper;
import org.apache.camel.tooling.util.Strings;
import org.apache.camel.util.json.JsonObject;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;

/**
 * Factory for generating code for Camel pojo beans that are intended for end user to use with Camel EIPs and
 * components.
 */
@Mojo(
        name = "generate-pojo-bean",
        threadSafe = true,
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
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

    @Inject
    public GeneratePojoBeanMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    private static class BeanPojoModel {
        private String name;
        private String title;
        private String className;
        private String interfaceName;
        private String description;
        private boolean deprecated;
        private final List<BeanPojoOptionModel> options = new ArrayList<>();

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

        public void addOption(BeanPojoOptionModel option) {
            this.options.add(option);
        }

        public List<BeanPojoOptionModel> getOptions() {
            return options;
        }
    }

    private static class BeanPojoOptionModel extends BaseOptionModel {}

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
                ClassInfo ci = a.target().asClass();
                model.setName(ci.simpleName());
                boolean deprecated = ci.hasAnnotation(Deprecated.class);
                String title = annotationValue(a, "title");
                if (title == null) {
                    title = Strings.camelCaseToDash(model.getName());
                    title = Strings.camelDashToTitle(title);
                }
                model.setTitle(title);
                model.setClassName(ci.name().toString());
                model.setDeprecated(deprecated);
                model.setDescription(annotationValue(a, "description"));
                model.setInterfaceName(annotationValue(a, "annotations", "interfaceName"));
                if (model.getInterfaceName() == null) {
                    // try to discover the interface
                    model.setInterfaceName(interfaceName(index, ci));
                }

                // find all fields with @Metadata as options (also from super class)
                while (ci != null) {
                    extractFields(ci, model);
                    DotName dn = ci.superName();
                    if (dn != null) {
                        ci = index.getClassByName(dn);
                    }
                }
                models.add(model);
            }
        });
        models.sort(Comparator.comparing(BeanPojoModel::getName));

        if (!models.isEmpty()) {
            try {
                StringJoiner names = new StringJoiner(" ");
                for (var model : models) {
                    names.add(model.getName());
                    JsonObject jo = asJsonObject(model);
                    String json = JsonMapper.serialize(jo);
                    String fn = sanitizeFileName(model.getName()) + PackageHelper.JSON_SUFIX;
                    boolean updated = updateResource(
                            resourcesOutputDir.toPath(), "META-INF/services/org/apache/camel/bean/" + fn, json + NL);
                    if (updated) {
                        getLog().info("Updated bean json: " + model.getName());
                    }
                }

                // generate marker file
                File camelMetaDir = new File(resourcesOutputDir, "META-INF/services/org/apache/camel/");
                int count = models.size();
                String properties = createProperties(project, "bean", names.toString());
                updateResource(camelMetaDir.toPath(), "bean.properties", properties);
                getLog().info("Generated bean.properties containing " + count + " Camel "
                        + (count > 1 ? "beans: " : "bean: ") + names);
            } catch (Exception e) {
                throw new MojoExecutionException(e);
            }
        }
    }

    private void extractFields(ClassInfo ci, BeanPojoModel model) {
        // need to load the class so we can get the field in the order they are declared in the source code
        Class<?> classElement = loadClass(ci.name().toString());
        List<Field> fields = Stream.of(classElement.getDeclaredFields())
                .filter(f -> f.getAnnotation(Metadata.class) != null)
                .toList();

        for (Field fi : fields) {
            BeanPojoOptionModel o = new BeanPojoOptionModel();
            Metadata ai = fi.getAnnotation(Metadata.class);
            o.setKind("property");
            o.setName(fi.getName());
            if (!ai.label().isEmpty()) {
                o.setLabel(ai.label());
            }
            if (!ai.defaultValue().isEmpty()) {
                o.setDefaultValue(ai.defaultValue());
            }
            o.setRequired(ai.required());
            String displayName = ai.displayName();
            if (displayName.isEmpty()) {
                displayName = Strings.asTitle(o.getName());
            }
            o.setDisplayName(displayName);
            o.setDeprecated(fi.getAnnotation(Deprecated.class) != null);
            o.setAutowired(ai.autowired());
            o.setSecret(ai.secret());
            String javaType = ai.javaType();
            if (javaType.isEmpty()) {
                javaType = fi.getType().getTypeName();
            }
            o.setJavaType(javaType);
            o.setDescription(ai.description());
            String enums = ai.enums();
            if (!enums.isEmpty()) {
                String[] values = enums.split(",");
                o.setEnums(Stream.of(values).map(String::trim).toList());
            }
            o.setType(getType(javaType, !enums.isEmpty(), false));
            model.addOption(o);
        }
    }

    private static String interfaceName(Index index, ClassInfo target) {
        for (DotName dn : target.interfaceNames()) {
            if (dn.packagePrefix().startsWith("org.apache.camel")) {
                return dn.toString();
            }
        }
        if (target.superName() != null) {
            DotName dn = target.superName();
            ClassInfo ci = index.getClassByName(dn);
            if (ci != null) {
                return interfaceName(index, ci);
            }
        }
        return null;
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

        if (!model.getOptions().isEmpty()) {
            JsonObject options = JsonMapper.asJsonObject(model.getOptions());
            jo.put("properties", options);
        }

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
