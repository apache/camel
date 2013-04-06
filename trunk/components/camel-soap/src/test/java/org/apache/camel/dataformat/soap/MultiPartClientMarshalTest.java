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
package org.apache.camel.dataformat.soap;

import java.util.HashMap;
import java.util.Map;

import com.example.customerservice.multipart.GetCustomersByName;
import com.example.customerservice.multipart.MultiPartCustomerService;
import com.example.customerservice.multipart.Product;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bean.BeanInvocation;
import org.apache.camel.component.xquery.XQueryBuilder;
import org.apache.camel.dataformat.soap.name.ServiceInterfaceStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;


public class MultiPartClientMarshalTest extends CamelTestSupport {
        
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                
                ServiceInterfaceStrategy strat =  
                       new ServiceInterfaceStrategy(com.example.customerservice.multipart.MultiPartCustomerService.class, true);
                SoapJaxbDataFormat soapDataFormat = new SoapJaxbDataFormat("com.example.customerservice.multipart", strat);
                              
                from("direct:start")
                        .marshal(soapDataFormat)
                        .log("marshal to: ${body}")
                        .end();
            }
        };
    }
    
    @Test
    public void testSendPayload() throws Exception {
                
        Exchange exchange = template.send("direct:start",  new Processor() {

            public void process(Exchange exchange) throws Exception {
                BeanInvocation beanInvocation = new BeanInvocation();
                GetCustomersByName getCustomersByName = new GetCustomersByName();
                getCustomersByName.setName("Dr. Multipart");
                beanInvocation.setMethod(MultiPartCustomerService.class.getMethod("getCustomersByName", 
                        GetCustomersByName.class, com.example.customerservice.multipart.Product.class));
                
                Product product = new Product();
                product.setName("Multiuse Product");
                product.setDescription("Useful for lots of things.");
                
                Object[] args = new Object[] {getCustomersByName, product};
                beanInvocation.setArgs(args);
                exchange.getIn().setBody(beanInvocation); 
            }
        });

        if (exchange.getException() != null) {
            throw exchange.getException();
        }
           
        Map<String, String> nsMap = new HashMap<String, String>();
        nsMap.put("soap", "http://schemas.xmlsoap.org/soap/envelope/");
        nsMap.put("example", "http://multipart.customerservice.example.com/");
        XQueryBuilder builder = XQueryBuilder.xquery("//soap:Envelope/soap:Header/example:product/name");
        builder.setNamespaces(nsMap);
        String result = builder.evaluateAsString(exchange);
        assertTrue(result.equals("Multiuse Product"));
        
        
        builder = XQueryBuilder.xquery("//soap:Envelope/soap:Body/example:getCustomersByName/name");
        builder.setNamespaces(nsMap);
        result = builder.evaluateAsString(exchange);
        assertTrue(result.equals("Dr. Multipart"));
    }  
}
