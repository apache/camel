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
package org.apache.camel.dataformat.soap12;

import java.io.IOException;
import java.io.InputStream;

import com.example.customerservice.GetCustomersByName;
import com.example.customerservice.NoSuchCustomer;
import com.example.customerservice.NoSuchCustomerException;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.soap.SoapJaxbDataFormat;
import org.apache.camel.dataformat.soap.TestUtil;
import org.apache.camel.dataformat.soap.name.ElementNameStrategy;
import org.apache.camel.dataformat.soap.name.TypeNameStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class Soap12MarshalTest extends CamelTestSupport {
    @EndpointInject("mock:result")
    protected MockEndpoint resultEndpoint;

    @Produce("direct:start")
    protected ProducerTemplate producer;

    /**
     * Test Soap marshalling by sending a GetCustomerByName object and checking
     * against a xml file.
     *
     * @throws java.io.IOException
     * @throws InterruptedException
     */
    @Test
    public void testMarshalNormalObject() throws IOException, InterruptedException {
        InputStream in = this.getClass().getResourceAsStream("SoapMarshalTestExpectedResult.xml");
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.expectedBodiesReceived(TestUtil.readStream(in));
        GetCustomersByName request = new GetCustomersByName();
        request.setName("Smith");
        producer.sendBody(request);
        resultEndpoint.assertIsSatisfied();
    }

    /**
     * Test Soap marshalling by sending a NoSuchCustomerException object and
     * checking against a xml file. We expect to receive a SOAP fault here that
     * contains a NoSuchCustomer object as detail.
     *
     * @throws java.io.IOException
     * @throws InterruptedException
     */
    @Test
    public void testMarshalException() throws IOException, InterruptedException {
        resultEndpoint.expectedMessageCount(1);
        resultEndpoint.message(0).body(String.class).contains("<ns2:Envelope xmlns:ns2=\"http://www.w3.org/2003/05/soap-envelope\" xmlns:ns3=\"http://customerservice.example.com/\">");
        resultEndpoint.message(0).body(String.class).contains("<ns2:Fault>");
        resultEndpoint.message(0).body(String.class).contains("<ns2:Value>ns2:Receiver</ns2:Value>");
        resultEndpoint.message(0).body(String.class).contains("<ns2:Text xml:lang=\"en\">No customer found</ns2:Text>");
        resultEndpoint.message(0).body(String.class).contains("<customerId>None</customerId>");
        resultEndpoint.message(0).body(String.class).contains("</ns2:Fault>");
        NoSuchCustomer noSuchCustomer = new NoSuchCustomer();
        noSuchCustomer.setCustomerId("None");
        NoSuchCustomerException exception = new NoSuchCustomerException("No customer found", noSuchCustomer);
        producer.sendBodyAndHeader(null, Exchange.EXCEPTION_CAUGHT, exception);
        resultEndpoint.assertIsSatisfied();
    }

    /**
     * Create data format by using the constructor
     */
    protected SoapJaxbDataFormat createDataFormat() {
        String jaxbPackage = GetCustomersByName.class.getPackage().getName();
        ElementNameStrategy elStrat = new TypeNameStrategy();
        SoapJaxbDataFormat answer = new SoapJaxbDataFormat(jaxbPackage, elStrat);
        answer.setVersion("1.2");
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                SoapJaxbDataFormat df = createDataFormat();
                from("direct:start") //
                    .marshal(df) //
                    .to("mock:result");
            }
        };
    }

}
