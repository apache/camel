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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version
 */
public class VertxRequestReplyTest extends VertxBaseTestSupport {

    protected String startUri = "direct:start";
    protected String middleUri = "vertx:foo.middle";
    protected String resultUri = "mock:result";

    protected MockEndpoint resultEndpoint;
    protected String body1 = "Camel";
    protected String body2 = "World";

    @Test
    public void testVertxMessages() throws Exception {
        resultEndpoint = context.getEndpoint(resultUri, MockEndpoint.class);
        resultEndpoint.expectedBodiesReceivedInAnyOrder("Bye Camel", "Bye World");

        String out = template.requestBody(startUri, body1, String.class);
        String out2 = template.requestBody(startUri, body2, String.class);

        resultEndpoint.assertIsSatisfied();

        assertEquals("Bye Camel", out);
        assertEquals("Bye World", out2);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(startUri).to(middleUri).to(resultUri);

                from(middleUri)
                    .transform(simple("Bye ${body}"));
            }
        };
    }
}