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
package org.apache.camel.component.twitter.consumer.trends;

import java.util.Date;
import java.util.List;

import org.apache.camel.component.twitter.TwitterEndpoint;
import org.apache.camel.component.twitter.consumer.Twitter4JConsumer;
import twitter4j.Trends;
import twitter4j.TwitterException;

/**
 * Consumes the public timeline
 */
public class WeeklyTrendConsumer extends Twitter4JConsumer {

    public WeeklyTrendConsumer(TwitterEndpoint te) {
        super(te);
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.twitter.consumer.Twitter4JConsumer#pollConsume()
     */
    public List<Trends> pollConsume() throws TwitterException {
        return getTrends();
    }

    /* (non-Javadoc)
     * @see org.apache.camel.component.twitter.consumer.Twitter4JConsumer#directConsume()
     */
    public List<Trends> directConsume() throws TwitterException {
        return getTrends();
    }

    /**
     * @return
     * @throws TwitterException
     */
    private List<Trends> getTrends() throws TwitterException {
        Date date = te.getProperties().parseDate();
        if (date != null) {
            return te.getProperties().getTwitter().getWeeklyTrends(
                    date, false);
        } else {
            return te.getProperties().getTwitter().getWeeklyTrends();
        }
    }
}
