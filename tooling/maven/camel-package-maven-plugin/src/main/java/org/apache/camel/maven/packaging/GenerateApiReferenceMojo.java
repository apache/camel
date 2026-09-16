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
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

import javax.inject.Inject;

import org.apache.camel.maven.packaging.generics.PackagePluginUtils;
import org.apache.camel.spi.Metadata;
import org.apache.camel.tooling.model.ApiReferenceModel;
import org.apache.camel.tooling.model.ApiReferenceModel.ApiMethodOptionModel;
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
import org.jboss.jandex.AnnotationTarget;
import org.jboss.jandex.Index;

import static org.apache.camel.maven.packaging.MojoHelper.annotationValue;

/**
 * Generates the compact API reference of the core Camel classes a route author's Java or Groovy code touches (Exchange,
 * Message, CamelContext, ...), for tooling and AI assistants.
 * <p>
 * A class with a class-level {@code @Metadata(label = "api", description = ...)} is a card; the card lists the methods
 * of the class and its super types that carry {@code @Metadata(label = "api")}, one entry per method name with the
 * signatures of every non-deprecated overload taken from the compiled class, so a renamed or removed method cannot
 * leave a stale entry behind. The class-level description is the place for the common mistakes.
 */
@Mojo(name = "generate-api-reference", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyCollection = ResolutionScope.COMPILE,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateApiReferenceMojo extends AbstractGeneratorMojo {

    private static final String API_LABEL = "api";

    /**
     * The project build directory
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    @Inject
    public GenerateApiReferenceMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
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

        List<ApiReferenceModel> models = new ArrayList<>();
        for (AnnotationInstance a : index.getAnnotations(GeneratePojoBeanMojo.METADATA)) {
            // a card is a class with @Metadata(label="api"); the methods are read from the compiled class
            if (a.target().kind() == AnnotationTarget.Kind.CLASS && isApi(annotationValue(a, "label"))) {
                models.add(createModel(loadClass(a.target().asClass().name().toString())));
            }
        }
        models.sort(Comparator.comparing(ApiReferenceModel::getName));

        if (!models.isEmpty()) {
            try {
                StringJoiner names = new StringJoiner(" ");
                for (ApiReferenceModel model : models) {
                    names.add(model.getName());
                    JsonObject jo = JsonMapper.asJsonObject(model);
                    String json = JsonMapper.serialize(jo);
                    String fn = model.getName() + PackageHelper.JSON_SUFIX;
                    boolean updated = updateResource(resourcesOutputDir.toPath(),
                            "META-INF/services/org/apache/camel/api/" + fn,
                            json + NL);
                    if (updated) {
                        getLog().info("Updated api json: " + model.getName());
                    }
                }

                // generate marker file
                File camelMetaDir = new File(resourcesOutputDir, "META-INF/services/org/apache/camel/");
                int count = models.size();
                String properties = createProperties(project, "api", names.toString());
                updateResource(camelMetaDir.toPath(), "api.properties", properties);
                getLog().info("Generated api.properties containing " + count + " Camel API "
                              + (count > 1 ? "references: " : "reference: ") + names);
            } catch (Exception e) {
                throw new MojoExecutionException(e);
            }
        }
    }

    private ApiReferenceModel createModel(Class<?> clazz) {
        ApiReferenceModel model = new ApiReferenceModel();
        model.setName(clazz.getSimpleName());
        model.setJavaType(clazz.getName());
        Metadata cm = clazz.getAnnotation(Metadata.class);
        String title = cm.title().isEmpty() ? Strings.asTitle(clazz.getSimpleName()) : cm.title();
        model.setTitle(title);
        model.setDescription(cm.description().isEmpty() ? null : cm.description());
        model.setDeprecated(clazz.isAnnotationPresent(Deprecated.class));
        model.setGroupId(project.getGroupId());
        model.setArtifactId(project.getArtifactId());
        model.setVersion(project.getVersion());

        // getMethods includes the public methods of the super types (Registry gets lookupByName from BeanRepository)
        Map<String, List<Method>> byName = new TreeMap<>();
        for (Method m : clazz.getMethods()) {
            if (isCandidate(m)) {
                byName.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(m);
            }
        }
        for (Map.Entry<String, List<Method>> entry : byName.entrySet()) {
            List<Method> overloads = entry.getValue();
            Method annotated = overloads.stream().filter(GenerateApiReferenceMojo::isApi).findFirst().orElse(null);
            if (annotated == null) {
                continue;
            }
            Metadata md = annotated.getAnnotation(Metadata.class);
            ApiMethodOptionModel o = new ApiMethodOptionModel();
            o.setName(entry.getKey());
            o.setJavaType(annotated.getReturnType().getName());
            o.setDescription(md.description().isEmpty() ? null : md.description());
            o.setImportant(md.important());
            o.setDeprecated(annotated.isAnnotationPresent(Deprecated.class));
            Arrays.stream(md.examples()).forEach(o::addExample);
            overloads.stream()
                    .filter(m -> !m.isAnnotationPresent(Deprecated.class))
                    .map(GenerateApiReferenceMojo::signature)
                    .sorted(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()))
                    .distinct()
                    .forEach(o::addSignature);
            model.addOption(o);
        }
        return model;
    }

    private static boolean isCandidate(Method m) {
        return !m.isSynthetic() && !m.isBridge() && !Modifier.isStatic(m.getModifiers())
                && m.getDeclaringClass() != Object.class;
    }

    private static boolean isApi(Method m) {
        Metadata md = m.getAnnotation(Metadata.class);
        return md != null && isApi(md.label());
    }

    private static boolean isApi(String label) {
        if (label == null) {
            return false;
        }
        for (String l : label.split(",")) {
            if (API_LABEL.equals(l.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * The signature of a method the way it reads in source, with simple type names: {@code <T> T getHeader(String name,
     * Class<T> type)}, {@code void setBody(Object body)}, {@code boolean removeHeaders(String pattern, String...
     * excludePatterns)}.
     */
    static String signature(Method m) {
        StringBuilder sb = new StringBuilder();
        TypeVariable<Method>[] typeParameters = m.getTypeParameters();
        if (typeParameters.length > 0) {
            StringJoiner tp = new StringJoiner(", ", "<", "> ");
            for (TypeVariable<Method> tv : typeParameters) {
                StringBuilder t = new StringBuilder(tv.getName());
                Type[] bounds = tv.getBounds();
                if (bounds.length != 1 || bounds[0] != Object.class) {
                    StringJoiner b = new StringJoiner(" & ", " extends ", "");
                    for (Type bound : bounds) {
                        b.add(simpleTypeName(bound));
                    }
                    t.append(b);
                }
                tp.add(t);
            }
            sb.append(tp);
        }
        sb.append(simpleTypeName(m.getGenericReturnType())).append(' ').append(m.getName()).append('(');
        java.lang.reflect.Parameter[] parameters = m.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            String type = simpleTypeName(parameters[i].getParameterizedType());
            if (m.isVarArgs() && i == parameters.length - 1 && type.endsWith("[]")) {
                type = type.substring(0, type.length() - 2) + "...";
            }
            sb.append(type).append(' ').append(parameters[i].getName());
        }
        sb.append(')');
        Type[] exceptions = m.getGenericExceptionTypes();
        if (exceptions.length > 0) {
            StringJoiner ex = new StringJoiner(", ", " throws ", "");
            for (Type e : exceptions) {
                ex.add(simpleTypeName(e));
            }
            sb.append(ex);
        }
        return sb.toString();
    }

    /**
     * The type name without packages, generics kept: {@code java.util.Map<java.lang.String, T>} is
     * {@code Map<String, T>}.
     */
    static String simpleTypeName(Type type) {
        return type.getTypeName().replaceAll("\\b[a-z][A-Za-z0-9_]*\\.", "").replace('$', '.');
    }

}
