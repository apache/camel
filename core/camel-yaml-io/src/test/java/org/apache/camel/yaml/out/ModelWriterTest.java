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
package org.apache.camel.yaml.out;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Paths;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.ExpressionSubElementDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.MarshalDefinition;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ResequenceDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.SetBodyDefinition;
import org.apache.camel.model.SetHeaderDefinition;
import org.apache.camel.model.SetHeadersDefinition;
import org.apache.camel.model.SetVariableDefinition;
import org.apache.camel.model.SetVariablesDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.model.dataformat.CsvDataFormat;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.model.rest.RestDefinition;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.apache.camel.util.IOHelper.stripLineComments;

public class ModelWriterTest {

    @Test
    public void testTimerLog() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute0");
        route.setInput(new FromDefinition("timer:yaml?period=1234&includeMetadata=true"));
        SetBodyDefinition sb = new SetBodyDefinition();
        sb.setExpression(new ConstantExpression("Hello from yaml"));
        route.addOutput(sb);
        route.addOutput(new LogDefinition("${body}"));

        writer.writeRouteDefinition(route);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route0b.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testFromTo() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute1");
        route.setInput(new FromDefinition("direct:start"));
        ToDefinition to = new ToDefinition("log:input");
        route.addOutput(to);
        ToDefinition to2 = new ToDefinition("mock:result");
        to2.setPattern("InOut");
        route.addOutput(to2);

        writer.writeRouteDefinition(route);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route1.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testFromSplitTo() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute2");
        route.setInput(new FromDefinition("direct:start2"));
        SplitDefinition sp = new SplitDefinition();
        SimpleExpression e = new SimpleExpression("${body}");
        e.setResultTypeName("int.class");
        sp.setExpression(e);
        sp.setStreaming("true");
        route.addOutput(sp);
        ToDefinition to = new ToDefinition("kafka:line");
        sp.addOutput(to);
        to = new ToDefinition("mock:result2");
        route.addOutput(to);

        writer.writeRouteDefinition(route);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route2.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testFromAggregateTo() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute3");
        route.setInput(new FromDefinition("direct:start2"));
        final AggregateDefinition ag = createAggregateDefinition();
        route.addOutput(ag);
        ToDefinition to = new ToDefinition("kafka:line");
        ag.addOutput(to);
        to = new ToDefinition("mock:result2");
        route.addOutput(to);

        writer.writeRouteDefinition(route);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route3.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    private static AggregateDefinition createAggregateDefinition() {
        AggregateDefinition ag = new AggregateDefinition();
        SimpleExpression e = new SimpleExpression("${body}");
        e.setResultTypeName("int.class");
        ag.setExpression(e);
        ag.setCorrelationExpression(new ExpressionSubElementDefinition(new HeaderExpression("myHeader")));
        ConstantExpression cons = new ConstantExpression("5");
        cons.setResultTypeName("int.class");
        ag.setCompletionSizeExpression(new ExpressionSubElementDefinition(cons));
        ag.setCompletionTimeoutExpression(new ExpressionSubElementDefinition(new ConstantExpression("4000")));
        return ag;
    }

    @Test
    public void testFromSetBodyTo() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute4");
        route.setInput(new FromDefinition("direct:start"));
        SetBodyDefinition body = new SetBodyDefinition();
        body.setExpression(new ConstantExpression("{\n key: '123'\n}"));
        route.addOutput(body);
        ToDefinition to = new ToDefinition("mock:result");
        route.addOutput(to);

        writer.writeRouteDefinition(route);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route4.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testFromLogSetBodyTo() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute5");
        route.setInput(new FromDefinition("direct:start"));
        LogDefinition log = new LogDefinition();
        log.setLoggingLevel("WARN");
        log.setLogName("myLogger");
        route.addOutput(log);
        SetBodyDefinition body = new SetBodyDefinition();
        body.setExpression(new SimpleExpression("${body}"));
        route.addOutput(body);
        ToDefinition to = new ToDefinition("mock:result");
        route.addOutput(to);

        writer.writeRouteDefinition(route);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route5.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Disabled("TODO: https://issues.apache.org/jira/browse/CAMEL-21490")
    @Test
    public void testFromChoice() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute6");
        route.setInput(new FromDefinition("direct:start6"));
        ChoiceDefinition choice = new ChoiceDefinition();
        route.addOutput(choice);
        choice.when().simple("${header.age} < 21").to("mock:young");
        choice.when().simple("${header.age} > 21 && ${header.age} < 70").to("mock:work");
        choice.otherwise().to("mock:senior");
        ToDefinition to = new ToDefinition("mock:result");
        route.addOutput(to);

        writer.writeRouteDefinition(route);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route6.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testFromTryCatch() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start7").routeId("myRoute7")
                    .doTry()
                        .to("mock:try1")
                        .to("mock:try2")
                    .doCatch(IOException.class)
                        .to("mock:io1")
                        .to("mock:io2")
                    .doFinally()
                        .to("mock:finally1")
                        .to("mock:finally2")
                    .end()
                    .to("mock:result");
            }
        });

        ModelCamelContext mcc = (ModelCamelContext) context;
        writer.writeRouteDefinition(mcc.getRouteDefinition("myRoute7"));

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route7.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testTwoRoutes() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        RoutesDefinition routes = new RoutesDefinition();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute0");
        route.setInput(new FromDefinition("timer:yaml?period=1234"));
        SetBodyDefinition sb = new SetBodyDefinition();
        sb.setExpression(new ConstantExpression("Hello from yaml"));
        route.addOutput(sb);
        route.addOutput(new LogDefinition("${body}"));
        routes.getRoutes().add(route);

        route = new RouteDefinition();
        route.setId("myRoute1");
        route.setInput(new FromDefinition("direct:start"));
        ToDefinition to = new ToDefinition("log:input");
        route.addOutput(to);
        ToDefinition to2 = new ToDefinition("mock:result");
        to2.setPattern("InOut");
        route.addOutput(to2);
        routes.getRoutes().add(route);

        writer.writeRoutesDefinition(routes);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route8b.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testMarshal() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute9");
        route.setInput(new FromDefinition("timer:foo"));
        MarshalDefinition mar = new MarshalDefinition();
        mar.setDataFormatType(new CsvDataFormat());
        route.addOutput(mar);
        route.addOutput(new LogDefinition("${body}"));

        writer.writeRouteDefinition(route);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route9.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    @Disabled("CAMEL-20402")
    public void testRest() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        RestDefinition rest = new RestDefinition();
        rest.verb("get").to("direct:start");
        writer.writeRestDefinition(rest);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute10");
        route.setInput(new FromDefinition("direct:start"));
        SetBodyDefinition sb = new SetBodyDefinition(new SimpleExpression("${body}${body}"));
        route.addOutput(sb);
        writer.writeRouteDefinition(route);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route10.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testSetHeaders() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRout12");
        route.setInput(new FromDefinition("timer:foo"));
        SetHeadersDefinition sh = new SetHeadersDefinition();
        sh.getHeaders().add(new SetHeaderDefinition("foo", new ConstantExpression("hello world")));
        sh.getHeaders().add(new SetHeaderDefinition("bar", new SimpleExpression("bye ${body}")));
        route.addOutput(sh);
        route.addOutput(new LogDefinition("${body}"));

        writer.writeRouteDefinition(route);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route12.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testSetVariables() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRout13");
        route.setInput(new FromDefinition("timer:foo"));
        SetVariablesDefinition sv = new SetVariablesDefinition();
        sv.getVariables().add(new SetVariableDefinition("foo", new ConstantExpression("hello2 world")));
        sv.getVariables().add(new SetVariableDefinition("bar", new SimpleExpression("bye2 ${body}")));
        route.addOutput(sv);
        route.addOutput(new LogDefinition("${body}"));

        writer.writeRouteDefinition(route);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route13.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testResequenceBatch() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRout14");
        route.setInput(new FromDefinition("timer:foo"));
        ResequenceDefinition rd = new ResequenceDefinition(new SimpleExpression("${body}"));
        rd.batch().size(300).timeout("4000");
        route.addOutput(rd);
        rd.addOutput(new ToDefinition("mock:result"));
        route.addOutput(new LogDefinition("${body}"));

        writer.writeRouteDefinition(route);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route14.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testResequenceStream() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRout15");
        route.setInput(new FromDefinition("timer:foo"));
        ResequenceDefinition rd = new ResequenceDefinition(new SimpleExpression("${body}"));
        rd.stream().capacity(123).timeout("4000").rejectOld();
        route.addOutput(rd);
        rd.addOutput(new ToDefinition("mock:result"));
        route.addOutput(new LogDefinition("${body}"));

        writer.writeRouteDefinition(route);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route15.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testTransacted() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRout16");
        route.setInput(new FromDefinition("jms:cheese"));
        TransactedDefinition td = new TransactedDefinition();
        route.addOutput(td);
        td.addOutput(new ToDefinition("bean:foo"));

        writer.writeRouteDefinition(route);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route16.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

}
