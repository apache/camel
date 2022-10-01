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

import com.github.dozermapper.core.loader.api.BeanMappingBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.dozer.dto.CustomerDTO;
import org.apache.camel.converter.dozer.model.Customer;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.camel.converter.dozer.DozerTestArtifactsFactory.createDtoCustomer;

public class DozerTypeConverterDTOTest extends CamelTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        BeanMappingBuilder beanMappingBuilder = new BeanMappingBuilder() {
            @Override
            protected void configure() {
                mapping(CustomerDTO.class, Customer.class);
            }
        };

        DozerBeanMapperConfiguration config = new DozerBeanMapperConfiguration();
        config.setBeanMappingBuilders(Arrays.asList(beanMappingBuilder));

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

        template.sendBody("direct:service-in", createDtoCustomer());

        MockEndpoint.assertIsSatisfied(context);
    }

}
