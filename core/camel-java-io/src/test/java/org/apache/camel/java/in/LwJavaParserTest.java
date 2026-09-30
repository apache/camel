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
package org.apache.camel.java.in;

import java.util.List;

import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.SetHeaderDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.TryDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LwJavaParserTest {

    private static final String ORDERS = """
            package com.acme.shop;

            import com.acme.shop.errors.InvalidOrderException;
            import org.apache.camel.LoggingLevel;
            import org.apache.camel.builder.RouteBuilder;

            public class OrderRoutes extends RouteBuilder {

                private static final String TOPIC = "orders";
                private static final String KAFKA = "kafka:" + TOPIC + "?brokers={{brokers}}";

                @Override
                public void configure() throws Exception {
                    // where orders come in
                    from("platform-http:/orders")
                        .routeId("order-intake")
                        .routeDescription("Order entry point")
                        .setHeader("source").constant("web")
                        .choice()
                            .when(header("priority").isEqualTo("high"))
                                .to(KAFKA)
                            .otherwise()
                                .log(LoggingLevel.INFO, "normal order ${body}")
                        .end()
                        .doTry()
                            .to("direct:validate")
                        .doCatch(InvalidOrderException.class)
                            .to("direct:dead-letter")
                        .end();

                    from("direct:validate").routeId("validate")
                        .split(body().tokenize("\\n")).streaming()
                            .process(exchange -> exchange.getIn().setHeader("x", 1))
                        .end()
                        .to(orderUri());
                }

                private String orderUri() {
                    return "direct:x";
                }
            }
            """;

    @Test
    void readsRoutesIntoTheModel() {
        JavaParseResult result = new LwJavaParser().parse(ORDERS);
        List<RouteDefinition> routes = result.routes().getRoutes();
        assertThat(routes).extracting(RouteDefinition::getRouteId).containsExactly("order-intake", "validate");

        RouteDefinition intake = routes.get(0);
        assertThat(intake.getInput().getUri()).isEqualTo("platform-http:/orders");
        assertThat(intake.getDescription()).isEqualTo("Order entry point");
        // setHeader(...).constant(...) is an expression clause: the DSL itself put it together
        SetHeaderDefinition setHeader = (SetHeaderDefinition) intake.getOutputs().get(0);
        assertThat(setHeader.getName()).isEqualTo("source");
        // the clause is kept as its language, as XML and YAML have it
        assertThat(RoundTripTest.dump(intake)).contains(".setHeader(\"source\", constant(\"web\"))");

        ChoiceDefinition choice = (ChoiceDefinition) intake.getOutputs().get(1);
        assertThat(choice.getWhenClauses()).hasSize(1);
        // constants and concatenation are worked out
        assertThat(((ToDefinition) choice.getWhenClauses().get(0).getOutputs().get(0)).getUri())
                .isEqualTo("kafka:orders?brokers={{brokers}}");
        assertThat(choice.getOtherwise().getOutputs()).hasSize(1);

        // end() closed the choice: the doTry follows it on the route
        TryDefinition doTry = (TryDefinition) intake.getOutputs().get(2);
        assertThat(doTry.getCatchClauses()).hasSize(1);
        assertThat(doTry.getCatchClauses().get(0).getExceptions())
                .containsExactly("com.acme.shop.errors.InvalidOrderException");

        // line numbers of the source
        assertThat(intake.getLineNumber()).isEqualTo(15);
        assertThat(choice.getLineNumber()).isEqualTo(19);

        RouteDefinition validate = routes.get(1);
        SplitDefinition split = (SplitDefinition) validate.getOutputs().get(0);
        assertThat(split.getStreaming()).isEqualTo("true");
        assertThat(split.getOutputs().get(0)).isInstanceOf(ProcessDefinition.class);
        // a helper method's value is not guessed
        assertThat(((ToDefinition) validate.getOutputs().get(1)).getUri())
                .isEqualTo(LwJavaParser.UNRESOLVED_PREFIX + "orderUri()}");

        assertThat(result.isComplete()).isFalse();
        assertThat(result.unresolved()).extracting(JavaParseResult.Unresolved::line).containsExactly(33, 35);
        assertThat(result.unresolved().get(0).reason()).contains("lambda");
        assertThat(result.unresolved().get(1).text()).isEqualTo("orderUri()");
    }

    @Test
    void neverLoadsTheProjectsClasses() {
        new LwJavaParser().parse(ORDERS);
        // the exception class was a stub of the parse, not loaded where the parser runs
        assertThatThrownBy(() -> Class.forName("com.acme.shop.errors.InvalidOrderException"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void readsASnippet() {
        JavaParseResult result = new LwJavaParser().parse("""
                from("timer:tick?period=5000")
                    .setBody(simple("Hello ${date:now}"))
                    .onException(IllegalStateException.class).handled(true).end()
                    .to("log:out");
                """);
        assertThat(result.routes().getRoutes()).hasSize(1);
        assertThat(result.isComplete()).as("%s", result.unresolved()).isTrue();
    }

    @Test
    void everyStepHasTheLineOfItsCall() {
        // CAMEL-25192: the TUI Source tab links a to(...) line to the route it sends to
        JavaParseResult result = new LwJavaParser().parse("""
                public class R extends RouteBuilder {
                    public void configure() {
                        from("direct:a")
                            .setHeader("x").constant("y")
                            .choice()
                                .when(header("x").isEqualTo("y"))
                                    .to("direct:b")
                                .otherwise()
                                    .toD("direct:${header.x}")
                            .end()
                            .wireTap("direct:tap");
                        from("direct:b").to("log:b");
                    }
                }
                """);
        List<RouteDefinition> routes = result.routes().getRoutes();
        RouteDefinition a = routes.get(0);
        assertThat(a.getLineNumber()).isEqualTo(3);
        assertThat(a.getInput().getLineNumber()).isEqualTo(3);
        assertThat(a.getOutputs()).extracting(ProcessorDefinition::getLineNumber).containsExactly(4, 5, 11);
        ChoiceDefinition choice = (ChoiceDefinition) a.getOutputs().get(1);
        assertThat(choice.getWhenClauses().get(0).getLineNumber()).isEqualTo(6);
        assertThat(choice.getWhenClauses().get(0).getOutputs().get(0).getLineNumber()).isEqualTo(7);
        assertThat(choice.getOtherwise().getLineNumber()).isEqualTo(8);
        assertThat(choice.getOtherwise().getOutputs().get(0).getLineNumber()).isEqualTo(9);
        assertThat(routes.get(1).getOutputs().get(0).getLineNumber()).isEqualTo(12);
    }

    @Test
    void globalErrorHandlingAndRest() {
        JavaParseResult result = new LwJavaParser().parse("""
                public class R extends RouteBuilder {
                    public void configure() {
                        onException(Exception.class).maximumRedeliveries(3).handled(true).to("direct:dlq");
                        rest("/api").get("/hello").to("direct:hello");
                        from("direct:hello").transform().constant("Hi");
                    }
                }
                """);
        assertThat(result.isComplete()).as("%s", result.unresolved()).isTrue();
        OnExceptionDefinition oe = result.routes().getOnExceptions().get(0);
        assertThat(oe.getExceptions()).containsExactly("java.lang.Exception");
        assertThat(oe.getRedeliveryPolicyType().getMaximumRedeliveries()).isEqualTo("3");
        assertThat(result.rests().getRests().get(0).getPath()).isEqualTo("/api");
    }

    @Test
    void fastForALargeSource() {
        StringBuilder sb = new StringBuilder("public class R extends RouteBuilder { public void configure() {\n");
        for (int i = 0; i < 200; i++) {
            sb.append(
                    "from(\"direct:r").append(i).append("\").routeId(\"r").append(i)
                    .append("\").filter(header(\"x\").isEqualTo(").append(i).append("))")
                    .append(".split(body()).to(\"log:a\").end().end().to(\"mock:out\");\n");
        }
        sb.append("}}");
        new LwJavaParser().parse(sb.toString());
        long start = System.nanoTime();
        JavaParseResult result = new LwJavaParser().parse(sb.toString());
        long ms = (System.nanoTime() - start) / 1_000_000;
        assertThat(result.routes().getRoutes()).hasSize(200);
        assertThat(ms).as("200 routes took %d ms", ms).isLessThan(2000);
    }

    @Test
    void routesInLoopsAndConditionsAreReadOnceAndMarked() {
        JavaParseResult result = new LwJavaParser().parse("""
                public class R extends RouteBuilder {
                    public void configure() {
                        for (int i = 0; i < 20; i++) {
                            from("seda:route-" + i).routeId("route-" + i).to("mock:result");
                        }
                        if (enabled) {
                            from("timer:tick").to("log:tick");
                        } else {
                            from("timer:tock").to("log:tock");
                        }
                        try {
                            from("direct:a").to("mock:a");
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }
                }
                """);
        assertThat(result.routes().getRoutes()).extracting(r -> r.getInput().getUri())
                .containsExactly(LwJavaParser.UNRESOLVED_PREFIX + "\"seda:route-\" + i}", "timer:tick", "timer:tock",
                        "direct:a");
        assertThat(result.unresolved()).extracting(JavaParseResult.Unresolved::line).containsExactly(4, 4);
    }

    @Test
    void constantsOfClassesTheParserCannotSeeComeFromTheResolver() {
        ConstantResolver resolver = (className, field) -> switch (className + "#" + field) {
            case "org.apache.camel.component.kafka.KafkaConstants#KEY" -> "kafka.KEY";
            case "com.acme.Application#QUEUE" -> "orders";
            case "com.acme.Names#TOPIC" -> "audit";
            default -> null;
        };
        JavaParseResult result = new LwJavaParser().setConstantResolver(resolver).parse("""
                package com.acme;

                import org.apache.camel.component.kafka.KafkaConstants;
                import static com.acme.Names.*;

                public class Routes extends RouteBuilder {
                    public void configure() {
                        from("seda:" + Application.QUEUE)
                            .setHeader(KafkaConstants.KEY, constant("x"))
                            .to("kafka:" + TOPIC)
                            .to("log:" + Unknown.FIELD);
                    }
                }
                """);
        RouteDefinition route = result.routes().getRoutes().get(0);
        assertThat(route.getInput().getUri()).isEqualTo("seda:orders");
        assertThat(((SetHeaderDefinition) route.getOutputs().get(0)).getName()).isEqualTo("kafka.KEY");
        assertThat(((ToDefinition) route.getOutputs().get(1)).getUri()).isEqualTo("kafka:audit");
        assertThat(result.unresolved()).singleElement()
                .satisfies(u -> assertThat(u.text()).isEqualTo("\"log:\" + Unknown.FIELD"));
    }

    @Test
    void theConstantsOfASource() {
        assertThat(LwJavaParser.constants("""
                public class Application {
                    public static final String PREFIX = "order";
                    public static final String QUEUE = PREFIX + "s";
                    public static final int PORT = 8080;
                    private final Object lock = new Object();
                }
                """)).containsEntry("QUEUE", "orders").containsEntry("PORT", 8080).doesNotContainKey("lock");
    }

    @Test
    void formattedUris() {
        JavaParseResult result = new LwJavaParser().parse("""
                public class R extends RouteBuilder {
                    private static final String QUEUE = "orders";
                    public void configure() {
                        fromF("seda:%s?size=%d", QUEUE, 10).routeId("a")
                            .setBody(simpleF("Hello %s", QUEUE))
                            .toF("spring-redis://%s:%d?redisTemplate=#redisTemplate", host, port);
                    }
                }
                """);
        RouteDefinition route = result.routes().getRoutes().get(0);
        assertThat(route.getInput().getUri()).isEqualTo("seda:orders?size=10");
        assertThat(RoundTripTest.dump(route)).contains("simple(\"Hello orders\")");
        assertThat(((ToDefinition) route.getOutputs().get(1)).getUri())
                .isEqualTo("spring-redis://?{host}:?{port}?redisTemplate=#redisTemplate");
        assertThat(result.unresolved()).extracting(JavaParseResult.Unresolved::text).containsExactly("host", "port");
    }

    @Test
    void lambdaRouteBuilders() {
        JavaParseResult result = new LwJavaParser().parse("""
                @Configuration
                public class Routes {
                    @Bean
                    public LambdaRouteBuilder first() {
                        return rb -> rb.from("timer:tick").routeId("first").to("log:tick");
                    }

                    @Bean
                    public LambdaRouteBuilder second() {
                        return builder -> {
                            builder.onException(Exception.class).handled(true);
                            builder.from("direct:b").routeId("second")
                                .process(e -> e.getIn().setBody("x"))
                                .to("mock:b");
                        };
                    }
                }
                """);
        assertThat(result.routes().getRoutes()).extracting(RouteDefinition::getRouteId).containsExactly("first", "second");
        assertThat(result.routes().getOnExceptions()).hasSize(1);
        // the processor lambda is not a builder: it is a step, reported as a lambda
        assertThat(result.unresolved()).singleElement().satisfies(u -> assertThat(u.reason()).contains("lambda"));
    }

    @Test
    void nestedClassesAndEndpointsOfTheEndpointDsl() {
        JavaParseResult result = new LwJavaParser().parse("""
                package com.acme;

                import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

                public class R extends EndpointRouteBuilder {
                    public void configure() {
                        from(direct("a")).routeId("a")
                            .doTry()
                                .to(direct("b"))
                            .doCatch(Errors.Invalid.class)
                                .to(mock("error"))
                            .end()
                            .routingSlip(endpoints(mock("m2"), direct("c")));
                    }
                }
                """);
        assertThat(result.isComplete()).as("%s", result.unresolved()).isTrue();
        String java = RoundTripTest.dump(result.routes().getRoutes().get(0));
        assertThat(java).contains("routingSlip(constant(\"mock://m2,direct://c\"))", "com.acme.Errors$Invalid");
    }

    @Test
    void classNamesFormatsAndArithmetic() {
        JavaParseResult result = new LwJavaParser().parse("""
                package com.acme;

                public class R extends RouteBuilder {
                    private static final int PORT = 8080;
                    private static final long DELAY = 5 * 1000L;

                    public void configure() {
                        from("jpa://" + SendEmail.class.getName() + "?delay=" + DELAY / 2).routeId("a")
                            .to(String.format("netty:tcp://localhost:%d?textline=%s", PORT + 1, true))
                            .to("log:" + 1 + 2)
                            .to("seda:" + (1 + 2))
                            .to(String.format("mina:tcp://localhost:%1$s", getPort()));
                    }
                }
                """);
        RouteDefinition route = result.routes().getRoutes().get(0);
        assertThat(route.getInput().getUri()).isEqualTo("jpa://com.acme.SendEmail?delay=2500");
        assertThat(route.getOutputs()).extracting(o -> ((ToDefinition) o).getUri()).containsExactly(
                "netty:tcp://localhost:8081?textline=true",
                "log:12",
                "seda:3",
                "mina:tcp://localhost:?{getPort()}");
        assertThat(result.unresolved()).extracting(JavaParseResult.Unresolved::text).containsExactly("getPort()");
    }

    /**
     * A placeholder for new CafeAggregationStrategy() goes where an AggregationStrategy is taken, not an Expression.
     */
    @Test
    void placeholdersGoWhereTheirNameSays() {
        JavaParseResult result = new LwJavaParser().parse("""
                from("direct:a").routeId("a")
                    .aggregate(new CafeAggregationStrategy()).method("waiter", "checkOrder")
                    .completionTimeout(1000)
                    .to("mock:out");
                """);
        assertThat(result.unresolved()).singleElement()
                .satisfies(u -> assertThat(u.text()).isEqualTo("new CafeAggregationStrategy()"));
        assertThat(RoundTripTest.dump(result.routes().getRoutes().get(0))).contains("method(\"waiter\", \"checkOrder\")");
    }

    @Test
    void constantsInheritedFromABaseClass() {
        ConstantResolver resolver = (className, field) -> "com.acme.BaseCassandra".equals(className)
                && "KEYSPACE_NAME".equals(field) ? "camel_ks" : null;
        JavaParseResult result = new LwJavaParser().setConstantResolver(resolver).parse("""
                package com.acme;

                public class CassandraIT extends BaseCassandra {
                    protected RouteBuilder createRouteBuilder() {
                        return new RouteBuilder() {
                            public void configure() {
                                from("direct:in").to("cql://localhost/" + KEYSPACE_NAME);
                            }
                        };
                    }
                }
                """);
        assertThat(result.isComplete()).as("%s", result.unresolved()).isTrue();
        assertThat(((ToDefinition) result.routes().getRoutes().get(0).getOutputs().get(0)).getUri())
                .isEqualTo("cql://localhost/camel_ks");
    }
}
