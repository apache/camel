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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.model.A2ASubTaskDefinition;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.BeanFactoryDefinition;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.CircuitBreakerDefinition;
import org.apache.camel.model.ClaimCheckDefinition;
import org.apache.camel.model.ConvertBodyDefinition;
import org.apache.camel.model.ConvertHeaderDefinition;
import org.apache.camel.model.DelayDefinition;
import org.apache.camel.model.DynamicRouterDefinition;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.ErrorHandlerDefinition;
import org.apache.camel.model.ExpressionSubElementDefinition;
import org.apache.camel.model.FilterDefinition;
import org.apache.camel.model.FinallyDefinition;
import org.apache.camel.model.FromDefinition;
import org.apache.camel.model.IdempotentConsumerDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.KameletDefinition;
import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.LoopDefinition;
import org.apache.camel.model.MarshalDefinition;
import org.apache.camel.model.MulticastDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.OtherwiseDefinition;
import org.apache.camel.model.PipelineDefinition;
import org.apache.camel.model.PollDefinition;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.ProcessDefinition;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.model.RedeliveryPolicyDefinition;
import org.apache.camel.model.RemoveHeaderDefinition;
import org.apache.camel.model.RemovePropertyDefinition;
import org.apache.camel.model.RemoveVariableDefinition;
import org.apache.camel.model.ResequenceDefinition;
import org.apache.camel.model.Resilience4jConfigurationDefinition;
import org.apache.camel.model.RollbackDefinition;
import org.apache.camel.model.RouteConfigurationDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteTemplateDefinition;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.model.SagaDefinition;
import org.apache.camel.model.SamplingDefinition;
import org.apache.camel.model.ScriptDefinition;
import org.apache.camel.model.SetPropertyDefinition;
import org.apache.camel.model.SetVariableDefinition;
import org.apache.camel.model.SortDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.StepDefinition;
import org.apache.camel.model.StopDefinition;
import org.apache.camel.model.ThreadsDefinition;
import org.apache.camel.model.ThrottleDefinition;
import org.apache.camel.model.ThrowExceptionDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.ToDynamicDefinition;
import org.apache.camel.model.TransactedDefinition;
import org.apache.camel.model.TransformDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.model.UnmarshalDefinition;
import org.apache.camel.model.ValidateDefinition;
import org.apache.camel.model.WhenDefinition;
import org.apache.camel.model.WireTapDefinition;
import org.apache.camel.model.app.BeansDefinition;
import org.apache.camel.model.config.BatchResequencerConfig;
import org.apache.camel.model.dataformat.CsvDataFormat;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.errorhandler.DeadLetterChannelDefinition;
import org.apache.camel.model.errorhandler.DefaultErrorHandlerDefinition;
import org.apache.camel.model.language.ConstantExpression;
import org.apache.camel.model.language.HeaderExpression;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.model.loadbalancer.FailoverLoadBalancerDefinition;
import org.apache.camel.model.loadbalancer.RoundRobinLoadBalancerDefinition;
import org.apache.camel.model.rest.GetDefinition;
import org.apache.camel.model.rest.PostDefinition;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestConfigurationDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.transformer.EndpointTransformerDefinition;
import org.apache.camel.model.transformer.TransformerDefinition;
import org.apache.camel.model.transformer.TransformersDefinition;
import org.apache.camel.model.validator.EndpointValidatorDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.model.validator.ValidatorsDefinition;
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
    public void testA2ASubTask() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

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

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));

        Assertions.assertTrue(out.contains("- a2aSubTask:"));
        Assertions.assertTrue(out.contains("emitBefore:"));
        Assertions.assertTrue(out.contains("Before ${body}"));
        Assertions.assertTrue(out.contains("emitAfter:"));
        Assertions.assertTrue(out.contains("After ${body}"));
        Assertions.assertTrue(out.contains("emitOnError:"));
        Assertions.assertTrue(out.contains("Failed ${exception.message}"));
        Assertions.assertTrue(out.contains("failIfNoTaskContext: true"));
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

    @Test
    public void testTryCatchFinally() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        TryDefinition tryDef = new TryDefinition();
        tryDef.addOutput(new ToDefinition("mock:try"));

        CatchDefinition catchDef = new CatchDefinition();
        catchDef.getExceptions().add("java.io.IOException");
        catchDef.addOutput(new ToDefinition("mock:catch"));
        tryDef.addOutput(catchDef);

        FinallyDefinition finallyDef = new FinallyDefinition();
        finallyDef.addOutput(new ToDefinition("mock:finally"));
        tryDef.addOutput(finallyDef);

        route.addOutput(tryDef);

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-trycatch.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testSaga() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        SagaDefinition saga = new SagaDefinition();
        saga.setCompensation("direct:compensate");
        saga.setCompletion("direct:complete");
        saga.setPropagation("MANDATORY");
        saga.addOutput(new ToDefinition("mock:saga"));
        route.addOutput(saga);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-saga.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testSetVariableRemoveHeader() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        SetVariableDefinition sv = new SetVariableDefinition();
        sv.setName("myVar");
        sv.setExpression(new SimpleExpression("${body}"));
        route.addOutput(sv);

        SetPropertyDefinition sp = new SetPropertyDefinition();
        sp.setName("myProp");
        sp.setExpression(new ConstantExpression("propValue"));
        route.addOutput(sp);

        route.addOutput(new RemoveHeaderDefinition("foo"));
        route.addOutput(new RemovePropertyDefinition("bar"));
        route.addOutput(new RemoveVariableDefinition("myVar"));
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-setvariable.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testConvertBodyHeader() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        ConvertBodyDefinition cb = new ConvertBodyDefinition("java.lang.String");
        cb.setCharset("UTF-8");
        route.addOutput(cb);

        ConvertHeaderDefinition ch = new ConvertHeaderDefinition();
        ch.setName("myHeader");
        ch.setType("java.lang.Integer");
        route.addOutput(ch);

        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-convertbody.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testLoop() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        LoopDefinition loop = new LoopDefinition();
        loop.setExpression(new ConstantExpression("3"));
        loop.setCopy("true");
        loop.addOutput(new ToDefinition("mock:loop"));
        route.addOutput(loop);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-loop.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testStep() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        StepDefinition step = new StepDefinition();
        step.setId("myStep");
        step.addOutput(new LogDefinition("step log"));
        step.addOutput(new ToDefinition("mock:step"));
        route.addOutput(step);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-step.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testPipeline() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        PipelineDefinition pipeline = new PipelineDefinition();
        pipeline.addOutput(new ToDefinition("direct:a"));
        pipeline.addOutput(new ToDefinition("direct:b"));
        pipeline.addOutput(new ToDefinition("direct:c"));
        route.addOutput(pipeline);

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-pipeline.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testToDynamic() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        ToDynamicDefinition toD = new ToDynamicDefinition();
        toD.setUri("${header.destination}");
        toD.setCacheSize("100");
        route.addOutput(toD);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-todynamic.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testProcessBean() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        ProcessDefinition process = new ProcessDefinition();
        process.setRef("#myProcessor");
        route.addOutput(process);

        BeanDefinition bean = new BeanDefinition();
        bean.setRef("#myBean");
        bean.setMethod("transform");
        route.addOutput(bean);

        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-processbean.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testResequence() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        ResequenceDefinition reseq = new ResequenceDefinition();
        reseq.setExpression(new SimpleExpression("${header.seqNum}"));
        BatchResequencerConfig batchConfig = new BatchResequencerConfig();
        batchConfig.setBatchSize("100");
        batchConfig.setBatchTimeout("2000");
        reseq.setBatchConfig(batchConfig);
        reseq.addOutput(new ToDefinition("mock:result"));
        route.addOutput(reseq);

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-resequence.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testClaimCheck() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        ClaimCheckDefinition push = new ClaimCheckDefinition();
        push.setOperation("Push");
        push.setKey("myKey");
        route.addOutput(push);

        route.addOutput(new ToDefinition("mock:process"));

        ClaimCheckDefinition pop = new ClaimCheckDefinition();
        pop.setOperation("Pop");
        pop.setKey("myKey");
        route.addOutput(pop);

        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-claimcheck.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testSamplingThreadsSortScript() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        SamplingDefinition sampling = new SamplingDefinition();
        sampling.setMessageFrequency("5");
        route.addOutput(sampling);

        ThreadsDefinition threads = new ThreadsDefinition();
        threads.setPoolSize("5");
        threads.setMaxPoolSize("10");
        route.addOutput(threads);

        SortDefinition<?> sort = new SortDefinition<>();
        sort.setExpression(new SimpleExpression("${body}"));
        route.addOutput(sort);

        ScriptDefinition script = new ScriptDefinition();
        script.setExpression(new SimpleExpression("log.info('hello')"));
        route.addOutput(script);

        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-misc.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testRollbackStopThrowException() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        route.addOutput(new StopDefinition());

        RollbackDefinition rollback = new RollbackDefinition();
        rollback.setMessage("forced rollback");
        route.addOutput(rollback);

        ThrowExceptionDefinition throwEx = new ThrowExceptionDefinition();
        throwEx.setExceptionType("java.lang.IllegalArgumentException");
        throwEx.setMessage("bad input");
        route.addOutput(throwEx);

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-stop.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testOnExceptionStandalone() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        OnExceptionDefinition onEx = new OnExceptionDefinition();
        onEx.getExceptions().add("java.io.IOException");
        onEx.setHandled(new ExpressionSubElementDefinition(new ConstantExpression("true")));
        RedeliveryPolicyDefinition redelivery = new RedeliveryPolicyDefinition();
        redelivery.setMaximumRedeliveries("3");
        onEx.setRedeliveryPolicyType(redelivery);
        onEx.addOutput(new ToDefinition("mock:error"));
        route.addOutput(onEx);

        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-onexception.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testPoll() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        PollDefinition poll = new PollDefinition();
        poll.setUri("file:inbox");
        poll.setTimeout("5000");
        route.addOutput(poll);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-poll.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testErrorHandlerDeadLetterChannel() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        ErrorHandlerDefinition ehDef = new ErrorHandlerDefinition();
        DeadLetterChannelDefinition dlc = new DeadLetterChannelDefinition();
        dlc.setDeadLetterUri("mock:dead");
        RedeliveryPolicyDefinition rp = new RedeliveryPolicyDefinition();
        rp.setMaximumRedeliveries("3");
        rp.setRedeliveryDelay("2000");
        dlc.setRedeliveryPolicy(rp);
        ehDef.setErrorHandlerType(dlc);

        JsonObject jo = writer.writeErrorHandlerDefinition(ehDef);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-errorhandler-dlc.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testErrorHandlerDefault() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        ErrorHandlerDefinition ehDef = new ErrorHandlerDefinition();
        DefaultErrorHandlerDefinition deh = new DefaultErrorHandlerDefinition();
        deh.setLevel("WARN");
        RedeliveryPolicyDefinition rp = new RedeliveryPolicyDefinition();
        rp.setMaximumRedeliveries("5");
        deh.setRedeliveryPolicy(rp);
        ehDef.setErrorHandlerType(deh);

        JsonObject jo = writer.writeErrorHandlerDefinition(ehDef);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-errorhandler-default.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testRestConfiguration() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RestConfigurationDefinition rc = new RestConfigurationDefinition();
        rc.setComponent("platform-http");
        rc.setHost("localhost");
        rc.setPort("8080");
        rc.setBindingMode(RestBindingMode.json);
        rc.setContextPath("/api");

        JsonObject jo = writer.writeRestConfigurationDefinition(rc);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-restconfiguration.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testTransformers() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        TransformersDefinition transformers = new TransformersDefinition();
        EndpointTransformerDefinition et = new EndpointTransformerDefinition();
        et.setFromType("xml");
        et.setToType("json");
        et.setUri("direct:transform");
        List<TransformerDefinition> tList = new ArrayList<>();
        tList.add(et);
        transformers.setTransformers(tList);

        JsonObject jo = writer.writeTransformersDefinition(transformers);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-transformers.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testValidators() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        ValidatorsDefinition validators = new ValidatorsDefinition();
        EndpointValidatorDefinition ev = new EndpointValidatorDefinition();
        ev.setType("json");
        ev.setUri("direct:validate");
        List<ValidatorDefinition> vList = new ArrayList<>();
        vList.add(ev);
        validators.setValidators(vList);

        JsonObject jo = writer.writeValidatorsDefinition(validators);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-validators.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testKamelet() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        KameletDefinition kamelet = new KameletDefinition();
        kamelet.setName("my-kamelet");
        kamelet.addOutput(new ToDefinition("mock:kamelet"));
        route.addOutput(kamelet);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-kamelet.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testTransacted() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));

        TransactedDefinition transacted = new TransactedDefinition();
        transacted.setRef("myTransactionPolicy");
        transacted.addOutput(new ToDefinition("mock:transacted"));
        route.addOutput(transacted);
        route.addOutput(new ToDefinition("mock:result"));

        JsonObject jo = writer.writeRouteDefinition(route);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-route-transacted.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testBeans() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        BeanFactoryDefinition<?> bean1 = new BeanFactoryDefinition<>();
        bean1.setName("myBean");
        bean1.setType("com.example.MyBean");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("greeting", "Hello");
        props.put("count", "5");
        bean1.setProperties(props);

        BeanFactoryDefinition<?> bean2 = new BeanFactoryDefinition<>();
        bean2.setName("myFactory");
        bean2.setType("com.example.MyFactory");
        bean2.setFactoryMethod("create");
        bean2.setInitMethod("init");
        bean2.setDestroyMethod("cleanup");

        List<JsonObject> result = new ArrayList<>();
        result.add(writer.writeBeanFactoryDefinition(bean1));
        result.add(writer.writeBeanFactoryDefinition(bean2));
        String out = writer.printAsYaml(result);
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-beans.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Test
    public void testBeansWithRoute() throws Exception {
        YamlModelWriter writer = new YamlModelWriter();

        BeansDefinition beansContainer = new BeansDefinition();

        BeanFactoryDefinition<?> bean = new BeanFactoryDefinition<>();
        bean.setName("myService");
        bean.setType("com.example.MyService");
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("url", "http://localhost:8080");
        bean.setProperties(props);
        List<BeanFactoryDefinition> beanList = new ArrayList<>();
        beanList.add(bean);
        beansContainer.setBeans(beanList);

        RouteDefinition route = new RouteDefinition();
        route.setId("myRoute");
        route.setInput(new FromDefinition("direct:start"));
        route.addOutput(new LogDefinition("${body}"));
        route.addOutput(new ToDefinition("mock:result"));
        beansContainer.setRoutes(List.of(route));

        JsonObject jo = writer.writeBeansDefinition(beansContainer);
        String out = writer.printAsYaml(List.of(jo));
        String expected = stripLineComments(Paths.get("src/test/resources/yaml-beans-with-route.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }
}
