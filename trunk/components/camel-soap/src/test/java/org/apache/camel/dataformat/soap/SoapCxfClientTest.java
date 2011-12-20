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

import java.util.List;

import javax.annotation.Resource;

import com.example.customerservice.Customer;
import com.example.customerservice.CustomerService;
import com.example.customerservice.GetAllCustomersResponse;
import com.example.customerservice.GetCustomersByName;
import com.example.customerservice.GetCustomersByNameResponse;
import com.example.customerservice.NoSuchCustomer;
import com.example.customerservice.NoSuchCustomerException;
import com.example.customerservice.SaveCustomer;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.dataformat.soap.name.ElementNameStrategy;
import org.apache.camel.dataformat.soap.name.ServiceInterfaceStrategy;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Checks for interoperability between a CXF client that is attached using the
 * Camel transport for CXF and the SOAP data format
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class SoapCxfClientTest extends RouteBuilder {
    private static CustomerServiceImpl serverBean;
    
    @Resource(name = "customerServiceCxfProxy")
    protected CustomerService customerService;


    @BeforeClass
    public static void initServerBean() {
        serverBean = new CustomerServiceImpl();
    }

    @Test
    public void testSuccess() throws NoSuchCustomerException {
        GetCustomersByName request = new GetCustomersByName();
        request.setName("test");
        GetCustomersByNameResponse response = customerService.getCustomersByName(request);
        Assert.assertNotNull(response);
        List<Customer> customers = response.getReturn();
        Assert.assertEquals(1, customers.size());
        Assert.assertEquals("test", customers.get(0).getName());
    }

    @Test
    public void testRoundTripGetAllCustomers() throws Exception {
        GetAllCustomersResponse response = customerService.getAllCustomers();
        Assert.assertEquals(1, response.getReturn().size());
        Customer firstCustomer = response.getReturn().get(0);
        Assert.assertEquals(100000.0, firstCustomer.getRevenue(), 0.00001);
    }

    @Test
    public void testRoundTripSaveCustomer() throws Exception {
        Customer testCustomer = new Customer();
        testCustomer.setName("testName");
        SaveCustomer request = new SaveCustomer();
        request.setCustomer(testCustomer);
        customerService.saveCustomer(request);
        Customer customer2 = serverBean.getLastSavedCustomer();
        Assert.assertEquals("testName", customer2.getName());
    }

    @Test
    public void testFault() {
        GetCustomersByName request = new GetCustomersByName();
        request.setName("none");
        try {
            customerService.getCustomersByName(request);
            Assert.fail("NoSuchCustomerException expected");
        } catch (NoSuchCustomerException e) {
            NoSuchCustomer info = e.getFaultInfo();
            Assert.assertEquals("none", info.getCustomerId());
        }

    }

    public void configure() throws Exception {
        String jaxbPackage = GetCustomersByName.class.getPackage().getName();
        ElementNameStrategy elNameStrat = new ServiceInterfaceStrategy(CustomerService.class, false);
        SoapJaxbDataFormat soapDataFormat = new SoapJaxbDataFormat(jaxbPackage, elNameStrat);
        getContext().setTracing(true);
        from("direct:cxfclient") //
                .onException(Exception.class).handled(true).marshal(soapDataFormat).end() //
                .unmarshal(soapDataFormat) //
                .bean(serverBean) //
                .marshal(soapDataFormat);
    }

}
