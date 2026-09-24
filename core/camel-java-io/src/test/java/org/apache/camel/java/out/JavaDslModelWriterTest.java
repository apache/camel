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
package org.apache.camel.java.out;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.apache.camel.model.A2ASubTaskDefinition;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.MulticastDefinition;
import org.apache.camel.model.OtherwiseDefinition;
import org.apache.camel.model.RemoveHeaderDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SetHeaderDefinition;
import org.apache.camel.model.SetVariableDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.TransformDefinition;
import org.apache.camel.model.WhenDefinition;
import org.apache.camel.model.WireTapDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.JsonPathExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JavaDslModelWriterTest {

    private String loadExpected(String name) throws IOException {
        return Files.readString(Paths.get("src/test/resources/" + name));
    }

    @Test
    public void testSimpleRoute() throws Exception {
        JavaDslModelWriter writer = new JavaDslModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));
        route.addOutput(new LogDefinition("${body}"));
        route.addOutput(new ToDefinition("mock:result"));

        String out = writer.writeRouteDefinition(route);
        Assertions.assertEquals(loadExpected("java-dsl-simple.txt"), out);
    }

    @Test
    public void testChoice() throws Exception {
        JavaDslModelWriter writer = new JavaDslModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        ChoiceDefinition choice = new ChoiceDefinition();

        WhenDefinition when1 = new WhenDefinition();
        when1.setExpression(new SimpleExpression("${header.type} == 'a'"));
        when1.addOutput(new ToDefinition("mock:a"));
        choice.getWhenClauses().add(when1);

        WhenDefinition when2 = new WhenDefinition();
        when2.setExpression(new SimpleExpression("${header.type} == 'b'"));
        when2.addOutput(new ToDefinition("mock:b"));
        choice.getWhenClauses().add(when2);

        OtherwiseDefinition ow = new OtherwiseDefinition();
        ow.addOutput(new ToDefinition("mock:other"));
        choice.setOtherwise(ow);

        route.addOutput(choice);
        route.addOutput(new ToDefinition("mock:result"));

        String out = writer.writeRouteDefinition(route);
        Assertions.assertEquals(loadExpected("java-dsl-choice.txt"), out);
    }

    @Test
    public void testFilter() throws Exception {
        JavaDslModelWriter writer = new JavaDslModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        FilterDefinition filter = new FilterDefinition();
        filter.setExpression(new SimpleExpression("${header.foo} == 'bar'"));
        filter.addOutput(new ToDefinition("mock:filtered"));
        route.addOutput(filter);
        route.addOutput(new ToDefinition("mock:result"));

        String out = writer.writeRouteDefinition(route);
        Assertions.assertEquals(loadExpected("java-dsl-filter.txt"), out);
    }

    @Test
    public void testSplit() throws Exception {
        JavaDslModelWriter writer = new JavaDslModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        SplitDefinition split = new SplitDefinition();
        split.setExpression(new SimpleExpression("${body}"));
        split.setStreaming("true");
        split.addOutput(new ToDefinition("mock:split"));
        route.addOutput(split);
        route.addOutput(new ToDefinition("mock:result"));

        String out = writer.writeRouteDefinition(route);
        Assertions.assertEquals(loadExpected("java-dsl-split.txt"), out);
    }

    @Test
    public void testSetHeaderAndTransform() throws Exception {
        JavaDslModelWriter writer = new JavaDslModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        SetHeaderDefinition sh = new SetHeaderDefinition();
        sh.setName("myHeader");
        sh.setExpression(new ConstantExpression("myValue"));
        route.addOutput(sh);

        SetVariableDefinition sv = new SetVariableDefinition();
        sv.setName("myVar");
        sv.setExpression(new SimpleExpression("${body}"));
        route.addOutput(sv);

        TransformDefinition transform = new TransformDefinition();
        transform.setExpression(new SimpleExpression("Hello ${body}"));
        route.addOutput(transform);

        route.addOutput(new ToDefinition("mock:result"));

        String out = writer.writeRouteDefinition(route);
        Assertions.assertEquals(loadExpected("java-dsl-set-header.txt"), out);
    }

    @Test
    public void testWireTap() throws Exception {
        JavaDslModelWriter writer = new JavaDslModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        WireTapDefinition<?> wt = new WireTapDefinition<>();
        wt.setUri("mock:tap");
        route.addOutput(wt);
        route.addOutput(new ToDefinition("mock:result"));

        String out = writer.writeRouteDefinition(route);
        Assertions.assertEquals(loadExpected("java-dsl-wiretap.txt"), out);
    }

    @Test
    public void testMulticast() throws Exception {
        JavaDslModelWriter writer = new JavaDslModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        MulticastDefinition multicast = new MulticastDefinition();
        multicast.addOutput(new ToDefinition("mock:a"));
        multicast.addOutput(new ToDefinition("mock:b"));
        multicast.addOutput(new ToDefinition("mock:c"));
        route.addOutput(multicast);

        String out = writer.writeRouteDefinition(route);
        Assertions.assertTrue(out.contains(".multicast()"));
        Assertions.assertTrue(out.contains(".to(\"mock:a\")"));
        Assertions.assertTrue(out.contains(".to(\"mock:b\")"));
        Assertions.assertTrue(out.contains(".to(\"mock:c\")"));
        Assertions.assertTrue(out.contains(".end()"));
    }

    @Test
    public void testToD() throws Exception {
        JavaDslModelWriter writer = new JavaDslModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));
        route.addOutput(new ToDynamicDefinition("${header.target}"));
        route.addOutput(new ToDefinition("mock:result"));

        String out = writer.writeRouteDefinition(route);
        Assertions.assertTrue(out.contains(".toD(\"${header.target}\")"));
    }

    @Test
    public void testRemoveHeader() throws Exception {
        JavaDslModelWriter writer = new JavaDslModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));
        route.addOutput(new RemoveHeaderDefinition("CamelHttpPath"));
        route.addOutput(new ToDefinition("mock:result"));

        String out = writer.writeRouteDefinition(route);
        Assertions.assertTrue(out.contains(".removeHeader(\"CamelHttpPath\")"));
    }

    @Test
    public void testA2ASubTask() throws Exception {
        JavaDslModelWriter writer = new JavaDslModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        A2ASubTaskDefinition subTask = new A2ASubTaskDefinition();
        subTask.setEmitBefore("Before ${body}");
        subTask.setEmitAfter("After ${body}");
        subTask.setEmitOnError("Failed ${exception.message}");
        subTask.setFailIfNoTaskContext("true");
        subTask.addOutput(new ToDefinition("mock:result"));
        route.addOutput(subTask);

        String out = writer.writeRouteDefinition(route);
        Assertions.assertTrue(out.contains(".a2aSubTask()"));
        Assertions.assertTrue(out.contains(".emitBefore(\"Before ${body}\")"));
        Assertions.assertTrue(out.contains(".emitAfter(\"After ${body}\")"));
        Assertions.assertTrue(out.contains(".emitOnError(\"Failed ${exception.message}\")"));
        Assertions.assertTrue(out.contains(".failIfNoTaskContext(true)"));
    }

    @Test
    public void testExpressionLanguages() throws Exception {
        JavaDslModelWriter writer = new JavaDslModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setInput(new FromDefinition("direct:start"));

        FilterDefinition filter1 = new FilterDefinition();
        filter1.setExpression(new SimpleExpression("${header.foo}"));
        filter1.addOutput(new ToDefinition("mock:simple"));
        route.addOutput(filter1);

        FilterDefinition filter2 = new FilterDefinition();
        filter2.setExpression(new HeaderExpression("bar"));
        filter2.addOutput(new ToDefinition("mock:header"));
        route.addOutput(filter2);

        FilterDefinition filter3 = new FilterDefinition();
        filter3.setExpression(new JsonPathExpression("$.store.book"));
        filter3.addOutput(new ToDefinition("mock:jsonpath"));
        route.addOutput(filter3);

        String out = writer.writeRouteDefinition(route);
        Assertions.assertTrue(out.contains("filter(simple(\"${header.foo}\"))"));
        Assertions.assertTrue(out.contains("filter(header(\"bar\"))"));
        Assertions.assertTrue(out.contains("filter(jsonpath(\"$.store.book\"))"));
    }

    @Test
    public void testRouteWithoutId() throws Exception {
        JavaDslModelWriter writer = new JavaDslModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setInput(new FromDefinition("direct:start"));
        route.addOutput(new ToDefinition("mock:result"));

        String out = writer.writeRouteDefinition(route);
        Assertions.assertTrue(out.startsWith("from(\"direct:start\")"));
        Assertions.assertFalse(out.contains(".routeId("));
        Assertions.assertTrue(out.contains(".to(\"mock:result\")"));
    }

    @Test
    public void testWriterIsReusable() throws Exception {
        JavaDslModelWriter writer = new JavaDslModelWriter();

        RouteDefinition route1 = new RouteDefinition();
        route1.setId("route1");
        route1.setInput(new FromDefinition("direct:a"));
        route1.addOutput(new ToDefinition("mock:a"));

        RouteDefinition route2 = new RouteDefinition();
        route2.setId("route2");
        route2.setInput(new FromDefinition("direct:b"));
        route2.addOutput(new ToDefinition("mock:b"));

        String out1 = writer.writeRouteDefinition(route1);
        String out2 = writer.writeRouteDefinition(route2);

        Assertions.assertTrue(out1.contains("direct:a"));
        Assertions.assertTrue(out2.contains("direct:b"));
        Assertions.assertFalse(out1.contains("direct:b"));
        Assertions.assertFalse(out2.contains("direct:a"));
    }
}
