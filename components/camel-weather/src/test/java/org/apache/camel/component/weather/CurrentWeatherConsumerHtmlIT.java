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
package org.apache.camel.component.weather;

import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.apache.camel.test.junit5.TestSupport.assertStringContains;

@EnabledIfSystemProperty(named = "enable.weather.tests", matches = "true",
                         disabledReason = "Disabled to avoid hitting API limits")
public class CurrentWeatherConsumerHtmlIT extends BaseWeatherConsumerIT {

    @Override
    protected void checkWeatherContent(String weather) {
        log.debug("The weather in {} format is {}{}", WeatherMode.HTML, LS, weather);

        assertStringContains(weather, "<!DOCTYPE html>");
        assertStringContains(weather, "<head>");
        assertStringContains(weather, "<body>");
        assertStringContains(weather,
                "<meta name=\"description\" content=\"A layer with current weather conditions in cities for world wide\" />");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("weather:foo?mode=HTML&appid=9162755b2efa555823cfe0451d7fff38&geolocationAccessKey=test&geolocationRequestHostIP=test&location=Rome")
                        .to("mock:result");
            }
        };
    }

}
