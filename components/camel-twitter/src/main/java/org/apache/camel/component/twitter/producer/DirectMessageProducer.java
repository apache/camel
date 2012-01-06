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
package org.apache.camel.component.twitter.producer;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Produces text as a direct message.
 * 
 */
public class DirectMessageProducer extends DefaultProducer implements Processor {

    private static final transient Logger LOG = LoggerFactory.getLogger(DirectMessageProducer.class);
    private TwitterEndpoint te;

    public DirectMessageProducer(TwitterEndpoint te) {
        super(te);
        this.te = te;
    }

    public void process(Exchange exchange) throws Exception {
        // send direct message
        String toUsername = te.getProperties().getUser();
        String text = exchange.getIn().getBody(String.class);

        if (toUsername.isEmpty()) {
            throw new CamelExchangeException("Username not configured on TwitterEndpoint", exchange);
        } else {
            LOG.debug("Sending to: {} message: {}", toUsername, text);
            te.getTwitter().sendDirectMessage(toUsername, text);
        }
    }

}
