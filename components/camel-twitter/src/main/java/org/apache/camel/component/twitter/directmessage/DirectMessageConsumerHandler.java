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

import java.util.Iterator;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.component.twitter.consumer.AbstractTwitterConsumerHandler;
import org.apache.camel.component.twitter.consumer.TwitterEventType;
import twitter4j.DirectMessage;
import twitter4j.DirectMessageList;
import twitter4j.TwitterException;

/**
 * Consumes a user's direct messages
 */
public class DirectMessageConsumerHandler extends AbstractTwitterConsumerHandler {

    public DirectMessageConsumerHandler(TwitterEndpoint te) {
        super(te);
    }

    @Override
    public List<Exchange> pollConsume() throws TwitterException {
        // the first call doesn't require any cursor as parameters
        DirectMessageList directMessages = directMessages(null, null);
        if (directMessages.size() > 0) {
            // the DM response list is in reverse chronological order, so the last id is the first element.
            setLastId(directMessages.get(0).getId());
        }
        return TwitterEventType.DIRECT_MESSAGE.createExchangeList(endpoint, directMessages);
    }

    private DirectMessageList directMessages(String previousCursor, String cursor) throws TwitterException {
        // https://developer.twitter.com/en/docs/direct-messages/sending-and-receiving/api-reference/list-events
        // if there are more DM to retrieve the the next_cursor parameter is set.
        // however next_cursor is always set in the response, so we must handle pagination correctly
        // after the first call, there is always a second call to check if there is new DM and we compare
        // the previous and current next_cursor, if not equals there is pagination.
        DirectMessageList directMessages;
        if (cursor != null) {
            directMessages = getTwitter().getDirectMessages(endpoint.getProperties().getCount(), cursor);
        } else {
            directMessages = getTwitter().getDirectMessages(endpoint.getProperties().getCount());
        }

        String nextCursor = directMessages.getNextCursor();
        // the condition will always be false for the first call.
        if (nextCursor != null && nextCursor.equals(previousCursor) || directMessages.isEmpty()) {
            directMessages.clear();
            return directMessages;
        }

        if (nextCursor != null) {
            DirectMessageList directMessages2 = directMessages(cursor, nextCursor);
            directMessages.addAll(directMessages2);
        }

        // filter out messages retrieved before.
        if (endpoint.getProperties().isFilterOld()) {
            Iterator<DirectMessage> iterator = directMessages.iterator();
            while (iterator.hasNext()) {
                DirectMessage dm = iterator.next();
                long id = dm.getId();
                if (getLastId() > 1 && id <= getLastId()) {
                    iterator.remove();
                }
            }
        }

        return directMessages;
    }

    @Override
    public List<Exchange> directConsume() throws TwitterException {
        DirectMessageList directMessages = directMessages(null, null);
        return TwitterEventType.DIRECT_MESSAGE.createExchangeList(endpoint, directMessages);
    }
}
