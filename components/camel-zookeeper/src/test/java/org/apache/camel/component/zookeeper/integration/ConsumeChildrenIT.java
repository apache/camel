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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class ConsumeChildrenIT extends ZooKeeperITSupport {

    @Override
    protected RouteBuilder[] createRouteBuilders() {
        return new RouteBuilder[] { new RouteBuilder() {
            public void configure() {
                from("zookeeper://{{zookeeper.connection.string}}/grimm?repeat=true&listChildren=true")
                        .sort(body(), new NaturalSortComparator(Order.Descending))
                        .to("mock:zookeeper-data");
            }
        } };
    }

    @Test
    public void shouldAwaitCreationAndGetDataNotification() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:zookeeper-data");
        mock.expectedMessageCount(5);

        client.createPersistent("/grimm", "parent");
        client.create("/grimm/hansel", "child");
        client.create("/grimm/gretel", "child");
        client.delete("/grimm/hansel");
        client.delete("/grimm/gretel");

        MockEndpoint.assertIsSatisfied(context);

        validateExchangesContainListings(mock, createChildListing(), createChildListing("hansel"),
                createChildListing("hansel", "gretel"), createChildListing("gretel"),
                createChildListing());
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
