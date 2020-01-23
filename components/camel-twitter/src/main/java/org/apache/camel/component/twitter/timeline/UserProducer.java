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
package org.apache.camel.component.twitter.timeline;

import org.apache.camel.Exchange;
import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.support.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.Status;
import twitter4j.StatusUpdate;

/**
 * Produces text as a status update.
 */
public class UserProducer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(UserProducer.class);

    private TwitterEndpoint endpoint;

    public UserProducer(TwitterEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        // update user's status
        Object in = exchange.getIn().getBody();
        Status response;
        if (in instanceof StatusUpdate) {
            response = updateStatus((StatusUpdate) in);
        } else {
            String s = exchange.getIn().getMandatoryBody(String.class);
            response = updateStatus(s);
        }

        /*
         * Support the InOut exchange pattern in order to provide access to
         * the unique identifier for the published tweet which is returned in the response
         * by the Twitter REST API: https://dev.twitter.com/docs/api/1/post/statuses/update
         */
        if (exchange.getPattern().isOutCapable()) {
            // here we just copy the header of in message to the out message
            exchange.getOut().copyFrom(exchange.getIn());
            exchange.getOut().setBody(response);
        }
    }

    private Status updateStatus(StatusUpdate status) throws Exception {
        Status response = endpoint.getProperties().getTwitter().updateStatus(status);
        LOG.debug("Updated status: {}", status);
        LOG.debug("Status id: {}", response.getId());
        return response;
    }

    private Status updateStatus(String status) throws Exception {
        Status response = endpoint.getProperties().getTwitter().updateStatus(status);
        LOG.debug("Updated status: {}", status);
        LOG.debug("Status id: {}", response.getId());
        return response;
    }
}
