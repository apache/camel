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

import java.util.Properties;

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.model.KameletDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.TransformDataTypeDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PipeLoaderTest extends YamlTestSupport {

    @Override
    public void doSetup() throws Exception {
        context.start();
    }

    @Test
    void pipeFromKameletToKameletWithPipeYamlExtension() throws Exception {
        loadBindingsExt("pipe.yaml", """
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                """);

        assertThat(context.getRouteDefinitions()).hasSize(3);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("timer-event-source");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message=Hello world!");
        assertThat(route.getInput().getLineNumber()).isEqualTo(6);
        assertThat(route.getOutputs().size()).isEqualTo(1);
        ToDefinition to = (ToDefinition) route.getOutputs().get(0);
        assertThat(to.getEndpointUri()).isEqualTo("kamelet:log-sink");
        assertThat(to.getLineNumber()).isEqualTo(13);
    }

    @Test
    void pipeFromUriToKamelet() throws Exception {
        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source
                spec:
                  source:
                    uri: timer:foo
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(2);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("timer-event-source");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("timer:foo");
        assertThat(route.getOutputs().size()).isEqualTo(1);
        assertThat(((ToDefinition) route.getOutputs().get(0)).getEndpointUri()).isEqualTo("kamelet:log-sink");
    }

    @Test
    void pipeFromUriToUri() throws Exception {
        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source
                spec:
                  source:
                    uri: timer:foo
                  sink:
                    uri: log:bar
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(1);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("timer-event-source");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("timer:foo");
        assertThat(route.getOutputs().size()).isEqualTo(1);
        assertThat(((ToDefinition) route.getOutputs().get(0)).getEndpointUri()).isEqualTo("log:bar");
    }

    @Test
    void pipeSteps() throws Exception {
        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: steps-pipe
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "Camel"
                  steps:
                  - ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: prefix-action
                    properties:
                      prefix: "Apache"
                  - ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: prefix-action
                    properties:
                      prefix: "Hello"
                  sink:
                    uri: log:info
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(4);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("steps-pipe");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message=Camel");
        assertThat(route.getInput().getLineNumber()).isEqualTo(6);
        assertThat(route.getOutputs().size()).isEqualTo(3);
        KameletDefinition k0 = (KameletDefinition) route.getOutputs().get(0);
        assertThat(k0.getName()).isEqualTo("prefix-action?prefix=Apache");
        assertThat(k0.getLineNumber()).isEqualTo(13);
        KameletDefinition k1 = (KameletDefinition) route.getOutputs().get(1);
        assertThat(k1.getName()).isEqualTo("prefix-action?prefix=Hello");
        assertThat(k1.getLineNumber()).isEqualTo(19);
        ToDefinition to = (ToDefinition) route.getOutputs().get(2);
        assertThat(to.getEndpointUri()).isEqualTo("log:info");
        assertThat(to.getLineNumber()).isEqualTo(26);
    }

    @Test
    void pipeStepsKameletUri() throws Exception {
        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: steps-pipe
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "Camel"
                  steps:
                  - ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: prefix-action
                    properties:
                      prefix: "Apache"
                  - uri: mock:dummy
                  sink:
                    uri: log:info
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(3);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("steps-pipe");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message=Camel");
        assertThat(route.getOutputs().size()).isEqualTo(3);
        assertThat(((KameletDefinition) route.getOutputs().get(0)).getName()).isEqualTo("prefix-action?prefix=Apache");
        assertThat(((ToDefinition) route.getOutputs().get(1)).getEndpointUri()).isEqualTo("mock:dummy");
        assertThat(((ToDefinition) route.getOutputs().get(2)).getEndpointUri()).isEqualTo("log:info");
    }

    @Test
    void pipeStepsUriUri() throws Exception {
        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: steps-pipe
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "Camel"
                  steps:
                  - uri: mock:dummy
                  - uri: kamelet:prefix-action?prefix=Apache
                  - uri: mock:dummy2
                    properties:
                      reportGroup: 5
                  sink:
                    uri: log:info
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(3);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("steps-pipe");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message=Camel");
        assertThat(route.getOutputs().size()).isEqualTo(4);
        assertThat(((ToDefinition) route.getOutputs().get(0)).getEndpointUri()).isEqualTo("mock:dummy");
        assertThat(((KameletDefinition) route.getOutputs().get(1)).getName()).isEqualTo("prefix-action?prefix=Apache");
        assertThat(((ToDefinition) route.getOutputs().get(2)).getEndpointUri()).isEqualTo("mock:dummy2?reportGroup=5");
        assertThat(((ToDefinition) route.getOutputs().get(3)).getEndpointUri()).isEqualTo("log:info");
    }

    @Test
    void pipeFromKameletToStrimzi() throws Exception {
        // stub kafka for testing as it requires to setup connection to a real kafka broker
        context.removeComponent("kafka");
        context.addComponent("kafka", context.getComponent("stub"));

        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: KafkaTopic
                      apiVersion: kafka.strimzi.io/v1beta2
                      name: my-topic
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(2);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("timer-event-source");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message=Hello world!");
        assertThat(route.getOutputs().size()).isEqualTo(1);
        assertThat(((ToDefinition) route.getOutputs().get(0)).getEndpointUri()).isEqualTo("kafka:my-topic");
    }

    @Test
    void pipeFromKameletToKnativeChannel() throws Exception {
        // stub knative for testing as it requires to setup connection to a real knative broker
        context.removeComponent("knative");
        context.addComponent("knative", context.getComponent("stub"));

        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: InMemoryChannel
                      apiVersion: messaging.knative.dev/v1
                      name: my-messages
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(2);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("timer-event-source");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message=Hello world!");
        assertThat(route.getOutputs().size()).isEqualTo(1);
        assertThat(((ToDefinition) route.getOutputs().get(0)).getEndpointUri()).isEqualTo("knative:channel/my-messages");
    }

    @Test
    void pipeFromKnativeChannelToKamelet() throws Exception {
        // stub knative for testing as it requires to setup connection to a real knative broker
        context.removeComponent("knative");
        context.addComponent("knative", context.getComponent("stub"));

        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: knative-event-source
                spec:
                  source:
                    ref:
                      kind: InMemoryChannel
                      apiVersion: messaging.knative.dev/v1
                      name: my-messages
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    properties:
                      showHeaders: true
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(2);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("knative-event-source");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("knative:channel/my-messages");
        assertThat(route.getOutputs().size()).isEqualTo(1);
        assertThat(((ToDefinition) route.getOutputs().get(0)).getEndpointUri()).isEqualTo("kamelet:log-sink?showHeaders=true");
    }

    @Test
    void pipeFromKameletToKnativeBroker() throws Exception {
        // stub knative for testing as it requires to setup connection to a real knative broker
        context.removeComponent("knative");
        context.addComponent("knative", context.getComponent("stub"));

        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Broker
                      apiVersion: eventing.knative.dev/v1
                      name: foo-broker
                    properties:
                      type: org.apache.camel.event.messages
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(2);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("timer-event-source");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message=Hello world!");
        assertThat(route.getOutputs().size()).isEqualTo(1);
        assertThat(((ToDefinition) route.getOutputs().get(0)).getEndpointUri())
                .isEqualTo("knative:event/org.apache.camel.event.messages?kind=Broker&name=foo-broker");
    }

    @Test
    void pipeFromKnativeBrokerToKamelet() throws Exception {
        // stub knative for testing as it requires to setup connection to a real knative broker
        context.removeComponent("knative");
        context.addComponent("knative", context.getComponent("stub"));

        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: knative-event-source
                spec:
                  source:
                    ref:
                      kind: Broker
                      apiVersion: eventing.knative.dev/v1
                      name: foo-broker
                    properties:
                      type: org.apache.camel.event.messages
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    properties:
                      showHeaders: true
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(2);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("knative-event-source");
        assertThat(route.getInput().getEndpointUri())
                .isEqualTo("knative:event/org.apache.camel.event.messages?kind=Broker&name=foo-broker");
        assertThat(route.getOutputs().size()).isEqualTo(1);
        assertThat(((ToDefinition) route.getOutputs().get(0)).getEndpointUri()).isEqualTo("kamelet:log-sink?showHeaders=true");
    }

    @Test
    void kameletStartRoute() throws Exception {
        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: route-timer-source
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(3);
        // global stream caching enabled
        assertThat(context.isStreamCaching()).isTrue();

        var route = context.getRouteDefinitions().get(1);
        assertThat(route.isTemplate()).isTrue();
        assertThat(route.getStreamCache()).isEqualTo("false");
        assertThat(route.getMessageHistory()).isEqualTo("true");
    }

    @Test
    void pipeNoSink() throws Exception {
        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "Hello world!"
                  steps:
                  - ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-action
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(3);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("timer-event-source");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message=Hello world!");
        assertThat(route.getInput().getLineNumber()).isEqualTo(6);
        assertThat(route.getOutputs()).hasSize(1);
        KameletDefinition k = (KameletDefinition) route.getOutputs().get(0);
        assertThat(k.getName()).isEqualTo("log-action");
        assertThat(k.getLineNumber()).isEqualTo(13);
    }

    @Test
    void pipeWithInputOutputDataTypes() throws Exception {
        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    dataTypes:
                      in:
                        format: text/plain
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    dataTypes:
                      out:
                        format: application/octet-stream
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(3);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("timer-event-source");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message=Hello world!");
        assertThat(route.getInput().getLineNumber()).isEqualTo(6);
        assertThat(route.getInputType().getUrn()).isEqualTo("text/plain");
        assertThat(route.getOutputType().getUrn()).isEqualTo("application/octet-stream");
        assertThat(route.getOutputs().size()).isEqualTo(1);
        ToDefinition to = (ToDefinition) route.getOutputs().get(0);
        assertThat(to.getEndpointUri()).isEqualTo("kamelet:log-sink");
        assertThat(to.getLineNumber()).isEqualTo(16);
    }

    @Test
    void pipeWithInputOutputDataTypesAndSchemes() throws Exception {
        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    dataTypes:
                      in:
                        scheme: camel
                        format: text/plain
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    dataTypes:
                      out:
                        scheme: camel
                        format: application/octet-stream
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(3);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("timer-event-source");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message=Hello world!");
        assertThat(route.getInput().getLineNumber()).isEqualTo(6);
        assertThat(route.getInputType().getUrn()).isEqualTo("camel:text/plain");
        assertThat(route.getOutputType().getUrn()).isEqualTo("camel:application/octet-stream");
        assertThat(route.getOutputs().size()).isEqualTo(1);
        ToDefinition to = (ToDefinition) route.getOutputs().get(0);
        assertThat(to.getEndpointUri()).isEqualTo("kamelet:log-sink");
        assertThat(to.getLineNumber()).isEqualTo(17);
    }

    @Test
    void pipeWithDataTypeTransformation() throws Exception {
        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    dataTypes:
                      out:
                        format: application/octet-stream
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    dataTypes:
                      in:
                        format: text/plain
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(3);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("timer-event-source");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message=Hello world!");
        assertThat(route.getInput().getLineNumber()).isEqualTo(6);
        assertThat(route.getOutputs().size()).isEqualTo(3);
        TransformDataTypeDefinition tdt0 = (TransformDataTypeDefinition) route.getOutputs().get(0);
        assertThat(tdt0.getToType()).isEqualTo("application/octet-stream");
        assertThat(tdt0.getLineNumber()).isEqualTo(-1);
        TransformDataTypeDefinition tdt1 = (TransformDataTypeDefinition) route.getOutputs().get(1);
        assertThat(tdt1.getToType()).isEqualTo("text/plain");
        assertThat(tdt1.getLineNumber()).isEqualTo(-1);
        ToDefinition to = (ToDefinition) route.getOutputs().get(2);
        assertThat(to.getEndpointUri()).isEqualTo("kamelet:log-sink");
        assertThat(to.getLineNumber()).isEqualTo(16);
    }

    @Test
    void pipeWithDataTypeSchemeTransformation() throws Exception {
        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-event-source
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    dataTypes:
                      out:
                        scheme: camel
                        format: application/octet-stream
                    properties:
                      message: "Hello world!"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    dataTypes:
                      in:
                        scheme: camel
                        format: text/plain
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(3);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("timer-event-source");
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message=Hello world!");
        assertThat(route.getInput().getLineNumber()).isEqualTo(6);
        assertThat(route.getOutputs().size()).isEqualTo(3);
        TransformDataTypeDefinition tdt0 = (TransformDataTypeDefinition) route.getOutputs().get(0);
        assertThat(tdt0.getToType()).isEqualTo("camel:application/octet-stream");
        assertThat(tdt0.getLineNumber()).isEqualTo(-1);
        TransformDataTypeDefinition tdt1 = (TransformDataTypeDefinition) route.getOutputs().get(1);
        assertThat(tdt1.getToType()).isEqualTo("camel:text/plain");
        assertThat(tdt1.getLineNumber()).isEqualTo(-1);
        ToDefinition to = (ToDefinition) route.getOutputs().get(2);
        assertThat(to.getEndpointUri()).isEqualTo("kamelet:log-sink");
        assertThat(to.getLineNumber()).isEqualTo(17);
    }

    @Test
    void pipeKameletPropertyWithPlaceholderShouldNotBeUrlEncoded() throws Exception {
        Properties props = new Properties();
        props.setProperty("my.message", "Hello Camel");
        context.getPropertiesComponent().setInitialProperties(props);

        loadBindings("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: placeholder-pipe
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "{{my.message}}"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                """);

        assertThat(context.getRouteDefinitions().size()).isEqualTo(3);

        var route = context.getRouteDefinitions().get(0);
        assertThat(route.getRouteId()).isEqualTo("placeholder-pipe");
        // CAMEL-23284: verify placeholder is preserved and NOT URL-encoded to %7B%7B...%7D%7D
        assertThat(route.getInput().getEndpointUri()).isEqualTo("kamelet:timer-source?message={{my.message}}");
        assertThat(route.getInput().getEndpointUri().contains("%7B")).isFalse();
        assertThat(route.getInput().getEndpointUri().contains("%7D")).isFalse();
    }

    @Test
    void pipeWithUnsupportedRefKindThrowsException() {
        String pipe = """
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: bad-ref-pipe
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "Hello"
                  sink:
                    ref:
                      kind: UnknownKind
                      apiVersion: unknown/v1
                      name: bad-sink
                """;

        Exception ex = assertThrows(Exception.class, () -> loadBindings(pipe));
        assertThat(ex.getMessage().contains("Unsupported Pipe ref kind")).isTrue();
    }
}
