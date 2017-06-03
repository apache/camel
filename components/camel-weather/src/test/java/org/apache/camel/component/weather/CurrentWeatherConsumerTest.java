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
package org.apache.camel.component.weather;

import org.apache.camel.builder.RouteBuilder;

public class CurrentWeatherConsumerTest extends BaseWeatherConsumerTest {

    @Override
    protected void checkWeatherContent(String weather) {
        log.debug("The weather in {} format is {}{}", WeatherMode.XML, LS, weather);

        //assertStringContains(weather, "<?xml version=\"1.0\" encoding=\"utf-8\"?>");
        assertStringContains(weather, "<coord");
        assertStringContains(weather, "<temperature");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("weather:foo?appid=9162755b2efa555823cfe0451d7fff38&lon=4&lat=52&mode=xml").
                        to("mock:result");
            }
        };
    }

}
