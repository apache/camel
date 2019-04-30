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
package org.apache.camel.example.pulsar.client;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.apache.camel.util.IOHelper;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Client that uses the <a href="http://camel.apache.org/message-endpoint.html">Message Endpoint</a>
 * pattern to easily exchange messages with the Server.
 * <p/>
 * Notice this very same API can use for all components in Camel, so if we were using TCP communication instead
 * of Pulsar messaging we could just use <code>camel.getEndpoint("mina:tcp://someserver:port")</code>.
 * <p/>
 * Requires that the Pulsar broker is running, as well as CamelServer
 */
public final class CamelClientEndpoint {

    private CamelClientEndpoint() {
        //Helper class
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("Notice this client requires that the CamelServer is already running!");

        AbstractApplicationContext context = new ClassPathXmlApplicationContext("camel-client.xml");
        CamelContext camel = context.getBean("camel-client", CamelContext.class);

        // get the endpoint from the camel context
        Endpoint endpoint = camel.getEndpoint("pulsar:tn1/ns1/cameltest");

        // create the exchange used for the communication
        // we use the in out pattern for a synchronized exchange where we expect a response
        Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
        // set the input on the in body
        // must be correct type to match the expected type of an Integer object
        exchange.getIn().setBody(11);

        // to send the exchange we need an producer to do it for us
        Producer producer = endpoint.createProducer();
        // start the producer so it can operate
        producer.start();

        // let the producer process the exchange where it does all the work in this oneline of code
        System.out.println("Invoking the multiply with 11");
        producer.process(exchange);

        // get the response from the out body and cast it to an integer
        int response = exchange.getOut().getBody(Integer.class);
        System.out.println("... the result is: " + response);

        // stopping the JMS producer has the side effect of the "ReplyTo Queue" being properly
        // closed, making this client not to try any further reads for the replies from the server
        producer.stop();

        // we're done so let's properly close the application context
        IOHelper.close(context);
    }

}
