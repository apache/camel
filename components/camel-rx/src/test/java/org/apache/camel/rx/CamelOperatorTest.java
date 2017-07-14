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

import org.apache.camel.Exchange;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import rx.Subscription;
import rx.observables.ConnectableObservable;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;


public class CamelOperatorTest extends RxTestSupport {

    @Test
    public void testCamelOperator() throws Exception {
        final MockEndpoint mockEndpoint1 = camelContext.getEndpoint("mock:results1", MockEndpoint.class);
        final MockEndpoint mockEndpoint2 = camelContext.getEndpoint("mock:results2", MockEndpoint.class);
        final MockEndpoint mockEndpoint3 = camelContext.getEndpoint("mock:results3", MockEndpoint.class);
        final MockEndpoint mockEndpoint4 = camelContext.getEndpoint("mock:results4", MockEndpoint.class);
        mockEndpoint1.expectedMessageCount(2);
        mockEndpoint2.expectedMessageCount(1);
        mockEndpoint3.expectedMessageCount(1);
        mockEndpoint4.expectedMessageCount(2);

        // Define an InOnly route
        ConnectableObservable<Exchange> inOnly = reactiveCamel.from("direct:start")
            .lift(new CamelOperator(mockEndpoint1))
            .lift(new CamelOperator(camelContext, "log:inOnly"))
            .debounce(1, TimeUnit.SECONDS)
            .lift(reactiveCamel.to(mockEndpoint2))
            .lift(reactiveCamel.to("mock:results3"))
            .publish();

        // Start the route
        Subscription inSubscription = inOnly.connect();

        // Send two test messages
        producerTemplate.sendBody("direct:start", "<test1/>");
        producerTemplate.sendBody("direct:start", "<test2/>");

        // Define an InOut route
        ConnectableObservable<Exchange> inOut = reactiveCamel.from("restlet:http://localhost:9080/test?restletMethod=POST")
            .map(exchange -> {
                exchange.getIn().setBody(exchange.getIn().getBody(String.class));
                return exchange;
            })
            .lift(reactiveCamel.to("log:inOut"))
            .map(exchange -> {
                exchange.getIn().setBody(exchange.getIn().getBody(String.class) + " back");
                return exchange;
            })
            .lift(reactiveCamel.to(mockEndpoint4))
            .publish();

        // Start the route
        Subscription inoutSubscription = inOut.connect();

        // Send two messages and check the responses
        given().body("hello").when().post("http://localhost:9080/test").then().assertThat().body(containsString("hello back"));
        given().body("holla").when().post("http://localhost:9080/test").then().assertThat().body(containsString("holla back"));

        mockEndpoint1.assertIsSatisfied();
        mockEndpoint2.assertIsSatisfied();
        mockEndpoint3.assertIsSatisfied();
        mockEndpoint4.assertIsSatisfied();

        // Stop the route
        inSubscription.unsubscribe();

        // Stop the route
        inoutSubscription.unsubscribe();
    }
}
