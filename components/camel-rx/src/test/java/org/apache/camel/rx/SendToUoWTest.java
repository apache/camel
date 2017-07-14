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

import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.SynchronizationAdapter;
import org.junit.Assert;
import org.junit.Test;
import rx.Observable;

public class SendToUoWTest extends RxTestSupport {

    private MyOnCompletion onCompletion = new MyOnCompletion();

    @Test
    public void testSendObservableToEndpoint() throws Exception {
        Order[] expectedBodies = {new Order("o1", 1.10), new Order("o2", 2.20), new Order("o3", 3.30)};
        Observable<Order> someObservable = Observable.from(expectedBodies);

        final MockEndpoint mockEndpoint = camelContext.getEndpoint("mock:results", MockEndpoint.class);
        mockEndpoint.expectedBodiesReceived((Object[]) expectedBodies);

        mockEndpoint.whenAnyExchangeReceived(exchange -> exchange.addOnCompletion(onCompletion));

        // lets send events on the observable to the camel endpoint
        reactiveCamel.sendTo(someObservable, "mock:results");

        mockEndpoint.assertIsSatisfied();

        Assert.assertEquals(3, onCompletion.getDone());
    }

    private static class MyOnCompletion extends SynchronizationAdapter {

        private int done;

        @Override
        public void onComplete(Exchange exchange) {
            done++;
        }

        public int getDone() {
            return done;
        }
    }
}
