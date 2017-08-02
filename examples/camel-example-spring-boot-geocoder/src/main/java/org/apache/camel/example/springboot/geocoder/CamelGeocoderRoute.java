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
package org.apache.camel.example.springboot.geocoder;

import com.google.code.geocoder.model.GeocodeResponse;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.springframework.stereotype.Component;

import static org.apache.camel.model.rest.RestParamType.query;

/**
 * A simple Camel REST DSL route example using the Geocoder component and documented with Swagger
 */
@Component
public class CamelGeocoderRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // rest-dsl is also configured in the application.properties file

        rest("/geocoder").description("Geocoder REST service")
            .consumes("application/json")
            .produces("application/json")

            .get().description("Geocoder address lookup").outType(GeocodeResponse.class)
                .param().name("address").type(query).description("The address to lookup").dataType("string").endParam()
                .responseMessage().code(200).message("Geocoder successful").endResponseMessage()
                // call the geocoder to lookup details from the provided address
                .toD("geocoder:address:${header.address}");
    }

}
