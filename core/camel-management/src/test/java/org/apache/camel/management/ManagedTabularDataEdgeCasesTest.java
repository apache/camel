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
package org.apache.camel.management;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.TabularData;

import org.apache.camel.CamelContext;
import org.apache.camel.ManagementStatisticsLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ChoiceDefinition;
import org.apache.camel.processor.aggregate.UseLatestAggregationStrategy;
import org.apache.camel.support.task.BackgroundTask;
import org.apache.camel.support.task.Tasks;
import org.apache.camel.support.task.budget.Budgets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.apache.camel.management.DefaultManagementObjectNameStrategy.TYPE_PROCESSOR;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedTabularDataEdgeCasesTest extends ManagementTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getManagementStrategy().getManagementAgent().setStatisticsLevel(ManagementStatisticsLevel.Extended);
        return context;
    }

    private ObjectName service(String name) throws Exception {
        return getMBeanServer().queryNames(new ObjectName("*:type=services,*"), null).stream()
                .filter(n -> n.getCanonicalName().contains(name)).findFirst().orElseThrow();
    }

    private static CompositeData row(TabularData data, String item, Object value) {
        return data.values().stream().map(CompositeData.class::cast)
                .filter(cd -> value.equals(cd.get(item))).findFirst().orElseThrow();
    }

    @Test
    public void testChoiceWithSamePredicateTwice() throws Exception {
        template.sendBodyAndHeader("direct:choice", "Hello", "type", "b");

        TabularData data = (TabularData) getMBeanServer().invoke(getCamelObjectName(TYPE_PROCESSOR, "myChoice"),
                "extendedInformation", null, null);
        // two whens with the same predicate (such as with different outputs for the same condition) and otherwise
        assertEquals(3, data.size());
    }

    @Test
    public void testChoiceWithDisabledWhen() throws Exception {
        template.sendBodyAndHeader("direct:disabled", "Hello", "b", "true");

        TabularData data = (TabularData) getMBeanServer().invoke(getCamelObjectName(TYPE_PROCESSOR, "disabledChoice"),
                "extendedInformation", null, null);
        CompositeData b = row(data, "predicate", "${header.b}");
        assertEquals(1L, b.get("matches"));
    }

    @Test
    public void testDoTryWithSameExceptionTwice() throws Exception {
        // caught by the first doCatch (onWhen), and by the second
        template.sendBodyAndHeader("direct:try", "Hello", "a", "x");
        template.sendBodyAndHeader("direct:try", "Hello", "a", "y");

        TabularData data = (TabularData) getMBeanServer().invoke(getCamelObjectName(TYPE_PROCESSOR, "myTry"),
                "extendedInformation", null, null);
        assertEquals(2, data.size());
    }

    @Test
    public void testEndpointUtilizationWithSecrets() throws Exception {
        template.sendBodyAndHeader("direct:dynamic", "Hello", "pw", "a");
        template.sendBodyAndHeader("direct:dynamic", "Hello", "pw", "b");

        TabularData data = (TabularData) getMBeanServer().invoke(getCamelObjectName(TYPE_PROCESSOR, "myToD"),
                "extendedInformation", null, null);
        // the two endpoints only differ in the password, which is masked
        assertEquals(1, data.size());
        assertEquals(2L, ((CompositeData) data.values().iterator().next()).get("hits"));
    }

    @Test
    public void testExchangeFactoryOfSameUri() throws Exception {
        TabularData data = (TabularData) getMBeanServer().invoke(service("DefaultExchangeFactoryManager"),
                "listStatistics", null, null);
        assertTrue(data.size() >= 2);
    }

    @Test
    public void testListEndpointsWithSecrets() throws Exception {
        context.getEndpoint("mock:secret?password=a");
        context.getEndpoint("mock:secret?password=b");

        TabularData data = (TabularData) getMBeanServer().invoke(service("DefaultEndpointRegistry"),
                "listEndpoints", null, null);
        CompositeData row = row(data, "url", "mock://result?password=xxxxxx");
        assertEquals(true, row.get("static"));
    }

    @Test
    public void testListTasksWithSameName() throws Exception {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(2);
        try {
            for (int i = 0; i < 2; i++) {
                BackgroundTask.BackgroundTaskBuilder builder = Tasks.backgroundTask();
                builder.withName("same");
                builder.withScheduledExecutor(executor)
                        .withBudget(Budgets.timeBudget().withInterval(Duration.ofMillis(100))
                                .withMaxDuration(Duration.ofSeconds(5)).build())
                        .build()
                        .schedule(context, () -> false);
            }
            MBeanServer mbeanServer = getMBeanServer();
            ObjectName on = service("DefaultTaskManagerRegistry");
            await().atMost(3, TimeUnit.SECONDS).untilAsserted(() -> {
                TabularData data = (TabularData) mbeanServer.invoke(on, "listTasks", null, null);
                assertEquals(2, data.size());
            });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void testAggregateForceCompletionOnStop() throws Exception {
        MBeanServer mbeanServer = getMBeanServer();
        ObjectName on = getCamelObjectName(TYPE_PROCESSOR, "myAggregate");
        assertEquals(Boolean.TRUE, mbeanServer.getAttribute(on, "ForceCompletionOnStop"));
        assertEquals(Boolean.FALSE, mbeanServer.getAttribute(on, "CompletionFromBatchConsumer"));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:choice")
                        .choice().id("myChoice")
                            .when(simple("${header.type} == 'a'")).to("mock:a")
                            .when(simple("${header.type} == 'a'")).to("mock:b")
                            .otherwise().to("mock:c")
                        .end();

                ChoiceDefinition choice = from("direct:disabled").choice().id("disabledChoice");
                choice.when(simple("${header.a}")).to("mock:a")
                        .when(simple("${header.b}")).to("mock:b")
                        .end();
                choice.getWhenClauses().get(0).setDisabled("true");

                from("direct:try")
                        .doTry().id("myTry")
                            .throwException(new IOException("Forced"))
                        .doCatch(IOException.class).onWhen(header("a").isEqualTo("x"))
                            .to("mock:io1")
                        .doCatch(IOException.class)
                            .to("mock:io2")
                        .end();

                from("direct:dynamic").toD("mock:secret?password=${header.pw}").id("myToD");

                from("seda:foo?multipleConsumers=true").routeId("foo1").to("mock:foo1");
                from("seda:foo?multipleConsumers=true").routeId("foo2").to("mock:foo2");

                from("direct:result").to("mock:result?password=secret");

                from("direct:aggregate")
                        .aggregate(header("id"), new UseLatestAggregationStrategy()).completionSize(5)
                        .forceCompletionOnStop().id("myAggregate")
                        .to("mock:aggregated");
            }
        };
    }
}
