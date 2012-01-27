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
package org.apache.camel.component.twitter.util;

import java.text.ParseException;

import org.apache.camel.Converter;

import twitter4j.DirectMessage;
import twitter4j.Status;
import twitter4j.Tweet;

/**
 * Utility for converting between Twitter4J and camel-twitter data layers.
 * 
 */
@Converter
public final class TwitterConverter {

    private TwitterConverter() {
        // Helper class
    }

    @Converter
    public static String toString(Status status) throws ParseException {
        StringBuilder s = new StringBuilder();
        s.append(status.getCreatedAt()).append(" (").append(status.getUser().getScreenName()).append(") ");
        s.append(status.getText());
        return s.toString();
    }

    @Converter
    public static String toString(Tweet tweet) throws ParseException {
        StringBuilder s = new StringBuilder();
        s.append(tweet.getCreatedAt()).append(" (").append(tweet.getFromUser()).append(") ");
        s.append(tweet.getText());
        return s.toString();
    }

    @Converter
    public static String toString(DirectMessage dm) throws ParseException {
        StringBuilder s = new StringBuilder();
        s.append(dm.getCreatedAt()).append(" (").append(dm.getSenderScreenName()).append(") ");
        s.append(dm.getText());
        return s.toString();
    }
}
