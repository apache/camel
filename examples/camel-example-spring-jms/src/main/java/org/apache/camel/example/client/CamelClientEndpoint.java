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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Client that uses the <a href="http://activemq.apache.org/camel/message-endpoint.html">Mesage Endpoint</a>
 * pattern to easily exchange messages with the Server.
 * <p/>
 * Notice this very same API can use for all components in Camel, so if we were using TCP communication instead
 * of JMS messaging we could just use <code>camel.getEndpoint("mina:tcp://someserver:port")</code>.
 * <p/>
 * Requires that the JMS broker is running, as well as CamelServer
 */
public final class CamelClientEndpoint {
    private CamelClientEndpoint() {
        //Helper class
    }

    // START SNIPPET: e1
    public static void main(final String[] args) throws Exception {
        System.out.println("Notice this client requires that the CamelServer is already running!");

        ApplicationContext context = new ClassPathXmlApplicationContext("camel-client.xml");
        CamelContext camel = (CamelContext) context.getBean("camel");

        // get the endpoint from the camel context
        Endpoint endpoint = camel.getEndpoint("jms:queue:numbers");

        // create the exchange used for the communication
        // we use the in out pattern for a synchronized exchange where we expect a response
        Exchange exchange = endpoint.createExchange(ExchangePattern.InOut);
        // set the input on the in body
        // must you correct type to match the expected type of an Integer object
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

        // stop and exit the client
        producer.stop();
        System.exit(0);
    }
    // END SNIPPET: e1

}
