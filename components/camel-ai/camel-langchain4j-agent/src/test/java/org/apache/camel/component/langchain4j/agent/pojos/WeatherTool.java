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

package org.apache.camel.component.langchain4j.agent.pojos;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Weather LangChain4j tools
 */
public class WeatherTool {

    private static final String WEATHER_INFO = "sunny";
    private static final String WEATHER_TEMP = "22";

    @Tool("Gets weather information for a city")
    public String getWeather(@P("City name") String city) {
        return "Weather in " + city + ": " + WEATHER_INFO + ", " + WEATHER_TEMP + "°C";
    }

    @Tool("Gets temperature for a city")
    public int getTemperature(@P("City name") String city) {
        return Integer.parseInt(WEATHER_TEMP);
    }
}
