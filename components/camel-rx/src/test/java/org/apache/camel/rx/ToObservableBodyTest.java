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
package org.apache.camel.rx;

import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import rx.Observable;

public class ToObservableBodyTest extends RxTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(ToObservableBodyTest.class);

    @Test
    public void testConsume() throws Exception {
        final MockEndpoint mockEndpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        mockEndpoint.expectedBodiesReceived("b", "d");

        // lets consume, filter and map events
        Observable<Order> observable = reactiveCamel.toObservable("seda:orders", Order.class);
        Observable<String> largeOrderIds = observable.filter(order -> order.getAmount() > 100.0).map(order -> order.getId());

        // lets route the largeOrderIds to the mock endpoint for testing
        largeOrderIds.take(2).subscribe(body -> {
            LOG.info("Processing  " + body);
            producerTemplate.sendBody(mockEndpoint, body);
        });

        // now lets send some orders in
        Order[] orders = {new Order("a", 49.95), new Order("b", 125.50), new Order("c", 22.95),
            new Order("d", 259.95), new Order("e", 1.25)};
        for (Order order : orders) {
            producerTemplate.sendBody("seda:orders", order);
        }

        mockEndpoint.assertIsSatisfied();
    }
}
