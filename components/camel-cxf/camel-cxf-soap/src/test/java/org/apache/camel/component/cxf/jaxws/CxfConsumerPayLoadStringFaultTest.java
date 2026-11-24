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
package org.apache.camel.component.cxf.jaxws;

import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.w3c.dom.Document;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.cxf.staxutils.StaxUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A route of a consumer in PAYLOAD mode replies with a SOAP Fault element that is not a CxfPayload (a String or a DOM
 * body): the client gets it as a SOAP fault (HTTP 500), not as a normal response.
 */
public class CxfConsumerPayLoadStringFaultTest extends CamelTestSupport {

    private static final String SOAP11_FAULT
            = "<soap:Fault xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
              + "<faultcode>soap:Client</faultcode><faultstring>No such person</faultstring></soap:Fault>";

    private static final String SOAP12_FAULT
            = "<env:Fault xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\">"
              + "<env:Code><env:Value>env:Sender</env:Value></env:Code>"
              + "<env:Reason><env:Text xml:lang=\"en\">No such person</env:Text></env:Reason></env:Fault>";

    private static final String RESPONSE
            = "<GetPersonResponse xmlns=\"http://camel.apache.org/wsdl-first/types\">"
              + "<personId>1</personId><ssn>000-000-0000</ssn><name>Fault</name></GetPersonResponse>";

    private static final String REQUEST_BODY
            = "<GetPerson xmlns=\"http://camel.apache.org/wsdl-first/types\"><personId>1</personId></GetPerson>";

    private static final String SOAP11_REQUEST
            = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\"><soap:Body>" + REQUEST_BODY
              + "</soap:Body></soap:Envelope>";

    private static final String SOAP12_REQUEST
            = "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"><soap:Body>" + REQUEST_BODY
              + "</soap:Body></soap:Envelope>";

    private static AvailablePortFinder.Port port;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @BeforeAll
    static void allocatePorts() {
        port = AvailablePortFinder.find();
    }

    @AfterAll
    static void releasePorts() {
        if (port != null) {
            port.release();
        }
    }

    private static String address(String path) {
        return "http://localhost:" + port.getPort() + "/CxfConsumerPayLoadStringFaultTest/" + path;
    }

    private static String consumer(String path, String serviceName) {
        return "cxf://" + address(path) + "?wsdlURL=classpath:person.wsdl&dataFormat=PAYLOAD"
               + "&serviceName={http://camel.apache.org/wsdl-first}" + serviceName
               + "&portName={http://camel.apache.org/wsdl-first}soap";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(consumer("string11", "PersonService")).setBody(constant(SOAP11_FAULT));

                from(consumer("validated11", "PersonService") + "&schemaValidationEnabled=true")
                        .setBody(constant(SOAP11_FAULT));

                from(consumer("dom11", "PersonService")).process(exchange -> {
                    Document doc = StaxUtils.read(new StringReader(SOAP11_FAULT));
                    exchange.getMessage().setBody(doc);
                });

                from(consumer("string12", "PersonService12")).setBody(constant(SOAP12_FAULT));

                from(consumer("response", "PersonService")).setBody(constant(RESPONSE));
            }
        };
    }

    @Test
    public void testSoap11FaultAsString() throws Exception {
        HttpResponse<String> response = post("string11", SOAP11_REQUEST, "text/xml; charset=UTF-8");
        assertEquals(500, response.statusCode(), response.body());
        assertTrue(response.body().contains("<faultstring>No such person</faultstring>"), response.body());
        assertTrue(response.body().contains(":Client</faultcode>"), response.body());
    }

    @Test
    public void testSoap11FaultAsStringWithSchemaValidation() throws Exception {
        HttpResponse<String> response = post("validated11", SOAP11_REQUEST, "text/xml; charset=UTF-8");
        assertEquals(500, response.statusCode(), response.body());
        assertTrue(response.body().contains("<faultstring>No such person</faultstring>"), response.body());
    }

    @Test
    public void testSoap11FaultAsDocument() throws Exception {
        HttpResponse<String> response = post("dom11", SOAP11_REQUEST, "text/xml; charset=UTF-8");
        assertEquals(500, response.statusCode(), response.body());
        assertTrue(response.body().contains("<faultstring>No such person</faultstring>"), response.body());
    }

    @Test
    public void testSoap12FaultAsString() throws Exception {
        HttpResponse<String> response = post("string12", SOAP12_REQUEST, "application/soap+xml; charset=UTF-8");
        assertEquals(500, response.statusCode(), response.body());
        assertTrue(response.body().contains("No such person</soap:Text>"), response.body());
        assertTrue(response.body().contains(":Sender</soap:Value>"), response.body());
    }

    @Test
    public void testResponseAsString() throws Exception {
        HttpResponse<String> response = post("response", SOAP11_REQUEST, "text/xml; charset=UTF-8");
        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains("<name>Fault</name>"), response.body());
        assertFalse(response.body().contains("faultstring"), response.body());
    }

    private HttpResponse<String> post(String path, String body, String contentType) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(address(path)))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
