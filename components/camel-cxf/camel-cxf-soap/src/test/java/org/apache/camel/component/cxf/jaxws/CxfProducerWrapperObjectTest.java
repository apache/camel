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

import java.util.List;

import jakarta.xml.ws.Endpoint;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.cxf.message.MessageContentsList;
import org.apache.hello_world_soap_http.GreeterImpl;
import org.apache.hello_world_soap_http.types.GreetMe;
import org.apache.hello_world_soap_http.types.SayHi;
import org.apache.hello_world_soap_http.types.TestNillable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The POJO producer invokes a document-literal wrapped operation with its parameters, unless wrapped is true. A body
 * that is the request wrapper object of the operation fails with a message that names the wrapped option.
 */
public class CxfProducerWrapperObjectTest extends CamelTestSupport {
    private static final String NS = "http://apache.org/hello_world_soap_http";

    private static AvailablePortFinder.Port port;
    private static Endpoint server;

    @BeforeAll
    static void startServer() {
        port = AvailablePortFinder.find();
        server = Endpoint.publish(address(), new GreeterImpl());
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
        if (port != null) {
            port.release();
        }
    }

    private static String address() {
        return "http://localhost:" + port.getPort() + "/CxfProducerWrapperObjectTest/SoapPort";
    }

    private static String uri(String operation, String options) {
        return "cxf://" + address() + "?serviceClass=org.apache.hello_world_soap_http.Greeter"
               + "&defaultOperationName=" + operation + "&defaultOperationNamespace=" + NS + options;
    }

    private Exchange send(String uri, Object body) {
        return template.send(uri, e -> {
            e.setPattern(ExchangePattern.InOut);
            e.getIn().setBody(body);
        });
    }

    private static GreetMe greetMe() {
        GreetMe wrapper = new GreetMe();
        wrapper.setRequestType("Willem");
        return wrapper;
    }

    private static TestNillable testNillable() {
        TestNillable wrapper = new TestNillable();
        wrapper.setNillElem("Willem");
        wrapper.setIntElem(1);
        return wrapper;
    }

    @Test
    public void wrapperObjectOfOneParameterOperationNamesTheWrappedOption() {
        Exchange reply = send(uri("greetMe", ""), greetMe());

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, reply.getException());
        assertEquals("The message body is a org.apache.hello_world_soap_http.types.GreetMe, the request wrapper object of"
                     + " the operation {" + NS + "}greetMe, but the producer invokes the operation with its parameters"
                     + " (java.lang.String) because the wrapped option is false. Set the wrapped option to true to send"
                     + " the wrapper object, or set the message body to the parameters of the operation.",
                e.getMessage());
    }

    @Test
    public void wrapperObjectOfTwoParameterOperationNamesTheWrappedOption() {
        Exchange reply = send(uri("testNillable", ""), testNillable());

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, reply.getException());
        assertTrue(e.getMessage().startsWith("The message body is a org.apache.hello_world_soap_http.types.TestNillable,"
                                             + " the request wrapper object of the operation {" + NS + "}testNillable"),
                e.getMessage());
        assertTrue(e.getMessage().contains("its parameters (java.lang.String, int) because the wrapped option is false"),
                e.getMessage());
    }

    @Test
    public void wrapperObjectOfOperationWithoutParametersNamesTheWrappedOption() {
        Exchange reply = send(uri("sayHi", ""), new SayHi());

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, reply.getException());
        assertTrue(e.getMessage().contains("its parameters () because the wrapped option is false"), e.getMessage());
    }

    @Test
    public void tooFewParametersNamesTheOperationAndItsParameterTypes() {
        Exchange reply = send(uri("testNillable", ""), List.of("Willem"));

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, reply.getException());
        assertEquals("Get the wrong parameter size to invoke the out service, Expect size 2, Parameter size 1. The"
                     + " operation {" + NS + "}testNillable expects the parameters (java.lang.String, int). Please check"
                     + " if the message body matches the CXFEndpoint POJO Dataformat request.",
                e.getMessage());
    }

    @Test
    public void tooManyParametersNamesTheOperationAndItsParameterTypes() {
        Exchange reply = send(uri("greetMe", ""), List.of("Willem", "Claus"));

        IllegalArgumentException e = assertInstanceOf(IllegalArgumentException.class, reply.getException());
        assertEquals("Get the wrong parameter size to invoke the out service, Expect size 1, Parameter size 2. The"
                     + " operation {" + NS + "}greetMe expects the parameters (java.lang.String). Please check"
                     + " if the message body matches the CXFEndpoint POJO Dataformat request.",
                e.getMessage());
    }

    @Test
    public void parametersAreSentWhenWrappedIsFalse() {
        Exchange reply = send(uri("greetMe", ""), List.of("Willem"));
        assertNull(reply.getException());
        assertEquals("Hello Willem", reply.getMessage().getBody(String.class));

        reply = send(uri("greetMe", ""), "Willem");
        assertNull(reply.getException());
        assertEquals("Hello Willem", reply.getMessage().getBody(String.class));

        reply = send(uri("testNillable", ""), List.of("Willem", 1));
        assertNull(reply.getException());
        assertEquals("Willem", reply.getMessage().getBody(String.class));
    }

    @Test
    public void wrapperObjectIsSentWhenWrappedIsTrueAndTheReplyIsNotWrapped() {
        Exchange reply = send(uri("greetMe", "&wrapped=true"), greetMe());

        // the reply holds the return value of the operation, not a GreetMeResponse
        assertNull(reply.getException());
        MessageContentsList body = assertInstanceOf(MessageContentsList.class, reply.getMessage().getBody());
        assertEquals(List.of("Hello Willem"), body);

        reply = send(uri("testNillable", "&wrapped=true"), testNillable());

        assertNull(reply.getException());
        body = assertInstanceOf(MessageContentsList.class, reply.getMessage().getBody());
        assertEquals(List.of("Willem"), body);
    }
}
