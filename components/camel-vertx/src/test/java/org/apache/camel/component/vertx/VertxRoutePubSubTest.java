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
package org.apache.camel.component.vertx;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version
 */
public class VertxRoutePubSubTest extends VertxBaseTestSupport {

    protected String startUri = "vertx:foo.start?pubSub=true";
    protected String middleUri = "vertx:foo.middle?pubSub=true";
    protected String resultUri = "mock:result";

    protected MockEndpoint resultEndpoint;
    protected String body1 = "{\"id\":1,\"description\":\"Message One\"}";
    protected String body2 = "{\"id\":2,\"description\":\"Message Two\"}";

    @Test
    public void testVertxMessages() throws Exception {
        resultEndpoint = context.getEndpoint(resultUri, MockEndpoint.class);
        resultEndpoint.expectedBodiesReceivedInAnyOrder(body1, body2);

        template.sendBody(startUri, body1);
        template.sendBody(startUri, body2);

        resultEndpoint.assertIsSatisfied();

        List<Exchange> list = resultEndpoint.getReceivedExchanges();
        for (Exchange exchange : list) {
            log.info("Received exchange: " + exchange + " headers: " + exchange.getIn().getHeaders());
        }
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(startUri).to(middleUri);
                from(middleUri).to(resultUri);
            }
        };
    }
}