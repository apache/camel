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
package org.apache.camel.example.client;

import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Client that uses the {@link org.apache.camel.ProducerTemplate} to easily exchange messages with the Server.
 * <p/>
 * Requires that the JMS broker is running, as well as CamelServer
 */
public final class CamelClientStop {
    private CamelClientStop() {
        // Helper class
    }

    // START SNIPPET: e1
    public static void main(final String[] args) throws Exception {
        System.out.println("Notice this client requires that the CamelServer is already running!");

        ApplicationContext context = new ClassPathXmlApplicationContext("camel-client.xml");

        // get the camel template for Spring template style sending of messages (= producer)
        ProducerTemplate camelTemplate = (ProducerTemplate) context.getBean("camelTemplate");

        System.out.println("Invoking with STOP command");
        camelTemplate.sendBody("activemq:queue:stop", ExchangePattern.InOnly, "STOP");
    }
    // END SNIPPET: e1

}