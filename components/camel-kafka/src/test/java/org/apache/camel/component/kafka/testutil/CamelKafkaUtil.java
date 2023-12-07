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
package org.apache.camel.component.kafka.testutil;

import java.util.Objects;

import org.apache.camel.Exchange;
import org.apache.camel.component.kafka.KafkaConstants;

public final class CamelKafkaUtil {

    private CamelKafkaUtil() {
    }

    public static String buildKafkaLogMessage(String msg, Exchange exchange, boolean includeBody) {
        String eol = "\n";

        StringBuilder sb = new StringBuilder();
        if (Objects.nonNull(msg)) {
            sb.append(msg);
            sb.append(eol);
        }

        sb.append("Message consumed from ");
        sb.append(exchange.getMessage().getHeader(KafkaConstants.TOPIC, String.class));
        sb.append(eol);
        sb.append("The Partition:Offset is ");
        sb.append(exchange.getMessage().getHeader(KafkaConstants.PARTITION, String.class));
        sb.append(":");
        sb.append(exchange.getMessage().getHeader(KafkaConstants.OFFSET, String.class));
        sb.append(eol);
        sb.append("The Key is ");
        sb.append(exchange.getMessage().getHeader(KafkaConstants.KEY, String.class));

        if (includeBody) {
            sb.append(eol);
            sb.append(exchange.getMessage().getBody(String.class));
        }

        return sb.toString();
    }

}
