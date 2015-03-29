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
package org.apache.camel.component.raspberry;

/**
 * Constants Class
 * 
 * @author gautric
 */
public final class RaspberryConstants {
    public static final String TYPE_ENDPOINT_PIN = "pin";
    public static final String TYPE_ENDPOINT_I2C = "i2c";
    public static final String TYPE_ENDPOINT_SERIAL = "serial";
    public static final String TYPE_ENDPOINT_SPI = "spi";
    public static final String LOG_COMPONENT = "log:org.apache.camel.component.raspberry?showAll=true&multiline=true";
    public static final String CAMEL_ID_ROUTE = "raspberry-pi";
    public static final String PROVIDER_NAME = "RaspberryPi GPIO Provider Mock";
    public static final String CAMEL_PI4J = "CamelPi4j";
    public static final String CAMEL_PI4J_PIN = CAMEL_PI4J + ".pin";
    public static final String CAMEL_PI4J_PIN_STATE = CAMEL_PI4J + ".pinState";
    public static final String CAMEL_PI4J_PIN_TYPE = CAMEL_PI4J + ".pinType";
    public static final String CAMEL_PI4J_PIN_VALUE = CAMEL_PI4J + ".pinValue";
    public static final String PI4J_PIN = "pi4j_pin";
    public static final String PI4J_STATE = "pi4j_state";
    public static final String PI4J_TYPE = "pi4j_type";
    public static final String PI4J_VALUE = "pi4j_value";

    private RaspberryConstants() {
        // Constants class
    }
}
