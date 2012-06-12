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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twitter4j.StatusUpdate;

/**
 * Produces text as a status update.
 */
public class UserProducer extends DefaultProducer implements Processor {

    private static final transient Logger LOG = LoggerFactory.getLogger(UserProducer.class);
    private TwitterEndpoint te;

    public UserProducer(TwitterEndpoint te) {
        super(te);
        this.te = te;
    }

    public void process(Exchange exchange) throws Exception {
        // update user's status
        Object in = exchange.getIn().getBody();
        if (in instanceof StatusUpdate) {
            updateStatus((StatusUpdate) in);
        } else {
            String s = exchange.getIn().getMandatoryBody(String.class);
            updateStatus(s);
        }
    }

    private void updateStatus(StatusUpdate status) throws Exception {
        te.getTwitter().updateStatus(status);
        LOG.debug("Updated status: {}", status);
    }

    private void updateStatus(String status) throws Exception {
        if (status.length() > 160) {
            LOG.warn("Message is longer than 160 characters. Message will be truncated!");
            status = status.substring(0, 160);
        }

        te.getTwitter().updateStatus(status);
        LOG.debug("Updated status: {}", status);
    }
}
