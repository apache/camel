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
package org.apache.camel.example.client;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Client that uses the {@link ProducerTemplate} to easily exchange messages with the Server.
 * <p/>
 * Requires that the JMS broker is running, as well as CamelServer
 */
public final class CamelClient {

    private static final Logger LOG = LoggerFactory.getLogger(CamelClient.class);

    private CamelClient() {
        // Helper class
    }

    // START SNIPPET: e1
    public static void main(final String[] args) throws Exception {
        LOG.info("Notice this client requires that the CamelServer is already running!");

        AbstractApplicationContext context = new ClassPathXmlApplicationContext("camel-client.xml");

        // get the camel template for Spring template style sending of messages (= producer)
        ProducerTemplate camelTemplate = context.getBean("camelTemplate", ProducerTemplate.class);

        LOG.info("Invoking the multiply with 22");

        // as opposed to the CamelClientRemoting example we need to define the service URI in this java code
        int response = camelTemplate.requestBody("jms:queue:numbers", 22, int.class);

        LOG.info("... the result is: {}", response);

        // we're done so let's properly close the application context
        IOHelper.close(context);
    }
    // END SNIPPET: e1

}
