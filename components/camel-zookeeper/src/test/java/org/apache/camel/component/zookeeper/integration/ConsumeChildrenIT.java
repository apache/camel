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
package org.apache.camel.component.zookeeper.integration;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.zookeeper.NaturalSortComparator;
import org.apache.camel.component.zookeeper.NaturalSortComparator.Order;
import org.apache.camel.component.zookeeper.ZooKeeperMessage;
import org.apache.camel.support.ExchangeHelper;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class ConsumeChildrenIT extends ZooKeeperITSupport {

    private static final String ZK_NODE = "/grimm-ConsumeChildrenIT";

    @Override
    protected RouteBuilder[] createRouteBuilders() {
        return new RouteBuilder[] { new RouteBuilder() {
            public void configure() {
                from("zookeeper://{{zookeeper.connection.string}}" + ZK_NODE + "?repeat=true&listChildren=true")
                        .sort(body(), new NaturalSortComparator(Order.Descending))
                        .to("mock:zookeeper-data");
            }
        } };
    }

    @Test
    public void shouldAwaitCreationAndGetDataNotification() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:zookeeper-data");
        mock.expectedMessageCount(5);

        client.createPersistent(ZK_NODE, "parent");
        awaitMockReceivedCount(mock, 1);
        client.create(ZK_NODE + "/hansel", "child");
        awaitMockReceivedCount(mock, 2);
        client.create(ZK_NODE + "/gretel", "child");
        awaitMockReceivedCount(mock, 3);
        client.delete(ZK_NODE + "/hansel");
        awaitMockReceivedCount(mock, 4);
        client.delete(ZK_NODE + "/gretel");

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);

        validateExchangesContainListings(mock, createChildListing(), createChildListing("hansel"),
                createChildListing("hansel", "gretel"), createChildListing("gretel"),
                createChildListing());
    }

    /**
     * Waits until the mock has received at least {@code count} messages before the next ZooKeeper mutation is fired.
     * <p>
     * This ensures the previous ChildrenChangedOperation copy has been taken off the queue and its
     * {@code installWatch()} called, which happens after the exchange is delivered to the mock. The tiny remaining gap
     * between the mock receipt and the actual watch registration is covered by the 200 ms pre-delay inside
     * {@link ZooKeeperITSupport.TestZookeeperClient#create} / {@code delete}. If that delay is ever removed, a short
     * post-delay or a deeper hook into the consumer would be needed here to avoid reintroducing flakiness.
     */
    private void awaitMockReceivedCount(MockEndpoint mock, int count) {
        Awaitility.await()
                .atMost(10, TimeUnit.SECONDS)
                .until(() -> mock.getReceivedCounter() >= count);
    }

    private void validateExchangesContainListings(MockEndpoint mock, List<?>... expected)
            throws CamelExchangeException {
        int index = 0;
        for (Exchange received : mock.getReceivedExchanges()) {
            Watcher.Event.EventType expectedEvent;
            if (index == 0) {
                expectedEvent = Watcher.Event.EventType.NodeCreated;
            } else {
                expectedEvent = Watcher.Event.EventType.NodeChildrenChanged;
            }
            List<?> actual = received.getIn().getMandatoryBody(List.class);
            assertEquals(expected[index++], actual);
            assertEquals(expectedEvent, ExchangeHelper.getMandatoryHeader(received, ZooKeeperMessage.ZOOKEEPER_EVENT_TYPE,
                    Watcher.Event.EventType.class));
            validateChildrenCountChangesEachTime(mock);
        }
    }

    protected void validateChildrenCountChangesEachTime(MockEndpoint mock) {
        int lastChildCount = -1;
        List<Exchange> received = mock.getReceivedExchanges();
        for (int x = 0; x < received.size(); x++) {
            Message zkm = mock.getReceivedExchanges().get(x).getIn();
            int childCount = ((Stat) zkm.getHeader(ZooKeeperMessage.ZOOKEEPER_STATISTICS)).getNumChildren();
            assertNotSame(lastChildCount, childCount, "Num of children did not change");
            lastChildCount = childCount;
        }
    }
}
