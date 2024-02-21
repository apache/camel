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
package org.apache.camel.component.mapstruct;

import org.apache.camel.CamelContext;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mapstruct.dto.CarDto;
import org.apache.camel.component.mapstruct.dto.VehicleDto;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class CarToVehicleTest extends CamelTestSupport {

    @ParameterizedTest()
    @ValueSource(strings = { "component", "converter" })
    void testCarToVehicleMapping(String endpoint) throws Exception {
        getMockEndpoint("mock:result-" + endpoint).expectedMessageCount(1);

        CarDto c = new CarDto();
        c.setBrand("Volvo");
        c.setModel("XC40");
        c.setYear(2021);
        c.setElectric(true);

        VehicleDto vehicle = template.requestBody("direct:" + endpoint, c, VehicleDto.class);
        assertNotNull(vehicle);

        assertEquals("Volvo", vehicle.getCompany());
        assertEquals("XC40", vehicle.getName());
        assertEquals(2021, vehicle.getYear());
        assertEquals("true", vehicle.getPower());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        MapStructMapperFinder converter = new DefaultMapStructFinder();
        converter.setMapperPackageName("org.apache.camel.component.mapstruct.mapper");

        CamelContext context = super.createCamelContext();
        context.addService(converter);
        return context;
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:component")
                        .toF("mapstruct:%s", VehicleDto.class.getName())
                        .to("mock:result-component");

                from("direct:converter")
                        .convertBodyTo(VehicleDto.class)
                        .to("mock:result-converter");
            }
        };
    }
}
