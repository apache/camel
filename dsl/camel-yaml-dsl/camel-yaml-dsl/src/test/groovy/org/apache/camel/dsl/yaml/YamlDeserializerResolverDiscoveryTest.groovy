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
package org.apache.camel.dsl.yaml

import org.apache.camel.CamelContextAware
import org.apache.camel.CamelContext
import org.apache.camel.component.mock.MockEndpoint
import org.apache.camel.dsl.yaml.common.YamlDeserializerBase
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolver
import org.apache.camel.dsl.yaml.common.YamlDeserializerResolverProvider
import org.apache.camel.dsl.yaml.common.exception.DuplicateKeyException
import org.apache.camel.dsl.yaml.common.exception.UnknownNodeIdException
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException
import org.apache.camel.dsl.yaml.support.YamlTestSupport
import org.apache.camel.model.LogDefinition
import org.apache.camel.model.StepDefinition
import org.apache.camel.model.ToDefinition
import org.snakeyaml.engine.v2.api.ConstructNode
import org.snakeyaml.engine.v2.nodes.Node

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import static org.apache.camel.dsl.yaml.common.YamlDeserializerSupport.asText

class YamlDeserializerResolverDiscoveryTest extends YamlTestSupport {

    def "discover custom route step resolver from classpath"() {
        when:
            loadRoutesNoValidate '''
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
            '''

        then:
            with(context.routeDefinitions[0].outputs[0], StepDefinition) {
                id == 'custom-step'

                with(outputs[0], LogDefinition) {
                    message == 'nested'
                }
                with(outputs[1], ToDefinition) {
                    endpointUri == 'mock:result'
                }
            }
    }

    def "discovered custom route step executes through nested outputs"() {
        given:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep:
                          steps:
                            - to:
                                uri: "mock:result"
            '''

            withMock('mock:result') {
                expectedBodiesReceived 'hello'
            }

        when:
            context.start()
            withTemplate {
                to('direct:start').withBody('hello').send()
            }

        then:
            MockEndpoint.assertIsSatisfied(context)
    }

    def "registry resolver contributes custom route step"() {
        given:
            context.registry.bind('registryStepResolver',
                    new FixedStepResolver('registryStep', 'registry-step', YamlDeserializerResolver.ORDER_DEFAULT))

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - registryStep: {}
            '''

        then:
            with(context.routeDefinitions[0].outputs[0], StepDefinition) {
                id == 'registry-step'
            }
    }

    def "registry resolver wins over classpath resolver at same order"() {
        given:
            context.registry.bind('registryCustomStepResolver',
                    new FixedStepResolver('customStep', 'registry-custom-step', YamlDeserializerResolver.ORDER_DEFAULT + 1))

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep: {}
            '''

        then:
            with(context.routeDefinitions[0].outputs[0], StepDefinition) {
                id == 'registry-custom-step'
            }
    }

    def "lower order classpath resolver wins over higher order registry resolver"() {
        given:
            context.registry.bind('registryCustomStepResolver',
                    new FixedStepResolver('customStep', 'registry-custom-step', YamlDeserializerResolver.ORDER_DEFAULT + 2))

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep: {}
            '''

        then:
            with(context.routeDefinitions[0].outputs[0], StepDefinition) {
                id == 'classpath-custom-step'
            }
    }

    def "same order classpath resolvers use deterministic class name ordering"() {
        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - sameOrderStep: {}
            '''

        then:
            with(context.routeDefinitions[0].outputs[0], StepDefinition) {
                id == 'alpha-same-order'
            }
    }

    def "resolver order chooses highest precedence resolver"() {
        given:
            context.registry.bind('lowestOrderResolver',
                    new FixedStepResolver('orderedStep', 'lowest-order', YamlDeserializerResolver.ORDER_LOWEST))
            context.registry.bind('defaultOrderResolver',
                    new FixedStepResolver('orderedStep', 'default-order', YamlDeserializerResolver.ORDER_DEFAULT))
            context.registry.bind('highestOrderResolver',
                    new FixedStepResolver('orderedStep', 'highest-order', YamlDeserializerResolver.ORDER_HIGHEST))

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - orderedStep: {}
            '''

        then:
            with(context.routeDefinitions[0].outputs[0], StepDefinition) {
                id == 'highest-order'
            }
    }

    def "same class same order registry resolvers use registry name tie breaker"() {
        given:
            context.registry.bind('alphaRegistryResolver',
                    new FixedStepResolver('sameClassRegistryStep', 'alpha-registry', YamlDeserializerResolver.ORDER_DEFAULT + 1))
            context.registry.bind('betaRegistryResolver',
                    new FixedStepResolver('sameClassRegistryStep', 'beta-registry', YamlDeserializerResolver.ORDER_DEFAULT + 1))

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - sameClassRegistryStep: {}
            '''

        then:
            with(context.routeDefinitions[0].outputs[0], StepDefinition) {
                id == 'alpha-registry'
            }
    }

    def "default order classpath resolver does not shadow generated built in step"() {
        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - to:
                          uri: "mock:result"
            '''

        then:
            with(context.routeDefinitions[0].outputs[0], ToDefinition) {
                endpointUri == 'mock:result'
            }
    }

    def "explicit lower order registry resolver can override generated built in step"() {
        given:
            context.registry.bind('overrideToResolver',
                    new FixedStepResolver('to', 'override-to', YamlDeserializerResolver.ORDER_DEFAULT - 1))

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - to:
                          uri: "mock:result"
            '''

        then:
            with(context.routeDefinitions[0].outputs[0], StepDefinition) {
                id == 'override-to'
            }
    }

    def "application context classloader resolver resource is discovered"() {
        given:
            def classLoader = resolverResourceClassLoader(
                    ApplicationContextStepResolver.name + ' # inline comments are allowed')
            context.applicationContextClassLoader = classLoader

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - applicationStep: {}
            '''

        then:
            with(context.routeDefinitions[0].outputs[0], StepDefinition) {
                id == 'application-context-step'
            }

        cleanup:
            classLoader?.close()
    }

    def "class resolver classloader resolver resource is discovered"() {
        given:
            def classLoader = resolverResourceClassLoader(ClassResolverStepResolver.name)
            context.classResolver.addClassLoader(classLoader)

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - classResolverStep: {}
            '''

        then:
            with(context.routeDefinitions[0].outputs[0], StepDefinition) {
                id == 'class-resolver-step'
            }

        cleanup:
            classLoader?.close()
    }

    def "provider discovered resolver receives camel context"() {
        given:
            context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                    new StaticResolverProvider('contextAwareStepResolver', new ContextAwareStepResolver(context)))

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - contextAwareStep: {}
            '''

        then:
            with(context.routeDefinitions[0].outputs[0], StepDefinition) {
                id == 'context-aware-step'
            }
    }

    def "context plugin provider contributes custom route step"() {
        given:
            context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                    new StaticResolverProvider('providerCustomStepResolver',
                            new FixedStepResolver('providerCustomStep', 'provider-custom-step',
                                    YamlDeserializerResolver.ORDER_DEFAULT + 1)))

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - providerCustomStep: {}
            '''

        then:
            with(context.routeDefinitions[0].outputs[0], StepDefinition) {
                id == 'provider-custom-step'
            }
    }

    def "context plugin provider replaces default classpath discovery"() {
        given:
            context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                    new StaticResolverProvider([:]))

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep: {}
            '''

        then:
            def e = thrown(UnknownNodeIdException)
            e.message.contains('Unknown node id: customStep')
    }

    def "registry resolver still loads when context plugin provider is installed"() {
        given:
            context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                    new StaticResolverProvider([:]))
            context.registry.bind('registryStepResolver',
                    new FixedStepResolver('registryStep', 'registry-step', YamlDeserializerResolver.ORDER_DEFAULT))

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - registryStep: {}
            '''

        then:
            with(context.routeDefinitions[0].outputs[0], StepDefinition) {
                id == 'registry-step'
            }
    }

    def "provider null resolver map is rejected with diagnostic"() {
        given:
            context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                    new NullMapResolverProvider())

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - to:
                          uri: "mock:result"
            '''

        then:
            def e = thrown(YamlDeserializationException)
            e.message.contains('PROVIDER')
            e.message.contains('null resolver map')
    }

    def "provider null resolver name is rejected with diagnostic"() {
        given:
            context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                    new StaticResolverProvider([(null): new FixedStepResolver('badStep', 'bad-step',
                            YamlDeserializerResolver.ORDER_DEFAULT)]))

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - to:
                          uri: "mock:result"
            '''

        then:
            def e = thrown(YamlDeserializationException)
            e.message.contains('PROVIDER')
            e.message.contains('null resolver name')
    }

    def "provider null resolver value is rejected with diagnostic"() {
        given:
            context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                    new StaticResolverProvider(['nullResolver': null]))

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - to:
                          uri: "mock:result"
            '''

        then:
            def e = thrown(YamlDeserializationException)
            e.message.contains('PROVIDER')
            e.message.contains('null resolver for nullResolver')
    }

    def "kamelet route template discovers custom route step"() {
        when:
            loadKamelets '''
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
            '''

        then:
            context.routeTemplateDefinitions.size() == 1
            with(context.routeTemplateDefinitions[0].route.outputs[0], StepDefinition) {
                id == 'kamelet-custom-step'
                with(outputs[0], ToDefinition) {
                    endpointUri == 'mock:result'
                }
            }
    }

    def "regular route template discovers custom route step"() {
        when:
            loadRoutesNoValidate '''
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
            '''

        then:
            context.routeTemplateDefinitions.size() == 1
            with(context.routeTemplateDefinitions[0].route.outputs[0], StepDefinition) {
                id == 'template-custom-step'
                with(outputs[0], ToDefinition) {
                    endpointUri == 'mock:result'
                }
            }
    }

    def "route configuration discovers custom route step"() {
        when:
            loadRoutesNoValidate '''
                - routeConfiguration:
                    onCompletion:
                      - onCompletion:
                          steps:
                            - customStep:
                                id: "route-configuration-custom-step"
                                steps:
                                  - to:
                                      uri: "mock:on-completion"
            '''

        then:
            context.getRouteConfigurationDefinitions().size() == 1
            with(context.getRouteConfigurationDefinitions().get(0).getOnCompletions().get(0).outputs[0], StepDefinition) {
                id == 'route-configuration-custom-step'
                with(outputs[0], ToDefinition) {
                    endpointUri == 'mock:on-completion'
                }
            }
    }

    def "unknown custom step reports node id"() {
        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - missingOptionalStep: {}
            '''

        then:
            def e = thrown(UnknownNodeIdException)
            e.message.contains('Unknown node id: missingOptionalStep')
    }

    def "custom route step with duplicate sibling keys reports duplicate key"() {
        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep: {}
                        to:
                          uri: "mock:result"
            '''

        then:
            thrown(DuplicateKeyException)
    }

    def "malformed custom route step scalar shape reports custom node id"() {
        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep: "not-a-map"
            '''

        then:
            def e = thrown(YamlDeserializationException)
            e.message.contains('Error constructing YAML node id: customStep')
            e.cause instanceof UnsupportedOperationException
    }

    def "malformed custom route step id shape reports custom node id"() {
        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep:
                          id:
                            - bad
            '''

        then:
            def e = thrown(YamlDeserializationException)
            e.message.contains('Error constructing YAML node id: customStep')
    }

    def "malformed custom route step steps shape reports expected sequence"() {
        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - customStep:
                          steps: "not-a-list"
            '''

        then:
            def e = thrown(YamlDeserializationException)
            e.message.contains('Error constructing YAML node id: customStep')
            e.cause.message.contains('expected array')
    }

    def "resolver failure reports resolver and node id"() {
        given:
            context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                    new StaticResolverProvider('throwingStepResolver', new ThrowingStepResolver()))

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - failingStep: {}
            '''

        then:
            def e = thrown(YamlDeserializationException)
            e.message.contains('Error resolving YAML node id: failingStep')
            e.message.contains('throwingStepResolver')
            e.cause instanceof IllegalStateException
    }

    def "custom step constructor failure reports custom node id"() {
        given:
            context.getCamelContextExtension().addContextPlugin(YamlDeserializerResolverProvider.class,
                    new StaticResolverProvider('failingConstructorStepResolver', new FailingConstructorStepResolver()))

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - constructFailureStep: {}
            '''

        then:
            def e = thrown(YamlDeserializationException)
            e.message.contains('Error constructing YAML node id: constructFailureStep')
            e.cause instanceof IllegalStateException
    }

    def "broken resolver service entry reports provider class"() {
        given:
            def classLoader = resolverResourceClassLoader('com.acme.camel.MissingYamlStepResolver')
            context.applicationContextClassLoader = classLoader

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - to:
                          uri: "mock:result"
            '''

        then:
            def e = thrown(YamlDeserializationException)
            e.message.contains('com.acme.camel.MissingYamlStepResolver')
            e.message.contains(YamlDeserializerResolver.RESOURCE_PATH)

        cleanup:
            classLoader?.close()
    }

    def "unreadable resolver resource reports sanitized resource location"() {
        given:
            context.applicationContextClassLoader = unreadableResolverResourceClassLoader()

        when:
            loadRoutesNoValidate '''
                - from:
                    uri: "direct:start"
                    steps:
                      - to:
                          uri: "mock:result"
            '''

        then:
            def e = thrown(YamlDeserializationException)
            e.message.contains(YamlDeserializerResolver.RESOURCE_PATH)
            e.message.contains('resolver resource')
            !e.message.contains('user:secret')
            !e.message.contains('internal.example.local')
            !e.message.contains('very-secret-path')
            !e.message.contains('token=')
            !e.message.contains('fragment')
    }

    private static URLClassLoader resolverResourceClassLoader(String resolverLine) {
        Path root = Files.createTempDirectory('yaml-resolver-provider')
        Path serviceDirectory = root.resolve('META-INF/services/org/apache/camel')
        Files.createDirectories(serviceDirectory)
        Files.writeString(
                serviceDirectory.resolve('YamlDeserializerResolver'),
                resolverLine + System.lineSeparator(),
                StandardCharsets.UTF_8)
        return new URLClassLoader([root.toUri().toURL()] as URL[], Thread.currentThread().contextClassLoader)
    }

    private static ClassLoader unreadableResolverResourceClassLoader() {
        URL resolverResource = new URL(null,
                'resolver://user:secret@internal.example.local/very-secret-path?token=abc#fragment',
                new URLStreamHandler() {
                    @Override
                    protected URLConnection openConnection(URL url) {
                        return new URLConnection(url) {
                            @Override
                            void connect() {
                            }

                            @Override
                            InputStream getInputStream() {
                                throw new IOException('cannot read resolver resource')
                            }
                        }
                    }
                })

        return new ClassLoader(Thread.currentThread().contextClassLoader) {
            @Override
            Enumeration<URL> getResources(String name) throws IOException {
                if (name == YamlDeserializerResolver.RESOURCE_PATH) {
                    return Collections.enumeration([resolverResource])
                }
                return super.getResources(name)
            }
        }
    }

    static class CustomStepResolver implements YamlDeserializerResolver {
        @Override
        int getOrder() {
            return YamlDeserializerResolver.ORDER_DEFAULT + 1
        }

        @Override
        ConstructNode resolve(String id) {
            if (id == 'customStep') {
                return new CustomStepDeserializer('classpath-custom-step')
            }
            return null
        }
    }

    static class LowerPrecedenceCustomStepResolver implements YamlDeserializerResolver {
        @Override
        int getOrder() {
            return YamlDeserializerResolver.ORDER_DEFAULT + 2
        }

        @Override
        ConstructNode resolve(String id) {
            if (id == 'customStep') {
                return new LowerPrecedenceCustomStepDeserializer()
            }
            return null
        }
    }

    static class CustomStepDeserializer extends YamlDeserializerBase<StepDefinition> {
        private final String defaultId

        CustomStepDeserializer(String defaultId) {
            super(StepDefinition.class)
            this.defaultId = defaultId
        }

        @Override
        protected StepDefinition newInstance() {
            def step = new StepDefinition()
            step.setId(defaultId)
            return step
        }

        @Override
        protected boolean setProperty(StepDefinition target, String propertyKey, String propertyName, Node value) {
            switch (propertyKey) {
                case 'id':
                    target.setId(asText(value))
                    break
                case 'steps':
                    setSteps(target, value)
                    break
                default:
                    return false
            }
            return true
        }
    }

    static class LowerPrecedenceCustomStepDeserializer extends YamlDeserializerBase<StepDefinition> {
        LowerPrecedenceCustomStepDeserializer() {
            super(StepDefinition.class)
        }

        @Override
        protected StepDefinition newInstance() {
            def step = new StepDefinition()
            step.setId('lower-precedence')
            return step
        }

        @Override
        protected boolean setProperty(StepDefinition target, String propertyKey, String propertyName, Node value) {
            return true
        }
    }

    static class FixedStepResolver implements YamlDeserializerResolver {
        private final String stepName
        private final String stepId
        private final int order

        FixedStepResolver(String stepName, String stepId, int order) {
            this.stepName = stepName
            this.stepId = stepId
            this.order = order
        }

        @Override
        int getOrder() {
            return order
        }

        @Override
        ConstructNode resolve(String id) {
            if (id == stepName) {
                return new FixedStepDeserializer(stepId)
            }
            return null
        }
    }

    static class FixedStepDeserializer extends YamlDeserializerBase<StepDefinition> {
        private final String stepId

        FixedStepDeserializer(String stepId) {
            super(StepDefinition.class)
            this.stepId = stepId
        }

        @Override
        protected StepDefinition newInstance() {
            def step = new StepDefinition()
            step.setId(stepId)
            return step
        }

        @Override
        protected boolean setProperty(StepDefinition target, String propertyKey, String propertyName, Node value) {
            return true
        }
    }

    static class AlphaSameOrderStepResolver extends FixedStepResolver {
        AlphaSameOrderStepResolver() {
            super('sameOrderStep', 'alpha-same-order', YamlDeserializerResolver.ORDER_DEFAULT + 1)
        }
    }

    static class BetaSameOrderStepResolver extends FixedStepResolver {
        BetaSameOrderStepResolver() {
            super('sameOrderStep', 'beta-same-order', YamlDeserializerResolver.ORDER_DEFAULT + 1)
        }
    }

    static class GeneratedToCollisionResolver extends FixedStepResolver {
        GeneratedToCollisionResolver() {
            super('to', 'shadowed-to', YamlDeserializerResolver.ORDER_DEFAULT + 1)
        }
    }

    static class ApplicationContextStepResolver extends FixedStepResolver {
        ApplicationContextStepResolver() {
            super('applicationStep', 'application-context-step', YamlDeserializerResolver.ORDER_DEFAULT + 1)
        }
    }

    static class ClassResolverStepResolver extends FixedStepResolver {
        ClassResolverStepResolver() {
            super('classResolverStep', 'class-resolver-step', YamlDeserializerResolver.ORDER_DEFAULT + 1)
        }
    }

    static class ContextAwareStepResolver extends FixedStepResolver implements CamelContextAware {
        private final CamelContext expectedCamelContext
        private CamelContext camelContext

        ContextAwareStepResolver(CamelContext expectedCamelContext) {
            super('contextAwareStep', 'context-aware-step', YamlDeserializerResolver.ORDER_DEFAULT + 1)
            this.expectedCamelContext = expectedCamelContext
        }

        @Override
        CamelContext getCamelContext() {
            return camelContext
        }

        @Override
        void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext
        }

        @Override
        ConstructNode resolve(String id) {
            if (camelContext.is(expectedCamelContext)) {
                return super.resolve(id)
            }
            return null
        }
    }

    static class ThrowingStepResolver implements YamlDeserializerResolver {
        @Override
        ConstructNode resolve(String id) {
            if (id == 'failingStep') {
                throw new IllegalStateException('resolver boom')
            }
            return null
        }
    }

    static class FailingConstructorStepResolver implements YamlDeserializerResolver {
        @Override
        ConstructNode resolve(String id) {
            if (id == 'constructFailureStep') {
                return new FailingConstructor()
            }
            return null
        }
    }

    static class FailingConstructor implements ConstructNode {
        @Override
        Object construct(Node node) {
            throw new IllegalStateException('constructor boom')
        }
    }

    static class NullMapResolverProvider implements YamlDeserializerResolverProvider {
        @Override
        Map<String, YamlDeserializerResolver> findResolvers(CamelContext camelContext) {
            return null
        }
    }

    static class StaticResolverProvider implements YamlDeserializerResolverProvider {
        private final Map<String, YamlDeserializerResolver> resolvers

        StaticResolverProvider(String name, YamlDeserializerResolver resolver) {
            this([(name): resolver])
        }

        StaticResolverProvider(Map<String, YamlDeserializerResolver> resolvers) {
            this.resolvers = resolvers
        }

        @Override
        Map<String, YamlDeserializerResolver> findResolvers(CamelContext camelContext) {
            return resolvers
        }
    }
}
