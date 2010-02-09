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

import junit.framework.Assert;

import com.example.customerservice.Customer;
import com.example.customerservice.CustomerService;
import com.example.customerservice.GetCustomersByName;
import com.example.customerservice.GetCustomersByNameResponse;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bean.ProxyHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.soap.name.ElementNameStrategy;
import org.apache.camel.dataformat.soap.name.TypeNameStrategy;
import org.apache.camel.test.CamelTestSupport;

public class SoapClientTest extends CamelTestSupport {
    private final class FileReplyProcessor implements Processor {
        private final InputStream in;

        private FileReplyProcessor(InputStream in) {
            this.in = in;
        }

        public void process(Exchange exchange) throws Exception {
            exchange.getIn().setBody(in);
        }
    }

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint resultEndpoint;
    protected CustomerService proxy;

    @Produce(uri = "direct:start")
    protected ProducerTemplate producer;

    @SuppressWarnings("unchecked")
    public void testRoundTrip() throws Exception {
        context.setTracing(true);
        Endpoint start = context.getEndpoint("direct:start");
        proxy = ProxyHelper.createProxy(start,
                this.getClass().getClassLoader(), CustomerService.class);

        GetCustomersByNameResponse response = proxy
                .getCustomersByName(new GetCustomersByName());
        Assert.assertEquals(1, response.getReturn().size());
        Customer firstCustomer = response.getReturn().get(0);
        Assert.assertEquals(100000.0, firstCustomer.getRevenue());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            String jaxbPackage = GetCustomersByName.class.getPackage()
                    .getName();

            @Override
            public void configure() throws Exception {
                ElementNameStrategy elNameStrat = new TypeNameStrategy();
                SoapJaxbDataFormat soapDataFormat = new SoapJaxbDataFormat(
                        jaxbPackage, elNameStrat);
                final InputStream in = this.getClass().getResourceAsStream(
                        "response.xml");
                from("direct:start").marshal(soapDataFormat).to("mock:result")
                        .process(new FileReplyProcessor(in)).unmarshal(
                                soapDataFormat);
            }
        };
    }

}
