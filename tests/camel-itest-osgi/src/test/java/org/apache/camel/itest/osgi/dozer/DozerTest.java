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
package org.apache.camel.itest.osgi.dozer;

import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.itest.osgi.OSGiIntegrationSpringTestSupport;
import org.apache.camel.itest.osgi.dozer.service.Customer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.springframework.osgi.context.support.OsgiBundleXmlApplicationContext;

import static org.ops4j.pax.exam.OptionUtils.combine;


/**
 * @version 
 */
@RunWith(PaxExam.class)
public class DozerTest extends OSGiIntegrationSpringTestSupport {

    @Override
    protected OsgiBundleXmlApplicationContext createApplicationContext() {
        return new OsgiBundleXmlApplicationContext(new String[]{"org/apache/camel/itest/osgi/dozer/CamelContext.xml"});
    }

    @Test
    public void testDozer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        // should be the model customer
        mock.message(0).body().isInstanceOf(org.apache.camel.itest.osgi.dozer.model.Customer.class);
        // and assert the model contains the expected data
        mock.message(0).simple("${body.firstName} == Homer");
        mock.message(0).simple("${body.lastName} == Simpson");
        mock.message(0).simple("${body.address.streetMame} == 'Somestreet 123'");
        mock.message(0).simple("${body.address.zipCode} == 26000");

        Customer serviceCustomer = new Customer();
        serviceCustomer.setFirstName("Homer");
        serviceCustomer.setLastName("Simpson");
        serviceCustomer.setStreet("Somestreet 123");
        serviceCustomer.setZip("26000");

        template.sendBody("direct:start", serviceCustomer);

        assertMockEndpointsSatisfied();
    }
    
    @Configuration
    public static Option[] configure() {
        Option[] options = combine(
            getDefaultCamelKarafOptions(),
            // using the features to install the other camel components             
            loadCamelFeatures("camel-dozer"));
        
        return options;
    }

}
