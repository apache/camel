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

package org.apache.camel.component.twitter.consumer.streaming;

import org.apache.camel.component.twitter.TwitterEndpoint;

import twitter4j.FilterQuery;
import twitter4j.TwitterStream;
import twitter4j.TwitterStreamFactory;

/**
 * Consumes the filter stream
 *
 */
public class FilterConsumer extends StreamingConsumer {

    public FilterConsumer(TwitterEndpoint te) {
        super(te);

        TwitterStream twitterStream = new TwitterStreamFactory(te.getProperties().getConfiguration())
            .getInstance();
        twitterStream.addListener(this);

        String allLocationsString = te.getProperties().getLocations();
        String[] locationStrings = allLocationsString.split(";");
        double[][] locations = new double[locationStrings.length][2];
        for (int i = 0; i < locationStrings.length; i++) {
            String[] coords = locationStrings[i].split(",");
            locations[i][0] = Double.valueOf(coords[0]);
            locations[i][1] = Double.valueOf(coords[1]);
        }

        FilterQuery fq = new FilterQuery();
        fq.locations(locations);

        twitterStream.filter(fq);
    }
}
