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

import com.github.dozermapper.core.Mapper;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.dozer.service.Customer;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.converter.dozer.DozerTestArtifactsFactory.createServiceCustomer;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class DozerTypeConverterTest extends CamelTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        DozerBeanMapperConfiguration config = new DozerBeanMapperConfiguration();
        config.setMappingFiles(Arrays.asList("mapping.xml"));

        try (DozerTypeConverterLoader dtcl = new DozerTypeConverterLoader(context, config)) {
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:service-in").bean(new CustomerProcessor()).to("mock:verify-model");
            }
        };
    }

    @Test
    void verifyCamelConversionViaDozer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:verify-model");
        mock.expectedMessageCount(1);

        template.sendBody("direct:service-in", createServiceCustomer());

        assertMockEndpointsSatisfied();
    }

    @Test
    void verifyCustomerMapping() {
        Mapper mapper = DozerTestArtifactsFactory.createMapper(context);
        Customer service = createServiceCustomer();
        org.apache.camel.converter.dozer.model.Customer model
                = mapper.map(service, org.apache.camel.converter.dozer.model.Customer.class);
        Customer roundTrip = mapper.map(model, Customer.class);
        assertEquals(service, roundTrip);
    }

}
