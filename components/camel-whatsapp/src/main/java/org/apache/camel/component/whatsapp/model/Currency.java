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
package org.apache.camel.component.whatsapp.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Currency {

    @JsonProperty("fallback_value")
    private String fallbackValue;
    /**
     * Currency code as defined in ISO 4217.
     */
    private String code;
    /**
     * Amount multiplied by 1000.
     */
    @JsonProperty("amount_1000")
    private String amount1000;

    public Currency() {
    }

    public String getFallbackValue() {
        return fallbackValue;
    }

    public void setFallbackValue(String fallbackValue) {
        this.fallbackValue = fallbackValue;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getAmount1000() {
        return amount1000;
    }

    public void setAmount1000(String amount1000) {
        this.amount1000 = amount1000;
    }

}
