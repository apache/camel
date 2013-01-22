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

import java.io.InputStream;

import com.example.customerservice.Customer;
import com.example.customerservice.CustomerService;
import com.example.customerservice.GetCustomersByName;
import com.example.customerservice.GetCustomersByNameResponse;

import org.apache.camel.Produce;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.soap.name.ElementNameStrategy;
import org.apache.camel.dataformat.soap.name.ServiceInterfaceStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Test that uses a dynamic proxy for CustomerService to send a request as SOAP
 * and work with a static return SOAP message.
 */
public class SoapClientTest extends CamelTestSupport {

    @Produce(uri = "direct:start")
    CustomerService customerService;

    @Test
    public void testRoundTripGetCustomersByName() throws Exception {
        GetCustomersByNameResponse response = customerService.getCustomersByName(new GetCustomersByName());

        assertEquals(1, response.getReturn().size());
        Customer firstCustomer = response.getReturn().get(0);
        assertEquals(100000.0, firstCustomer.getRevenue(), 0.0D);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            String jaxbPackage = GetCustomersByName.class.getPackage().getName();

            @Override
            public void configure() throws Exception {
                ElementNameStrategy elNameStrat = new ServiceInterfaceStrategy(CustomerService.class, true);
                SoapJaxbDataFormat soapDataFormat = new SoapJaxbDataFormat(jaxbPackage, elNameStrat);
                final InputStream in = this.getClass().getResourceAsStream("response.xml");
                from("direct:start").marshal(soapDataFormat).process(new FileReplyProcessor(in))
                        .unmarshal(soapDataFormat);
            }
        };
    }

}
