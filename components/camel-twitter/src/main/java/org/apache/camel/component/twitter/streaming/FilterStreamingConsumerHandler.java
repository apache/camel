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
package org.apache.camel.component.twitter.streaming;

import org.apache.camel.component.twitter.TwitterEndpoint;
import twitter4j.FilterQuery;
import twitter4j.StallWarning;

/**
 * Consumes the filter stream
 */
public class FilterStreamingConsumerHandler extends AbstractStreamingConsumerHandler {

    private String keywords;

    public FilterStreamingConsumerHandler(TwitterEndpoint endpoint, String keywords) {
        super(endpoint);
        this.keywords = keywords;
    }

    @Override
    public void start() {
        getTwitterStream().filter(createFilter());
    }

    @Override
    public void onStallWarning(StallWarning stallWarning) {
        // noop
    }

    private FilterQuery createFilter() {
        FilterQuery filterQuery = new FilterQuery();
        String allLocationsString = endpoint.getProperties().getLocations();
        if (allLocationsString != null) {
            String[] locationStrings = allLocationsString.split(";");
            double[][] locations = new double[locationStrings.length][2];
            for (int i = 0; i < locationStrings.length; i++) {
                String[] coords = locationStrings[i].split(",");
                locations[i][0] = Double.valueOf(coords[0]);
                locations[i][1] = Double.valueOf(coords[1]);
            }
            filterQuery.locations(locations);
        }

        String keywords = this.keywords;
        if (keywords != null && keywords.length() > 0) {
            filterQuery.track(keywords.split(","));
        }

        String userIds = endpoint.getProperties().getUserIds();
        if (userIds != null) {
            String[] stringUserIds = userIds.split(",");
            long[] longUserIds = new long[stringUserIds.length];
            for (int i = 0; i < stringUserIds.length; i++) {
                longUserIds[i] = Long.valueOf(stringUserIds[i]);
            }
            filterQuery.follow(longUserIds);
        }

        if (allLocationsString == null && keywords == null && userIds == null) {
            throw new IllegalArgumentException("At least one filter parameter is required");
        }

        return filterQuery;
    }
}
