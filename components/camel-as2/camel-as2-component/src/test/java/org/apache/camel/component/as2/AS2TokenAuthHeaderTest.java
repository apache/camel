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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.AS2ServerConnection;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.hc.core5.http.HttpRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verify that the authorization header is added to the request sent to the AS2 server for Bearer Auth.
 */
public class AS2TokenAuthHeaderTest extends AbstractAS2ITSupport {

    private static final String ACCESS_TOKEN = "MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3";
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
    private static AS2ClientManagerIT.RequestHandler requestHandler;

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
    public void clientSendsBasicAuthHeader() throws Exception {
        Map<String, Object> headers = configureNonAuthHeaders();
        headers.put("CamelAs2.accessToken", ACCESS_TOKEN);
        requestBodyAndHeaders("direct://SEND", EDI_MESSAGE, headers);

        HttpRequest request = requestHandler.getRequest();
        assertNotNull(request);
        assertNotNull(request.getHeader("Authorization"));
        assertEquals("Bearer " + ACCESS_TOKEN, request.getHeader("Authorization").getValue());
    }

    @Test
    public void clientSendsBasicAuthHeaderRouteConfig() throws Exception {
        requestBodyAndHeaders("direct://SEND2", EDI_MESSAGE, configureNonAuthHeaders());

        HttpRequest request = requestHandler.getRequest();
        assertNotNull(request);
        assertNotNull(request.getHeader("Authorization"));
        assertEquals("Bearer " + ACCESS_TOKEN, request.getHeader("Authorization").getValue());
    }

    private Map<String, Object> configureNonAuthHeaders() {
        final Map<String, Object> headers = new HashMap<>();
        headers.put("CamelAs2.requestUri", "/");
        headers.put("CamelAs2.subject", "Test Case");
        headers.put("CamelAs2.from", "mrAS@example.org");
        headers.put("CamelAs2.as2From", "878051556");
        headers.put("CamelAs2.as2To", "878051556");
        headers.put("CamelAs2.as2MessageStructure", AS2MessageStructure.PLAIN);
        headers.put("CamelAs2.ediMessageContentType", AS2MediaType.APPLICATION_EDIFACT);
        headers.put("CamelAs2.ediMessageTransferEncoding", "7bit");
        headers.put("CamelAs2.dispositionNotificationTo", "mrAS2@example.com");
        headers.put("CamelAs2.attachedFileName", "");

        return headers;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct://SEND")
                        .to("as2://client/send?inBody=ediMessage&httpSocketTimeout=5m&httpConnectionTimeout=5m");

                from("direct://SEND2")
                        .toF("as2://client/send?accessToken=%s" +
                             "&inBody=ediMessage&httpSocketTimeout=5m&httpConnectionTimeout=5m", ACCESS_TOKEN);
            }
        };
    }

    private static void receiveTestMessages() throws IOException {
        serverConnection = new AS2ServerConnection(
                "1.1", "AS2ClientManagerIntegrationTest Server",
                "server.example.com", 8888, AS2SignatureAlgorithm.SHA256WITHRSA,
                null, null, null,
                "TBD", null, null, null, null, null);
        requestHandler = new AS2ClientManagerIT.RequestHandler();
        serverConnection.listen("/", requestHandler);
    }
}
