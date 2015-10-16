/*
 * #%L
 * Wildfly Camel :: Testsuite
 * %%
 * Copyright (C) 2013 - 2014 RedHat
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

package org.apache.camel.converter.dozer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.dozer.model.Customer;
import org.apache.camel.converter.dozer.model.CustomerA;
import org.apache.camel.converter.dozer.model.CustomerB;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Test;

public class DozerBeanMappingTest {

    @Test
    public void testMarshalViaDozer() throws Exception {

        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").convertBodyTo(HashMap.class);
            }
        });

        DozerBeanMapperConfiguration mconfig = new DozerBeanMapperConfiguration();
        mconfig.setMappingFiles(Arrays.asList(new String[] { "bean-to-map-dozer-mappings.xml" }));
        new DozerTypeConverterLoader(camelctx, mconfig);

        camelctx.start();
        try {
            ProducerTemplate producer = camelctx.createProducerTemplate();
            Map<?, ?> result = producer.requestBody("direct:start", new Customer("John", "Doe", null), Map.class);
            Assert.assertEquals("John", result.get("firstName"));
            Assert.assertEquals("Doe", result.get("lastName"));
        } finally {
            camelctx.stop();
        }
    }

    @Test
    public void testBeanMapping() throws Exception {

        CamelContext camelctx = new DefaultCamelContext();
        camelctx.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").convertBodyTo(CustomerB.class);
            }
        });

        DozerBeanMapperConfiguration mconfig = new DozerBeanMapperConfiguration();
        mconfig.setMappingFiles(Arrays.asList(new String[] { "bean-to-bean-dozer-mappings.xml" }));
        new DozerTypeConverterLoader(camelctx, mconfig);

        CustomerA customerA = new CustomerA("Peter", "Post", "SomeStreet", "12345");

        camelctx.start();
        try {
            ProducerTemplate producer = camelctx.createProducerTemplate();
            CustomerB result = producer.requestBody("direct:start", customerA, CustomerB.class);
            Assert.assertEquals(customerA.getFirstName(), result.getFirstName());
            Assert.assertEquals(customerA.getLastName(), result.getLastName());
            Assert.assertEquals(customerA.getStreet(), result.getAddress().getStreet());
            Assert.assertEquals(customerA.getZip(), result.getAddress().getZip());
        } finally {
            camelctx.stop();
        }
    }
}
