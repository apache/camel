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
package org.apache.camel.component.bean.validator;

import javax.validation.GroupSequence;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@GroupSequence({CarWithRedefinedDefaultGroup.class, OptionalChecks.class })
public class CarWithRedefinedDefaultGroup implements Car {

    @NotNull(groups = RequiredChecks.class)
    private String manufacturer;

    @NotNull(groups = RequiredChecks.class)
    @Size(min = 5, max = 14, groups = OptionalChecks.class)
    private String licensePlate;
    
    public CarWithRedefinedDefaultGroup(String manufacturer, String licencePlate) {
        this.manufacturer = manufacturer;
        this.licensePlate = licencePlate;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getLicensePlate() {
        return licensePlate;
    }

    public void setLicensePlate(String licensePlate) {
        this.licensePlate = licensePlate;
    }
}