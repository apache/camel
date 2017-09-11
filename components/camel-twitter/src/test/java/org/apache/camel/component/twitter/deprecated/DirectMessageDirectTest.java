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
package org.apache.camel.component.twitter.deprecated;

import java.util.Date;

import org.apache.camel.component.twitter.CamelTwitterConsumerTestSupport;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import twitter4j.Twitter;
import twitter4j.TwitterFactory;

/**
 * consumes tweets
 */
@Deprecated
public class DirectMessageDirectTest extends CamelTwitterConsumerTestSupport {

    @Override
    public void setUp() throws Exception {
        super.setUp();
        /* Uncomment when you need a test direct message
        TwitterConfiguration properties = new TwitterConfiguration();
        properties.setConsumerKey(consumerKey);
        properties.setConsumerSecret(consumerSecret);
        properties.setAccessToken(accessToken);
        properties.setAccessTokenSecret(accessTokenSecret);
        Twitter twitter = properties.getTwitter();
        twitter.sendDirectMessage(twitter.getScreenName(), "Test Direct Message: " + new Date().toString());
        */
    }

    @Override
    protected String getUri() {
        return "twitter://directmessage?type=direct&";
    }

    @Override
    protected Logger getLogger() {
        return LoggerFactory.getLogger(DirectMessageDirectTest.class);
    }
}
