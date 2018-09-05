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
package org.apache.camel.component.twitter.timeline;

import java.util.List;

import org.apache.camel.component.twitter.TwitterEndpoint;
import twitter4j.Paging;
import twitter4j.Status;
import twitter4j.TwitterException;

/**
 * Consumes tweets in which the user has been mentioned.
 */
public class MentionsConsumerHandler extends AbstractStatusConsumerHandler {

    public MentionsConsumerHandler(TwitterEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    protected List<Status> doPoll() throws TwitterException {
        Paging paging = getLastIdPaging();
        log.trace("doPoll.getMentionsTimeline(sinceId={})", paging.getSinceId());
        return getTwitter().getMentionsTimeline(paging);
    }

    @Override
    protected List<Status> doDirect() throws TwitterException {
        log.trace("doDirect.getMentionsTimeline()");
        return getTwitter().getMentionsTimeline();
    }
}
