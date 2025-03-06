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
package org.apache.camel.component.kamelet.utils.transform;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.util.ObjectHelper;

public class TimestampRouter {

    public void process(
            @ExchangeProperty("topicFormat") String topicFormat, @ExchangeProperty("timestampFormat") String timestampFormat,
            @ExchangeProperty("timestampHeaderName") String timestampHeaderName, Exchange ex) {
        final Pattern TOPIC = Pattern.compile("$[topic]", Pattern.LITERAL);

        final Pattern TIMESTAMP = Pattern.compile("$[timestamp]", Pattern.LITERAL);

        final SimpleDateFormat fmt = new SimpleDateFormat(timestampFormat);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));

        Long timestamp = null;
        String topicName = ex.getMessage().getHeader("kafka.TOPIC", String.class);
        Object rawTimestamp = ex.getMessage().getHeader(timestampHeaderName);
        if (rawTimestamp instanceof Long) {
            timestamp = (Long) rawTimestamp;
        } else if (rawTimestamp instanceof Instant) {
            timestamp = ((Instant) rawTimestamp).toEpochMilli();
        } else if (ObjectHelper.isNotEmpty(rawTimestamp)) {
            timestamp = Long.parseLong(rawTimestamp.toString());
        }
        if (ObjectHelper.isNotEmpty(timestamp)) {
            final String formattedTimestamp = fmt.format(new Date(timestamp));
            String replace1;
            String updatedTopic;

            if (ObjectHelper.isNotEmpty(topicName)) {
                replace1 = TOPIC.matcher(topicFormat).replaceAll(Matcher.quoteReplacement(topicName));
                updatedTopic = TIMESTAMP.matcher(replace1).replaceAll(Matcher.quoteReplacement(formattedTimestamp));
            } else {
                replace1 = TOPIC.matcher(topicFormat).replaceAll(Matcher.quoteReplacement(""));
                updatedTopic = TIMESTAMP.matcher(replace1).replaceAll(Matcher.quoteReplacement(formattedTimestamp));
            }
            ex.getMessage().setHeader("kafka.OVERRIDE_TOPIC", updatedTopic);
        }
    }

}
