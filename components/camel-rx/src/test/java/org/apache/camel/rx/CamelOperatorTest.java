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

import java.util.concurrent.TimeUnit;

import org.apache.camel.Message;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;
import rx.Subscription;
import rx.observables.ConnectableObservable;

/**
 */
public class CamelOperatorTest extends RxTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(CamelOperatorTest.class);

    @Test
    public void testCamelOperator() throws Exception {
        final MockEndpoint mockEndpoint1 = camelContext.getEndpoint("mock:results1", MockEndpoint.class);
        final MockEndpoint mockEndpoint2 = camelContext.getEndpoint("mock:results2", MockEndpoint.class);
        final MockEndpoint mockEndpoint3 = camelContext.getEndpoint("mock:results3", MockEndpoint.class);
        mockEndpoint1.expectedMessageCount(2);
        mockEndpoint2.expectedMessageCount(1);
        mockEndpoint3.expectedMessageCount(1);

        ConnectableObservable<Message> route = reactiveCamel.toObservable("direct:start")
            .lift(new CamelOperator(mockEndpoint1))
            .lift(new CamelOperator(camelContext, "log:foo"))
            .debounce(1, TimeUnit.SECONDS)
            .lift(reactiveCamel.to(mockEndpoint2))
            .lift(reactiveCamel.to("mock:results3"))
            .publish();

        // Start the route
        Subscription routeSubscription = route.connect();

        // Send two test messages
        producerTemplate.sendBody("direct:start", "<test/>");
        producerTemplate.sendBody("direct:start", "<test/>");

        mockEndpoint1.assertIsSatisfied();
        mockEndpoint2.assertIsSatisfied();
        mockEndpoint3.assertIsSatisfied();

        // Stop the route
        routeSubscription.unsubscribe();
    }
}
