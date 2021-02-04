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
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;

/**
 * Abstract class for @InvokeOnHeader/@InvokeOnHeaders factory generator.
 */
@Mojo(name = "generate-invoke-on-header", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyCollection = ResolutionScope.COMPILE,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateInvokeOnHeaderMojo extends AbstractGeneratorMojo {

    public static final DotName HEADER_ANNOTATION = DotName.createSimple("org.apache.camel.spi.InvokeOnHeader");
    public static final DotName HEADERS_ANNOTATION = DotName.createSimple("org.apache.camel.spi.InvokeOnHeaders");

    /**
     * The project build directory
     */
    @Parameter(defaultValue = "${project.build.directory}")
    protected File buildDir;

    @Parameter(defaultValue = "${project.basedir}/src/generated/java")
    protected File sourcesOutputDir;
    @Parameter(defaultValue = "${project.basedir}/src/generated/resources")
    protected File resourcesOutputDir;

    private static class InvokeOnHeaderModel {
        private String key;
        private String methodName;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getMethodName() {
            return methodName;
        }

        public void setMethodName(String methodName) {
            this.methodName = methodName;
        }
    }

    public GenerateInvokeOnHeaderMojo() {
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if ("pom".equals(project.getPackaging())) {
            return;
        }

        buildDir = new File(project.getBuild().getDirectory());

        if (sourcesOutputDir == null) {
            sourcesOutputDir = new File(project.getBasedir(), "src/generated/java");
        }
        if (resourcesOutputDir == null) {
            resourcesOutputDir = new File(project.getBasedir(), "src/generated/resources");
        }

        Path output = Paths.get(project.getBuild().getOutputDirectory());
        Index index;
        try (InputStream is = Files.newInputStream(output.resolve("META-INF/jandex.idx"))) {
            index = new IndexReader(is).read();
        } catch (IOException e) {
            throw new MojoExecutionException("IOException: " + e.getMessage(), e);
        }

        Map<String, Set<InvokeOnHeaderModel>> classes = new HashMap<>();
        List<AnnotationInstance> annotations = index.getAnnotations(HEADER_ANNOTATION);
        annotations.forEach(a -> {
            String currentClass = a.target().asClass().name().toString();
            String value = a.value().asString();
            String methodName = a.target().asMethod().name();
            Set<InvokeOnHeaderModel> set = classes.get(currentClass);
            if (set == null) {
                set = new HashSet<>();
            }
            InvokeOnHeaderModel model = new InvokeOnHeaderModel();
            model.key = value;
            model.methodName = methodName;
            set.add(model);
        });

        try {
            for (Map.Entry<String, Set<InvokeOnHeaderModel>> entry : classes.entrySet()) {
                createInvokeOnHeaderFactory(entry.getKey(), entry.getValue());
            }
        } catch (IOException e) {
            throw new MojoExecutionException("IOException: " + e.getMessage(), e);
        }
    }

    protected void createInvokeOnHeaderFactory(String fqn, Set<InvokeOnHeaderModel> models) throws IOException {
        generateInvokeOnHeaderFactory(fqn, models, sourcesOutputDir);
        updateResource(resourcesOutputDir.toPath(),
                "META-INF/services/org/apache/camel/invoke-on-header/" + fqn,
                "# " + GENERATED_MSG + NL + "class=" + fqn + NL);
    }

    @Deprecated
    private void generateInvokeOnHeaderFactory(
            String fqn, Set<InvokeOnHeaderModel> models, File outputDir)
            throws IOException {

        int pos = fqn.lastIndexOf('.');
        String pn = fqn.substring(0, pos);
        String cn = fqn.substring(pos + 1) + "InvokeOnHeaderFactory";
        String en = fqn;
        String pfqn = fqn;
        String psn = "org.apache.camel.support.component.InvokeOnHeaderSupport";

        StringWriter sw = new StringWriter();
        generateInvokeOnHeaderSource(pn, cn, en, pfqn, psn, sw, models);

        String source = sw.toString();

        String fileName = pn.replace('.', '/') + "/" + cn + ".java";
        outputDir.mkdirs();
        boolean updated = updateResource(buildContext, outputDir.toPath().resolve(fileName), source);
        if (updated) {
            getLog().info("Updated " + fileName);
        }
    }

    private void generateInvokeOnHeaderSource(
            String pn, String cn, String en, String pfqn, String psn, StringWriter w, Set<InvokeOnHeaderModel> models) {
        w.write("/* " + AbstractGeneratorMojo.GENERATED_MSG + " */\n");
        w.write("package " + pn + ";\n");
        w.write("\n");
        w.write("import java.util.Map;\n");
        w.write("\n");
        w.write("import org.apache.camel.CamelContext;\n");
        w.write("import org.apache.camel.spi.InvokeOnHeaderStrategy;\n");
        w.write("import org.apache.camel.util.CaseInsensitiveMap;\n");
        w.write("import " + pfqn + ";\n");
        w.write("\n");
        w.write("/**\n");
        w.write(" * " + AbstractGeneratorMojo.GENERATED_MSG + "\n");
        w.write(" */\n");
        w.write("@SuppressWarnings(\"unchecked\")\n");
        w.write("public class " + cn + " extends " + psn
                + " implements InvokeOnHeaderStrategy");
        w.write(" {\n");
        w.write("\n");

        // sort options A..Z so they always have same order
        if (!models.isEmpty()) {
            models = models.stream().sorted(Comparator.comparing(InvokeOnHeaderModel::getKey)).collect(Collectors.toSet());
        }

        w.write("    @Override\n");
        w.write("    public Object invoke(String key, Exchange exchange) {\n");
        if (!models.isEmpty()) {
            w.write("        switch (name) {\n");
            for (InvokeOnHeaderModel option : models) {
                String invoke = option.getMethodName() + "(exchange.getMessage());";
                if (!option.getKey().toLowerCase().equals(option.getKey())) {
                    w.write(String.format("        case \"%s\":\n", option.getKey().toLowerCase()));
                }
                w.write(String.format("        case \"%s\": return %s;\n", option.getKey(), invoke));
            }
        }
        w.write("    }\n");

        w.write("}\n");
        w.write("\n");
    }

}
