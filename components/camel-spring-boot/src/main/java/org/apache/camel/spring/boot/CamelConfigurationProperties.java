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
     * Sets the name of the CamelContext.
     */
    private String name;

    /**
     * Enable JMX in your Camel application.
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
     * Directory to scan for adding additional XML routes.
     * You can turn this off by setting the value to false.
     */
    private String xmlRoutes = "classpath:camel/*.xml";

    /**
     * Directory to scan for adding additional XML rests.
     * You can turn this off by setting the value to false.
     */
    private String xmlRests = "classpath:camel-rest/*.xml";

    /**
     * Whether to use the main run controller to ensure the Spring-Boot application
     * keeps running until being stopped or the JVM terminated.
     * You typically only need this if you run Spring-Boot standalone.
     * If you run Spring-Boot with spring-boot-starter-web then the web container keeps the JVM running.
     */
    private boolean mainRunController;

    /**
     * Is used to limit the maximum length of the logging Camel message bodies. If the message body
     * is longer than the limit, the log message is clipped. Use a value of 0 or negative to have unlimited length.
     * Use for example 1000 to log at at most 1000 chars.
     */
    private int logDebugMaxChars;

    // Getters & setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

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

    public String getXmlRoutes() {
        return xmlRoutes;
    }

    public void setXmlRoutes(String xmlRoutes) {
        this.xmlRoutes = xmlRoutes;
    }

    public String getXmlRests() {
        return xmlRests;
    }

    public void setXmlRests(String xmlRests) {
        this.xmlRests = xmlRests;
    }

    public boolean isMainRunController() {
        return mainRunController;
    }

    public void setMainRunController(boolean mainRunController) {
        this.mainRunController = mainRunController;
    }

    public int getLogDebugMaxChars() {
        return logDebugMaxChars;
    }

    public void setLogDebugMaxChars(int logDebugMaxChars) {
        this.logDebugMaxChars = logDebugMaxChars;
    }
}
