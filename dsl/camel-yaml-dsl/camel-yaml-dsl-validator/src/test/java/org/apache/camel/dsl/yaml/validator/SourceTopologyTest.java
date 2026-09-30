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
package org.apache.camel.dsl.yaml.validator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.yaml.validator.SourceTopology.BodyOrigin;
import org.apache.camel.dsl.yaml.validator.SourceTopology.Finding;
import org.apache.camel.dsl.yaml.validator.SourceTopology.Result;
import org.apache.camel.dsl.yaml.validator.SourceTopology.RouteInfo;
import org.apache.camel.dsl.yaml.validator.SourceTopology.Skipped;
import org.apache.camel.spi.RouteTopologyDumper.TopologyEdge;
import org.apache.camel.spi.RouteTopologyDumper.TopologyExternalEndpoint;
import org.apache.camel.spi.RouteTopologyDumper.TopologyNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24956: the topology of YAML route files, read from the source: routes, the connections between them, the
 * endpoints that leave and enter the application, where the body comes from, and what looks wrong.
 */
public class SourceTopologyTest {

    private static final String TICK = """
            - route:
                id: tick
                from:
                  uri: timer:tick
                  steps:
                    - to:
                        uri: direct:lookup
            """;

    private static final String LOOKUP = """
            - route:
                id: lookup
                from:
                  uri: direct:lookup
                  steps:
                    - log: found
            """;

    /** The files by name, in the order given. */
    private static Map<String, String> files(String... nameAndContent) {
        Map<String, String> answer = new LinkedHashMap<>();
        for (int i = 0; i < nameAndContent.length; i += 2) {
            answer.put(nameAndContent[i], nameAndContent[i + 1]);
        }
        return answer;
    }

    private static RouteInfo info(Result result, String routeId) {
        return result.routes().stream().filter(r -> r.routeId().equals(routeId)).findFirst().orElseThrow();
    }

    private static List<String> kinds(Result result) {
        return result.findings().stream().map(Finding::kind).toList();
    }

    @Test
    public void testARouteSendingToTheRouteOfAnotherFileIsAnEdge() {
        Result result = SourceTopology.analyze(files("tick.camel.yaml", TICK, "lookup.camel.yaml", LOOKUP));

        assertThat(result.topology().nodes()).extracting(TopologyNode::routeId).containsExactly("tick", "lookup");
        assertThat(result.topology().edges())
                .containsExactly(new TopologyEdge("tick", "lookup", "direct:lookup", "internal"));
        assertThat(result.routes()).extracting(RouteInfo::file).containsExactly("tick.camel.yaml", "lookup.camel.yaml");
        assertThat(info(result, "lookup").callers()).containsExactly("tick");
        assertThat(result.findings()).isEmpty();
        assertThat(result.skipped()).isEmpty();
    }

    @Test
    public void testTheNodeSaysWhereTheRouteStartsAndWhetherItIsATrigger() {
        String described = """
                - route:
                    id: tick
                    description: Ticks every second
                    from:
                      uri: timer:tick?period=1000
                      steps:
                        - log: tick
                """;
        Result result = SourceTopology.analyze(files("tick.camel.yaml", described, "lookup.camel.yaml", LOOKUP));

        assertThat(result.topology().nodes()).containsExactly(
                new TopologyNode("tick", "Ticks every second", "timer:tick", "timer", "trigger"),
                new TopologyNode("lookup", null, "direct:lookup", "direct", "route"));
    }

    @Test
    public void testTheEndpointIsTheSameWhateverWayItIsWritten() {
        String sender = TICK.replace("uri: direct:lookup", "uri: direct://lookup?timeout=1000");

        Result result = SourceTopology.analyze(files("tick.camel.yaml", sender, "lookup.camel.yaml", LOOKUP));

        assertThat(result.topology().edges())
                .containsExactly(new TopologyEdge("tick", "lookup", "direct:lookup", "internal"));
    }

    @Test
    public void testAnEndpointGivenAsAPathInTheParametersIsFound() {
        String sender = """
                - route:
                    id: tick
                    from:
                      uri: timer:tick
                      steps:
                        - to:
                            uri: direct
                            parameters:
                              name: lookup
                """;

        Result result = SourceTopology.analyze(files("tick.camel.yaml", sender, "lookup.camel.yaml", LOOKUP));

        assertThat(result.topology().edges())
                .containsExactly(new TopologyEdge("tick", "lookup", "direct:lookup", "internal"));
    }

    @Test
    public void testARouteWithoutAnIdIsTheNthOfItsFile() {
        String routes = """
                - from:
                    uri: timer:tick
                    steps:
                      - log: tick
                - from:
                    uri: timer:tock
                    steps:
                      - log: tock
                """;

        Result result = SourceTopology.analyze(files("a.camel.yaml", routes));

        assertThat(result.topology().nodes()).extracting(TopologyNode::routeId)
                .containsExactly("a.camel.yaml#1", "a.camel.yaml#2");
    }

    @Test
    public void testTheRemoteSystemsARouteReadsFromAndSendsToAreExternalEndpoints() {
        String route = """
                - route:
                    id: consume
                    from:
                      uri: kafka:orders
                      steps:
                        - to:
                            uri: http:api/orders?bridgeEndpoint=true
                        - to: log:done
                """;

        Result result = SourceTopology.analyze(files("consume.camel.yaml", route));

        // log: is not a remote system, as the catalog has it
        assertThat(result.topology().externalEndpoints()).containsExactly(
                new TopologyExternalEndpoint("in-consume", "kafka:orders", "kafka", "in", "consume"),
                new TopologyExternalEndpoint("out-consume-0", "http:api/orders", "http", "out", "consume"));
        assertThat(result.topology().edges()).isEmpty();
    }

    @Test
    public void testATriggerAndARouteOfTheApplicationAreNotExternalEndpoints() {
        Result result = SourceTopology.analyze(files("tick.camel.yaml", TICK, "lookup.camel.yaml", LOOKUP));

        assertThat(result.topology().externalEndpoints()).isEmpty();
    }

    @Test
    public void testTheBodyOfARouteOnlyATimerCallsIsNone() {
        Result result = SourceTopology.analyze(files("tick.camel.yaml", TICK, "lookup.camel.yaml", LOOKUP));

        assertThat(info(result, "tick").bodyOrigin()).isEqualTo(BodyOrigin.NONE);
        assertThat(info(result, "lookup").bodyOrigin()).isEqualTo(BodyOrigin.NONE);
    }

    @Test
    public void testTheBodyOfARouteCalledAfterTheBodyIsSetComesFromItsCaller() {
        String sender = """
                - route:
                    id: tick
                    from:
                      uri: timer:tick
                      steps:
                        - setBody:
                            constant: hello
                        - to:
                            uri: direct:lookup
                """;

        Result result = SourceTopology.analyze(files("tick.camel.yaml", sender, "lookup.camel.yaml", LOOKUP));

        assertThat(info(result, "tick").bodySetBy()).isEqualTo("setBody");
        assertThat(info(result, "lookup").bodyOrigin()).isEqualTo(BodyOrigin.CALLERS);
        assertThat(info(result, "lookup").bodySetBy()).isNull();
    }

    @Test
    public void testTheBodyOfAConsumerOfItsOwnComesFromTheConsumer() {
        String route = """
                - route:
                    id: consume
                    from:
                      uri: kafka:orders
                      steps:
                        - log: got it
                """;

        Result result = SourceTopology.analyze(files("consume.camel.yaml", route));

        assertThat(info(result, "consume").bodyOrigin()).isEqualTo(BodyOrigin.CONSUMER);
    }

    @Test
    public void testTheBodyOfARouteNoRouteCallsIsUnknown() {
        Result result = SourceTopology.analyze(files("lookup.camel.yaml", LOOKUP));

        assertThat(info(result, "lookup").bodyOrigin()).isEqualTo(BodyOrigin.UNKNOWN);
        assertThat(info(result, "lookup").callers()).isEmpty();
    }

    @Test
    public void testAnEndpointNoRouteConsumesIsAFinding() {
        Result result = SourceTopology.analyze(files("tick.camel.yaml", TICK));

        assertThat(result.findings()).singleElement().satisfies(f -> {
            assertThat(f.kind()).isEqualTo(SourceTopology.UNCONSUMED_ENDPOINT);
            assertThat(f.routeId()).isEqualTo("tick");
            assertThat(f.file()).isEqualTo("tick.camel.yaml");
            assertThat(f.endpoint()).isEqualTo("direct:lookup");
            // it does not claim more than the files read show: Java and XML routes are not read
            assertThat(f.message()).startsWith("route tick: sends to direct:lookup, and no route in the YAML files read"
                                               + " consumes it - if nothing else does (a Java or XML route, a file not read)")
                    .contains("the route fails to start with No consumers available on endpoint");
        });
    }

    @Test
    public void testAnEndpointAnotherFileConsumesIsNotAFinding() {
        Result result = SourceTopology.analyze(files("tick.camel.yaml", TICK, "lookup.camel.yaml", LOOKUP));

        assertThat(kinds(result)).doesNotContain(SourceTopology.UNCONSUMED_ENDPOINT);
    }

    @Test
    public void testAReadOfTheBodyNoRouteSetsIsAFindingAcrossFiles() {
        String reader = """
                - route:
                    id: read
                    from:
                      uri: direct:lookup
                      steps:
                        - setHeader:
                            name: id
                            expression:
                              jsonpath:
                                expression: $.id
                """;

        Result result = SourceTopology.analyze(files("tick.camel.yaml", TICK, "read.camel.yaml", reader));

        assertThat(result.findings()).singleElement().satisfies(f -> {
            assertThat(f.kind()).isEqualTo(SourceTopology.BODY_NEVER_SET);
            assertThat(f.routeId()).isEqualTo("read");
            assertThat(f.file()).isEqualTo("read.camel.yaml");
            assertThat(f.message()).startsWith("route read: jsonpath reads the message body")
                    .contains("the routes that call it do not set one either");
        });
    }

    @Test
    public void testAReadOfTheBodyAfterItIsSetIsNotAFinding() {
        String reader = """
                - route:
                    id: read
                    from:
                      uri: timer:tick
                      steps:
                        - setBody:
                            constant: '{"id": 1}'
                        - setHeader:
                            name: id
                            expression:
                              jsonpath:
                                expression: $.id
                """;

        Result result = SourceTopology.analyze(files("read.camel.yaml", reader));

        assertThat(result.findings()).isEmpty();
    }

    @Test
    public void testARouteNothingSendsToIsAFinding() {
        Result result = SourceTopology.analyze(files("lookup.camel.yaml", LOOKUP));

        assertThat(result.findings()).singleElement().satisfies(f -> {
            assertThat(f.kind()).isEqualTo(SourceTopology.UNCALLED_ROUTE);
            assertThat(f.routeId()).isEqualTo("lookup");
            assertThat(f.endpoint()).isEqualTo("direct:lookup");
            assertThat(f.message()).contains("nothing in the YAML route files sends to direct:lookup");
        });
    }

    @Test
    public void testARouteThatARestOperationSendsToIsCalled() {
        String rest = """
                - rest:
                    get:
                      - path: /lookup
                        to: direct:lookup
                """;

        Result result = SourceTopology.analyze(files("lookup.camel.yaml", LOOKUP, "rest.camel.yaml", rest));

        assertThat(kinds(result)).doesNotContain(SourceTopology.UNCALLED_ROUTE);
    }

    @Test
    public void testNoRouteIsUncalledWhenAnOpenApiSpecificationBindsTheOperations() {
        String rest = """
                - rest:
                    openApi:
                      specification: petstore.json
                """;

        Result result = SourceTopology.analyze(files("lookup.camel.yaml", LOOKUP, "rest.camel.yaml", rest));

        assertThat(result.findings()).isEmpty();
    }

    @Test
    public void testNoRouteIsUncalledWhenTheEndpointToSendToIsOnlyKnownAtRuntime() {
        String dynamic = """
                - route:
                    id: tick
                    from:
                      uri: timer:tick
                      steps:
                        - toD: direct:${header.target}
                """;

        Result result = SourceTopology.analyze(files("tick.camel.yaml", dynamic, "lookup.camel.yaml", LOOKUP));

        assertThat(result.findings()).isEmpty();
    }

    @Test
    public void testARouteTemplateKeepsTheFindingsThatItCouldAnswerQuiet() {
        String template = """
                - routeTemplate:
                    id: any
                    from:
                      uri: direct:any
                """;

        Result result = SourceTopology.analyze(
                files("tick.camel.yaml", TICK, "lookup.camel.yaml", LOOKUP.replace("direct:lookup", "direct:other"),
                        "template.camel.yaml", template));

        assertThat(result.findings()).isEmpty();
        assertThat(result.skipped())
                .containsExactly(new Skipped("template.camel.yaml", SourceTopology.ROUTE_TEMPLATES_NOT_READ));
        assertThat(result.topology().nodes()).extracting(TopologyNode::routeId).containsExactly("tick", "lookup");
    }

    @Test
    public void testAKameletIsSkipped() {
        String kamelet = """
                apiVersion: camel.apache.org/v1
                kind: Kamelet
                metadata:
                  name: source
                """;

        Result result = SourceTopology.analyze(files("source.kamelet.yaml", kamelet));

        assertThat(result.skipped()).containsExactly(new Skipped("source.kamelet.yaml", "kamelet"));
        assertThat(result.topology().nodes()).isEmpty();
    }

    @Test
    public void testAFileThatIsNotYamlIsSkippedAndTheOthersAreRead() {
        Result result = SourceTopology.analyze(files("broken.camel.yaml", "- route: {", "lookup.camel.yaml", LOOKUP));

        assertThat(result.skipped()).containsExactly(new Skipped("broken.camel.yaml", "unparseable"));
        assertThat(result.topology().nodes()).extracting(TopologyNode::routeId).containsExactly("lookup");
    }

    @Test
    public void testNothingToReadGivesAnEmptyTopology() {
        Result none = SourceTopology.analyze(Map.of());
        Result blank = SourceTopology.analyze(files("blank.camel.yaml", "  \n"));
        Result notRoutes = SourceTopology.analyze(files("application.yaml", "server:\n  port: 8080\n"));

        for (Result result : List.of(none, blank, notRoutes)) {
            assertThat(result.topology().nodes()).isEmpty();
            assertThat(result.topology().edges()).isEmpty();
            assertThat(result.topology().externalEndpoints()).isEmpty();
            assertThat(result.routes()).isEmpty();
            assertThat(result.findings()).isEmpty();
            assertThat(result.skipped()).isEmpty();
        }
    }

    @Test
    public void testATopicBetweenTwoRoutesIsAConnectionAndNotTheBorderOfTheApplication() {
        String sender = """
                - route:
                    id: a
                    from:
                      uri: timer:tick
                      steps:
                        - to:
                            uri: kafka:shared
                """;
        String receiver = """
                - route:
                    id: b
                    from:
                      uri: kafka:shared
                      steps:
                        - log: got it
                """;

        Result result = SourceTopology.analyze(files("a.camel.yaml", sender, "b.camel.yaml", receiver));

        assertThat(result.topology().edges()).containsExactly(new TopologyEdge("a", "b", "kafka:shared", "external"));
        assertThat(result.topology().externalEndpoints()).isEmpty();
    }

    @Test
    public void testARouteThatSendsToTheSameEndpointTwiceHasOneExternalEndpointForIt() {
        String route = """
                - route:
                    id: a
                    from:
                      uri: timer:tick
                      steps:
                        - to:
                            uri: http:api
                        - to:
                            uri: http:api?bridgeEndpoint=true
                        - to:
                            uri: http:other
                """;

        Result result = SourceTopology.analyze(files("a.camel.yaml", route));

        // the index only counts what was added
        assertThat(result.topology().externalEndpoints()).containsExactly(
                new TopologyExternalEndpoint("out-a-0", "http:api", "http", "out", "a"),
                new TopologyExternalEndpoint("out-a-1", "http:other", "http", "out", "a"));
    }

    @Test
    public void testLogBeanAndMockAreNotRemoteSystems() {
        String route = """
                - route:
                    id: a
                    from:
                      uri: timer:tick
                      steps:
                        - to: log:done
                        - to: bean:orders
                        - to: mock:result
                """;

        Result result = SourceTopology.analyze(files("a.camel.yaml", route));

        assertThat(result.topology().externalEndpoints()).isEmpty();
    }

    @Test
    public void testAComponentTheCatalogDoesNotKnowIsRemoteUnlessItIsInternalOrATrigger() {
        String route = """
                - route:
                    id: a
                    from:
                      uri: timer:tick
                      steps:
                        - to: vm:inbox
                        - to: direct-vm:inbox
                        - to: nosuchcomponent:somewhere
                """;

        Result result = SourceTopology.analyze(files("a.camel.yaml", route));

        assertThat(result.topology().externalEndpoints()).extracting(TopologyExternalEndpoint::uri)
                .containsExactly("nosuchcomponent:somewhere");
    }

    @Test
    public void testTheCatalogCanBeGiven() {
        String route = """
                - route:
                    id: consume
                    from:
                      uri: kafka:orders
                      steps:
                        - to: http:api
                        - to: log:done
                """;

        Result withDefault = SourceTopology.analyze(files("consume.camel.yaml", route));
        Result given = SourceTopology.analyze(files("consume.camel.yaml", route), new DefaultCamelCatalog());

        assertThat(given.topology()).isEqualTo(withDefault.topology());
        assertThat(given.topology().externalEndpoints()).extracting(TopologyExternalEndpoint::uri)
                .containsExactly("kafka:orders", "http:api");
    }

    @Test
    public void testARouteThatIsTheDeadLetterOfAGlobalErrorHandlerIsCalled() {
        String routes = """
                - errorHandler:
                    deadLetterChannel:
                      deadLetterUri: "direct:dlq"
                - route:
                    id: dlq
                    from:
                      uri: direct:dlq
                      steps:
                        - log: dead
                """;

        assertThat(SourceTopology.analyze(files("dlq.camel.yaml", routes)).findings()).isEmpty();
    }

    @Test
    public void testARouteThatIsTheDeadLetterOfARouteConfigurationIsCalled() {
        String routes = """
                - routeConfiguration:
                    errorHandler:
                      deadLetterChannel:
                        deadLetterUri: direct:dlq
                - route:
                    id: dlq
                    from:
                      uri: direct:dlq
                      steps:
                        - log: dead
                """;

        assertThat(SourceTopology.analyze(files("dlq.camel.yaml", routes)).findings()).isEmpty();
    }

    @Test
    public void testARouteThatIsTheDeadLetterOfARouteIsCalled() {
        String routes = """
                - route:
                    id: work
                    errorHandler:
                      deadLetterChannel:
                        deadLetterUri: direct:dlq
                    from:
                      uri: timer:tick
                      steps:
                        - log: work
                - route:
                    id: dlq
                    from:
                      uri: direct:dlq
                      steps:
                        - log: dead
                """;

        assertThat(SourceTopology.analyze(files("dlq.camel.yaml", routes)).findings()).isEmpty();
    }

    @Test
    public void testTheRoutesOfASagaAreCalled() {
        String routes = """
                - route:
                    id: work
                    from:
                      uri: timer:tick
                      steps:
                        - saga:
                            compensation: direct:compensate
                            completion: "direct:complete"
                            steps:
                              - log: work
                - route:
                    id: compensate
                    from:
                      uri: direct:compensate
                      steps:
                        - log: compensate
                - route:
                    id: complete
                    from:
                      uri: direct:complete
                      steps:
                        - log: complete
                """;

        assertThat(SourceTopology.analyze(files("saga.camel.yaml", routes)).findings()).isEmpty();
    }

    @Test
    public void testADeadLetterUriDoesNotHideAnotherRouteNobodyCalls() {
        String routes = """
                - errorHandler:
                    deadLetterChannel:
                      deadLetterUri: "direct:dlq"
                - route:
                    id: dlq
                    from:
                      uri: direct:dlq
                      steps:
                        - log: dead
                - route:
                    id: orphan
                    from:
                      uri: direct:orphan
                      steps:
                        - log: nobody calls me
                """;

        assertThat(SourceTopology.analyze(files("dlq.camel.yaml", routes)).findings())
                .singleElement().satisfies(f -> {
                    assertThat(f.kind()).isEqualTo(SourceTopology.UNCALLED_ROUTE);
                    assertThat(f.routeId()).isEqualTo("orphan");
                });
    }

    @Test
    public void testTheRoutesOfAFileWithARouteTemplateAreRead() {
        String mixed = """
                - routeTemplate:
                    id: any
                    from:
                      uri: direct:any
                - route:
                    id: regular
                    from:
                      uri: timer:tick
                      steps:
                        - to: direct:missing
                """;

        Result result = SourceTopology.analyze(files("mixed.camel.yaml", mixed));

        assertThat(result.topology().nodes()).extracting(TopologyNode::routeId).containsExactly("regular");
        assertThat(result.routes()).extracting(RouteInfo::file).containsExactly("mixed.camel.yaml");
        assertThat(result.skipped())
                .containsExactly(new Skipped("mixed.camel.yaml", SourceTopology.ROUTE_TEMPLATES_NOT_READ));
        // what the template creates could consume direct:missing, so nothing is said about it
        assertThat(result.findings()).isEmpty();
    }

    @Test
    public void testACallerThatWritesTheEndpointWithSlashesIsSeenByTheBodyToo() {
        String sender = TICK.replace("uri: direct:lookup", "uri: direct://lookup?timeout=1000");
        String reader = """
                - route:
                    id: lookup
                    from:
                      uri: direct:lookup
                      steps:
                        - setHeader:
                            name: id
                            expression:
                              jsonpath:
                                expression: $.id
                """;

        Result result = SourceTopology.analyze(files("tick.camel.yaml", sender, "lookup.camel.yaml", reader));

        assertThat(result.topology().edges())
                .containsExactly(new TopologyEdge("tick", "lookup", "direct:lookup", "internal"));
        // the same as with direct:lookup: nothing sets a body, and the route reads one
        assertThat(info(result, "lookup").bodyOrigin()).isEqualTo(BodyOrigin.NONE);
        assertThat(kinds(result)).containsExactly(SourceTopology.BODY_NEVER_SET);
    }

    private static final String DEAD_LETTER = """
            - errorHandler:
                deadLetterChannel:
                  deadLetterUri: "direct:dlq"
            """;

    private static final String TIMER_TO_DLQ = """
            - route:
                id: tick
                from:
                  uri: timer:tick
                  steps:
                    - to: direct:dlq
            """;

    private static final String DLQ_READER = """
            - route:
                id: dlq
                from:
                  uri: direct:dlq
                  steps:
                    - setHeader:
                        name: id
                        expression:
                          jsonpath:
                            expression: $.id
            """;

    @Test
    public void testTheBodyOfADeadLetterRouteIsTheMessageThatWasBeingHandled() {
        String dlq = """
                - route:
                    id: dlq
                    from:
                      uri: direct:dlq
                      steps:
                        - log: dead
                """;

        Result result = SourceTopology.analyze(files("dlq.camel.yaml", DEAD_LETTER + dlq));

        // not unknown: something calls it, an error handler
        assertThat(info(result, "dlq").bodyOrigin()).isEqualTo(BodyOrigin.HANDLER);
        assertThat(info(result, "dlq").callers()).isEmpty();
        assertThat(result.findings()).isEmpty();
    }

    @Test
    public void testARouteThatIsAlsoADeadLetterIsNotSaidToHaveNoBody() {
        // a timer calls it and nothing sets a body, but the error handler calls it with the message that failed
        Result withHandler = SourceTopology.analyze(
                files("routes.camel.yaml", DEAD_LETTER + TIMER_TO_DLQ + DLQ_READER));

        assertThat(info(withHandler, "dlq").bodyOrigin()).isEqualTo(BodyOrigin.HANDLER);
        assertThat(info(withHandler, "dlq").callers()).containsExactly("tick");
        assertThat(kinds(withHandler)).doesNotContain(SourceTopology.BODY_NEVER_SET);

        // the same routes without the error handler: nothing sets a body, and the route reads one
        Result without = SourceTopology.analyze(files("routes.camel.yaml", TIMER_TO_DLQ + DLQ_READER));

        assertThat(info(without, "dlq").bodyOrigin()).isEqualTo(BodyOrigin.NONE);
        assertThat(kinds(without)).containsExactly(SourceTopology.BODY_NEVER_SET);
    }

    @Test
    public void testTheRoutesASagaRouteCallsAreNotSaidToHaveNoBodyEither() {
        String routes = """
                - route:
                    id: work
                    from:
                      uri: timer:t
                      steps:
                        - saga:
                            compensation: direct:comp
                            steps:
                              - log: work
                        - to: direct:comp
                - route:
                    id: comp
                    from:
                      uri: direct:comp
                      steps:
                        - to: direct:audit
                - route:
                    id: audit
                    from:
                      uri: direct:audit
                      steps:
                        - setHeader:
                            name: id
                            expression:
                              jsonpath:
                                expression: $.id
                """;

        Result result = SourceTopology.analyze(files("saga.camel.yaml", routes));

        assertThat(info(result, "comp").bodyOrigin()).isEqualTo(BodyOrigin.HANDLER);
        // called by comp, whose body is not known
        assertThat(info(result, "audit").bodyOrigin()).isEqualTo(BodyOrigin.CALLERS);
        assertThat(result.findings()).isEmpty();
    }
}
