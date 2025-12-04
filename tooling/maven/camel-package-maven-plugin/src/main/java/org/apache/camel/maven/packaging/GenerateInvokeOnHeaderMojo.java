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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.inject.Inject;

import org.apache.camel.maven.packaging.generics.PackagePluginUtils;
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
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

/**
 * Factory for generating code for @InvokeOnHeader.
 */
@Mojo(
        name = "generate-invoke-on-header",
        threadSafe = true,
        defaultPhase = LifecyclePhase.PROCESS_CLASSES,
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

    @Inject
    public GenerateInvokeOnHeaderMojo(MavenProjectHelper projectHelper, BuildContext buildContext) {
        super(projectHelper, buildContext);
    }

    public static class InvokeOnHeaderModel {
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
            Set<InvokeOnHeaderModel> set = classes.computeIfAbsent(
                    currentClass, k -> new TreeSet<>(Comparator.comparing(InvokeOnHeaderModel::getKey)));
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
        int pos = fqn.lastIndexOf('.');
        String pn = fqn.substring(0, pos);
        String cn = fqn.substring(pos + 1) + "InvokeOnHeaderFactory";

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("pn", pn);
        ctx.put("cn", cn);
        ctx.put("en", fqn);
        ctx.put("pfqn", fqn);
        ctx.put("models", models);
        ctx.put("mojo", this);
        String source = velocity("velocity/invoke-on-header.vm", ctx);

        String fileName = pn.replace('.', '/') + "/" + cn + ".java";
        boolean updated = updateResource(sourcesOutputDir.toPath(), fileName, source);
        if (updated) {
            getLog().info("Updated " + fileName);
        }
        String tfqn = pn + "." + cn;
        updateResource(
                resourcesOutputDir.toPath(),
                "META-INF/services/org/apache/camel/invoke-on-header/" + fqn,
                "# " + GENERATED_MSG + NL + "class=" + tfqn + NL);
    }

    @SuppressWarnings("unused")
    public String bindArg(String type) {
        return switch (type) {
            case "org.apache.camel.Exchange" -> "exchange";
            case "org.apache.camel.Message" -> "exchange.getMessage()";
            case "org.apache.camel.AsyncCallback" -> "callback";
            case "org.apache.camel.CamelContext" -> "exchange.getContext()";
            default -> "exchange.getMessage().getBody(" + type + ".class)";
        };
    }
}
