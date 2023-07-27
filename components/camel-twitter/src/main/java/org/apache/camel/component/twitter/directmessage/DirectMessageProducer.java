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
package org.apache.camel.component.twitter.directmessage;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.component.twitter.TwitterConstants;
import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.v1.User;

/**
 * Produces text as a direct message.
 */
public class DirectMessageProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(DirectMessageProducer.class);

    private TwitterEndpoint endpoint;
    private String user;

    public DirectMessageProducer(TwitterEndpoint endpoint, String user) {
        super(endpoint);
        this.endpoint = endpoint;
        this.user = user;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // send direct message
        String toUsername = user;
        if (ObjectHelper.isNotEmpty(exchange.getIn().getHeader(TwitterConstants.TWITTER_USER, String.class))) {
            toUsername = exchange.getIn().getHeader(TwitterConstants.TWITTER_USER, String.class);
        }
        String text = exchange.getIn().getBody(String.class);

        if (toUsername.isEmpty()) {
            throw new CamelExchangeException("Username not configured on TwitterEndpoint", exchange);
        } else {
            LOG.debug("Sending to: {} message: {}", toUsername, text);
            User userStatus = endpoint.getProperties().getTwitter().v1().users().showUser(toUsername);
            endpoint.getProperties().getTwitter().v1().directMessages().sendDirectMessage(userStatus.getId(), text);
        }
    }

}
