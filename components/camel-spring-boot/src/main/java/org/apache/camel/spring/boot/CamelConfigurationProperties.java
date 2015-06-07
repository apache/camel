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
package org.apache.camel.spring.boot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "camel.springboot")
public class CamelConfigurationProperties {

    // Properties

    /**
     * Enable JMX support for the CamelContext.
     */
    private boolean jmxEnabled = true;

    /**
     * Producer template endpoints cache size.
     */
    private int producerTemplateCacheSize = 1000;

    /**
     * Consumer template endpoints cache size.
     */
    private int consumerTemplateCacheSize = 1000;

    /**
     * Enables enhanced Camel/Spring type conversion.
     */
    private boolean typeConversion = true;

    /**
     * Sets the name of the this CamelContext.
     */
    private String name;

    // Getters & setters

    public boolean isJmxEnabled() {
        return jmxEnabled;
    }

    public void setJmxEnabled(boolean jmxEnabled) {
        this.jmxEnabled = jmxEnabled;
    }

    public int getProducerTemplateCacheSize() {
        return producerTemplateCacheSize;
    }

    public void setProducerTemplateCacheSize(int producerTemplateCacheSize) {
        this.producerTemplateCacheSize = producerTemplateCacheSize;
    }

    public int getConsumerTemplateCacheSize() {
        return consumerTemplateCacheSize;
    }

    public void setConsumerTemplateCacheSize(int consumerTemplateCacheSize) {
        this.consumerTemplateCacheSize = consumerTemplateCacheSize;
    }

    public boolean isTypeConversion() {
        return typeConversion;
    }

    public void setTypeConversion(boolean typeConversion) {
        this.typeConversion = typeConversion;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
