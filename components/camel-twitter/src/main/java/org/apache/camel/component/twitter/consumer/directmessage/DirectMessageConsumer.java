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
package org.apache.camel.component.twitter.consumer.directmessage;

import java.util.List;

import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.component.twitter.consumer.Twitter4JConsumer;

import twitter4j.DirectMessage;
import twitter4j.Paging;
import twitter4j.TwitterException;

/**
 * Consumes a user's direct messages
 */
public class DirectMessageConsumer extends Twitter4JConsumer {

    public DirectMessageConsumer(TwitterEndpoint te) {
        super(te);
    }

    public List<DirectMessage> pollConsume() throws TwitterException {
        List<DirectMessage> list = te.getProperties().getTwitter().getDirectMessages(new Paging(lastId));
        for (DirectMessage dm : list) {
            checkLastId(dm.getId());
        }
        return list;
    }

    public List<DirectMessage> directConsume() throws TwitterException {
        return te.getProperties().getTwitter().getDirectMessages();
    }
}
