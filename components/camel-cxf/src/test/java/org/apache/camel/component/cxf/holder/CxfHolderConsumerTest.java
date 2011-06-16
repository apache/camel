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
package org.apache.camel.component.cxf.holder;

import java.util.List;

import javax.xml.ws.Holder;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.HelloService;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.cxf.BusFactory;
import org.apache.cxf.frontend.ClientFactoryBean;
import org.apache.cxf.frontend.ClientProxyFactoryBean;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.version.Version;
import org.junit.Test;

public class CxfHolderConsumerTest extends CamelTestSupport {
    protected static final String ADDRESS = "http://localhost:28080/test";
    protected static final String CXF_ENDPOINT_URI = "cxf://" + ADDRESS
        + "?serviceClass=org.apache.camel.component.cxf.holder.MyOrderEndpoint";
       
    
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(CXF_ENDPOINT_URI).process(new Processor() {
                    @SuppressWarnings("unchecked")
                    public void process(Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        List parameters = in.getBody(List.class);
                        int amount = (Integer) parameters.remove(1);
                        Holder<String> customer = (Holder<String>)parameters.get(1);
                        if (customer.value.length() == 0) {
                            customer.value = "newCustomer";
                        }
                        parameters.add(0, "Ordered ammount " + amount);
                        //reuse the MessageContentList at this time to test CAMEL-4113
                        exchange.getOut().setBody(parameters);
                    }
                }); 
            }
        };
    }
    

    @Test
    public void testInvokingServiceFromCXFClient() throws Exception {
        if (Version.getCurrentVersion().equals("2.4.1")) {
            // This test will be failed with CXF 2.4.1, as 
            // the inObjects and outObjects of HolderOutInterceptor are equals
            return;   
        }
        JaxWsProxyFactoryBean proxyFactory = new JaxWsProxyFactoryBean();
        ClientFactoryBean clientBean = proxyFactory.getClientFactoryBean();
        clientBean.setAddress(ADDRESS);
        clientBean.setServiceClass(MyOrderEndpoint.class);
        
        MyOrderEndpoint client = (MyOrderEndpoint) proxyFactory.create();
        
        Holder<String> strPart = new Holder<String>();
        strPart.value = "parts";
        Holder<String> strCustomer = new Holder<String>();
        strCustomer.value = "";

        String result = client.myOrder(strPart, 2, strCustomer);
        assertEquals("Get a wrong order result", "Ordered ammount 2", result);
        assertEquals("Get a wrong parts", "parts", strPart.value);
        assertEquals("Get a wrong customer", "newCustomer", strCustomer.value);
    }

}
