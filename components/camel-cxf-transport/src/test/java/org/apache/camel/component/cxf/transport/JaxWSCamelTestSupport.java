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
package org.apache.camel.component.cxf.transport;

import java.util.concurrent.Future;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.ws.AsyncHandler;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Response;
import javax.xml.ws.Service;

import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.cxf.Bus;
import org.apache.cxf.BusFactory;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
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
    
    private Bus bus;

    /**
     * Sample WebService
     */
    @WebService(targetNamespace = "urn:test", serviceName = "testService", portName = "testPort")
    public interface SampleWS {

        @WebMethod(operationName = "getSomething")
        @WebResult(name = "result", targetNamespace = "urn:test")
        String getSomething();
       
    }
    
    @WebService(targetNamespace = "urn:test", serviceName = "testService", portName = "testPort")
    public interface SampleWSAsync {
        @WebMethod(operationName = "getSomething")
        @WebResult(name = "result", targetNamespace = "urn:test")
        String getSomething();
        
        @WebMethod(operationName = "getSomething")
        Response<String> getSomethingAsync();
        
        @WebMethod(operationName = "getSomething")
        Future<?> getSomethingAsync(@WebParam(name = "asyncHandler", targetNamespace = "")
            AsyncHandler<String> asyncHandler);
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
        bus = BusFactory.getThreadDefaultBus();
        // make sure the CamelTransportFactory is injected with right camel context
        bus.getExtension(CamelTransportFactory.class).setCamelContext(context);
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
    
    public SampleWS getSampleWSWithCXFAPI(String camelEndpoint) {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setAddress("camel://" + camelEndpoint);
        factory.setServiceClass(SampleWS.class);
        factory.setBus(bus);
        return factory.create(SampleWS.class);
    }
    
    public SampleWSAsync getSampleWSAsyncWithCXFAPI(String camelEndpoint) {
        JaxWsProxyFactoryBean factory = new JaxWsProxyFactoryBean();
        factory.setAddress("camel://" + camelEndpoint);
        factory.setServiceClass(SampleWSAsync.class);
        factory.setBus(bus);
        return factory.create(SampleWSAsync.class);
    }
    
    /**
     * Create a SampleWS Server to a specified route
     * @param camelEndpoint
     */
    
    public Endpoint publishSampleWS(String camelEndpoint) {
        return Endpoint.publish("camel://" + camelEndpoint, new SampleWSImpl());
        
    }
    
    /**
     * Create a SampleWS Server with Gzip enabled to a specified route
     * @param camelEndpoint
     */
    
    public Endpoint publishSampleWSWithGzipEnabled(String camelEndpoint) {
        EndpointImpl endpoint = (EndpointImpl)Endpoint.publish("camel://" + camelEndpoint, new SampleWSImpl());
        endpoint.getInInterceptors().add(new org.apache.cxf.transport.common.gzip.GZIPInInterceptor());
        endpoint.getOutInterceptors().add(new org.apache.cxf.transport.common.gzip.GZIPOutInterceptor(0));
        return endpoint;
        
    }

}
