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

class BindKnativeChannelTest extends CamelCommandBaseTest {

    @Test
    public void shouldBindToKnativeChannel() throws Exception {
        Bind command = createCommand("timer-source", "knative:channel:my-channel");
        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals(
                """
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-channel-my-channel
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
                      kind: Channel
                      apiVersion: messaging.knative.dev/v1
                      name: my-channel
                    #properties:
                      #key: "value"
                """
                        .trim(),
                output);
    }

    @Test
    public void shouldBindToKnativeChannelWithProperties() throws Exception {
        Bind command = createCommand("timer-source", "knative:channel:my-channel");

        command.properties = new String[] {
            "source.message=Hello", "sink.type=my-event",
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals(
                """
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-channel-my-channel
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: Hello
                  sink:
                    ref:
                      kind: Channel
                      apiVersion: messaging.knative.dev/v1
                      name: my-channel
                    properties:
                      type: my-event
                """
                        .trim(),
                output);
    }

    @Test
    public void shouldBindToKnativeChannelWithUriProperties() throws Exception {
        Bind command = createCommand("timer-source", "knative:channel:my-channel?type=my-event&source=camel");

        command.properties = new String[] {
            "source.message=Hello",
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals(
                """
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-channel-my-channel
                spec:
                  source:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: timer-source
                    properties:
                      message: Hello
                  sink:
                    ref:
                      kind: Channel
                      apiVersion: messaging.knative.dev/v1
                      name: my-channel
                    properties:
                      type: my-event
                      source: camel
                """
                        .trim(),
                output);
    }

    @Test
    public void shouldBindKnativeChannelSource() throws Exception {
        Bind command = createCommand("knative:channel:my-channel", "log-sink");
        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals(
                """
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: channel-my-channel-to-log
                spec:
                  source:
                    ref:
                      kind: Channel
                      apiVersion: messaging.knative.dev/v1
                      name: my-channel
                    #properties:
                      #key: "value"
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    #properties:
                      #key: "value"
                """
                        .trim(),
                output);
    }

    @Test
    public void shouldBindKnativeChannelSourceWithProperties() throws Exception {
        Bind command = createCommand("knative:channel:my-channel", "log-sink");

        command.properties = new String[] {
            "source.type=my-event", "sink.showHeaders=true",
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals(
                """
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: channel-my-channel-to-log
                spec:
                  source:
                    ref:
                      kind: Channel
                      apiVersion: messaging.knative.dev/v1
                      name: my-channel
                    properties:
                      type: my-event
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    properties:
                      showHeaders: true
                """
                        .trim(),
                output);
    }

    @Test
    public void shouldBindKnativeChannelSourceWithUriProperties() throws Exception {
        Bind command = createCommand("knative:channel:my-channel?type=my-event&source=camel", "log-sink");

        command.properties = new String[] {
            "sink.showHeaders=true",
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals(
                """
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: channel-my-channel-to-log
                spec:
                  source:
                    ref:
                      kind: Channel
                      apiVersion: messaging.knative.dev/v1
                      name: my-channel
                    properties:
                      type: my-event
                      source: camel
                  sink:
                    ref:
                      kind: Kamelet
                      apiVersion: camel.apache.org/v1
                      name: log-sink
                    properties:
                      showHeaders: true
                """
                        .trim(),
                output);
    }

    private Bind createCommand(String source, String sink) {
        Bind command = new Bind(new CamelJBangMain().withPrinter(printer));

        String sourceName;
        if (source.startsWith("knative:")) {
            sourceName = StringHelper.after(source, "knative:").replaceAll(":", "-");
            if (sourceName.contains("?")) {
                sourceName = StringHelper.before(sourceName, "?");
            }
        } else {
            sourceName = StringHelper.before(source, "-source");
        }

        String sinkName;
        if (sink.startsWith("knative:")) {
            sinkName = StringHelper.after(sink, "knative:").replaceAll(":", "-");
            if (sinkName.contains("?")) {
                sinkName = StringHelper.before(sinkName, "?");
            }
        } else {
            sinkName = StringHelper.before(sink, "-sink");
        }

        command.file = sourceName + "-to-" + sinkName + ".yaml";
        command.source = source;
        command.sink = sink;
        command.output = "yaml";

        return command;
    }
}
