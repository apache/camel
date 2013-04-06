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
package org.apache.camel.example.gae;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReportGenerator implements Processor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(RequestProcessor.class);

    public void process(Exchange exchange) throws Exception {
        ReportData data = exchange.getIn().getBody(ReportData.class);
        
        XPathFactory xpfactory = XPathFactory.newInstance();
        XPath xpath = xpfactory.newXPath();

        // Extract result values via XPath
        String city = xpath.evaluate("//forecast_information/city/@data", data.getWeather());
        String cond = xpath.evaluate("//current_conditions/condition/@data", data.getWeather());
        String temp = xpath.evaluate("//current_conditions/temp_c/@data", data.getWeather());
        
        if (city == null || city.length() == 0) {
            city = data.getCity();
            cond = "<error retrieving current condition>";
            temp = "<error retrieving current temperature>";
        }
        
        String result = new StringBuilder()
            .append("\n").append("Weather report for:  ").append(city)
            .append("\n").append("Current condition:   ").append(cond)
            .append("\n").append("Current temperature: ").append(temp).append(" (Celsius)").toString();
        
        LOGGER.info("Get the result" + result);
        exchange.getIn().setBody(result);
    }

}
