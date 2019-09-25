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

import org.apache.camel.example.server.Multiplier;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Client that uses Camel Spring Remoting for very easy integration with the server.
 * <p/>
 * Requires that the JMS broker is running, as well as CamelServer
 */
public final class CamelClientRemoting {

    private static final Logger LOG = LoggerFactory.getLogger(CamelClientRemoting.class);

    private CamelClientRemoting() {
        //Helper class
    }

    // START SNIPPET: e1
    public static void main(final String[] args) {
        LOG.info("Notice this client requires that the CamelServer is already running!");

        AbstractApplicationContext context = new ClassPathXmlApplicationContext("camel-client-remoting.xml");
        // just get the proxy to the service and we as the client can use the "proxy" as it was
        // a local object we are invoking. Camel will under the covers do the remote communication
        // to the remote ActiveMQ server and fetch the response.
        Multiplier multiplier = context.getBean("multiplierProxy", Multiplier.class);

        LOG.info("Invoking the multiply with 33");

        int response = multiplier.multiply(33);

        LOG.info("... the result is: {}", response);

        // we're done so let's properly close the application context
        IOHelper.close(context);
    }
    // END SNIPPET: e1

}
