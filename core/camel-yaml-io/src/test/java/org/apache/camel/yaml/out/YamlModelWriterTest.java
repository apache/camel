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

import java.nio.file.Paths;
import java.util.List;

import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.DelayDefinition;
import org.apache.camel.model.DynamicRouterDefinition;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.ExpressionSubElementDefinition;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.IdempotentConsumerDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.MarshalDefinition;
import org.apache.camel.model.MulticastDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.OtherwiseDefinition;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.ThrottleDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.TransformDefinition;
import org.apache.camel.model.UnmarshalDefinition;
import org.apache.camel.model.ValidateDefinition;
import org.apache.camel.model.WhenDefinition;
import org.apache.camel.model.WireTapDefinition;
import org.apache.camel.model.dataformat.CsvDataFormat;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.model.loadbalancer.FailoverLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.RoundRobinLoadBalancerDefinition;
import org.apache.camel.model.rest.GetDefinition;
import org.apache.camel.model.rest.PostDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.util.json.JsonObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.apache.camel.util.IOHelper.stripLineComments;

public class YamlModelWriterTest {

    @Test
    public void testSimpleRoute() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));
        route.addOutput(new LogDefinition("${body}"));
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-simple.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testTwoRoutes() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route1 = new RouteDefinition();
        route1.setId("route1");
        route1.setInput(new FromDefinition("direct:a"));
        route1.addOutput(new ToDefinition("mock:a"));

        RouteDefinition route2 = new RouteDefinition();
        route2.setId("route2");
        route2.setInput(new FromDefinition("direct:b"));
        route2.addOutput(new LogDefinition("${body}"));
        route2.addOutput(new ToDefinition("mock:b"));

        List<JsonObject> roots = List.of(
                writer.writeRouteDefinition(route1),
                writer.writeRouteDefinition(route2));
        String out = writer.printAsYaml(roots);
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-two.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testChoice() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

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

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-choice.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testTransformExpression() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        TransformDefinition transform = new TransformDefinition();
        transform.setExpression(new SimpleExpression("Hello ${body}"));
        route.addOutput(transform);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-transform.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testMarshalUnmarshal() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        MarshalDefinition marshal = new MarshalDefinition();
        marshal.setDataFormatType(new CsvDataFormat());
        route.addOutput(marshal);

        UnmarshalDefinition unmarshal = new UnmarshalDefinition();
        JsonDataFormat jsonDf = new JsonDataFormat();
        jsonDf.setLibrary(JsonLibrary.Jackson);
        unmarshal.setDataFormatType(jsonDf);
        route.addOutput(unmarshal);

        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-marshal.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testMulticast() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        MulticastDefinition multicast = new MulticastDefinition();
        multicast.addOutput(new ToDefinition("mock:a"));
        multicast.addOutput(new ToDefinition("mock:b"));
        multicast.addOutput(new ToDefinition("mock:c"));
        route.addOutput(multicast);

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-multicast.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testWireTap() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        WireTapDefinition<?> wt = new WireTapDefinition<>();
        wt.setUri("mock:tap");
        route.addOutput(wt);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-wiretap.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testSplit() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        SplitDefinition split = new SplitDefinition();
        split.setExpression(new SimpleExpression("${body}"));
        split.setStreaming("true");
        split.addOutput(new ToDefinition("mock:split"));
        route.addOutput(split);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-split.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testCircuitBreaker() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        CircuitBreakerDefinition cb = new CircuitBreakerDefinition();
        Resilience4jConfigurationDefinition r4j = new Resilience4jConfigurationDefinition();
        r4j.setMinimumNumberOfCalls("5");
        r4j.setFailureRateThreshold("70");
        cb.setResilience4jConfiguration(r4j);
        cb.addOutput(new ToDefinition("mock:service"));
        route.addOutput(cb);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-circuitbreaker.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testRouteConfiguration() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteConfigurationDefinition config = new RouteConfigurationDefinition();
        config.setId("myConfig");

        OnExceptionDefinition onEx = new OnExceptionDefinition();
        onEx.getExceptions().add("java.lang.Exception");
        onEx.setHandled(new ExpressionSubElementDefinition(new ConstantExpression("true")));
        onEx.addOutput(new ToDefinition("mock:error"));
        config.getOnExceptions().add(onEx);

        InterceptDefinition intercept = new InterceptDefinition();
        intercept.addOutput(new LogDefinition("intercepted"));
        config.getIntercepts().add(intercept);

        JsonObject jo = writer.writeRouteConfigurationDefinition(config);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-routeconfig.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testRouteTemplate() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteTemplateDefinition template = new RouteTemplateDefinition();
        template.setId("myTemplate");
        template.templateParameter("foo", null, "the foo parameter");
        template.templateParameter("bar", "defaultBar", "the bar parameter");

        RouteDefinition route = new RouteDefinition();
        route.setInput(new FromDefinition("direct:{{foo}}"));
        route.addOutput(new ToDefinition("mock:{{bar}}"));
        template.setRoute(route);

        JsonObject jo = writer.writeRouteTemplateDefinition(template);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-template.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testRestDsl() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RestDefinition rest = new RestDefinition();
        rest.setPath("/api");

        GetDefinition get = new GetDefinition();
        get.setPath("/hello");
        get.setTo(new ToDefinition("direct:hello"));
        rest.getVerbs().add(get);

        PostDefinition post = new PostDefinition();
        post.setPath("/bye");
        post.setConsumes("application/json");
        post.setTo(new ToDefinition("direct:bye"));
        rest.getVerbs().add(post);

        JsonObject jo = writer.writeRestDefinition(rest);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-rest.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testFilter() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        FilterDefinition filter = new FilterDefinition();
        filter.setExpression(new SimpleExpression("${header.foo} == 'bar'"));
        filter.addOutput(new ToDefinition("mock:filtered"));
        route.addOutput(filter);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-filter.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testLoadBalanceRoundRobin() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        LoadBalanceDefinition lb = new LoadBalanceDefinition();
        lb.setLoadBalancerType(new RoundRobinLoadBalancerDefinition());
        lb.addOutput(new ToDefinition("mock:a"));
        lb.addOutput(new ToDefinition("mock:b"));
        route.addOutput(lb);

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-loadbalance.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testLoadBalanceFailover() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        LoadBalanceDefinition lb = new LoadBalanceDefinition();
        FailoverLoadBalancerDefinition fo = new FailoverLoadBalancerDefinition();
        fo.setMaximumFailoverAttempts("3");
        fo.setRoundRobin("true");
        lb.setLoadBalancerType(fo);
        lb.addOutput(new ToDefinition("mock:a"));
        lb.addOutput(new ToDefinition("mock:b"));
        route.addOutput(lb);

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-loadbalance-failover.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testDynamicRouter() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        DynamicRouterDefinition<?> dr = new DynamicRouterDefinition<>();
        dr.setExpression(new SimpleExpression("${header.route}"));
        dr.setUriDelimiter(",");
        dr.setIgnoreInvalidEndpoints("true");
        route.addOutput(dr);

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-dynamic-router.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testRoutingSlip() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        RoutingSlipDefinition<?> rs = new RoutingSlipDefinition<>();
        rs.setExpression(new HeaderExpression("mySlip"));
        rs.setUriDelimiter(",");
        route.addOutput(rs);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-routing-slip.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testRecipientList() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        RecipientListDefinition<?> rl = new RecipientListDefinition<>();
        rl.setExpression(new HeaderExpression("recipients"));
        rl.setDelimiter(",");
        rl.setParallelProcessing("true");
        route.addOutput(rl);

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-recipient-list.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testEnrichPollEnrich() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        EnrichDefinition enrich = new EnrichDefinition();
        enrich.setExpression(new ConstantExpression("direct:resource"));
        enrich.setAggregationStrategy("#myStrategy");
        route.addOutput(enrich);

        PollEnrichDefinition pollEnrich = new PollEnrichDefinition();
        pollEnrich.setExpression(new ConstantExpression("file:inbox"));
        pollEnrich.setTimeout("5000");
        route.addOutput(pollEnrich);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-enrich.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testDelay() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        DelayDefinition delay = new DelayDefinition();
        delay.setExpression(new ConstantExpression("1000"));
        delay.setAsyncDelayed("true");
        route.addOutput(delay);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-delay.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testThrottle() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        ThrottleDefinition throttle = new ThrottleDefinition();
        throttle.setExpression(new ConstantExpression("10"));
        throttle.setTimePeriodMillis("1000");
        throttle.setCorrelationExpression(new ExpressionSubElementDefinition(new HeaderExpression("clientId")));
        route.addOutput(throttle);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-throttle.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testValidate() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        ValidateDefinition validate = new ValidateDefinition();
        validate.setExpression(new SimpleExpression("${body} != null"));
        route.addOutput(validate);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-validate.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testIdempotentConsumer() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        IdempotentConsumerDefinition ic = new IdempotentConsumerDefinition();
        ic.setExpression(new HeaderExpression("messageId"));
        ic.setIdempotentRepository("#myRepo");
        ic.setEager("true");
        ic.setSkipDuplicate("true");
        ic.addOutput(new ToDefinition("mock:result"));
        route.addOutput(ic);

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-idempotent.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testOnCompletion() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        OnCompletionDefinition oc = new OnCompletionDefinition();
        oc.setOnCompleteOnly("true");
        oc.addOutput(new LogDefinition("completed"));
        oc.addOutput(new ToDefinition("mock:done"));
        route.addOutput(oc);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-oncompletion.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }
}
