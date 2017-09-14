/**
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
package org.apache.camel.component.zookeeper.policy;

import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.zookeeper.ZooKeeperTestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FailoverRoutePolicyTest extends ZooKeeperTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(FailoverRoutePolicyTest.class);

    protected CamelContext createCamelContext() throws Exception {
        disableJMX();
        // set up the parent nodes used to control the election
        client.createPersistent("/someapp", "App node to contain policy election nodes...");
        client.createPersistent("/someapp/somepolicy", "Node used by route policy to control active routes...");
        return super.createCamelContext();
    }

    @Test
    public void masterSlaveScenarioContolledByPolicy() throws Exception {
        ZookeeperPolicyEnforcedContext tetrisisMasterOfBlocks = createEnforcedContext("master");
        ZookeeperPolicyEnforcedContext slave = createEnforcedContext("slave");

        // http://bit.ly/9gTlGe ;). Send messages to the master and the slave.
        // The route is enabled in the master and gets through, but that sent to
        // the slave context is rejected.
        tetrisisMasterOfBlocks.sendMessageToEnforcedRoute("LIIIIIIIIIINNNNNNNNNEEEEEEE PEEEEEEICCCE", 1);
        slave.sendMessageToEnforcedRoute("But lord there is no place for a square!??!", 0);

        // trigger failover by killing the master... then assert that the slave
        // can now receive messages.
        tetrisisMasterOfBlocks.shutdown();
        slave.sendMessageToEnforcedRoute("What a cruel and angry god...", 1);
    }

    private static class ZookeeperPolicyEnforcedContext {
        private CamelContext controlledContext;
        private ProducerTemplate template;
        private MockEndpoint mock;
        private String routename;

        ZookeeperPolicyEnforcedContext(String name) throws Exception {
            controlledContext = new DefaultCamelContext();
            routename = name;
            template = controlledContext.createProducerTemplate();
            mock = controlledContext.getEndpoint("mock:controlled", MockEndpoint.class);
            controlledContext.addRoutes(new FailoverRoute(name));
            controlledContext.start();
        }

        public void sendMessageToEnforcedRoute(String message, int expected) throws InterruptedException {
            mock.expectedMessageCount(expected);
            try {
                template.sendBody("vm:" + routename, ExchangePattern.InOut, message);
            } catch (Exception e) {
                if (expected > 0) {
                    LOG.error(e.getMessage(), e);
                    fail("Expected messages...");
                }
            }
            mock.await(2, TimeUnit.SECONDS);
            mock.assertIsSatisfied(1000);
        }

        public void shutdown() throws Exception {
            LoggerFactory.getLogger(getClass()).debug("stopping");
            controlledContext.stop();
            LoggerFactory.getLogger(getClass()).debug("stopped");
        }
    }

    private ZookeeperPolicyEnforcedContext createEnforcedContext(String name) throws Exception, InterruptedException {
        ZookeeperPolicyEnforcedContext context = new ZookeeperPolicyEnforcedContext(name);
        delay(1000);
        return context;
    }

    public static class FailoverRoute extends RouteBuilder {

        private String routename;

        public FailoverRoute(String routename) {
            // need names as if we use the same direct ep name in two contexts
            // in the same vm shutting down one context shuts the endpoint for
            // both.
            this.routename = routename;
        }

        public void configure() throws Exception {
            ZooKeeperRoutePolicy policy = new ZooKeeperRoutePolicy("zookeeper:localhost:" + getServerPort() + "/someapp/somepolicy", 1);
            from("vm:" + routename).routePolicy(policy).id(routename).to("mock:controlled");
        }
    };
}
