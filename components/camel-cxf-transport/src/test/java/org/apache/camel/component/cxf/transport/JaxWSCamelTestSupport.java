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

import javax.jws.WebMethod;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Service;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.cxf.BusFactory;
import org.junit.Before;

public class JaxWSCamelTestSupport extends CamelTestSupport {
    /**
     * Expected SOAP answer for the 'SampleWS.getSomething' method
     */
    public static final String ANSWER = "<Envelope xmlns='http://schemas.xmlsoap.org/soap/envelope/'>"
                                        + "<Body>" + "<getSomethingResponse xmlns='urn:test'>"
                                        + "<result>Something</result>" + "</getSomethingResponse>"
                                        + "</Body>" + "</Envelope>";
    
    public static final String REQUEST = "<Envelope xmlns='http://schemas.xmlsoap.org/soap/envelope/'>"
        + "<Body>" + "<getSomething xmlns='urn:test'/>"
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
    
    public static class SampleWSImpl implements SampleWS {

        @Override
        public String getSomething() {
            return "something!";
        }
        
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
     * @param camelEndpoint
     * @return
     */
    public SampleWS getSampleWS(String camelEndpoint) {
        QName serviceName = new QName("urn:test", "testService");
        Service s = Service.create(serviceName);

        QName portName = new QName("urn:test", "testPort");
        s.addPort(portName, "http://schemas.xmlsoap.org/soap/", "camel://" + camelEndpoint);

        return s.getPort(SampleWS.class);
    }
    
    /**
     * Create a SampleWS Server to a specified route
     * @param camelEndpoint
     */
    
    public Endpoint publishSampleWS(String camelEndpoint) {
        return Endpoint.publish("camel://" + camelEndpoint, new SampleWSImpl());
        
    }

}
