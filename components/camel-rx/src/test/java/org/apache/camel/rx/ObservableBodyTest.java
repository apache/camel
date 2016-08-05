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

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import rx.Observable;

public class ObservableBodyTest extends CamelTestSupport {
    protected MyObservableBody observableBody = new MyObservableBody();

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce(uri = "direct:start")
    protected ProducerTemplate template;

    @Test
    public void testUseObservableInRoute() throws Exception {
        resultEndpoint.expectedBodiesReceived("Hello James", "Hello Claus");

        template.sendBody("James");
        template.sendBody("Claus");

        assertMockEndpointsSatisfied();
    }

    public class MyObservableBody extends ObservableBody<String> {
        public MyObservableBody() {
            super(String.class);
        }

        @Override
        protected void configure(Observable<String> observable) {
            // lets process the messages using the RX API
            observable.map(body -> "Hello " + body).subscribe(body -> {
                template.sendBody(resultEndpoint, body);
            });
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").process(observableBody);
            }
        };
    }
}
