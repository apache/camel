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

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.twitter.AbstractTwitterEndpoint;
import org.apache.camel.component.twitter.TwitterConfiguration;
import org.apache.camel.component.twitter.TwitterConstants;
import org.apache.camel.component.twitter.TwitterHelper;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;

import static org.apache.camel.component.twitter.TwitterConstants.SCHEME_DIRECT_MESSAGE;

/**
 * Send and receive Twitter direct messages.
 */
@UriEndpoint(firstVersion = "2.10.0", scheme = SCHEME_DIRECT_MESSAGE, title = "Twitter Direct Message",
             syntax = "twitter-directmessage:user",
             category = { Category.SAAS, Category.SOCIAL }, headersClass = TwitterConstants.class)
public class TwitterDirectMessageEndpoint extends AbstractTwitterEndpoint {

    @UriPath(description = "The user name to send a direct message. This will be ignored for consumer.")
    @Metadata(required = true)
    private String user;

    public TwitterDirectMessageEndpoint(String uri, String remaining, TwitterDirectMessageComponent component,
                                        TwitterConfiguration properties) {
        super(uri, component, properties);
        this.user = remaining;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new DirectMessageProducer(this, user);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return TwitterHelper.createConsumer(processor, this, new DirectMessageConsumerHandler(this));
    }

}
