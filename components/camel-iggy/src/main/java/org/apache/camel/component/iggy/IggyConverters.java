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
package org.apache.camel.component.iggy;

import java.util.Locale;

import org.apache.camel.Converter;
import org.apache.iggy.message.Partitioning;

/**
 * Converts the text of the partitioning option to the Iggy {@link Partitioning}.
 */
@Converter(generateLoader = true)
public final class IggyConverters {

    private static final String PARTITION_ID = "partitionid:";
    private static final String MESSAGES_KEY = "messageskey:";

    private IggyConverters() {
        // Utility Class
    }

    /**
     * Converts balanced, partitionId:&lt;partition id&gt; or messagesKey:&lt;key&gt; (the prefixes are not
     * case-sensitive) to a {@link Partitioning}.
     */
    @Converter
    public static Partitioning toPartitioning(String text) {
        String value = text.trim();
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.equals("balanced")) {
            return Partitioning.balanced();
        }
        if (lower.startsWith(PARTITION_ID)) {
            return Partitioning.partitionId(Long.valueOf(value.substring(PARTITION_ID.length()).trim()));
        }
        if (lower.startsWith(MESSAGES_KEY)) {
            return Partitioning.messagesKey(value.substring(MESSAGES_KEY.length()));
        }
        throw new IllegalArgumentException(
                "Unknown partitioning: " + text + ". Use balanced, partitionId:<partition id> or messagesKey:<key>");
    }
}
