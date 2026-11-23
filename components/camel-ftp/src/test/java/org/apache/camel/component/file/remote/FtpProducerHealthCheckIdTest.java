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
package org.apache.camel.component.file.remote;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.integration.FtpServerTestSupport;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.support.service.ServiceHelper;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two producers to the same server, as two routes that upload into different directories, each have a health check of
 * their own: stopping one producer removes its own check only, and the checks never connect a stopped producer.
 */
class FtpProducerHealthCheckIdTest extends FtpServerTestSupport {

    private RemoteFileEndpoint<?> endpointA;
    private RemoteFileEndpoint<?> endpointB;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        HealthCheckHelper.getHealthCheckRepository(context, "producers").setEnabled(true);
        // the result of every check, not only the first one when all are UP
        HealthCheckRegistry.get(context).setExposureLevel("full");
        return context;
    }

    @Test
    void eachProducerHasItsOwnCheck() {
        template.sendBody("direct:a", "to a");
        template.sendBody("direct:b", "to b");

        List<HealthCheck.Result> results = producerChecks();
        List<String> ids = ids(results);
        assertEquals(2, results.size(), "the checks called: " + ids);
        assertTrue(ids.contains("producer:ftp-localhost-" + endpointA.getId()), ids.toString());
        assertTrue(ids.contains("producer:ftp-localhost-" + endpointB.getId()), ids.toString());
        // the id has no credentials
        assertTrue(ids.stream().noneMatch(id -> id.contains("admin")), ids.toString());
        assertTrue(results.stream().allMatch(r -> r.getState() == HealthCheck.State.UP));
    }

    @Test
    void stoppingOneProducerKeepsTheCheckOfTheOther() throws Exception {
        // the producer of route b starts first, so its check is the first one in the repository
        template.sendBody("direct:b", "to b");
        template.sendBody("direct:a", "to a");
        await().atMost(10, TimeUnit.SECONDS).until(() -> service.countConnections() == 2);

        context.getRouteController().stopRoute("a");
        await().atMost(10, TimeUnit.SECONDS).until(() -> service.countConnections() == 1);

        List<HealthCheck.Result> results = producerChecks();
        assertEquals(1, service.countConnections(), "a check connected the stopped producer of route a");
        assertEquals(1, results.size(), "the checks called: " + ids(results));
        assertEquals("producer:ftp-localhost-" + endpointB.getId(), results.get(0).getCheck().getId(),
                "the check of the running producer of route b");
        assertEquals(HealthCheck.State.UP, results.get(0).getState());
    }

    @Test
    void producersOfOneEndpointHaveTheirOwnCheck() throws Exception {
        // the producer is not a singleton: an endpoint can have more than one at a time, such as in a pool
        RemoteFileEndpoint<?> endpoint
                = context.getEndpoint("ftp://admin@localhost:{{ftp.server.port}}/c?password=admin", RemoteFileEndpoint.class);
        String id = "producer:ftp-localhost-" + endpoint.getId();
        Producer first = endpoint.createProducer();
        Producer second = endpoint.createProducer();
        Producer third = endpoint.createProducer();
        try {
            first.start();
            second.start();
            assertEquals(List.of(id, id + "-2"), checkIds(id));

            first.stop();
            assertEquals(List.of(id + "-2"), checkIds(id));

            // the number of the stopped producer is used again
            third.start();
            assertEquals(List.of(id, id + "-2"), checkIds(id));
        } finally {
            ServiceHelper.stopService(first, second, third);
        }
    }

    private List<String> checkIds(String idOfEndpoint) {
        return HealthCheckHelper.getHealthCheckRepository(context, "producers").stream()
                .map(HealthCheck::getId)
                .filter(id -> id.equals(idOfEndpoint) || id.startsWith(idOfEndpoint + "-"))
                .sorted()
                .toList();
    }

    private List<HealthCheck.Result> producerChecks() {
        return HealthCheckHelper.invokeReadiness(context).stream()
                .filter(r -> r.getCheck().getId().startsWith("producer:ftp-"))
                .toList();
    }

    private static List<String> ids(List<HealthCheck.Result> results) {
        return results.stream().map(r -> r.getCheck().getId()).toList();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                endpointA = endpoint("ftp://admin@localhost:{{ftp.server.port}}/a?password=admin", RemoteFileEndpoint.class);
                endpointB = endpoint("ftp://admin@localhost:{{ftp.server.port}}/b?password=admin", RemoteFileEndpoint.class);
                from("direct:a").routeId("a").to(endpointA);
                from("direct:b").routeId("b").to(endpointB);
            }
        };
    }
}
