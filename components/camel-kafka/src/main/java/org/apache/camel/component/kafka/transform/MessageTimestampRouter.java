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
package org.apache.camel.component.kafka.transform;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.util.ObjectHelper;

public class MessageTimestampRouter {

    public void process(
            @ExchangeProperty("topicFormat") String topicFormat, @ExchangeProperty("timestampFormat") String timestampFormat,
            @ExchangeProperty("timestampKeys") String timestampKeys,
            @ExchangeProperty("timestampKeyFormat") String timestampKeyFormat, Exchange ex)
            throws ParseException {
        final Pattern TOPIC = Pattern.compile("$[topic]", Pattern.LITERAL);

        final Pattern TIMESTAMP = Pattern.compile("$[timestamp]", Pattern.LITERAL);

        final SimpleDateFormat fmt = new SimpleDateFormat(timestampFormat);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));

        ObjectMapper mapper = new ObjectMapper();
        List<String> splittedKeys = new ArrayList<>();
        JsonNode jsonNodeBody = ex.getMessage().getBody(JsonNode.class);
        Map<Object, Object> body = mapper.convertValue(jsonNodeBody, new TypeReference<Map<Object, Object>>() {
        });
        if (ObjectHelper.isNotEmpty(timestampKeys)) {
            splittedKeys = Arrays.stream(timestampKeys.split(",")).collect(Collectors.toList());
        }

        Object rawTimestamp = null;
        String topicName = ex.getMessage().getHeader("kafka.TOPIC", String.class);
        for (String key : splittedKeys) {
            if (ObjectHelper.isNotEmpty(key)) {
                rawTimestamp = body.get(key);
                break;
            }
        }
        Long timestamp = null;
        if (ObjectHelper.isNotEmpty(timestampKeyFormat) && ObjectHelper.isNotEmpty(rawTimestamp)
                && !timestampKeyFormat.equalsIgnoreCase("timestamp")) {
            final SimpleDateFormat timestampKeyFmt = new SimpleDateFormat(timestampKeyFormat);
            timestampKeyFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            timestamp = timestampKeyFmt.parse((String) rawTimestamp).getTime();
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
