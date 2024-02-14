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
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;

import org.apache.camel.maven.packaging.generics.PackagePluginUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

/**
 * Factory for generating code for @InvokeOnHeader.
 */
@Mojo(name = "generate-invoke-on-header", threadSafe = true, defaultPhase = LifecyclePhase.PROCESS_CLASSES,
      requiresDependencyCollection = ResolutionScope.COMPILE,
      requiresDependencyResolution = ResolutionScope.COMPILE)
public class GenerateInvokeOnHeaderMojo extends AbstractGeneratorMojo {

    public static final DotName HEADER_ANNOTATION = DotName.createSimple("org.apache.camel.spi.InvokeOnHeader");

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
        private String returnType;
        private final List<String> args = new ArrayList<>();

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

        public String getReturnType() {
            return returnType;
        }

        public void setReturnType(String returnType) {
            this.returnType = returnType;
        }

        public List<String> getArgs() {
            return args;
        }

        public void addArgs(String arg) {
            args.add(arg);
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

        Index index = PackagePluginUtils.readJandexIndexIgnoreMissing(project, getLog());
        if (index == null) {
            return;
        }

        Map<String, Set<InvokeOnHeaderModel>> classes = new HashMap<>();
        List<AnnotationInstance> annotations = index.getAnnotations(HEADER_ANNOTATION);
        annotations.forEach(a -> {
            String currentClass = a.target().asMethod().declaringClass().name().toString();
            String value = a.value().asString();
            MethodInfo mi = a.target().asMethod();

            InvokeOnHeaderModel model = new InvokeOnHeaderModel();
            model.setKey(value);
            model.setMethodName(mi.name());

            boolean isVoid = Type.Kind.VOID == mi.returnType().kind();
            if (isVoid) {
                model.setReturnType("VOID");
            } else {
                model.setReturnType(mi.returnType().toString());
            }
            for (Type type : mi.parameterTypes()) {
                String arg = type.name().toString();
                model.addArgs(arg);
            }
            Set<InvokeOnHeaderModel> set = classes.computeIfAbsent(currentClass,
                    k -> new TreeSet<>(Comparator.comparing(InvokeOnHeaderModel::getKey)));
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
        String tfqn = generateInvokeOnHeaderFactory(fqn, models, sourcesOutputDir);
        updateResource(resourcesOutputDir.toPath(),
                "META-INF/services/org/apache/camel/invoke-on-header/" + fqn,
                "# " + GENERATED_MSG + NL + "class=" + tfqn + NL);
    }

    @Deprecated
    private String generateInvokeOnHeaderFactory(
            String fqn, Set<InvokeOnHeaderModel> models, File outputDir) {

        int pos = fqn.lastIndexOf('.');
        String pn = fqn.substring(0, pos);
        String cn = fqn.substring(pos + 1) + "InvokeOnHeaderFactory";
        String en = fqn;
        String pfqn = fqn;

        StringWriter sw = new StringWriter();
        generateInvokeOnHeaderSource(pn, cn, en, pfqn, sw, models);

        String source = sw.toString();

        String fileName = pn.replace('.', '/') + "/" + cn + ".java";
        outputDir.mkdirs();
        boolean updated = updateResource(buildContext, outputDir.toPath().resolve(fileName), source);
        if (updated) {
            getLog().info("Updated " + fileName);
        }
        return pn + "." + cn;
    }

    private void generateInvokeOnHeaderSource(
            String pn, String cn, String en, String pfqn, StringWriter w, Set<InvokeOnHeaderModel> models) {
        w.write("/* " + AbstractGeneratorMojo.GENERATED_MSG + " */\n");
        w.write("package " + pn + ";\n");
        w.write("\n");
        w.write("import org.apache.camel.AsyncCallback;\n");
        w.write("import org.apache.camel.Exchange;\n");
        w.write("import org.apache.camel.spi.InvokeOnHeaderStrategy;\n");
        w.write("import " + pfqn + ";\n");
        w.write("\n");
        w.write("/**\n");
        w.write(" * " + AbstractGeneratorMojo.GENERATED_MSG + "\n");
        w.write(" */\n");
        w.write("@SuppressWarnings(\"unchecked\")\n");
        w.write("public class " + cn + " implements InvokeOnHeaderStrategy");
        w.write(" {\n");
        w.write("\n");

        w.write("    @Override\n");
        w.write("    public Object invoke(Object obj, String key, Exchange exchange, AsyncCallback callback) throws Exception {\n");
        w.write("        " + en + " target = (" + en + ") obj;\n");
        if (!models.isEmpty()) {
            w.write("        switch (key) {\n");
            for (InvokeOnHeaderModel option : models) {
                boolean sync = true;
                String invoke = "target." + option.getMethodName() + "(";
                if (!option.getArgs().isEmpty()) {
                    StringJoiner sj = new StringJoiner(", ");
                    for (String arg : option.getArgs()) {
                        String ba = bindArg(arg);
                        // if callback is in use then we are no long synchronous
                        sync &= !ba.equals("callback");
                        sj.add(ba);
                    }
                    invoke += sj.toString();
                }
                String ret = "null";
                if (!sync) {
                    // return the callback instance in async mode to signal that callback are in use
                    ret = "callback";
                }
                invoke += ")";

                if (!option.getKey().toLowerCase().equals(option.getKey())) {
                    w.write(String.format("        case \"%s\":\n", option.getKey().toLowerCase()));
                }
                if (!sync || option.getReturnType().equals("VOID")) {
                    w.write(String.format("        case \"%s\": %s; return %s;\n", option.getKey(), invoke, ret));
                } else {
                    w.write(String.format("        case \"%s\": return %s;\n", option.getKey(), invoke));
                }
            }
            w.write("        default: return null;\n");
            w.write("        }\n");
        }
        w.write("    }\n");
        w.write("\n");

        w.write("}\n");
        w.write("\n");
    }

    protected String bindArg(String type) {
        if ("org.apache.camel.Exchange".equals(type)) {
            return "exchange";
        } else if ("org.apache.camel.Message".equals(type)) {
            return "exchange.getMessage()";
        } else if ("org.apache.camel.AsyncCallback".equals(type)) {
            return "callback";
        } else if ("org.apache.camel.CamelContext".equals(type)) {
            return "exchange.getContext()";
        } else {
            return "exchange.getMessage().getBody(" + type + ".class)";
        }
    }

}
