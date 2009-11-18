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

import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.example.reportincident.InputReportIncident;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Client that uses the {@link ProducerTemplate} to easily exchange messages with the Server.
 * <p/>
 * Requires that the CamelServer is already running
 */
public final class CamelClient {

    private static final int SIZE = 100;

    private CamelClient() {
        // Helper class
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("Notice this client requires that the CamelServer is already running!");

        ApplicationContext context = new ClassPathXmlApplicationContext("META-INF/spring/camel-client.xml");

        // get the camel template for Spring template style sending of messages (= producer)
        final ProducerTemplate producer = (ProducerTemplate) context.getBean("camelTemplate");

        final MockEndpoint mock = (MockEndpoint) context.getBean("result");
        mock.expectedMessageCount(SIZE);
        // expect no duplicate bodies
        mock.expectsNoDuplicates().body();

        // now send a lot of messages
        System.out.println("Sending ...");
        for (int i = 0; i < SIZE; i++) {
            InputReportIncident input = new InputReportIncident();
            input.setIncidentId("" + i);
            input.setIncidentDate("20091116");
            input.setGivenName("Claus");
            input.setFamilyName("Ibsen");
            input.setSummary("Camel rocks");
            input.setDetails("More bla");
            input.setEmail("davsclaus@apache.org");
            input.setPhone("55512345678");

            // send our input to the Camel route
            producer.sendBody("direct:start", input);
        }
        System.out.println("... Send done");

        System.out.println("Asserting ...");
        mock.assertIsSatisfied();
        System.out.println("... Asserting done");

        System.exit(0);
    }

}
