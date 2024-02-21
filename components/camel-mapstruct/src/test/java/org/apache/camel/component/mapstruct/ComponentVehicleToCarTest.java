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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ComponentVehicleToCarTest extends CamelTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        MapstructComponent mc = context.getComponent("mapstruct", MapstructComponent.class);
        mc.setMapperPackageName("org.apache.camel.component.mapstruct.mapper");

        return context;
    }

    @Test
    public void testComponent() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        VehicleDto v = new VehicleDto();
        v.setCompany("Volvo");
        v.setName("XC40");
        v.setYear(2021);
        v.setPower("true");

        CarDto car = template.requestBody("direct:component", v, CarDto.class);
        Assertions.assertNotNull(car);

        Assertions.assertEquals("Volvo", car.getBrand());
        Assertions.assertEquals("XC40", car.getModel());
        Assertions.assertEquals(2021, car.getYear());
        Assertions.assertEquals(true, car.isElectric());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testConverter() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(1);

        VehicleDto v = new VehicleDto();
        v.setCompany("Volvo");
        v.setName("XC60");
        v.setYear(2018);
        v.setPower("false");

        CarDto car = template.requestBody("direct:convert", v, CarDto.class);
        Assertions.assertNotNull(car);

        Assertions.assertEquals("Volvo", car.getBrand());
        Assertions.assertEquals("XC60", car.getModel());
        Assertions.assertEquals(2018, car.getYear());
        Assertions.assertEquals(false, car.isElectric());

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:component")
                        .to("mapstruct:" + CarDto.class.getName())
                        .to("mock:result");

                from("direct:convert")
                        .convertBodyTo(CarDto.class)
                        .to("mock:result");
            }
        };
    }
}
