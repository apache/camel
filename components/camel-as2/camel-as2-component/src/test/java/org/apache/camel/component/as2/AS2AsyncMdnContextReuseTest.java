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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.AS2ServerConnection;
import org.apache.camel.component.as2.api.AS2SignatureAlgorithm;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * The AS2 request handler creates its {@code HttpContext} once per connection and reuses it for every request handled
 * on that connection. A request that asks for an asynchronous MDN leaves the recipient address and the report on that
 * context; a later request on the same connection that does not ask for one must not inherit them and trigger a second
 * delivery (CAMEL-24435).
 */
public class AS2AsyncMdnContextReuseTest extends AbstractAS2ITSupport {

    @RegisterExtension
    AvailablePortFinder.Port jettyPort = AvailablePortFinder.find();

    private static final String EDI_MESSAGE = """
            UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'
            UNH+00000000000117+INVOIC:D:97B:UN'
            BGM+380+342459+9'
            UNT+23+00000000000117'
            UNZ+1+00000000000778'
            """;

    private AS2ServerConnection serverConnection;
    private int targetPort;

    @Override
    public void setupResources() throws Exception {
        serverConnection = new AS2ServerConnection(
                "1.1", "AS2AsyncMdnContextReuseTest Server", "server.example.com", 0,
                AS2SignatureAlgorithm.SHA256WITHRSA, null, null, null, "TBD", null, null,
                null, null, null, "localhost");
        targetPort = serverConnection.getLocalPort();
        serverConnection.listen("/", new AS2AsyncMDNServerManagerIT.RequestHandler());
    }

    @Override
    public void cleanupResources() {
        if (serverConnection != null) {
            serverConnection.close();
        }
    }

    @Test
    public void asyncMdnStateDoesNotLeakToTheNextRequestOnTheSameConnection() throws Exception {
        MockEndpoint receipts = getMockEndpoint("mock:receipts");

        // the first message asks for an asynchronous receipt, so exactly one MDN must be delivered
        receipts.expectedMessageCount(1);
        receipts.setResultWaitTime(TimeUnit.SECONDS.toMillis(10));
        requestBodyAndHeaders("direct://SEND", EDI_MESSAGE,
                as2Headers("http://localhost:" + jettyPort.getPort() + "/handle-receipts"));
        receipts.assertIsSatisfied();

        // the second message does not, and it travels over the same pooled connection. The recipient address left
        // on that connection's context must not be reused to deliver a second MDN.
        receipts.reset();
        receipts.expectedMessageCount(0);
        // keep asserting for a while after the send, so a leaked delivery has time to show up
        receipts.setAssertPeriod(TimeUnit.SECONDS.toMillis(5));
        requestBodyAndHeaders("direct://SEND", EDI_MESSAGE, as2Headers(null));
        receipts.assertIsSatisfied();
    }

    private Map<String, Object> as2Headers(String asyncMdnDeliveryAddress) {
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
        if (asyncMdnDeliveryAddress != null) {
            headers.put("CamelAs2.receiptDeliveryOption", asyncMdnDeliveryAddress);
        }
        return headers;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct://SEND")
                        .to("as2://client/send?inBody=ediMessage&httpSocketTimeout=5m&httpConnectionTimeout=5m");

                from("jetty:http://localhost:" + jettyPort.getPort() + "/handle-receipts")
                        .to("mock:receipts");
            }
        };
    }

    @Override
    protected void customizeConfiguration(AS2Configuration configuration) {
        configuration.setTargetPortNumber(targetPort);
    }
}
