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
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RoutesDefinition;
import org.apache.camel.model.SetBodyDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.dataformat.CsvDataFormat;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.apache.camel.util.IOHelper.stripLineComments;

public class ModelWriterUriAsParametersTest {

    @Test
    public void testTimerLog() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);
        writer.setUriAsParameters(true);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute0");
        route.setInput(new FromDefinition("timer:yaml?period=1234&includeMetadata=true"));
        SetBodyDefinition sb = new SetBodyDefinition();
        sb.setExpression(new ConstantExpression("Hello from yaml"));
        route.addOutput(sb);
        route.addOutput(new LogDefinition("${body}"));

        writer.writeRouteDefinition(route);

        String out = sw.toString();
        String expected = stripLineComments(Paths.get("src/test/resources/route0.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testFromTo() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);
        writer.setUriAsParameters(true);

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
        writer.setUriAsParameters(true);

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
        writer.setUriAsParameters(true);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute3");
        route.setInput(new FromDefinition("direct:start2"));
        AggregateDefinition ag = new AggregateDefinition();
        SimpleExpression e = new SimpleExpression("${body}");
        e.setResultTypeName("int.class");
        ag.setExpression(e);
        ag.setCorrelationExpression(new ExpressionSubElementDefinition(new HeaderExpression("myHeader")));
        ConstantExpression cons = new ConstantExpression("5");
        cons.setResultTypeName("int.class");
        ag.setCompletionSizeExpression(new ExpressionSubElementDefinition(cons));
        ag.setCompletionTimeoutExpression(new ExpressionSubElementDefinition(new ConstantExpression("4000")));
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

    @Test
    public void testFromSetBodyTo() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);
        writer.setUriAsParameters(true);

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
        writer.setUriAsParameters(true);

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

    @Test
    public void testFromChoice() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);
        writer.setUriAsParameters(true);

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
        writer.setUriAsParameters(true);

        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
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
        writer.setUriAsParameters(true);

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
        String expected = stripLineComments(Paths.get("src/test/resources/route8.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testMarshal() throws Exception {
        StringWriter sw = new StringWriter();
        ModelWriter writer = new ModelWriter(sw);
        writer.setUriAsParameters(true);

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

}
