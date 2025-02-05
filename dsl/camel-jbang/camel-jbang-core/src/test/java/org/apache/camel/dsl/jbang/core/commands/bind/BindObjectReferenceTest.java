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

class BindObjectReferenceTest extends CamelCommandBaseTest {

    @Test
    public void shouldBindToObjectReference() throws Exception {
        Bind command = createCommand("timer", "foo");

        command.sink = "sandbox.camel.apache.org/v1:Foo:bar";

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-foo
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
                      kind: Foo
                      apiVersion: sandbox.camel.apache.org/v1
                      name: bar
                    #properties:
                      #key: "value"
                """.trim(), output);
    }

    @Test
    public void shouldBindToNamespacedObjectReference() throws Exception {
        Bind command = createCommand("timer", "foo");

        command.sink = "sandbox.camel.apache.org/v1alpha1:Foo:my-namespace/bar";

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-foo
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
                      kind: Foo
                      apiVersion: sandbox.camel.apache.org/v1alpha1
                      name: bar
                      namespace: my-namespace
                    #properties:
                      #key: "value"
                """.trim(), output);
    }

    @Test
    public void shouldBindToObjectReferenceWithProperties() throws Exception {
        Bind command = createCommand("timer", "foo");

        command.sink = "sandbox.camel.apache.org/v1:Foo:bar";
        command.properties = new String[] {
                "source.message=Hello",
                "sink.foo=bar",
                "sink.bar=baz",
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-foo
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
                      kind: Foo
                      apiVersion: sandbox.camel.apache.org/v1
                      name: bar
                    properties:
                      bar: baz
                      foo: bar
                """.trim(), output);
    }

    @Test
    public void shouldBindToObjectReferenceWithUriProperties() throws Exception {
        Bind command = createCommand("timer", "foo");

        command.sink = "sandbox.camel.apache.org/v1:Foo:bar?bar=baz&foo=bar";
        command.properties = new String[] {
                "source.message=Hello",
        };

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals("""
                apiVersion: camel.apache.org/v1
                kind: Pipe
                metadata:
                  name: timer-to-foo
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
                      kind: Foo
                      apiVersion: sandbox.camel.apache.org/v1
                      name: bar
                    properties:
                      bar: baz
                      foo: bar
                """.trim(), output);
    }

    @Test
    public void shouldHandleInvalidObjectReference() throws Exception {
        Bind command = createCommand("timer", "foo");

        command.sink = "sandbox.camel.apache.org:Foo:bar"; // missing api version

        command.doCall();

        String output = printer.getOutput();
        Assertions.assertEquals(
                """
                        ERROR: Failed to resolve endpoint URI expression sandbox.camel.apache.org:Foo:bar - no matching binding provider found
                        ERROR: Failed to construct Pipe resource
                        """
                        .trim(),
                output);
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
