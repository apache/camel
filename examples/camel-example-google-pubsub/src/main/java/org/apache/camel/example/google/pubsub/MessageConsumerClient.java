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

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.google.pubsub.GooglePubsubComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MessageConsumerClient {

    private static final Logger LOG = LoggerFactory.getLogger(MessageConsumerClient.class);

    private MessageConsumerClient() {
    }

    public static void main(String[] args) throws Exception {

        LOG.info("About to run Google-pubsub-camel integration...");

        CamelContext camelContext = new DefaultCamelContext();

        // setup google pubsub component
        GooglePubsubComponent googlePubsub = PubsubUtil.createComponent();
        camelContext.addComponent("google-pubsub", googlePubsub);

        // Add route to send messages to Google pubsub

        camelContext.addRoutes(new RouteBuilder() {
            public void configure() {
                camelContext.getPropertiesComponent().setLocation("classpath:example.properties");

                log.info("About to start route: Google Pubsub -> Log ");

                from("google-pubsub:{{pubsub.projectId}}:{{pubsub.subscription}}?"
                        + "maxMessagesPerPoll={{consumer.maxMessagesPerPoll}}&"
                        + "concurrentConsumers={{consumer.concurrentConsumers}}")
                        .routeId("FromGooglePubsub")
                        .log("${body}");
            }
        });
        camelContext.start();

        // let it run for 5 minutes before shutting down
        Thread.sleep(5 * 60 * 1000);

        camelContext.stop();
    }

}
