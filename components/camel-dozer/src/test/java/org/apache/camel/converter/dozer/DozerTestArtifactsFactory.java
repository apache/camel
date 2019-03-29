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
import org.apache.camel.CamelContext;
import org.apache.camel.converter.dozer.dto.AddressDTO;
import org.apache.camel.converter.dozer.dto.CustomerDTO;
import org.apache.camel.converter.dozer.model.Address;
import org.apache.camel.converter.dozer.service.Customer;

public final class DozerTestArtifactsFactory {

    private DozerTestArtifactsFactory() {
    }

    public static Customer createServiceCustomer() {
        return new Customer("Bob", "Roberts", "12345", "1 main st");
    }

    public static org.apache.camel.converter.dozer.model.Customer createModelCustomer() {
        return new org.apache.camel.converter.dozer.model.Customer("Bob", "Roberts", new Address("12345", "1 main st"));
    }

    public static CustomerDTO createDtoCustomer() {
        return new CustomerDTO("Bob", "Roberts", new AddressDTO("12345", "1 main st"));
    }

    public static Mapper createMapper(CamelContext camelContext) {
        DozerBeanMapperConfiguration config = new DozerBeanMapperConfiguration();
        config.setMappingFiles(Arrays.asList("mapping.xml"));

        MapperFactory factory = new MapperFactory(camelContext, config);
        return factory.create();
    }

    public static Mapper createCleanMapper(CamelContext camelContext) {
        MapperFactory factory = new MapperFactory(camelContext, null);
        return factory.create();
    }

}
