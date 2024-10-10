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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangeProperty;
import org.apache.camel.component.kafka.KafkaConstants;
import org.apache.camel.util.ObjectHelper;

public class RegexRouter {

    public void process(
            @ExchangeProperty("regex") String regex, @ExchangeProperty("replacement") String replacement, Exchange ex) {
        Pattern regexPattern = Pattern.compile(regex);
        String topicName = ex.getMessage().getHeader(KafkaConstants.TOPIC, String.class);
        if (ObjectHelper.isNotEmpty(topicName)) {
            final Matcher matcher = regexPattern.matcher(topicName);
            if (matcher.matches()) {
                final String topicUpdated = matcher.replaceFirst(replacement);
                ex.getMessage().setHeader(KafkaConstants.OVERRIDE_TOPIC, topicUpdated);
            }
        }
        String ceType = ex.getMessage().getHeader("ce-type", String.class);
        if (ObjectHelper.isNotEmpty(ceType)) {
            final Matcher matcher = regexPattern.matcher(ceType);
            if (matcher.matches()) {
                final String ceTypeUpdated = matcher.replaceFirst(replacement);
                ex.getMessage().setHeader("ce-type", ceTypeUpdated);
            }
        }
    }

}
