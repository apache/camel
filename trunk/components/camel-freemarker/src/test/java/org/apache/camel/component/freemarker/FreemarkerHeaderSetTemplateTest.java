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
package org.apache.camel.component.freemarker;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Freemarker unit test
 */
public class FreemarkerHeaderSetTemplateTest extends CamelTestSupport {

    @Test
    public void testReceivesFooResponse() throws Exception {
        assertRespondsWith("foo", "<hello>foo</hello>");
    }

    @Test
    public void testReceivesBarResponse() throws Exception {
        assertRespondsWith("bar", "<hello>bar</hello>");
    }

    protected void assertRespondsWith(final String value, String expectedBody) throws Exception {
        Exchange response = template.request("direct:a", new Processor() {
            public void process(Exchange exchange) throws Exception {
                Message in = exchange.getIn();
                in.setBody("answer");
                in.setHeader("cheese", value);
            }
        });
        assertOutMessageBodyEquals(response, expectedBody);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: example
                from("direct:a").
                        setHeader(FreemarkerConstants.FREEMARKER_RESOURCE_URI).constant("org/apache/camel/component/freemarker/example.ftl").
                        to("freemarker:dummy");
                // END SNIPPET: example
            }
        };
    }
}
