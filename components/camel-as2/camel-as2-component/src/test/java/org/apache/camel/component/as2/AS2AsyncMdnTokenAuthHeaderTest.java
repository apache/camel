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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.AS2ServerConnection;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.http.common.HttpMessage;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verify that the authorization header is added to the Async AS2 MDN request sent by the server for Bearer Auth.
 */
public class AS2AsyncMdnTokenAuthHeaderTest extends AbstractAS2ITSupport {

    private static final String MDN_ACCESS_TOKEN = "MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3";
    private static final int RECEIPT_SERVER_PORT = AvailablePortFinder.getNextAvailable();
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

    private static AS2ServerConnection serverConnection;

    @BeforeAll
    public static void setupTest() throws Exception {
        receiveTestMessages();
    }

    @AfterAll
    public static void tearDownTest() {
        if (serverConnection != null) {
            serverConnection.close();
        }
    }

    @Test
    public void asyncMdnHasTokenAuthHeader() throws Exception {
        requestBodyAndHeaders("direct://SEND", EDI_MESSAGE,
                getAS2Headers("http://localhost:" + RECEIPT_SERVER_PORT + "/handle-receipts"));

        MockEndpoint mockEndpoint = getMockEndpoint("mock:as2RcvRcptMsgs");
        mockEndpoint.expectedMinimumMessageCount(1);
        mockEndpoint.setResultWaitTime(TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS));
        mockEndpoint.assertIsSatisfied();
    }

    private Map<String, Object> getAS2Headers(String asyncMdnDeliveryAddress) {
        Map<String, Object> headers = new HashMap<>();
        headers.put("CamelAs2.requestUri", "/");
        headers.put("CamelAs2.subject", "Test Case");
        headers.put("CamelAs2.from", "mrAS@example.org");
        headers.put("CamelAs2.as2From", "878051556");
        headers.put("CamelAs2.as2To", "878051556");
        headers.put("CamelAs2.as2MessageStructure", AS2MessageStructure.PLAIN);
        headers.put("CamelAs2.ediMessageContentType", AS2MediaType.APPLICATION_EDIFACT);
        headers.put("CamelAs2.ediMessageTransferEncoding", "7bit");
        headers.put("CamelAs2.dispositionNotificationTo", "mrAS2@example.com");
        headers.put("CamelAs2.receiptDeliveryOption", asyncMdnDeliveryAddress);
        return headers;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                Processor proc = exchange -> {
                    HttpMessage message = exchange.getIn(HttpMessage.class);

                    assertNotNull(message.getHeader("Authorization"));
                    assertEquals("Bearer " + MDN_ACCESS_TOKEN, message.getHeader("Authorization"));
                };

                from("direct://SEND")
                        .to("as2://client/send?inBody=ediMessage&httpSocketTimeout=5m&httpConnectionTimeout=5m");

                from("jetty:http://localhost:" + RECEIPT_SERVER_PORT + "/handle-receipts").process(proc)
                        .to("mock:as2RcvRcptMsgs");
            }
        };
    }

    // AS2 server adds Authorization header to MDN returned asynchronously
    private static void receiveTestMessages() throws IOException {
        serverConnection = new AS2ServerConnection(
                "1.1", "AS2ClientManagerIntegrationTest Server",
                "server.example.com", 8888, AS2SignatureAlgorithm.SHA256WITHRSA,
                null, null, null,
                "TBD", null, null,
                // server authorization config
                null, null, MDN_ACCESS_TOKEN);
        serverConnection.listen("/", new AS2AsyncMDNServerManagerIT.RequestHandler());
    }
}
