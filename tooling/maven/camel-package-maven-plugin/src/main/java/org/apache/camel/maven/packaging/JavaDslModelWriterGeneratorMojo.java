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
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.camel.maven.packaging.generics.JandexStore;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.codehaus.plexus.build.BuildContext;
import org.jboss.jandex.ClassInfo;
import org.jboss.jandex.DotName;
import org.jboss.jandex.Index;
import org.jboss.jandex.IndexReader;
import org.jboss.jandex.MethodInfo;
import org.jboss.jandex.Type;

/**
 * Generate Java DSL Model Writer that produces fluent Java DSL source code from Camel model definitions.
 */
@Mojo(name = "generate-java-dsl-writer", threadSafe = true,
      requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
      defaultPhase = LifecyclePhase.PROCESS_CLASSES)
public class JavaDslModelWriterGeneratorMojo extends ModelWriterGeneratorMojo {

    public static final String WRITER_PACKAGE = "org.apache.camel.java.out";

    private static final Set<String> RUNTIME_ONLY_TYPES = Set.of(
            "org.apache.camel.Endpoint",
            "org.apache.camel.spi.EndpointUriFactory",
            "org.apache.camel.builder.EndpointProducerBuilder",
            "org.apache.camel.builder.EndpointConsumerBuilder",
            "org.apache.camel.AggregationStrategy",
            "org.apache.camel.spi.Policy",
            "org.apache.camel.Predicate",
            "org.apache.camel.Processor",
            "java.util.function.Supplier",
            "org.apache.camel.builder.ExpressionClause",
            "org.apache.camel.spi.DataType",
            "java.lang.Exception",
            "java.lang.Throwable");

    @Parameter(defaultValue = "${camel-generate-java-dsl-writer}")
    protected boolean generateJavaDslWriter;

    private Map<String, List<PrimaryArg>> primaryArgsCache;

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
     * Called from the Velocity template to get primary argument metadata for a given DSL method. Uses Jandex to
     * introspect {@code ProcessorDefinition} method signatures and match parameters to Definition class fields.
     *
     * @param  xmlElementName the XML element name (e.g., "aggregate", "poll", "setHeader")
     * @param  defClass       the Definition class (e.g., AggregateDefinition.class)
     * @return                list of primary args, empty if none found
     */
    public List<PrimaryArg> getPrimaryArgs(String xmlElementName, Class<?> defClass) {
        if (primaryArgsCache == null) {
            primaryArgsCache = buildPrimaryArgsMap();
        }
        // fallback for EIPs whose DSL method is not on ProcessorDefinition
        List<PrimaryArg> fallback = getFallbackPrimaryArgs(defClass);

        List<PrimaryArg> args = primaryArgsCache.get(xmlElementName);
        if (args == null) {
            if (!fallback.isEmpty()) {
                return fallback;
            }
            return Collections.emptyList();
        }
        // prefer fallback when it provides expressionSub (e.g., correlationExpression for aggregate)
        // because the XML model uses a different field than what Jandex finds on ProcessorDefinition
        if (!fallback.isEmpty() && fallback.stream().anyMatch(a -> "expressionSub".equals(a.renderType))) {
            return fallback;
        }
        // verify that the getters exist on the definition class
        for (PrimaryArg arg : args) {
            try {
                defClass.getMethod(arg.getter);
            } catch (NoSuchMethodException e) {
                // getter not found on this class — check superclasses via reflection
                boolean found = false;
                for (Class<?> c = defClass.getSuperclass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    try {
                        c.getMethod(arg.getter);
                        found = true;
                        break;
                    } catch (NoSuchMethodException ignored) {
                    }
                }
                if (!found) {
                    return Collections.emptyList();
                }
            }
        }
        return args;
    }

    private Map<String, List<PrimaryArg>> buildPrimaryArgsMap() {
        Map<String, List<PrimaryArg>> result = new HashMap<>();
        try {
            Index index = loadJandexIndex();
            if (index == null) {
                return result;
            }

            ClassInfo procDef = index.getClassByName(
                    DotName.createSimple("org.apache.camel.model.ProcessorDefinition"));
            if (procDef == null) {
                getLog().warn("ProcessorDefinition not found in Jandex index");
                return result;
            }

            // group methods by name, find simplest model-compatible overload
            Map<String, List<MethodInfo>> methodsByName = new HashMap<>();
            for (MethodInfo method : procDef.methods()) {
                if (method.parametersCount() == 0 || "<init>".equals(method.name())) {
                    continue;
                }
                methodsByName.computeIfAbsent(method.name(), k -> new ArrayList<>()).add(method);
            }

            for (Map.Entry<String, List<MethodInfo>> entry : methodsByName.entrySet()) {
                String methodName = entry.getKey();
                List<MethodInfo> overloads = entry.getValue();

                // find the simplest model-compatible overload, prefer ExpressionDefinition over Expression
                MethodInfo best = overloads.stream()
                        .filter(this::isModelCompatible)
                        .min(Comparator.comparingInt(MethodInfo::parametersCount)
                                .thenComparingInt(m -> -preferExpressionDefinition(m)))
                        .orElse(null);

                if (best != null) {
                    List<PrimaryArg> args = mapParametersToFields(best);
                    if (!args.isEmpty()) {
                        // check if a richer overload captures more fields (e.g. setHeader(String,Expression))
                        List<PrimaryArg> richer = findRicherOverload(overloads, args.size());
                        result.put(methodName, richer != null ? richer : args);
                    }
                }
            }
        } catch (Exception e) {
            getLog().warn("Failed to build primary args map from Jandex: " + e.getMessage());
        }
        return result;
    }

    private boolean isModelCompatible(MethodInfo method) {
        // skip methods returning ExpressionClause (fluent-style where expression is set via chaining)
        Type returnType = method.returnType();
        if (isExpressionClauseReturn(returnType)) {
            return false;
        }
        for (int i = 0; i < method.parametersCount(); i++) {
            Type paramType = method.parameterType(i);
            String typeName = paramType.name().toString();
            if (RUNTIME_ONLY_TYPES.contains(typeName)) {
                return false;
            }
            // skip varargs overloads (arrays)
            if (paramType.kind() == Type.Kind.ARRAY) {
                return false;
            }
        }
        return true;
    }

    private boolean isExpressionClauseReturn(Type type) {
        if (type.kind() == Type.Kind.PARAMETERIZED_TYPE) {
            return "org.apache.camel.builder.ExpressionClause".equals(
                    type.asParameterizedType().name().toString());
        }
        if (type.kind() == Type.Kind.CLASS) {
            return "org.apache.camel.builder.ExpressionClause".equals(type.name().toString());
        }
        return false;
    }

    private List<PrimaryArg> mapParametersToFields(MethodInfo method) {
        List<PrimaryArg> args = new ArrayList<>();

        for (int i = 0; i < method.parametersCount(); i++) {
            Type paramType = method.parameterType(i);
            String typeName = paramType.name().toString();

            PrimaryArg arg = mapSingleParam(typeName, i, method);
            if (arg != null) {
                args.add(arg);
            } else {
                // if we can't map any param, abort — partial primary args would be wrong
                return Collections.emptyList();
            }
        }
        return args;
    }

    private PrimaryArg mapSingleParam(String typeName, int paramIndex, MethodInfo method) {
        return switch (typeName) {
            case "org.apache.camel.Expression",
                    "org.apache.camel.model.language.ExpressionDefinition" ->
                new PrimaryArg("expression", "getExpression", "expression");
            case "org.apache.camel.model.ExpressionSubElementDefinition" ->
                new PrimaryArg("correlationExpression", "getCorrelationExpression", "expressionSub");
            case "java.lang.String" ->
                mapStringParam(method, paramIndex);
            case "java.lang.Class" ->
                mapClassParam(method, paramIndex);
            case "org.apache.camel.ExchangePattern" ->
                new PrimaryArg("pattern", "getPattern", "enumString", "ExchangePattern");
            case "org.apache.camel.LoggingLevel" ->
                new PrimaryArg("loggingLevel", "getLoggingLevel", "enumString", "LoggingLevel");
            case "boolean" ->
                mapBooleanParam(method, paramIndex);
            case "long" ->
                mapLongParam(method, paramIndex);
            default -> {
                if (isEnumType(typeName)) {
                    String simpleName = typeName.substring(typeName.lastIndexOf('.') + 1);
                    yield new PrimaryArg("pattern", "getPattern", "enumString", simpleName);
                }
                yield null;
            }
        };
    }

    private PrimaryArg mapStringParam(MethodInfo method, int paramIndex) {
        // determine which string field based on method name and param position
        String methodName = method.name();

        // count how many String params come before this one
        int stringIndex = 0;
        for (int i = 0; i < paramIndex; i++) {
            if ("java.lang.String".equals(method.parameterType(i).name().toString())) {
                stringIndex++;
            }
        }

        // common patterns based on method name
        if (isUriMethod(methodName)) {
            return new PrimaryArg("uri", "getUri", "string");
        }
        if ("rollback".equals(methodName)) {
            return new PrimaryArg("message", "getMessage", "string");
        }
        if ("log".equals(methodName)) {
            return new PrimaryArg("message", "getMessage", "string");
        }
        if ("policy".equals(methodName) || "process".equals(methodName)) {
            return new PrimaryArg("ref", "getRef", "string");
        }
        if ("removeProperties".equals(methodName) || "removeHeaders".equals(methodName)) {
            return new PrimaryArg("pattern", "getPattern", "string");
        }
        if ("removeHeader".equals(methodName) || "removeProperty".equals(methodName)
                || "removeVariable".equals(methodName)) {
            return new PrimaryArg("name", "getName", "string");
        }

        // methods with name + type pattern (convertHeaderTo, convertVariableTo, setHeader, setProperty, setVariable)
        if (methodName.startsWith("convert") && methodName.endsWith("To")) {
            if ("convertBodyTo".equals(methodName)) {
                // convertBodyTo(Class, String charset) — charset is not stored in the model
                return null;
            }
            return stringIndex == 0
                    ? new PrimaryArg("name", "getName", "string")
                    : null;
        }
        if (methodName.startsWith("set") && (methodName.endsWith("Header") || methodName.endsWith("Property")
                || methodName.endsWith("Variable"))) {
            return stringIndex == 0
                    ? new PrimaryArg("name", "getName", "string")
                    : null;
        }

        // setExchangePattern(String) - model stores pattern as String
        if ("setExchangePattern".equals(methodName)) {
            return new PrimaryArg("pattern", "getPattern", "enumString", "ExchangePattern");
        }

        // transformDataType - single-param overload is toType, two-param has fromType first
        if ("transformDataType".equals(methodName)) {
            if (method.parametersCount() == 1) {
                return new PrimaryArg("toType", "getToType", "string");
            }
            return stringIndex == 0
                    ? new PrimaryArg("fromType", "getFromType", "string")
                    : new PrimaryArg("toType", "getToType", "string");
        }

        // throwException
        if ("throwException".equals(methodName)) {
            return new PrimaryArg("message", "getMessage", "string");
        }

        // kamelet
        if ("kamelet".equals(methodName)) {
            return new PrimaryArg("name", "getName", "string");
        }

        // generic: first required string attr is usually the primary one
        return null;
    }

    private PrimaryArg mapClassParam(MethodInfo method, int paramIndex) {
        String methodName = method.name();
        if (methodName.startsWith("convert")) {
            return new PrimaryArg("type", "getType", "class");
        }
        if ("throwException".equals(methodName)) {
            return new PrimaryArg("exceptionType", "getExceptionType", "class");
        }
        return null;
    }

    private PrimaryArg mapBooleanParam(MethodInfo method, int paramIndex) {
        return null;
    }

    private PrimaryArg mapLongParam(MethodInfo method, int paramIndex) {
        if ("poll".equals(method.name())) {
            return new PrimaryArg("timeout", "getTimeout", "long");
        }
        return null;
    }

    private boolean isUriMethod(String methodName) {
        return Set.of("to", "toD", "toF", "poll", "enrich", "pollEnrich",
                "interceptSendToEndpoint", "interceptFrom", "wireTap").contains(methodName);
    }

    private List<PrimaryArg> findRicherOverload(List<MethodInfo> overloads, int currentArgCount) {
        // look for an overload with exactly one more param where all params map successfully
        for (MethodInfo method : overloads) {
            if (!isModelCompatible(method) || method.parametersCount() != currentArgCount + 1) {
                continue;
            }
            List<PrimaryArg> args = mapParametersToFields(method);
            if (args.size() == method.parametersCount()) {
                // reject overloads where multiple params map to the same field
                long uniqueFields = args.stream().map(PrimaryArg::getFieldName).distinct().count();
                if (uniqueFields == args.size()) {
                    return args;
                }
            }
        }
        return null;
    }

    private int preferExpressionDefinition(MethodInfo method) {
        for (int i = 0; i < method.parametersCount(); i++) {
            if ("org.apache.camel.model.language.ExpressionDefinition".equals(
                    method.parameterType(i).name().toString())) {
                return 10;
            }
        }
        return 0;
    }

    private List<PrimaryArg> getFallbackPrimaryArgs(Class<?> defClass) {
        // check if class extends ExpressionNode (filter, split, when, loop, etc.)
        for (Class<?> c = defClass; c != null; c = c.getSuperclass()) {
            if ("ExpressionNode".equals(c.getSimpleName()) || "BasicExpressionNode".equals(c.getSimpleName())) {
                return List.of(new PrimaryArg("expression", "getExpression", "expression"));
            }
        }
        // check for correlationExpression (AggregateDefinition)
        try {
            java.lang.reflect.Method m = defClass.getMethod("getCorrelationExpression");
            if ("ExpressionSubElementDefinition".equals(m.getReturnType().getSimpleName())) {
                return List.of(new PrimaryArg("correlationExpression", "getCorrelationExpression", "expressionSub"));
            }
        } catch (NoSuchMethodException ignored) {
        }
        return Collections.emptyList();
    }

    private boolean isEnumType(String typeName) {
        return typeName.startsWith("org.apache.camel.") && typeName.contains("Pattern");
    }

    private Index loadJandexIndex() {
        try (DynamicClassLoader classLoader
                = DynamicClassLoader.createDynamicClassLoader(project.getCompileClasspathElements())) {
            Class<?> routesDefClass
                    = classLoader.loadClass(XmlModelWriterGeneratorMojo.MODEL_PACKAGE + ".RoutesDefinition");
            String resName = routesDefClass.getName().replace('.', '/') + ".class";
            String url
                    = classLoader.getResource(resName).toExternalForm().replace(resName, JandexStore.DEFAULT_NAME);
            try (InputStream is = URI.create(url).toURL().openStream()) {
                return new IndexReader(is).read();
            }
        } catch (DependencyResolutionRequiredException | ClassNotFoundException | IOException e) {
            getLog().warn("Could not load Jandex index: " + e.getMessage());
            return null;
        }
    }

    public static class PrimaryArg {
        private final String fieldName;
        private final String getter;
        private final String renderType;
        private final String typeName;

        public PrimaryArg(String fieldName, String getter, String renderType) {
            this(fieldName, getter, renderType, null);
        }

        public PrimaryArg(String fieldName, String getter, String renderType, String typeName) {
            this.fieldName = fieldName;
            this.getter = getter;
            this.renderType = renderType;
            this.typeName = typeName;
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
    }
}
