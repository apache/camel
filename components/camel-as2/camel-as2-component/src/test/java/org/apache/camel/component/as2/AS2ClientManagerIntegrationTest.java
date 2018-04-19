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
package org.apache.camel.component.as2;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.as2.api.AS2Charset;
import org.apache.camel.component.as2.api.AS2Constants;
import org.apache.camel.component.as2.api.AS2Header;
import org.apache.camel.component.as2.api.AS2MediaType;
import org.apache.camel.component.as2.api.AS2MessageStructure;
import org.apache.camel.component.as2.api.AS2MimeType;
import org.apache.camel.component.as2.api.AS2ServerConnection;
import org.apache.camel.component.as2.api.AS2ServerManager;
import org.apache.camel.component.as2.api.entity.ApplicationEDIEntity;
import org.apache.camel.component.as2.api.entity.DispositionNotificationMultipartReportEntity;
import org.apache.camel.component.as2.api.entity.MimeEntity;
import org.apache.camel.component.as2.api.util.HttpMessageUtils;
import org.apache.camel.component.as2.internal.AS2ApiCollection;
import org.apache.camel.component.as2.internal.AS2ClientManagerApiMethod;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link org.apache.camel.component.as2.api.AS2ClientManager} APIs.
 */
public class AS2ClientManagerIntegrationTest extends AbstractAS2TestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(AS2ClientManagerIntegrationTest.class);
    private static final String PATH_PREFIX = AS2ApiCollection.getCollection().getApiName(AS2ClientManagerApiMethod.class).getName();

    private static final String REQUEST_URI = "/";
    private static final String SUBJECT = "Test Case";
    private static final String AS2_NAME = "878051556";
    private static final String FROM = "mrAS@example.org";
    
    private static final String MDN_FROM = "as2Test@server.example.com";
    private static final String MDN_SUBJECT_PREFIX = "MDN Response:";
    
    private static final String EDI_MESSAGE = 
            "UNB+UNOA:1+005435656:1+006415160:1+060515:1434+00000000000778'\n"
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
    
    private static final String EXPECTED_AS2_VERSION = "1.1";
    private static final String EXPECTED_MDN_SUBJECT = MDN_SUBJECT_PREFIX + SUBJECT;
    
    private static AS2ServerConnection serverConnection;

    @Test
    public void plainMessageSendTest() throws Exception {
        final Map<String, Object> headers = new HashMap<String, Object>();
        // parameter type is String
        headers.put("CamelAS2.ediMessage", EDI_MESSAGE);
        // parameter type is String
        headers.put("CamelAS2.requestUri", REQUEST_URI);
        // parameter type is String
        headers.put("CamelAS2.subject", SUBJECT);
        // parameter type is String
        headers.put("CamelAS2.from", FROM);
        // parameter type is String
        headers.put("CamelAS2.as2From", AS2_NAME);
        // parameter type is String
        headers.put("CamelAS2.as2To", AS2_NAME);
        // parameter type is org.apache.camel.component.as2.api.AS2MessageStructure
        headers.put("CamelAS2.as2MessageStructure", AS2MessageStructure.PLAIN);
        // parameter type is org.apache.http.entity.ContentType
        headers.put("CamelAS2.ediMessageContentType", ContentType.create(AS2MediaType.APPLICATION_EDIFACT, AS2Charset.US_ASCII));
        // parameter type is String
        headers.put("CamelAS2.ediMessageTransferEncoding", null);
        // parameter type is String
        headers.put("CamelAS2.signingAlgorithmName", null);
        // parameter type is java.security.cert.Certificate[]
        headers.put("CamelAS2.signingCertificateChain", null);
        // parameter type is java.security.PrivateKey
        headers.put("CamelAS2.signingPrivateKey", null);
        // parameter type is String
        headers.put("CamelAS2.dispositionNotificationTo", "mrAS2@example.com");
        // parameter type is String[]
        headers.put("CamelAS2.signedReceiptMicAlgorithms", null);

        final org.apache.http.protocol.HttpCoreContext result = requestBodyAndHeaders("direct://SEND", null, headers);

        assertNotNull("send result", result);
        LOG.debug("send: " + result);
        HttpRequest request = result.getRequest();
        assertNotNull("Request", request);
        assertTrue("Request does not contain body", request instanceof HttpEntityEnclosingRequest);
        HttpEntity entity = ((HttpEntityEnclosingRequest)request).getEntity();
        assertNotNull("Request body", entity);
        assertTrue("Request body does not contain EDI entity", entity instanceof ApplicationEDIEntity);
        String ediMessage = ((ApplicationEDIEntity)entity).getEdiMessage();
        assertEquals("EDI message is different", EDI_MESSAGE, ediMessage);
        
        HttpResponse response = result.getResponse();
        assertNotNull("Response", response);
        assertEquals("Unexpected response type", AS2MimeType.MULTIPART_REPORT, HttpMessageUtils.getHeaderValue(response, AS2Header.CONTENT_TYPE));
        assertEquals("Unexpected mime version", AS2Constants.MIME_VERSION, HttpMessageUtils.getHeaderValue(response, AS2Header.MIME_VERSION));
        assertEquals("Unexpected AS2 version", EXPECTED_AS2_VERSION, HttpMessageUtils.getHeaderValue(response, AS2Header.AS2_VERSION));
        assertEquals("Unexpected MDN subject", EXPECTED_MDN_SUBJECT, HttpMessageUtils.getHeaderValue(response, AS2Header.SUBJECT));
        assertEquals("Unexpected MDN from", MDN_FROM, HttpMessageUtils.getHeaderValue(response, AS2Header.FROM));
        assertEquals("Unexpected AS2 from", AS2_NAME, HttpMessageUtils.getHeaderValue(response, AS2Header.AS2_FROM));
        assertEquals("Unexpected AS2 to", AS2_NAME, HttpMessageUtils.getHeaderValue(response, AS2Header.AS2_TO));
        assertNotNull("Missing message id", HttpMessageUtils.getHeaderValue(response, AS2Header.MESSAGE_ID));
        
        HttpEntity responseEntity = response.getEntity();
        assertNotNull("Response entity", responseEntity);
        assertTrue("Unexpected response entity type", responseEntity instanceof DispositionNotificationMultipartReportEntity);
        DispositionNotificationMultipartReportEntity reportEntity = (DispositionNotificationMultipartReportEntity)responseEntity;
        assertEquals("Unexpected number of body parts in report", 2, reportEntity.getPartCount());
        MimeEntity firstPart = reportEntity.getPart(0);
        assertEquals("Unexpected content type in first body part of report", ContentType.create(AS2MimeType.TEXT_PLAIN, AS2Charset.US_ASCII).toString(), firstPart.getContentTypeValue());
        MimeEntity secondPart = reportEntity.getPart(1);
        assertEquals("Unexpected content type in second body part of report",
                ContentType.create(AS2MimeType.MESSAGE_DISPOSITION_NOTIFICATION, AS2Charset.US_ASCII).toString(),
                secondPart.getContentTypeValue());
    }

    @BeforeClass
    public static void setupTest() throws Exception {
        receiveTestMessages();
    }
    
    @AfterClass
    public static void teardownTest() throws Exception {
        if (serverConnection != null) {
            serverConnection.stopListening("/");
        }
    }

    public static class RequestHandler implements HttpRequestHandler {

        @Override
        public void handle(HttpRequest request, HttpResponse response, HttpContext context)
                throws HttpException, IOException {
            LOG.info("Received test message: " + request);
            context.setAttribute(AS2ServerManager.FROM, MDN_FROM);
            context.setAttribute(AS2ServerManager.SUBJECT, MDN_SUBJECT_PREFIX);
        }

    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for send
                from("direct://SEND").to("as2://" + PATH_PREFIX + "/send");

            }
        };
    }
   
    private static void receiveTestMessages() throws IOException {
        serverConnection = new AS2ServerConnection("1.1", "AS2ClientManagerIntegrationTest Server",
                "server.example.com", 8888, null, null);
        serverConnection.listen("/", new RequestHandler());
    }
}
