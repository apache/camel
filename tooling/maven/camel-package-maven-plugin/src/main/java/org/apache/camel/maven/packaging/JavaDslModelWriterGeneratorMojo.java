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
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.camel.spi.annotations.DslArg;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;

/**
 * Generate Java DSL Model Writer that produces fluent Java DSL source code from Camel model definitions.
 *
 * Primary argument metadata is read from {@link DslArg} annotations on model Definition class fields, replacing the
 * previous Jandex-based approach that introspected ProcessorDefinition method signatures.
 */
@Mojo(name = "generate-java-dsl-writer", threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
      defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class JavaDslModelWriterGeneratorMojo extends ModelWriterGeneratorMojo {

    public static final String WRITER_PACKAGE = "org.apache.camel.java.out";

    @Parameter(defaultValue = "${camel-generate-java-dsl-writer}")
    protected boolean generateJavaDslWriter;

    private final Map<String, List<PrimaryArg>> primaryArgsCache = new HashMap<>();

    @Inject
    public JavaDslModelWriterGeneratorMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    @Override
    public void execute(MavenProject project) throws MojoFailureException, MojoExecutionException {
        sourcesOutputDir = new File(project.getBasedir(), "src/generated/java");
        generateJavaDslWriter
                = Boolean.parseBoolean(project.getProperties().getProperty("camel-generate-java-dsl-writer", "false"));
        super.execute(project);
    }

    @Override
    public void execute() throws MojoExecutionException {
        if (!generateJavaDslWriter) {
            return;
        }
        Path javaDir = sourcesOutputDir.toPath();
        String writer = generateWriter();
        updateResource(javaDir, (getWriterPackage() + ".JavaDslModelWriter").replace('.', '/') + ".java", writer);
    }

    @Override
    String getWriterPackage() {
        return WRITER_PACKAGE;
    }

    @Override
    protected String getTemplateName() {
        return "velocity/model-java-dsl-writer.vm";
    }

    /**
     * Called from the Velocity template to get primary argument metadata for a given DSL method. Reads {@link DslArg}
     * annotations from the Definition class hierarchy.
     *
     * @param  xmlElementName the XML element name (e.g., "aggregate", "poll", "setHeader")
     * @param  defClass       the Definition class (e.g., AggregateDefinition.class)
     * @return                list of primary args sorted by position, empty if none found
     */
    public List<PrimaryArg> getPrimaryArgs(String xmlElementName, Class<?> defClass) {
        return primaryArgsCache.computeIfAbsent(defClass.getName(), k -> buildFromAnnotations(defClass));
    }

    private List<PrimaryArg> buildFromAnnotations(Class<?> defClass) {
        // Collect excluded field names from class-level @DslArg(exclude = ...)
        Set<String> excluded = new HashSet<>();
        DslArg classAnn = defClass.getAnnotation(DslArg.class);
        if (classAnn != null && classAnn.exclude().length > 0) {
            excluded.addAll(Arrays.asList(classAnn.exclude()));
        }

        List<PrimaryArg> args = new ArrayList<>();
        for (Class<?> c = defClass; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (excluded.contains(f.getName())) {
                    continue;
                }
                DslArg ann = f.getAnnotation(DslArg.class);
                if (ann != null && ann.primary()) {
                    String renderType = ann.renderType().isEmpty() ? autoDetectRenderType(f) : ann.renderType();
                    String typeName = ann.typeName().isEmpty() ? null : ann.typeName();
                    String getter = "get" + Character.toUpperCase(f.getName().charAt(0)) + f.getName().substring(1);
                    args.add(new PrimaryArg(f.getName(), getter, renderType, typeName, ann.position()));
                }
            }
        }
        args.sort(Comparator.comparingInt(PrimaryArg::getPosition));
        return args;
    }

    private String autoDetectRenderType(Field f) {
        String typeName = f.getType().getName();
        return switch (typeName) {
            case "org.apache.camel.model.language.ExpressionDefinition" -> "expression";
            case "org.apache.camel.model.ExpressionSubElementDefinition" -> "expressionSub";
            case "java.util.List" -> "classList";
            default -> "string";
        };
    }

    public static class PrimaryArg {
        private final String fieldName;
        private final String getter;
        private final String renderType;
        private final String typeName;
        private final int position;

        public PrimaryArg(String fieldName, String getter, String renderType, String typeName, int position) {
            this.fieldName = fieldName;
            this.getter = getter;
            this.renderType = renderType;
            this.typeName = typeName;
            this.position = position;
        }

        public String getFieldName() {
            return fieldName;
        }

        public String getGetter() {
            return getter;
        }

        public String getRenderType() {
            return renderType;
        }

        public String getTypeName() {
            return typeName;
        }

        public int getPosition() {
            return position;
        }
    }
}
