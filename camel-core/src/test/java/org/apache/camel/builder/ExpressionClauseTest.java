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
package org.apache.camel.builder;

import java.util.Map;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;

import org.apache.camel.Attachment;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultAttachment;

/**
 * @version 
 */
public class ExpressionClauseTest extends ContextTestSupport {

    public void testConstant() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().constant("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    public void testAttachments() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.expectedBodiesReceivedInAnyOrder("log4j2.properties", "jndi-example.properties");

        template.send("direct:begin", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                Message m = exchange.getIn();
                m.setBody("Hello World");
                m.addAttachmentObject("log4j", new DefaultAttachment(new FileDataSource("src/test/resources/log4j2.properties")));
                m.addAttachment("jndi-example", new DataHandler(new FileDataSource("src/test/resources/jndi-example.properties")));
            }
        });

        assertMockEndpointsSatisfied();
        Map<String, Attachment> attachments = mock.getExchanges().get(0).getIn().getAttachmentObjects();
        assertTrue(attachments == null || attachments.size() == 0);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").to("mock:result");
                from("direct:begin")
                    .split().attachments()
                    // extract just the name from the DataHandler/DataSource to simplify assertions
                    .bean(new Extractor())
                    .to("mock:result");
            }
        };
    }
    
    public final class Extractor {
        public String extractName(DataHandler body) {
            DataSource ds = (body != null) ? body.getDataSource() : null;
            if (ds instanceof FileDataSource) {
                return ((FileDataSource)ds).getName();
            }
            return null;
        }
    }
}
