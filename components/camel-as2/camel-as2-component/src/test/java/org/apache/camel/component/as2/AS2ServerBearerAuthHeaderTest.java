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
package org.apache.camel.component.as2;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.as2.api.AS2ClientConnection;
import org.apache.camel.component.as2.api.AS2ClientManager;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.common.HttpMessage;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test that the server adds a Bearer Auth authorization header when sending an async MDN.
 */
public class AS2ServerBearerAuthHeaderTest extends AbstractAS2ITSupport {

    private static final String MDN_ACCESS_TOKEN = "MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3";
    private static final int JETTY_PORT = AvailablePortFinder.getNextAvailable();
    private static final String EDI_MESSAGE = "UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'\n"
                                              + "UNH+00000000000117+INVOIC:D:97B:UN'\n"
                                              + "BGM+380+342459+9'\n"
                                              + "DTM+3:20060515:102'\n"
                                              + "RFF+ON:521052'\n"
                                              + "NAD+BY+792820524::16++CUMMINS MID-RANGE ENGINE PLANT'\n"
                                              + "NAD+SE+005435656::16++GENERAL WIDGET COMPANY'\n"
                                              + "CUX+1:USD'\n"
                                              + "LIN+1++157870:IN'\n"
                                              + "IMD+F++:::WIDGET'\n"
                                              + "QTY+47:1020:EA'\n"
                                              + "ALI+US'\n"
                                              + "MOA+203:1202.58'\n"
                                              + "PRI+INV:1.179'\n"
                                              + "LIN+2++157871:IN'\n"
                                              + "IMD+F++:::DIFFERENT WIDGET'\n"
                                              + "QTY+47:20:EA'\n"
                                              + "ALI+JP'\n"
                                              + "MOA+203:410'\n"
                                              + "PRI+INV:20.5'\n"
                                              + "UNS+S'\n"
                                              + "MOA+39:2137.58'\n"
                                              + "ALC+C+ABG'\n"
                                              + "MOA+8:525'\n"
                                              + "UNT+23+00000000000117'\n"
                                              + "UNZ+1+00000000000778'\n";

    // verify that the jetty server receives a Bearer Auth header
    @Test
    public void serverRouteWithBearerAuthConfig() throws Exception {
        clientSend("/bearer", "http://localhost:" + JETTY_PORT + "/receiptsWithBearerAuth");

        MockEndpoint mockEndpoint = getMockEndpoint("mock:as2RcvRcptMsgs2");
        mockEndpoint.expectedMinimumMessageCount(1);
        mockEndpoint.setResultWaitTime(TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS));
        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {

                Processor tokenHeaderProc = exchange -> {
                    HttpMessage message = exchange.getIn(HttpMessage.class);
                    assertNotNull(message.getHeader("Authorization"));
                    assertEquals("Bearer " + MDN_ACCESS_TOKEN, message.getHeader("Authorization"));
                };

                from(String.format("as2://server/listen?requestUriPattern=/bearer&mdnAccessToken=%s",
                        MDN_ACCESS_TOKEN))
                        .to("mock:as2RcvMsgs");

                from("jetty:http://localhost:" + JETTY_PORT + "/receiptsWithBearerAuth")
                        .process(tokenHeaderProc)
                        .to("mock:as2RcvRcptMsgs2");
            }
        };
    }

    private void clientSend(String requestUri, String receiptDeliveryOption) throws Exception {
        AS2ClientConnection clientConnection = getAs2ClientConnection();
        new AS2ClientManager(clientConnection).send(EDI_MESSAGE, requestUri, "Test Case", "mrAS@example.org", "878051556",
                "878051556", AS2MessageStructure.PLAIN,
                AS2MediaType.APPLICATION_EDIFACT, null, null, null, null, null,
                null, "mrAS@example.org", "sha1,md5", null, null, null, receiptDeliveryOption,
                null, null, null);
    }

    private AS2ClientConnection getAs2ClientConnection() throws IOException {
        return new AS2ClientConnection(
                "1.1", "Camel AS2 Endpoint", "example.org", "localhost", 8888, Duration.ofSeconds(5),
                Duration.ofSeconds(5), 5, Duration.ofMinutes(15), null, null);
    }
}
