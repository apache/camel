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
package org.apache.camel.component.zookeeper;

import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.zookeeper.KeeperException.NodeExistsException;
import org.junit.Test;

public class ConsumeDataTest extends ZooKeeperTestSupport {

    @Override
    protected RouteBuilder[] createRouteBuilders() throws Exception {
        return new RouteBuilder[] {new RouteBuilder() {
            public void configure() throws Exception {
                from("zoo://localhost:39913/camel?repeat=true").to("mock:zookeeper-data");
            }
        }};
    }

    @Test
    public void shouldAwaitCreationAndGetDataNotification() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:zookeeper-data");
        mock.expectedMinimumMessageCount(10);

        createCamelNode();
        updateNode(10);

        mock.await(5, TimeUnit.SECONDS);
        mock.assertIsSatisfied();

        validateExchangesReceivedInOrderWithIncreasingVersion(mock);
    }

    @Test
    public void deletionOfAwaitedNodeCausesNoFailure() throws Exception {

        MockEndpoint mock = getMockEndpoint("mock:zookeeper-data");
        mock.expectedMessageCount(11);
        createCamelNode();

        delay(200);

        // by now we are back waiting for a change so delete the node
        client.delete("/camel");

        // recreate and update a number of times.
        createCamelNode();
        updateNode(10);

        mock.await(5, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    private void updateNode(int times) throws InterruptedException, Exception {
        for (int x = 1; x < times; x++) {
            delay(200);
            client.setData("/camel", testPayload + "_" + x, -1);
        }
    }

    private void createCamelNode() throws InterruptedException, Exception {
        try {
            delay(1000);
            client.create("/camel", testPayload + "_0");
        } catch (NodeExistsException e) {
        }
    }
}
