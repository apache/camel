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
package org.apache.camel.component.smb;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SmbNoDownloadIT extends SmbServerTestSupport {

    @Override
    public void doPostSetup() throws Exception {
        prepareSmbServer();
    }

    protected String getSmbUrl() {
        return String.format(
                "smb:%s/%s?username=%s&password=%s&path=/nodownload&download=false",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Test
    public void testNoDownload() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:received_send");
        mock.expectedMessageCount(1);
        mock.message(0).body().isNull();
        mock.message(0).header(Exchange.FILE_NAME).isEqualTo("hello.txt");

        mock.assertIsSatisfied();
    }

    private void prepareSmbServer() {
        template.sendBodyAndHeader(getSmbUrl(), "Hello World", Exchange.FILE_NAME, "hello.txt");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getSmbUrl()).process(new Processor() {
                    public void process(Exchange exchange) {
                        assertNull(exchange.getIn().getBody(), "Should not download the file");
                        assertEquals("hello.txt", exchange.getIn().getHeader(Exchange.FILE_NAME));
                    }
                })
                        .to("mock:received_send");
            }
        };
    }
}
