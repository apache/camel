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
package org.apache.camel.component.pulsar.utils;

enum TopicType {
    PERSISTENT("persistent"),
    NON_PERSISTENT("non-persistent");

    private String topicType;

    TopicType(String topicType) {
        this.topicType = topicType;
    }

    public String getTopicType() {
        return topicType;
    }
}

public final class TopicTypeUtils {

    public static String parse(final String value) {
        if(TopicType.PERSISTENT.getTopicType().equalsIgnoreCase(value)) {
            return TopicType.PERSISTENT.getTopicType();
        } else if(TopicType.NON_PERSISTENT.getTopicType().equalsIgnoreCase(value)) {
            return TopicType.NON_PERSISTENT.getTopicType();
        } else {
            throw new IllegalArgumentException("Invalid topic type - " + value);
        }
    }
}
