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
package org.apache.camel.dsl.yaml;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dsl.yaml.common.YamlDeserializerBase;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver;
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolverProvider;
import org.apache.camel.dsl.yaml.common.exception.DuplicateKeyException;
import org.apache.camel.dsl.yaml.common.exception.UnknownNodeIdException;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.StepDefinition;
import org.apache.camel.model.ToDefinition;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.ConstructNode;
import org.snakeyaml.engine.v2.nodes.Node;

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asText;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class YamlDeserializerResolverDiscoveryTest extends YamlTestSupport {

    @Test
    void discoverCustomRouteStepResolverFromClasspath() throws Exception {
        loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep:
                          id: "custom-step"
                          steps:
                            - log:
                                message: "nested"
                            - to:
                                uri: "mock:result"
                """);

        var step = (StepDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(step.getId()).isEqualTo("custom-step");
        assertThat(((LogDefinition) step.getOutputs().get(0)).getMessage()).isEqualTo("nested");
        assertThat(((ToDefinition) step.getOutputs().get(1)).getEndpointUri()).isEqualTo("mock:result");
    }

    @Test
    void discoveredCustomRouteStepExecutesThroughNestedOutputs() throws Exception {
        loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep:
                          steps:
                            - to:
                                uri: "mock:result"
                """);

        withMock("mock:result", mock -> mock.expectedBodiesReceived("hello"));

        context.start();
        withTemplate(t -> t.to("direct:start").withBody("hello").send());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void registryResolverContributesCustomRouteStep() throws Exception {
        context.getRegistry().bind("registryStepResolver",
                new FixedStepResolver("registryStep", "registry-step", YamlDeserializerResolver.ORDER_DEFAULT));

        loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - registryStep: {}
                """);

        var step = (StepDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(step.getId()).isEqualTo("registry-step");
    }

    @Test
    void registryResolverWinsOverClasspathResolverAtSameOrder() throws Exception {
        context.getRegistry().bind("registryCustomStepResolver",
                new FixedStepResolver(
                        "customStep", "registry-custom-step",
                        YamlDeserializerResolver.ORDER_DEFAULT + 1));

        loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep: {}
                """);

        var step = (StepDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(step.getId()).isEqualTo("registry-custom-step");
    }

    @Test
    void lowerOrderClasspathResolverWinsOverHigherOrderRegistryResolver() throws Exception {
        context.getRegistry().bind("registryCustomStepResolver",
                new FixedStepResolver(
                        "customStep", "registry-custom-step",
                        YamlDeserializerResolver.ORDER_DEFAULT + 2));

        loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep: {}
                """);

        var step = (StepDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(step.getId()).isEqualTo("classpath-custom-step");
    }

    @Test
    void sameOrderClasspathResolversUseDeterministicClassNameOrdering() throws Exception {
        loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - sameOrderStep: {}
                """);

        var step = (StepDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(step.getId()).isEqualTo("alpha-same-order");
    }

    @Test
    void resolverOrderChoosesHighestPrecedenceResolver() throws Exception {
        context.getRegistry().bind("lowestOrderResolver",
                new FixedStepResolver("orderedStep", "lowest-order", YamlDeserializerResolver.ORDER_LOWEST));
        context.getRegistry().bind("defaultOrderResolver",
                new FixedStepResolver("orderedStep", "default-order", YamlDeserializerResolver.ORDER_DEFAULT));
        context.getRegistry().bind("highestOrderResolver",
                new FixedStepResolver("orderedStep", "highest-order", YamlDeserializerResolver.ORDER_HIGHEST));

        loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - orderedStep: {}
                """);

        var step = (StepDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(step.getId()).isEqualTo("highest-order");
    }

    @Test
    void sameClassSameOrderRegistryResolversUseRegistryNameTieBreaker() throws Exception {
        context.getRegistry().bind("alphaRegistryResolver",
                new FixedStepResolver(
                        "sameClassRegistryStep", "alpha-registry",
                        YamlDeserializerResolver.ORDER_DEFAULT + 1));
        context.getRegistry().bind("betaRegistryResolver",
                new FixedStepResolver(
                        "sameClassRegistryStep", "beta-registry",
                        YamlDeserializerResolver.ORDER_DEFAULT + 1));

        loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - sameClassRegistryStep: {}
                """);

        var step = (StepDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(step.getId()).isEqualTo("alpha-registry");
    }

    @Test
    void defaultOrderClasspathResolverDoesNotShadowGeneratedBuiltInStep() throws Exception {
        loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - to:
                          uri: "mock:result"
                """);

        var to = (ToDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(to.getEndpointUri()).isEqualTo("mock:result");
    }

    @Test
    void explicitLowerOrderRegistryResolverCanOverrideGeneratedBuiltInStep() throws Exception {
        context.getRegistry().bind("overrideToResolver",
                new FixedStepResolver("to", "override-to", YamlDeserializerResolver.ORDER_DEFAULT - 1));

        loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - to:
                          uri: "mock:result"
                """);

        var step = (StepDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(step.getId()).isEqualTo("override-to");
    }

    @Test
    void applicationContextClassloaderResolverResourceIsDiscovered() throws Exception {
        URLClassLoader classLoader = resolverResourceClassLoader(
                ApplicationContextStepResolver.class.getName() + " # inline comments are allowed");
        context.setApplicationContextClassLoader(classLoader);
        try {
            loadRoutesNoValidate("""
                    - from:
                        uri: "direct:start"
                        steps:
                          - applicationStep: {}
                    """);

            var step = (StepDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
            assertThat(step.getId()).isEqualTo("application-context-step");
        } finally {
            classLoader.close();
        }
    }

    @Test
    void classResolverClassloaderResolverResourceIsDiscovered() throws Exception {
        URLClassLoader classLoader = resolverResourceClassLoader(ClassResolverStepResolver.class.getName());
        context.getClassResolver().addClassLoader(classLoader);
        try {
            loadRoutesNoValidate("""
                    - from:
                        uri: "direct:start"
                        steps:
                          - classResolverStep: {}
                    """);

            var step = (StepDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
            assertThat(step.getId()).isEqualTo("class-resolver-step");
        } finally {
            classLoader.close();
        }
    }

    @Test
    void providerDiscoveredResolverReceivesCamelContext() throws Exception {
        context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                new StaticResolverProvider(
                        "contextAwareStepResolver",
                        new ContextAwareStepResolver(context)));

        loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - contextAwareStep: {}
                """);

        var step = (StepDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(step.getId()).isEqualTo("context-aware-step");
    }

    @Test
    void contextPluginProviderContributesCustomRouteStep() throws Exception {
        context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                new StaticResolverProvider(
                        "providerCustomStepResolver",
                        new FixedStepResolver(
                                "providerCustomStep", "provider-custom-step",
                                YamlDeserializerResolver.ORDER_DEFAULT + 1)));

        loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - providerCustomStep: {}
                """);

        var step = (StepDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(step.getId()).isEqualTo("provider-custom-step");
    }

    @Test
    void contextPluginProviderReplacesDefaultClasspathDiscovery() {
        context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                new StaticResolverProvider(Map.of()));

        var e = assertThrows(UnknownNodeIdException.class, () -> loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep: {}
                """));
        assertThat(e.getMessage().contains("Unknown node id: customStep")).isTrue();
    }

    @Test
    void registryResolverStillLoadsWhenContextPluginProviderIsInstalled() throws Exception {
        context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                new StaticResolverProvider(Map.of()));
        context.getRegistry().bind("registryStepResolver",
                new FixedStepResolver("registryStep", "registry-step", YamlDeserializerResolver.ORDER_DEFAULT));

        loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - registryStep: {}
                """);

        var step = (StepDefinition) context.getRouteDefinitions().get(0).getOutputs().get(0);
        assertThat(step.getId()).isEqualTo("registry-step");
    }

    @Test
    void providerNullResolverMapIsRejectedWithDiagnostic() {
        context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                new NullMapResolverProvider());

        var e = assertThrows(YamlDeserializationException.class, () -> loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - to:
                          uri: "mock:result"
                """));
        assertThat(e.getMessage().contains("PROVIDER")).isTrue();
        assertThat(e.getMessage().contains("null resolver map")).isTrue();
    }

    @Test
    void providerNullResolverNameIsRejectedWithDiagnostic() {
        context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                new StaticResolverProvider(
                        Collections.singletonMap(null,
                                new FixedStepResolver("badStep", "bad-step", YamlDeserializerResolver.ORDER_DEFAULT))));

        var e = assertThrows(YamlDeserializationException.class, () -> loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - to:
                          uri: "mock:result"
                """));
        assertThat(e.getMessage().contains("PROVIDER")).isTrue();
        assertThat(e.getMessage().contains("null resolver name")).isTrue();
    }

    @Test
    void providerNullResolverValueIsRejectedWithDiagnostic() {
        context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                new StaticResolverProvider(Collections.singletonMap("nullResolver", null)));

        var e = assertThrows(YamlDeserializationException.class, () -> loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - to:
                          uri: "mock:result"
                """));
        assertThat(e.getMessage().contains("PROVIDER")).isTrue();
        assertThat(e.getMessage().contains("null resolver for nullResolver")).isTrue();
    }

    @Test
    void kameletRouteTemplateDiscoversCustomRouteStep() throws Exception {
        loadKamelets("""
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: custom-step-source
                spec:
                  definition:
                    title: "Custom Step Source"
                    type: object
                    properties: {}
                  template:
                    from:
                      uri: "kamelet:source"
                      steps:
                        - customStep:
                            id: "kamelet-custom-step"
                            steps:
                              - to:
                                  uri: "mock:result"
                """);

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);
        var step = (StepDefinition) context.getRouteTemplateDefinitions().get(0).getRoute().getOutputs().get(0);
        assertThat(step.getId()).isEqualTo("kamelet-custom-step");
        assertThat(((ToDefinition) step.getOutputs().get(0)).getEndpointUri()).isEqualTo("mock:result");
    }

    @Test
    void regularRouteTemplateDiscoversCustomRouteStep() throws Exception {
        loadRoutesNoValidate("""
                - routeTemplate:
                    id: "customTemplate"
                    from:
                      uri: "direct:{{name}}"
                      steps:
                        - customStep:
                            id: "template-custom-step"
                            steps:
                              - to:
                                  uri: "mock:result"
                """);

        assertThat(context.getRouteTemplateDefinitions().size()).isEqualTo(1);
        var step = (StepDefinition) context.getRouteTemplateDefinitions().get(0).getRoute().getOutputs().get(0);
        assertThat(step.getId()).isEqualTo("template-custom-step");
        assertThat(((ToDefinition) step.getOutputs().get(0)).getEndpointUri()).isEqualTo("mock:result");
    }

    @Test
    void routeConfigurationDiscoversCustomRouteStep() throws Exception {
        loadRoutesNoValidate("""
                - routeConfiguration:
                    onCompletion:
                      - onCompletion:
                          steps:
                            - customStep:
                                id: "route-configuration-custom-step"
                                steps:
                                  - to:
                                      uri: "mock:on-completion"
                """);

        assertThat(context.getRouteConfigurationDefinitions().size()).isEqualTo(1);
        var step = (StepDefinition) context.getRouteConfigurationDefinitions().get(0)
                .getOnCompletions().get(0).getOutputs().get(0);
        assertThat(step.getId()).isEqualTo("route-configuration-custom-step");
        assertThat(((ToDefinition) step.getOutputs().get(0)).getEndpointUri()).isEqualTo("mock:on-completion");
    }

    @Test
    void unknownCustomStepReportsNodeId() {
        var e = assertThrows(UnknownNodeIdException.class, () -> loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - missingOptionalStep: {}
                """));
        assertThat(e.getMessage().contains("Unknown node id: missingOptionalStep")).isTrue();
    }

    @Test
    void customRouteStepWithDuplicateSiblingKeysReportsDuplicateKey() {
        assertThrows(DuplicateKeyException.class, () -> loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep: {}
                        to:
                          uri: "mock:result"
                """));
    }

    @Test
    void malformedCustomRouteStepScalarShapeReportsCustomNodeId() {
        var e = assertThrows(YamlDeserializationException.class, () -> loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep: "not-a-map"
                """));
        assertThat(e.getMessage().contains("Error constructing YAML node id: customStep")).isTrue();
        assertThat(e.getCause()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void malformedCustomRouteStepIdShapeReportsCustomNodeId() {
        var e = assertThrows(YamlDeserializationException.class, () -> loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep:
                          id:
                            - bad
                """));
        assertThat(e.getMessage().contains("Error constructing YAML node id: customStep")).isTrue();
    }

    @Test
    void malformedCustomRouteStepStepsShapeReportsExpectedSequence() {
        var e = assertThrows(YamlDeserializationException.class, () -> loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep:
                          steps: "not-a-list"
                """));
        assertThat(e.getMessage().contains("Error constructing YAML node id: customStep")).isTrue();
        assertThat(e.getCause().getMessage().contains("expected array")).isTrue();
    }

    @Test
    void resolverFailureReportsResolverAndNodeId() {
        context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                new StaticResolverProvider("throwingStepResolver", new ThrowingStepResolver()));

        var e = assertThrows(YamlDeserializationException.class, () -> loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - failingStep: {}
                """));
        assertThat(e.getMessage().contains("Error resolving YAML node id: failingStep")).isTrue();
        assertThat(e.getMessage().contains("throwingStepResolver")).isTrue();
        assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void customStepConstructorFailureReportsCustomNodeId() {
        context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                new StaticResolverProvider(
                        "failingConstructorStepResolver",
                        new FailingConstructorStepResolver()));

        var e = assertThrows(YamlDeserializationException.class, () -> loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - constructFailureStep: {}
                """));
        assertThat(e.getMessage().contains("Error constructing YAML node id: constructFailureStep")).isTrue();
        assertThat(e.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void brokenResolverServiceEntryReportsProviderClass() throws Exception {
        URLClassLoader classLoader = resolverResourceClassLoader("com.acme.camel.MissingYamlStepResolver");
        context.setApplicationContextClassLoader(classLoader);
        try {
            var e = assertThrows(YamlDeserializationException.class, () -> loadRoutesNoValidate("""
                    - from:
                        uri: "direct:start"
                        steps:
                          - to:
                              uri: "mock:result"
                    """));
            assertThat(e.getMessage().contains("com.acme.camel.MissingYamlStepResolver")).isTrue();
            assertThat(e.getMessage().contains(YamlDeserializerResolver.RESOURCE_PATH)).isTrue();
        } finally {
            classLoader.close();
        }
    }

    @Test
    void unreadableResolverResourceReportsSanitizedResourceLocation() throws Exception {
        context.setApplicationContextClassLoader(unreadableResolverResourceClassLoader());

        var e = assertThrows(YamlDeserializationException.class, () -> loadRoutesNoValidate("""
                - from:
                    uri: "direct:start"
                    steps:
                      - to:
                          uri: "mock:result"
                """));
        assertThat(e.getMessage().contains(YamlDeserializerResolver.RESOURCE_PATH)).isTrue();
        assertThat(e.getMessage().contains("resolver resource")).isTrue();
        assertThat(!e.getMessage().contains("user:secret")).isTrue();
        assertThat(!e.getMessage().contains("internal.example.local")).isTrue();
        assertThat(!e.getMessage().contains("very-secret-path")).isTrue();
        assertThat(!e.getMessage().contains("token=")).isTrue();
        assertThat(!e.getMessage().contains("fragment")).isTrue();
    }

    // ==================== Helper methods ====================

    private static URLClassLoader resolverResourceClassLoader(String resolverLine) throws Exception {
        Path root = Files.createTempDirectory("yaml-resolver-provider");
        Path serviceDirectory = root.resolve("META-INF/services/org/apache/camel");
        Files.createDirectories(serviceDirectory);
        Files.writeString(
                serviceDirectory.resolve("YamlDeserializerResolver"),
                resolverLine + System.lineSeparator(),
                StandardCharsets.UTF_8);
        return new URLClassLoader(
                new URL[] { root.toUri().toURL() },
                Thread.currentThread().getContextClassLoader());
    }

    private static ClassLoader unreadableResolverResourceClassLoader() throws Exception {
        URL resolverResource = new URL(
                null,
                "resolver://user:secret@internal.example.local/very-secret-path?token=abc#fragment",
                new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL url) {
                        return new URLConnection(url) {
                            @Override
                            public void connect() {
                            }

                            @Override
                            public InputStream getInputStream() throws IOException {
                                throw new IOException("cannot read resolver resource");
                            }
                        };
                    }
                });

        return new ClassLoader(Thread.currentThread().getContextClassLoader()) {
            @Override
            public Enumeration<URL> getResources(String name) throws IOException {
                if (name.equals(YamlDeserializerResolver.RESOURCE_PATH)) {
                    return Collections.enumeration(Collections.singletonList(resolverResource));
                }
                return super.getResources(name);
            }
        };
    }

    // ==================== Static inner classes ====================

    public static class CustomStepResolver implements YamlDeserializerResolver {
        @Override
        public int getOrder() {
            return YamlDeserializerResolver.ORDER_DEFAULT + 1;
        }

        @Override
        public ConstructNode resolve(String id) {
            if ("customStep".equals(id)) {
                return new CustomStepDeserializer("classpath-custom-step");
            }
            return null;
        }
    }

    public static class LowerPrecedenceCustomStepResolver implements YamlDeserializerResolver {
        @Override
        public int getOrder() {
            return YamlDeserializerResolver.ORDER_DEFAULT + 2;
        }

        @Override
        public ConstructNode resolve(String id) {
            if ("customStep".equals(id)) {
                return new LowerPrecedenceCustomStepDeserializer();
            }
            return null;
        }
    }

    public static class CustomStepDeserializer extends YamlDeserializerBase<StepDefinition> {
        private final String defaultId;

        public CustomStepDeserializer(String defaultId) {
            super(StepDefinition.class);
            this.defaultId = defaultId;
        }

        @Override
        protected StepDefinition newInstance() {
            StepDefinition step = new StepDefinition();
            step.setId(defaultId);
            return step;
        }

        @Override
        protected boolean setProperty(StepDefinition target, String propertyKey, String propertyName, Node value) {
            switch (propertyKey) {
                case "id":
                    target.setId(asText(value));
                    break;
                case "steps":
                    setSteps(target, value);
                    break;
                default:
                    return false;
            }
            return true;
        }
    }

    public static class LowerPrecedenceCustomStepDeserializer extends YamlDeserializerBase<StepDefinition> {
        public LowerPrecedenceCustomStepDeserializer() {
            super(StepDefinition.class);
        }

        @Override
        protected StepDefinition newInstance() {
            StepDefinition step = new StepDefinition();
            step.setId("lower-precedence");
            return step;
        }

        @Override
        protected boolean setProperty(StepDefinition target, String propertyKey, String propertyName, Node value) {
            return true;
        }
    }

    public static class FixedStepResolver implements YamlDeserializerResolver {
        private final String stepName;
        private final String stepId;
        private final int order;

        public FixedStepResolver(String stepName, String stepId, int order) {
            this.stepName = stepName;
            this.stepId = stepId;
            this.order = order;
        }

        @Override
        public int getOrder() {
            return order;
        }

        @Override
        public ConstructNode resolve(String id) {
            if (stepName.equals(id)) {
                return new FixedStepDeserializer(stepId);
            }
            return null;
        }
    }

    public static class FixedStepDeserializer extends YamlDeserializerBase<StepDefinition> {
        private final String stepId;

        public FixedStepDeserializer(String stepId) {
            super(StepDefinition.class);
            this.stepId = stepId;
        }

        @Override
        protected StepDefinition newInstance() {
            StepDefinition step = new StepDefinition();
            step.setId(stepId);
            return step;
        }

        @Override
        protected boolean setProperty(StepDefinition target, String propertyKey, String propertyName, Node value) {
            return true;
        }
    }

    public static class AlphaSameOrderStepResolver extends FixedStepResolver {
        public AlphaSameOrderStepResolver() {
            super("sameOrderStep", "alpha-same-order", YamlDeserializerResolver.ORDER_DEFAULT + 1);
        }
    }

    public static class BetaSameOrderStepResolver extends FixedStepResolver {
        public BetaSameOrderStepResolver() {
            super("sameOrderStep", "beta-same-order", YamlDeserializerResolver.ORDER_DEFAULT + 1);
        }
    }

    public static class GeneratedToCollisionResolver extends FixedStepResolver {
        public GeneratedToCollisionResolver() {
            super("to", "shadowed-to", YamlDeserializerResolver.ORDER_DEFAULT + 1);
        }
    }

    public static class ApplicationContextStepResolver extends FixedStepResolver {
        public ApplicationContextStepResolver() {
            super("applicationStep", "application-context-step", YamlDeserializerResolver.ORDER_DEFAULT + 1);
        }
    }

    public static class ClassResolverStepResolver extends FixedStepResolver {
        public ClassResolverStepResolver() {
            super("classResolverStep", "class-resolver-step", YamlDeserializerResolver.ORDER_DEFAULT + 1);
        }
    }

    public static class ContextAwareStepResolver extends FixedStepResolver implements CamelContextAware {
        private final CamelContext expectedCamelContext;
        private CamelContext camelContext;

        public ContextAwareStepResolver(CamelContext expectedCamelContext) {
            super("contextAwareStep", "context-aware-step", YamlDeserializerResolver.ORDER_DEFAULT + 1);
            this.expectedCamelContext = expectedCamelContext;
        }

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        @Override
        public ConstructNode resolve(String id) {
            if (camelContext == expectedCamelContext) {
                return super.resolve(id);
            }
            return null;
        }
    }

    public static class ThrowingStepResolver implements YamlDeserializerResolver {
        @Override
        public ConstructNode resolve(String id) {
            if ("failingStep".equals(id)) {
                throw new IllegalStateException("resolver boom");
            }
            return null;
        }
    }

    public static class FailingConstructorStepResolver implements YamlDeserializerResolver {
        @Override
        public ConstructNode resolve(String id) {
            if ("constructFailureStep".equals(id)) {
                return new FailingConstructor();
            }
            return null;
        }
    }

    public static class FailingConstructor implements ConstructNode {
        @Override
        public Object construct(Node node) {
            throw new IllegalStateException("constructor boom");
        }
    }

    public static class NullMapResolverProvider implements YamlDeserializerResolverProvider {
        @Override
        public Map<String, YamlDeserializerResolver> findResolvers(CamelContext camelContext) {
            return null;
        }
    }

    public static class StaticResolverProvider implements YamlDeserializerResolverProvider {
        private final Map<String, YamlDeserializerResolver> resolvers;

        public StaticResolverProvider(String name, YamlDeserializerResolver resolver) {
            this(Map.of(name, resolver));
        }

        public StaticResolverProvider(Map<String, YamlDeserializerResolver> resolvers) {
            this.resolvers = resolvers;
        }

        @Override
        public Map<String, YamlDeserializerResolver> findResolvers(CamelContext camelContext) {
            return resolvers;
        }
    }
}
