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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadRuntimeException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.cxf.BusFactory;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.message.MessageContentsList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The message bodies the cxf producer sends and the cxf consumer replies in each data format.
 */
public class CxfBodyTypesTest extends CamelTestSupport {

    @RegisterExtension
    static final AvailablePortFinder.Port PORT = AvailablePortFinder.find();

    private static final String NS = "http://jaxws.cxf.component.camel.apache.org/";

    private static final String ECHO_TEXT_REQUEST = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                                                    + "<soap:Body><ns1:echoText xmlns:ns1=\"" + NS + "\">"
                                                    + "<arg0 xmlns=\"" + NS + "\">Hello World</arg0>"
                                                    + "</ns1:echoText></soap:Body></soap:Envelope>";

    private static final String ECHO_TEXT_RESPONSE = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                                                     + "<soap:Body><ns1:echoTextResponse xmlns:ns1=\"" + NS + "\">"
                                                     + "<return xmlns=\"" + NS + "\">echo from envelope</return>"
                                                     + "</ns1:echoTextResponse></soap:Body></soap:Envelope>";

    private static String address(String path) {
        return "http://localhost:" + PORT.getPort() + "/CxfBodyTypesTest/" + path;
    }

    private static String uri(String path) {
        return "cxf://" + address(path) + "?serviceClass=" + BodyTypeService.class.getName();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(uri("pojo")).process(CxfBodyTypesTest::echo);

                from(uri("payload") + "&dataFormat=PAYLOAD").setBody(constant(Map.of("foo", "bar")));

                from(uri("raw") + "&dataFormat=RAW").setBody(constant(Map.of("foo", "bar")));

                from(uri("message") + "&dataFormat=CXF_MESSAGE")
                        .process(e -> e.getMessage().setBody(
                                new ByteArrayInputStream(ECHO_TEXT_RESPONSE.getBytes(StandardCharsets.UTF_8))));

                from("direct:pojo").to(uri("pojo") + "&defaultOperationName=echoText");

                from("direct:message").to(uri("pojo") + "&dataFormat=CXF_MESSAGE&defaultOperationName=echoText");
            }
        };
    }

    private static void echo(Exchange exchange) {
        String operation = exchange.getIn().getHeader(CxfConstants.OPERATION_NAME, String.class);
        Object arg = exchange.getIn().getBody(MessageContentsList.class).get(0);
        if ("echoBytes".equals(operation)) {
            exchange.getMessage().setBody(("echo " + new String((byte[]) arg, StandardCharsets.UTF_8))
                    .getBytes(StandardCharsets.UTF_8));
        } else {
            exchange.getMessage().setBody("echo " + arg);
        }
    }

    private static BodyTypeService client(String path) {
        ClientProxyFactoryBean proxyFactory = new ClientProxyFactoryBean();
        proxyFactory.getClientFactoryBean().setAddress(address(path));
        proxyFactory.getClientFactoryBean().setServiceClass(BodyTypeService.class);
        proxyFactory.getClientFactoryBean().setBus(BusFactory.getDefaultBus());
        return (BodyTypeService) proxyFactory.create();
    }

    @Test
    public void testPojoConsumerRepliesByteArray() {
        byte[] reply = client("pojo").echoBytes("Hello".getBytes(StandardCharsets.UTF_8));

        assertEquals("echo Hello", new String(reply, StandardCharsets.UTF_8));
    }

    @Test
    public void testPojoProducerSendsTextWithCommaAsOneParameter() {
        Object reply = template.requestBody("direct:pojo", "Hello,World");

        assertEquals("echo Hello,World", ((List<?>) reply).get(0));
    }

    @Test
    public void testPojoProducerSendsByteArrayAsOneParameter() {
        Object reply = template.requestBodyAndHeader("direct:pojo", "Hello".getBytes(StandardCharsets.UTF_8),
                CxfConstants.OPERATION_NAME, "echoBytes");

        assertArrayEquals("echo Hello".getBytes(StandardCharsets.UTF_8), (byte[]) ((List<?>) reply).get(0));
    }

    @Test
    public void testPojoProducerSendsListAsParameters() {
        Object reply = template.requestBody("direct:pojo", List.of("Hello,World"));

        assertEquals("echo Hello,World", ((List<?>) reply).get(0));
    }

    @Test
    public void testCxfMessageProducerSendsTextEnvelope() {
        String reply = template.requestBody("direct:message", ECHO_TEXT_REQUEST, String.class);

        assertTrue(reply.contains("echo Hello World"), "Wrong reply: " + reply);
    }

    @Test
    public void testCxfMessageProducerFailsForBodyThatIsNotAnEnvelope() {
        Exception e = assertThrows(Exception.class,
                () -> template.requestBody("direct:message", Map.of("foo", "bar")));

        assertInstanceOf(InvalidPayloadRuntimeException.class, e.getCause());
        assertTrue(e.getCause().getMessage().contains("DOMSource"), "Wrong exception: " + e.getCause().getMessage());
    }

    @Test
    public void testCxfMessageConsumerRepliesStreamEnvelope() {
        assertEquals("echo from envelope", client("message").echoText("Hello"));
    }

    @Test
    public void testPayloadConsumerFailsForReplyThatDoesNotConvert() {
        Exception e = assertThrows(Exception.class, () -> client("payload").echoText("Hello"));

        assertTrue(e.getMessage().contains("CxfPayload"), "Wrong fault: " + e.getMessage());
    }

    @Test
    public void testRawConsumerFailsForReplyThatDoesNotConvert() {
        Exception e = assertThrows(Exception.class, () -> client("raw").echoText("Hello"));

        assertTrue(e.getMessage().contains("InputStream"), "Wrong fault: " + e.getMessage());
    }

}
