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

package org.apache.camel.dsl.jbang.core.commands;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BindTest extends CamelCommandBaseTest {

    @Test
    public void shouldBindKameletSourceToKameletSink() throws Exception {
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.file = "timer-to-log";
        command.source = "timer-source";
        command.sink = "log-sink";
        command.output = "yaml";

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
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.file = "timer-to-log";
        command.source = "timer-source";
        command.sink = "log-sink";
        command.output = "yaml";

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
    public void shouldBindWithSteps() throws Exception {
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.file = "timer-to-http";
        command.source = "timer-source";
        command.sink = "http-sink";
        command.output = "yaml";

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
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.file = "timer-to-http";
        command.source = "timer-source";
        command.sink = "http-sink";
        command.output = "yaml";

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
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.file = "timer-to-http";
        command.source = "timer-source";
        command.sink = "http-sink";
        command.output = "yaml";

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
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.file = "timer-to-http";
        command.source = "timer-source";
        command.sink = "http-sink";
        command.output = "yaml";

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
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.file = "timer-to-http";
        command.source = "timer-source";
        command.sink = "http-sink";
        command.output = "yaml";

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
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.file = "timer-to-log";
        command.source = "timer-source";
        command.sink = "log:info";
        command.output = "yaml";

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
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.file = "timer-to-log";
        command.source = "timer-source";
        command.sink = "log:info";
        command.output = "yaml";

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
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.file = "timer-to-log";
        command.source = "timer-source";
        command.sink = "log:info?showStreams=false";
        command.output = "yaml";

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
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.file = "timer-to-log";
        command.source = "timer:tick";
        command.sink = "log:info";
        command.output = "yaml";

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
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.file = "timer-to-log";
        command.source = "timer:tick";
        command.sink = "log:info";
        command.output = "yaml";

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
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.file = "timer-to-log";
        command.source = "timer:tick?period=10000";
        command.sink = "log:info?showStreams=false";
        command.output = "yaml";

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
    public void shouldSupportJsonOutput() throws Exception {
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.file = "timer-to-log";
        command.source = "timer-source";
        command.sink = "log-sink";
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
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));
        command.file = "timer-to-log";
        command.source = "timer-source";
        command.sink = "log-sink";
        command.output = "wrong";

        Assertions.assertEquals(-1, command.doCall());

        Assertions.assertEquals("Unsupported output format 'wrong' (supported: file, yaml, json)", printer.getOutput());
    }
}
