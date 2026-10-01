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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.ToDefinition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The endpoint DSL read without it on the class path: by its naming rules, or by a resolver a tool plugs in
 * (CAMEL-25148).
 */
class EndpointDslTest {

    private static List<String> uris(JavaParseResult result) {
        RouteDefinition route = result.routes().getRoutes().get(0);
        return Stream.concat(
                Stream.of(route.getInput().getUri()),
                route.getOutputs().stream().filter(ToDefinition.class::isInstance).map(o -> ((ToDefinition) o).getUri()))
                .toList();
    }

    @Test
    void endpointRouteBuilder() {
        JavaParseResult result = new LwJavaParser().parse("""
                import org.apache.camel.builder.endpoint.EndpointRouteBuilder;

                public class Orders extends EndpointRouteBuilder {
                    public void configure() {
                        from(platformHttp("/orders").httpMethodRestrict("POST"))
                            .to(kafka("orders").brokers("{{brokers}}").advanced().synchronous(true))
                            .to(aws2S3("my-bucket").region("eu-west-1").autoCreateBucket(false))
                            .to(kafka("myKafka", "audit"));
                    }
                }
                """);
        assertThat(result.isComplete()).as("%s", result.unresolved()).isTrue();
        assertThat(uris(result)).containsExactly(
                "platform-http:///orders?httpMethodRestrict=POST",
                "kafka://orders?brokers={{brokers}}&synchronous=true",
                "aws2-s3://my-bucket?region=eu-west-1&autoCreateBucket=false",
                "myKafka://audit");
    }

    @Test
    void multiValueOptionsNeedTheCatalog() {
        JavaParseResult result = new LwJavaParser().parse("""
                import static org.apache.camel.builder.endpoint.StaticEndpointBuilders.*;
                from(timer("tick").period(1000).schedulerProperties("foo", "bar")).to("log:tick");
                """);
        assertThat(uris(result)).containsExactly("timer://tick?period=1000", "log:tick");
        assertThat(result.unresolved()).singleElement()
                .satisfies(u -> assertThat(u.reason()).contains("schedulerProperties", "catalog"));
    }

    @Test
    void notGuessedWithoutTheEndpointDsl() {
        JavaParseResult result = new LwJavaParser().parse("""
                from("direct:a").to(orderUri());
                """);
        assertThat(uris(result)).containsExactly("direct:a", LwJavaParser.UNRESOLVED_PREFIX + "orderUri()}");
    }

    @Test
    void aResolverKnowsItsComponents() {
        // as a tool with the catalog would: it knows kafka, and nothing called orderUri
        EndpointDslResolver catalogLike = (factory, args, options) -> factory.equals("kafka")
                ? EndpointDslResolver.NAMING.endpoint(factory, args, options) : null;
        JavaParseResult result = new LwJavaParser().setEndpointDslResolver(catalogLike).parse("""
                from(kafka("orders")).to(orderUri());
                """);
        assertThat(uris(result)).containsExactly("kafka://orders", LwJavaParser.UNRESOLVED_PREFIX + "orderUri()}");
    }

    /** Every factory of the generated endpoint DSL gives its scheme back by the naming rules. */
    @Test
    void everyFactoryFollowsTheRules() throws Exception {
        Path builders = Path.of(
                "../../dsl/camel-endpointdsl/src/generated/java/org/apache/camel/builder/endpoint/StaticEndpointBuilders.java");
        assumeTrue(Files.isRegularFile(builders), "camel-endpointdsl is in the source tree");
        Matcher m = Pattern
                .compile("public static [\\w.]+ (\\w+)\\(String path\\) \\{\\s*return [\\w.]+\\(\\s*\"([^\"]+)\",\\s*path\\)")
                .matcher(Files.readString(builders));
        int factories = 0;
        while (m.find()) {
            factories++;
            assertThat(NamingEndpointDslResolver.scheme(m.group(1))).as(m.group(1)).isEqualTo(m.group(2));
        }
        assertThat(factories).isGreaterThan(300);
    }

    /**
     * In the endpoint DSL, bean("x") is the bean component, not AggregationStrategies.bean (not imported statically).
     */
    @Test
    void theBuildersOwnMethodsComeFirst() {
        JavaParseResult result = new LwJavaParser().parse("""
                import org.apache.camel.builder.endpoint.LambdaEndpointRouteBuilder;

                public class App {
                    public LambdaEndpointRouteBuilder myRoute() {
                        return rb -> rb
                                .from(rb.timer("timer").period(2000))
                                .to(rb.bean("myBean").method("saySomething"))
                                .log("${body}");
                    }
                }
                """);
        assertThat(result.isComplete()).as("%s", result.unresolved()).isTrue();
        assertThat(uris(result)).containsExactly("timer://timer?period=2000", "bean://myBean?method=saySomething");
    }
}
