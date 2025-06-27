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

package org.apache.camel.dsl.jbang.core.commands.bind;

import org.apache.camel.dsl.jbang.core.commands.CamelCommandBaseTest;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.util.StringHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BindTest extends CamelCommandBaseTest {

    @Test
    public void shouldBindKameletSourceToKameletSink() throws Exception {
        Bind command = createCommand("timer", "log");
        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
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
    public void shouldBindNamespacedKamelets() throws Exception {
        Bind command = createCommand("timer", "log");
        command.source = "my-namespace/timer-source";
        command.sink = "my-namespace/log-sink";

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                      namespace: my-namespace
                    properties:
                      message: "hello world"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                      namespace: my-namespace
                    #properties:
                      #key: "value"
                """.trim(), output);
    }

    @Test
    public void shouldBindKameletsExplicitPrefix() throws Exception {
        Bind command = createCommand("timer", "log");
        command.source = "kamelet:timer-source";
        command.sink = "kamelet:log-sink";

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
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
    public void shouldBindKameletSourceToKameletSinkWithProperties() throws Exception {
        Bind command = createCommand("timer", "log");

        command.properties = new String[] {
                "source.message=Hello",
                "source.period=5000",
                "sink.showHeaders=true",
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: Hello
                      period: 5000
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    properties:
                      showHeaders: true
                """.trim(), output);
    }

    @Test
    public void shouldBindKameletsWithUriProperties() throws Exception {
        Bind command = createCommand("timer", "log");
        command.source = "timer-source?message=Hi";
        command.sink = "log-sink?showHeaders=true";

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: Hi
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    properties:
                      showHeaders: true
                """.trim(), output);
    }

    @Test
    public void shouldBindWithSteps() throws Exception {
        Bind command = createCommand("timer", "http");

        command.steps = new String[] {
                "set-body-action",
                "log-action"
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-http
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "hello world"
                  steps:
                  - ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: set-body-action
                    properties:
                      value: "value"
                  - ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-action
                    #properties:
                      #key: "value"
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
    public void shouldBindWithUriSteps() throws Exception {
        Bind command = createCommand("timer", "http");

        command.steps = new String[] {
                "set-body-action",
                "log:info"
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-http
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "hello world"
                  steps:
                  - ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: set-body-action
                    properties:
                      value: "value"
                  - uri: log:info
                    #properties:
                      #key: "value"
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
    public void shouldBindWithStepsAndProperties() throws Exception {
        Bind command = createCommand("timer", "http");

        command.steps = new String[] {
                "set-body-action",
                "log-action"
        };

        command.properties = new String[] {
                "step-1.value=\"Camel rocks!\"",
                "step-2.showHeaders=true",
                "step-2.showExchangePattern=false"
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-http
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "hello world"
                  steps:
                  - ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: set-body-action
                    properties:
                      value: "Camel rocks!"
                  - ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-action
                    properties:
                      showHeaders: true
                      showExchangePattern: false
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
    public void shouldBindWithUriStepsAndProperties() throws Exception {
        Bind command = createCommand("timer", "http");

        command.steps = new String[] {
                "set-body-action",
                "log:info"
        };

        command.properties = new String[] {
                "step-1.value=\"Camel rocks!\"",
                "step-2.showHeaders=true"
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-http
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "hello world"
                  steps:
                  - ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: set-body-action
                    properties:
                      value: "Camel rocks!"
                  - uri: log:info
                    properties:
                      showHeaders: true
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
    public void shouldBindWithUriStepsAndUriProperties() throws Exception {
        Bind command = createCommand("timer", "http");

        command.steps = new String[] {
                "set-body-action",
                "log:info?showExchangePattern=false&showStreams=true"
        };

        command.properties = new String[] {
                "step-1.value=\"Camel rocks!\"",
                "step-2.showHeaders=true"
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-http
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "hello world"
                  steps:
                  - ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: set-body-action
                    properties:
                      value: "Camel rocks!"
                  - uri: log:info
                    properties:
                      showStreams: true
                      showHeaders: true
                      showExchangePattern: false
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
    public void shouldBindKameletSourceToUri() throws Exception {
        Bind command = createCommand("timer", "log:info");

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: "hello world"
                  sink:
                    uri: log:info
                    #properties:
                      #key: "value"
                """.trim(), output);
    }

    @Test
    public void shouldBindKameletSourceToUriWithProperties() throws Exception {
        Bind command = createCommand("timer", "log:info");

        command.properties = new String[] {
                "source.message=Hello",
                "sink.showHeaders=true",
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: Hello
                  sink:
                    uri: log:info
                    properties:
                      showHeaders: true
                """.trim(), output);
    }

    @Test
    public void shouldBindKameletSourceToUriWithUriProperties() throws Exception {
        Bind command = createCommand("timer", "log:info?showStreams=false");

        command.properties = new String[] {
                "source.message=Hello",
                "sink.showHeaders=true",
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: Hello
                  sink:
                    uri: log:info
                    properties:
                      showStreams: false
                      showHeaders: true
                """.trim(), output);
    }

    @Test
    public void shouldBindUriToUri() throws Exception {
        Bind command = createCommand("timer:tick", "log:info");

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
                spec:
                  source:
                    uri: timer:tick
                    #properties:
                      #key: "value"
                  sink:
                    uri: log:info
                    #properties:
                      #key: "value"
                """.trim(), output);
    }

    @Test
    public void shouldBindUriToUriWithProperties() throws Exception {
        Bind command = createCommand("timer:tick", "log:info");

        command.properties = new String[] {
                "source.message=Hello",
                "sink.showHeaders=true",
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
                spec:
                  source:
                    uri: timer:tick
                    properties:
                      message: Hello
                  sink:
                    uri: log:info
                    properties:
                      showHeaders: true
                """.trim(), output);
    }

    @Test
    public void shouldBindUriToUriWithUriProperties() throws Exception {
        Bind command = createCommand("timer:tick?period=10000", "log:info?showStreams=false");

        command.properties = new String[] {
                "source.message=Hello",
                "sink.showHeaders=true",
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
                spec:
                  source:
                    uri: timer:tick
                    properties:
                      period: 10000
                      message: Hello
                  sink:
                    uri: log:info
                    properties:
                      showStreams: false
                      showHeaders: true
                """.trim(), output);
    }

    @Test
    public void shouldBindKameletSinkErrorHandler() throws Exception {
        Bind command = createCommand("timer", "log");

        command.errorHandler = "sink:log-sink";

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
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
                  errorHandler:
                    sink:
                      endpoint:
                        ref:
                          kind: Kamelet
                          apiVersion: camel.apache.org/v1
                          name: log-sink
                        #properties:
                          #key: "value"
                      parameters: {}
                """.trim(), output);
    }

    @Test
    public void shouldBindKameletSinkErrorHandlerWithParameters() throws Exception {
        Bind command = createCommand("timer", "log");

        command.errorHandler = "sink:log-sink";

        command.properties = new String[] {
                "error-handler.maximumRedeliveries=3",
                "error-handler.redeliveryDelay=2000"
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
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
                  errorHandler:
                    sink:
                      endpoint:
                        ref:
                          kind: Kamelet
                          apiVersion: camel.apache.org/v1
                          name: log-sink
                        #properties:
                          #key: "value"
                      parameters:
                        redeliveryDelay: 2000
                        maximumRedeliveries: 3
                """.trim(), output);
    }

    @Test
    public void shouldBindKameletSinkErrorHandlerAndSinkProperties() throws Exception {
        Bind command = createCommand("timer", "log");

        command.errorHandler = "sink:log-sink";

        command.properties = new String[] {
                "error-handler.sink.showHeaders=true",
                "error-handler.maximumRedeliveries=3",
                "error-handler.redeliveryDelay=2000"
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
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
                  errorHandler:
                    sink:
                      endpoint:
                        ref:
                          kind: Kamelet
                          apiVersion: camel.apache.org/v1
                          name: log-sink
                        properties:
                          showHeaders: true
                      parameters:
                        redeliveryDelay: 2000
                        maximumRedeliveries: 3
                """.trim(), output);
    }

    @Test
    public void shouldBindEndpointUriSinkErrorHandler() throws Exception {
        Bind command = createCommand("timer", "log");

        command.errorHandler = "sink:log:error";

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
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
                  errorHandler:
                    sink:
                      endpoint:
                        uri: log:error
                        #properties:
                          #key: "value"
                      parameters: {}
                """.trim(), output);
    }

    @Test
    public void shouldBindEndpointUriSinkErrorHandlerWithParameters() throws Exception {
        Bind command = createCommand("timer", "log");

        command.errorHandler = "sink:log:error";

        command.properties = new String[] {
                "error-handler.maximumRedeliveries=3",
                "error-handler.redeliveryDelay=2000"
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
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
                  errorHandler:
                    sink:
                      endpoint:
                        uri: log:error
                        #properties:
                          #key: "value"
                      parameters:
                        redeliveryDelay: 2000
                        maximumRedeliveries: 3
                """.trim(), output);
    }

    @Test
    public void shouldBindEndpointUriSinkErrorHandlerAndSinkProperties() throws Exception {
        Bind command = createCommand("timer", "log");

        command.errorHandler = "sink:log:error";

        command.properties = new String[] {
                "error-handler.sink.showHeaders=true",
                "error-handler.maximumRedeliveries=3",
                "error-handler.redeliveryDelay=2000"
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
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
                  errorHandler:
                    sink:
                      endpoint:
                        uri: log:error
                        properties:
                          showHeaders: true
                      parameters:
                        redeliveryDelay: 2000
                        maximumRedeliveries: 3
                """.trim(), output);
    }

    @Test
    public void shouldBindEndpointUriSinkErrorHandlerAndUriProperties() throws Exception {
        Bind command = createCommand("timer", "log");

        command.errorHandler = "sink:log:error?showStreams=false";

        command.properties = new String[] {
                "error-handler.sink.showHeaders=true",
                "error-handler.maximumRedeliveries=3",
                "error-handler.redeliveryDelay=2000"
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
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
                  errorHandler:
                    sink:
                      endpoint:
                        uri: log:error
                        properties:
                          showStreams: false
                          showHeaders: true
                      parameters:
                        redeliveryDelay: 2000
                        maximumRedeliveries: 3
                """.trim(), output);
    }

    @Test
    public void shouldBindWithLogErrorHandler() throws Exception {
        Bind command = createCommand("timer", "log");

        command.errorHandler = "log";

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
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
                  errorHandler:
                    log:
                      parameters: {}
                """.trim(), output);
    }

    @Test
    public void shouldBindWithLogErrorHandlerWithParameters() throws Exception {
        Bind command = createCommand("timer", "log");

        command.errorHandler = "log";

        command.properties = new String[] {
                "error-handler.maximumRedeliveries=3",
                "error-handler.redeliveryDelay=2000"
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
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
                  errorHandler:
                    log:
                      parameters:
                        redeliveryDelay: 2000
                        maximumRedeliveries: 3
                """.trim(), output);
    }

    @Test
    public void shouldBindWithNoErrorHandler() throws Exception {
        Bind command = createCommand("timer", "log");

        command.errorHandler = "none";

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-log
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
                  errorHandler:
                    none: {}
                """.trim(), output);
    }

    @Test
    public void shouldSupportJsonOutput() throws Exception {
        Bind command = createCommand("timer", "log");
        command.output = "json";

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                 {
                  "apiVersion": "camel.apache.org/v1",
                  "kind": "Pipe",
                  "metadata": {
                    "name": "timer-to-log"
                  },
                  "spec": {
                    "source": {
                      "ref": {
                        "kind": "Kamelet",
                        "apiVersion": "camel.apache.org/v1",
                        "name": "timer-source"
                      },
                      "properties": {
                        "message": "hello world"
                      }
                    },
                    "sink": {
                      "ref": {
                        "kind": "Kamelet",
                        "apiVersion": "camel.apache.org/v1",
                        "name": "log-sink"
                      }
                    }
                  }
                }
                 """.trim(), output);
    }

    @Test
    public void shouldHandleUnsupportedOutputFormat() throws Exception {
        Bind command = createCommand("timer", "log");
        command.output = "wrong";

        Assertions.assertEquals(-1, command.doCall());

        Assertions.assertEquals("ERROR: Unsupported output format 'wrong' (supported: file, yaml, json)", printer.getOutput());
    }

    private Bind createCommand(String source, String sink) {
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));

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

        command.file = sourceName + "-to-" + sinkName + ".yaml";
        command.source = sourceUri;
        command.sink = sinkUri;
        command.output = "yaml";

        return command;
    }
}
