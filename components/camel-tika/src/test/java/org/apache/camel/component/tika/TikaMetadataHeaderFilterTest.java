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

import java.nio.charset.StandardCharsets;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The metadata names handed to {@code convertMetadataToHeaders} come out of the parsed document, so they are chosen by
 * whoever produced it. An HTML {@code <meta name="..">} is the most direct way to demonstrate that: the name attribute
 * reaches Tika's metadata verbatim, so a document can ask for any header name at all.
 */
class TikaMetadataHeaderFilterTest extends CamelTestSupport {

    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Test
    void documentMetadataCannotSetCamelInternalHeaders() throws Exception {
        String html = "<html><head>"
                      + "<meta name=\"CamelFileName\" content=\"../../pwned\"/>"
                      + "<meta name=\"camelfilename\" content=\"../../pwned\"/>"
                      + "<meta name=\"CAMELHttpUri\" content=\"http://other.example/x\"/>"
                      + "<meta name=\"org.apache.camel.internal\" content=\"nope\"/>"
                      + "<meta name=\"author\" content=\"kept\"/>"
                      + "<title>t</title></head><body>hi</body></html>";

        resultEndpoint.setExpectedMessageCount(1);
        template.sendBody("direct:start", html.getBytes(StandardCharsets.UTF_8));
        resultEndpoint.assertIsSatisfied();

        Exchange exchange = resultEndpoint.getExchanges().get(0);
        assertThat(exchange.getIn().getHeader(Exchange.FILE_NAME)).isNull();
        assertThat(exchange.getIn().getHeader("camelfilename")).isNull();
        assertThat(exchange.getIn().getHeader("CAMELHttpUri")).isNull();
        assertThat(exchange.getIn().getHeader("org.apache.camel.internal")).isNull();

        // metadata outside the internal namespace is still mapped, so the filter has not simply dropped everything
        assertThat(exchange.getIn().getHeader("dc:creator")).isEqualTo("kept");
        assertThat(exchange.getIn().getHeader("dc:title")).isEqualTo("t");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("tika:parse").to("mock:result");
            }
        };
    }
}
