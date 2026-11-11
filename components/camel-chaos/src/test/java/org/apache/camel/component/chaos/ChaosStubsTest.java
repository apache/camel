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
package org.apache.camel.component.chaos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.stub.StubComponent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Chaos sees through a stub that stands in for another component, as {@code camel run --stub=remote} serves
 * {@code kafka:} with the stub component (CAMEL-26765): the suggestions take it as remote when the component is, and a
 * consumer keeps a failed message by the options of its URI, which the report marks as kept by a stub.
 */
class ChaosStubsTest extends ChaosTestSupport {

    private static final String REFUNDS = "kafka:refunds?brokers=localhost:9092&breakOnFirstError=true";

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        // remotemq is remote by its JSON (in the test resources); kafka has no JSON here, as its JAR is not there
        context.addComponent("remotemq", shadow());
        context.addComponent("kafka", shadow());
        return context;
    }

    private static StubComponent shadow() {
        StubComponent stub = new StubComponent();
        stub.setShadow(true);
        return stub;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("remotemq:in").routeId("mq").to("remotemq:out");
                from(REFUNDS).routeId("refunds").to("mock:refunds");
                from("kafka:payments?brokers=localhost:9092").routeId("payments").to("mock:payments");
                from("direct:plain").routeId("plain").to("stub:audit");
            }
        };
    }

    private static Map<String, String> rules(List<ChaosAdvisor.Suggestion> suggestions) {
        Map<String, String> answer = new LinkedHashMap<>();
        suggestions.forEach(s -> answer.put(s.route() + " " + s.recipe() + " " + s.kind().key, s.rule()));
        return answer;
    }

    @Test
    void aStubOfARemoteComponentIsRemote() {
        assertThat(ChaosStubs.stubbedScheme(context.getEndpoint("remotemq:in"))).isEqualTo("remotemq");
        assertThat(ChaosStubs.isRemote(context.getEndpoint("remotemq:in"))).isTrue();
        // a component whose JSON is not on the classpath is not taken as remote
        assertThat(ChaosStubs.isRemote(context.getEndpoint("kafka:payments?brokers=localhost:9092"))).isFalse();
        // a plain stub: endpoint stands in for nothing
        assertThat(ChaosStubs.stubbedScheme(context.getEndpoint("stub:audit"))).isNull();
        assertThat(ChaosStubs.isRemote(context.getEndpoint("stub:audit"))).isFalse();
        assertThat(ChaosStubs.stubbedScheme(context.getEndpoint("mock:refunds"))).isNull();
    }

    @Test
    void theSuggestionsSeeTheStubbedRemoteEndpoints() {
        Map<String, String> rules = rules(ChaosAdvisor.suggest(context));
        assertThat(rules)
                .containsEntry("mq route-error consumer", "route=mq error rate=0.05 until=10m expect=mq:failed<=0")
                .containsEntry("mq connection-refused errors",
                        "endpoint=remotemq://out error exception=java.net.ConnectException rate=0.25 until=10m"
                                                               + " expect=mq:failed<=0");
        // the stub of audit stands in for nothing, so there is nothing remote to try in route plain
        assertThat(rules.keySet()).noneMatch(k -> k.startsWith("plain "));
    }

    @Test
    void aStubOfKafkaKeepsByTheOptionsOfItsUri() throws Exception {
        assertThat(ChaosConsumers.keepsFailed(context.getEndpoint(REFUNDS))).isTrue();
        assertThat(ChaosConsumers.keepsFailed(context.getEndpoint("kafka:payments?brokers=localhost:9092"))).isFalse();

        chaos().addRule("route=refunds error until=2");
        chaos().addRule("route=payments error until=1");
        template.sendBody(REFUNDS, "R-1");
        template.sendBody(REFUNDS, "R-2");
        template.sendBody("kafka:payments?brokers=localhost:9092", "P-1");
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> routeCount("refunds", "failed") == 2 && routeCount("payments", "failed") == 1);

        // kept by kafka (breakOnFirstError=true), but the stub does not read them again: the report says so
        assertThat(routeReport("refunds").getLong("failedKeptByConsumer")).isEqualTo(2);
        assertThat(routeReport("refunds").getString("stubbedConsumer")).isEqualTo("kafka");
        assertThat(routeReport("payments").getLong("failedKeptByConsumer")).isZero();
        assertThat(routeReport("payments").getString("stubbedConsumer")).isEqualTo("kafka");
        assertThat(chaos().report().toText())
                .contains("refunds: completed 0, failed 2 (kept by the consumer 2, a stub of kafka that does not read"
                          + " them again)");

        // a route whose consumer is not a stub has no mark
        template.sendBody("direct:plain", "A-1");
        await().atMost(10, TimeUnit.SECONDS).until(() -> routeCount("plain", "completed") == 1);
        assertThat(routeReport("plain").getString("stubbedConsumer")).isNull();
    }
}
