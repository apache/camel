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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.zookeeper.ZooKeeperTestSupport;
import org.junit.Test;

public class ZookeeperRoutePolicyTest extends ZooKeeperTestSupport {

    @Test
    public void routeCanBeControlledByPolicy() throws Exception {
        // set up the parent used to control the election
        client.createPersistent("/someapp", "App node to contain policy election nodes...");
        client.createPersistent("/someapp/somepolicy", "Policy node used by route policy to control routes...");
        context.addRoutes(new ZooKeeperPolicyEnforcedRoute());

        MockEndpoint mock = getMockEndpoint("mock:controlled");
        mock.setExpectedMessageCount(1);
        sendBody("direct:policy-controlled", "This is a test");
        mock.await(5, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    public static class ZooKeeperPolicyEnforcedRoute extends RouteBuilder {
        public void configure() throws Exception {
            ZooKeeperRoutePolicy policy = new ZooKeeperRoutePolicy("zookeeper:localhost:" + getServerPort() + "/someapp/somepolicy", 1);
            from("direct:policy-controlled").routePolicy(policy).to("mock:controlled");
        }
    };
}
