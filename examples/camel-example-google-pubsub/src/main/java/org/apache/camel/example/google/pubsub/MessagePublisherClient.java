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
package org.apache.camel.example.google.pubsub;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MessagePublisherClient {

    private static final Logger LOG = LoggerFactory.getLogger(MessagePublisherClient.class);

    private MessagePublisherClient() {
    }

    public static void main(String[] args) throws Exception {

        LOG.info("About to run Google-pubsub-camel integration...");

        String testPubsubMessage = "Test Message from  MessagePublisherClient " + Calendar.getInstance().getTime();

        CamelContext camelContext = new DefaultCamelContext();

        // Add route to send messages to Google Pubsub

        camelContext.addRoutes(new RouteBuilder() {
            public void configure() {
                camelContext.getPropertiesComponent().setLocation("classpath:example.properties");

                // setup google pubsub component
                GooglePubsubComponent googlePubsub = PubsubUtil.createComponent();
                camelContext.addComponent("google-pubsub", googlePubsub);

                from("direct:googlePubsubStart").routeId("DirectToGooglePubsub")
                        .to("google-pubsub:{{pubsub.projectId}}:{{pubsub.topic}}").log("${headers}");


                // Takes input from the command line.

                from("stream:in")
                        .to("direct:googlePubsubStart");

            }

        });

        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        camelContext.start();

        Map<String, Object> headers = new HashMap<>();

        producerTemplate.sendBodyAndHeaders("direct:googlePubsubStart", testPubsubMessage, headers);

        LOG.info("Successfully published message to Pubsub.");
        System.out.println("Enter text on the line below : [Press Ctrl-C to exit.] ");

        Thread.sleep(5 * 60 * 1000);

        camelContext.stop();
    }

}
