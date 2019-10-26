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
package org.apache.camel.example;

/**
 * Class to configure the Camel application.
 */
public class MyConfiguration {

    /**
     * Creates the Artemis JMS ConnectionFactory and bind it to the Camel registry
     * so we can do autowiring on the Camel JMS component.
     * See more details in the application.properties file.
     * <p/>
     * Note: This autowiring is disabled in this example as we use camel-main-maven-plugin
     * to do classpath scanning to detect the Artemis JMS Client and automatic create a autowire.properties
     * file with some binding details, and then provide additional configuraions in the application.properties file.
     */
//    @BindToRegistry
//    public ConnectionFactory myArtemisClient(@PropertyInject("artemisBroker") String brokerUrl) {
//        ActiveMQConnectionFactory cf = new ActiveMQJMSConnectionFactory(brokerUrl);
//        cf.setUser("admin");
//        cf.setPassword("admin");
//        return cf;
//    }

    public void configure() {
        // this method is optional and can be removed if no additional configuration is needed.
    }

}
