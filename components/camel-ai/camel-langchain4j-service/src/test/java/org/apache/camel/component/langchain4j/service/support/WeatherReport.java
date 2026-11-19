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
package org.apache.camel.component.langchain4j.service.support;

/**
 * Structured output fixture: an AI Service method returning this type makes LangChain4j append its own JSON format
 * instruction to the prompt and parse the answer back into an object.
 * <p>
 * A plain class with a no-argument constructor rather than a record, both because the Camel codebase does not use
 * records and because the output parser constructs it reflectively.
 */
public class WeatherReport {

    private String location;
    private int temperatureCelsius;
    private String summary;

    public WeatherReport() {
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public void setTemperatureCelsius(int temperatureCelsius) {
        this.temperatureCelsius = temperatureCelsius;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    @Override
    public String toString() {
        return "WeatherReport[location=" + location + ", temperatureCelsius=" + temperatureCelsius
               + ", summary=" + summary + "]";
    }
}
