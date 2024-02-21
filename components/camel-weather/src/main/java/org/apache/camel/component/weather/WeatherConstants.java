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

import org.apache.camel.spi.Metadata;

/**
 * The Weather constants
 */
public final class WeatherConstants {

    @Metadata(label = "producer", description = "Used by the producer to override the endpoint location and use the\n" +
                                                "location from this header instead.",
              javaType = "String")
    public static final String WEATHER_LOCATION = "CamelWeatherLocation";
    @Metadata(description = "The original query URL sent to the Open Weather Map site", javaType = "String")
    public static final String WEATHER_QUERY = "CamelWeatherQuery";

    private WeatherConstants() {
    }

}
