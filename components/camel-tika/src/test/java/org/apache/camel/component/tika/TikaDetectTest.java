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
package org.apache.camel.component.tika;

import java.io.File;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;

public class TikaDetectTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Test
    public void testDocumentDetect() throws Exception {
        File document = new File("src/test/resources/test.doc");
        template.sendBody("direct:start", document);

        resultEndpoint.setExpectedMessageCount(1);

        resultEndpoint.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                Object body = exchange.getIn().getBody(String.class);
                assertThat(body, instanceOf(String.class));
                assertThat((String) body, containsString("application/x-tika-msoffice"));
                return true;
            }
        });
        resultEndpoint.assertIsSatisfied();
    }

    @Test
    public void testImageDetect() throws Exception {
        File document = new File("src/test/resources/testGIF.gif");
        template.sendBody("direct:start", document);

        resultEndpoint.setExpectedMessageCount(1);

        resultEndpoint.expectedMessagesMatches(new Predicate() {
            @Override
            public boolean matches(Exchange exchange) {
                Object body = exchange.getIn().getBody(String.class);
                assertThat(body, instanceOf(String.class));
                assertThat((String) body, containsString("image/gif"));
                return true;
            }
        });
        resultEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("tika:detect").to("mock:result");
            }
        };
    }
}
