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

        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").convertBodyTo(HashMap.class);
            }
        });

        DozerBeanMapperConfiguration mconfig = new DozerBeanMapperConfiguration();
        mconfig.setMappingFiles(Arrays.asList("bean-to-map-dozer-mappings.xml"));
        new DozerTypeConverterLoader(context, mconfig);

        context.start();
        try {
            ProducerTemplate producer = context.createProducerTemplate();
            Map<?, ?> result = producer.requestBody("direct:start", new Customer("John", "Doe", null), Map.class);
            Assert.assertEquals("John", result.get("firstName"));
            Assert.assertEquals("Doe", result.get("lastName"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testMarshalToInterfaceViaDozer() throws Exception {

        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").convertBodyTo(Map.class);
            }
        });

        DozerBeanMapperConfiguration mconfig = new DozerBeanMapperConfiguration();
        mconfig.setMappingFiles(Arrays.asList("bean-to-map-dozer-mappings.xml"));
        new DozerTypeConverterLoader(context, mconfig);

        context.start();
        try {
            ProducerTemplate producer = context.createProducerTemplate();
            Map<?, ?> result = producer.requestBody("direct:start", new Customer("John", "Doe", null), Map.class);
            Assert.assertEquals("John", result.get("firstName"));
            Assert.assertEquals("Doe", result.get("lastName"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testBeanMapping() throws Exception {

        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").convertBodyTo(CustomerB.class);
            }
        });

        DozerBeanMapperConfiguration mconfig = new DozerBeanMapperConfiguration();
        mconfig.setMappingFiles(Arrays.asList("bean-to-bean-dozer-mappings.xml"));
        new DozerTypeConverterLoader(context, mconfig);

        CustomerA customerA = new CustomerA("Peter", "Post", "SomeStreet", "12345");

        context.start();
        try {
            ProducerTemplate producer = context.createProducerTemplate();
            CustomerB result = producer.requestBody("direct:start", customerA, CustomerB.class);
            Assert.assertEquals(customerA.getFirstName(), result.getFirstName());
            Assert.assertEquals(customerA.getLastName(), result.getLastName());
            Assert.assertEquals(customerA.getStreet(), result.getAddress().getStreet());
            Assert.assertEquals(customerA.getZip(), result.getAddress().getZip());
        } finally {
            context.stop();
        }
    }
}
