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
package org.apache.camel.component.cxf.multipart;

import jakarta.xml.ws.Endpoint;
import jakarta.xml.ws.Service;

import javax.xml.namespace.QName;

import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.cxf.multipart.MultiPartInvoke;
import org.apache.camel.cxf.multipart.types.InE;
import org.apache.camel.cxf.multipart.types.ObjectFactory;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CXFMultiPartTest extends CamelSpringTestSupport {
    public static final QName SERVICE_NAME = new QName(
            "http://camel.apache.org/cxf/multipart",
            "MultiPartInvokeService");

    public static final QName ROUTE_PORT_NAME = new QName(
            "http://camel.apache.org/cxf/multipart",
            "MultiPartInvokePort");
    protected static Endpoint endpoint;

    @BeforeAll
    public static void startService() {
        Object implementor = new MultiPartInvokeImpl();
        String address = "http://localhost:" + CXFTestSupport.getPort1() + "/CXFMultiPartTest/SoapContext/SoapPort";
        endpoint = Endpoint.publish(address, implementor);

    }

    @AfterAll
    public static void stopService() {
        if (endpoint != null) {
            endpoint.stop();
        }
    }

    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {
        String reply = invokeMultiPartService("http://localhost:" + CXFTestSupport.getPort3()
                                              + "/CXFMultiPartTest/CamelContext/RouterPort",
                "in0", "in1");
        assertNotNull(reply, "No response received from service");
        assertEquals("in0 in1", reply);

        assertNotNull(reply, "No response received from service");
        assertEquals("in0 in1", reply);

    }

    private String invokeMultiPartService(String address, String in0, String in1) {

        Service service = Service.create(SERVICE_NAME);
        service.addPort(ROUTE_PORT_NAME, "http://schemas.xmlsoap.org/soap/", address);
        MultiPartInvoke multiPartClient = service.getPort(ROUTE_PORT_NAME, MultiPartInvoke.class);

        InE e0 = new ObjectFactory().createInE();
        InE e1 = new ObjectFactory().createInE();
        e0.setV(in0);
        e1.setV(in1);

        jakarta.xml.ws.Holder<InE> h = new jakarta.xml.ws.Holder<>();
        jakarta.xml.ws.Holder<InE> h1 = new jakarta.xml.ws.Holder<>();
        multiPartClient.foo(e0, e1, h, h1);
        return h.value.getV() + " " + h1.value.getV();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/multipart/MultiPartTest.xml");
    }

}
