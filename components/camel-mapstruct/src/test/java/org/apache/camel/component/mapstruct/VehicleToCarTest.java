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

import org.apache.camel.component.mapstruct.dto.CarDto;
import org.apache.camel.component.mapstruct.dto.VehicleDto;
import org.apache.camel.component.mapstruct.mapper.CarMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

public class VehicleToCarTest {

    @Test
    public void testMapstruct() throws Exception {
        CarMapper mapper = Mappers.getMapper(CarMapper.class);

        VehicleDto v = new VehicleDto();
        v.setCompany("Volvo");
        v.setName("XC40");
        v.setYear(2021);
        v.setPower("true");
        CarDto car = mapper.toCar(v);

        Assertions.assertEquals("Volvo", car.getBrand());
        Assertions.assertEquals("XC40", car.getModel());
        Assertions.assertEquals(2021, car.getYear());
        Assertions.assertEquals(true, car.isElectric());
    }
}
