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
package org.apache.camel.component.cxf.transport;

import static org.hamcrest.CoreMatchers.is;

import javax.jws.WebMethod;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.Service;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.cxf.BusFactory;
import org.junit.Before;
import org.junit.Test;

/**
 * Test CXF-CamelConduit when the destination is not a pipeline
 */
public class JaxWSCamelConduitTest extends CamelTestSupport {
    
    /**
     * Expected SOAP answer for the 'SampleWS.getSomething' method
     */
    public static final String ANSWER = "<Envelope xmlns='http://schemas.xmlsoap.org/soap/envelope/'>"
                                        + "<Body>" + "<getSomethingResponse xmlns='urn:test'>"
                                        + "<result>Something</result>" + "</getSomethingResponse>"
                                        + "</Body>" + "</Envelope>";

    /**
     * Sample WebService
     */
    @WebService(targetNamespace = "urn:test", serviceName = "testService", portName = "testPort")
    public interface SampleWS {

        @WebMethod
        @WebResult(name = "result", targetNamespace = "urn:test")
        String getSomething();
    }

    /**
     * Initialize CamelTransportFactory without Spring
     */
    @Before
    public void setUpCXFCamelContext() {
        BusFactory.getThreadDefaultBus().getExtension(CamelTransportFactory.class).setCamelContext(context);
    }

    /**
     * Create a SampleWS JAXWS-Proxy to a specified route
     * 
     * @param camelRoute
     * @return
     */
    public SampleWS getSampleWS(String camelRoute) {
        QName serviceName = new QName("urn:test", "testService");
        Service s = Service.create(serviceName);

        QName portName = new QName("urn:test", "testPort");
        s.addPort(portName, "http://schemas.xmlsoap.org/soap/", "camel://" + camelRoute);

        return s.getPort(SampleWS.class);
    }

    

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            public void configure() throws Exception {

                from("direct:start1").setBody(constant(ANSWER));

                from("direct:start2").setBody(constant(ANSWER)).log("Force pipeline creation");
            }
        };
    }

   
    @Test
    public void testStart1() {
        assertThat(getSampleWS("direct:start1").getSomething(), is("Something"));
    }

    /**
     * Success
     */
    @Test
    public void testStart2() {
        assertThat(getSampleWS("direct:start2").getSomething(), is("Something"));
    }
}
