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

import java.io.Serializable;

import org.w3c.dom.Document;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;

public class ReportData implements Serializable {

    private static final long serialVersionUID = -468314239950430108L;

    private String city;
    private String recipient;
    private String requestor;
    private Document weather;
    
    public ReportData(String city, String recipient, String requestor) {
        this.city = city;
        this.recipient = recipient;
        this.requestor = requestor;
    }
    
    public String getCity() {
        return city;
    }
    
    public String getRecipient() {
        return recipient;
    }
    
    public String getRequestor() {
        return requestor;
    }
 
    public Document getWeather() {
        return weather;
    }
    
    public void setWeather(Document weather) {
        this.weather = weather;
    }
    
    public static Expression city() {
        return new Expression() {
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                return type.cast(exchange.getIn().getBody(ReportData.class).getCity());
            }
        };
    }

    public static Expression recipient() {
        return new Expression() {
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                return type.cast(exchange.getIn().getBody(ReportData.class).getRecipient());
            }
        };
    }

    public static Expression requestor() {
        return new Expression() {
            public <T> T evaluate(Exchange exchange, Class<T> type) {
                return type.cast(exchange.getIn().getBody(ReportData.class).getRequestor());
            }
        };
    }

}
