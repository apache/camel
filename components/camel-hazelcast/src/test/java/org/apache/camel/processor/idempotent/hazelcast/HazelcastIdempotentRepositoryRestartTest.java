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
package org.apache.camel.processor.idempotent.hazelcast;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A repository without a Hazelcast instance of the user creates a local instance when it starts and shuts it down when
 * it stops. It must work again when it is started again: the idempotent consumer stops it when its route is removed,
 * and starts it when the route is added again (such as by a reload).
 */
class HazelcastIdempotentRepositoryRestartTest extends CamelTestSupport {

    private final HazelcastIdempotentRepository repo = new HazelcastIdempotentRepository();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @AfterEach
    void stopRepository() {
        repo.stop();
    }

    @Test
    void localInstanceRepositoryStartedAgain() {
        repo.start();
        assertTrue(repo.add("first"));
        repo.stop();

        repo.start();
        assertTrue(repo.add("second"));
        assertTrue(repo.contains("second"));
        assertFalse(repo.add("second"));
    }

    @Test
    void routeAddedAgainWithTheSameRepository() throws Exception {
        context.addRoutes(route());
        context.start();
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("one");
        template.sendBodyAndHeader("direct:start", "one", "id", "1");
        template.sendBodyAndHeader("direct:start", "one again", "id", "1");
        mock.assertIsSatisfied();

        // a reload: the route is removed (which stops the repository) and added again
        context.getRouteController().stopRoute("idempotent");
        context.removeRoute("idempotent");
        context.addRoutes(route());

        MockEndpoint again = getMockEndpoint("mock:result");
        again.reset();
        again.expectedBodiesReceived("two");
        template.sendBodyAndHeader("direct:start", "two", "id", "2");
        again.assertIsSatisfied();
    }

    private RouteBuilder route() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").routeId("idempotent")
                        .idempotentConsumer(header("id"), repo)
                        .to("mock:result");
            }
        };
    }
}
