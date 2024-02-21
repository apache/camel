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
package org.apache.camel.component.twitter.util;

import org.apache.camel.Converter;
import twitter4j.v1.DirectMessage;
import twitter4j.v1.Status;
import twitter4j.v1.Trend;
import twitter4j.v1.Trends;
import twitter4j.v1.UserList;

/**
 * Utility for converting between Twitter4J and camel-twitter data layers.
 */
@Converter(generateLoader = true)
public final class TwitterConverter {

    private TwitterConverter() {
        // Helper class
    }

    @Converter
    public static String toString(Status status) {
        return status.getCreatedAt() + " (" +
               status.getUser().getScreenName() + ") " +
               status.getText();
    }

    @Converter
    public static String toString(DirectMessage dm) {
        return dm.getCreatedAt() +
               " (" + dm.getSenderId() + ") " +
               dm.getText();
    }

    @Converter
    public static String toString(Trend trend) {
        return trend.getName();
    }

    @Converter
    public static String toString(Trends trends) {
        StringBuilder s = new StringBuilder();
        s.append("(")
                .append(trends.getTrendAt().toString())
                .append(") ");

        boolean first = true;
        for (Trend trend : trends.getTrends()) {
            if (first) {
                first = false;
            } else {
                s.append(",");
            }
            s.append(toString(trend));
        }
        return s.toString();
    }

    @Converter
    public static String toString(UserList userList) {
        return userList.getCreatedAt() +
               " (" + userList.getUser().getScreenName() + ") " +
               userList.getFullName() +
               ',' +
               userList.getURI() +
               ',';
    }
}
