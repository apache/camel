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
package org.apache.camel.component.dropbox.integration.producer;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dropbox.integration.DropboxTestSupport;
import org.apache.camel.component.dropbox.util.DropboxConstants;
import org.apache.camel.component.dropbox.util.DropboxResultHeader;
import org.apache.camel.component.dropbox.util.DropboxUploadMode;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class DropboxProducerPutWithRemotePathTest extends DropboxTestSupport {

    public DropboxProducerPutWithRemotePathTest() throws Exception { }

    @Test
    public void testCamelDropbox() throws Exception {
        template.send("direct:start", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader("test", "test");
            }
        });


        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);       
        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = mock.getReceivedExchanges();
        Exchange exchange = exchanges.get(0);
        Object header =  exchange.getIn().getHeader(DropboxResultHeader.UPLOADED_FILES.name());
        Object body = exchange.getIn().getBody();
        assertNotNull(header);
        assertNotNull(body);
    }

    @Test
    public void testCamelDropboxWithOptionInHeader() throws Exception {
        template.send("direct:start2", new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader("test", "test");
            }
        });

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        assertMockEndpointsSatisfied();

        List<Exchange> exchanges = mock.getReceivedExchanges();
        Exchange exchange = exchanges.get(0);
        Object header =  exchange.getIn().getHeader(DropboxResultHeader.UPLOADED_FILES.name());
        Object body = exchange.getIn().getBody();
        assertNotNull(header);
        assertNotNull(body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                        .to("dropbox://put?accessToken={{accessToken}}&uploadMode=add&localPath=/XXX&remotePath=/XXX")
                        .to("mock:result");

                from("direct:start2")
                    .setHeader(DropboxConstants.HEADER_LOCAL_PATH, constant("/XXX"))
                    .setHeader(DropboxConstants.HEADER_REMOTE_PATH, constant("/XXX"))
                    .setHeader(DropboxConstants.HEADER_UPLOAD_MODE, constant(DropboxUploadMode.add))
                    .to("dropbox://put?accessToken={{accessToken}}")
                    .to("mock:result");
            }
        };
    }
}
