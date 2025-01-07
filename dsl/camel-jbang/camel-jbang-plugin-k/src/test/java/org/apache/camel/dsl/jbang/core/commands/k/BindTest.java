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

package org.apache.camel.dsl.jbang.core.commands.k;

import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.StringHelper;
import org.apache.camel.v1.Pipe;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled("Deprecated and resource intensive")
class BindTest extends CamelKBaseTest {

    @Test
    public void shouldCreatePipe() throws Exception {
        Bind command = createCommand("timer", "http");
        command.output = null;
        command.doCall();

        Assertions.assertEquals("Pipe timer-to-http created", printer.getOutput());

        Pipe created = kubernetesClient.resources(Pipe.class).withName("timer-to-http").get();
        Assertions.assertEquals("camel-k", created.getMetadata().getAnnotations().get(CamelKCommand.OPERATOR_ID_LABEL));
    }

    @Test
    public void shouldUpdatePipe() throws Exception {
        Pipe pipe = createPipe("timer-to-log");
        kubernetesClient.resources(Pipe.class).resource(pipe).create();

        Bind command = createCommand("timer", "log");
        command.output = null;

        command.properties = new String[] {
                "sink.showHeaders=true"
        };
        command.doCall();

        Assertions.assertEquals("Pipe timer-to-log updated", printer.getOutput());

        Pipe created = kubernetesClient.resources(Pipe.class).withName("timer-to-log").get();
        Assertions.assertEquals("camel-k", created.getMetadata().getAnnotations().get(CamelKCommand.OPERATOR_ID_LABEL));
    }

    @Test
    public void shouldBindWithDefaultOperatorId() throws Exception {
        Bind command = createCommand("timer", "log");

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
                  annotations:
                    camel.apache.org/operator.id: camel-k
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "hello world"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    #properties:
                      #key: "value"
                """.trim(), output);
    }

    @Test
    public void shouldBindWithAnnotations() throws Exception {
        Bind command = createCommand("timer", "log");

        command.annotations = new String[] {
                "app=camel-k"
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
                  annotations:
                    app: camel-k
                    camel.apache.org/operator.id: camel-k
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "hello world"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    #properties:
                      #key: "value"
                """.trim(), output);
    }

    @Test
    public void shouldBindWithTraits() throws Exception {
        Bind command = createCommand("timer", "log");

        command.traits = new String[] {
                "mount.configs=configmap:my-cm",
                "logging.color=true",
                "logging.level=DEBUG"
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
                  annotations:
                    camel.apache.org/operator.id: camel-k
                spec:
                  integration:
                    spec:
                      traits:
                        logging:
                          color: true
                          level: DEBUG
                        mount:
                          configs:
                          - configmap:my-cm
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "hello world"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    #properties:
                      #key: "value"
                """.trim(), output);
    }

    @Test
    public void shouldBindWithServiceBindings() throws Exception {
        Bind command = createCommand("timer", "http");

        command.connects = new String[] {
                "serving.knative.dev/v1:Service:my-service"
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-http
                  annotations:
                    camel.apache.org/operator.id: camel-k
                spec:
                  integration:
                    spec:
                      traits:
                        service-binding:
                          services:
                          - serving.knative.dev/v1:Service:my-service
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "hello world"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: http-sink
                    properties:
                      url: "https://my-service/path"
                """.trim(), output);
    }

    @Test
    public void shouldFailWithMissingOperatorId() throws Exception {
        Bind command = createCommand("timer:tick", "log");

        command.operatorId = "";

        Assertions.assertEquals(-1, command.doCall());

        Assertions.assertEquals("Operator id must be set", printer.getOutput());
    }

    private Bind createCommand(String source, String sink) {
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.withClient(kubernetesClient);

        String sourceName;
        String sourceUri;
        if (source.contains(":")) {
            sourceName = StringHelper.before(source, ":");
            sourceUri = source;
        } else {
            sourceName = source;
            sourceUri = source + "-source";
        }

        String sinkName;
        String sinkUri;
        if (sink.contains(":")) {
            sinkName = StringHelper.before(sink, ":");
            sinkUri = sink;
        } else {
            sinkName = sink;
            sinkUri = sink + "-sink";
        }

        command.name = sourceName + "-to-" + sinkName + ".yaml";
        command.source = sourceUri;
        command.sink = sinkUri;
        command.output = "yaml";

        return command;
    }
}
