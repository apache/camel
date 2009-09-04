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

package org.apache.camel.web.util;

import org.apache.camel.builder.DeadLetterChannelBuilder;
import org.apache.camel.builder.ErrorHandlerBuilder;
import org.apache.camel.processor.RedeliveryPolicy;

/**
 * 
 */
public final class ErrorHandlerRenderer {
    private ErrorHandlerRenderer() {
        // Utility class, no public or protected default constructor
    }

    public static void render(StringBuilder buffer, ErrorHandlerBuilder errorHandler) {
        if (errorHandler instanceof DeadLetterChannelBuilder) {
            DeadLetterChannelBuilder deadLetter = (DeadLetterChannelBuilder)errorHandler;
            buffer.append("errorHandler(deadLetterChannel(\"").append(deadLetter.getDeadLetterUri()).append("\")");

            // render the redelivery policy
            RedeliveryPolicy redelivery = deadLetter.getRedeliveryPolicy();
            int maxRediliveries = redelivery.getMaximumRedeliveries();
            if (maxRediliveries != 0) {
                buffer.append(".maximumRedeliveries(").append(maxRediliveries).append(")");
            }
            long redeliverDelay = redelivery.getRedeliverDelay();
            if (redeliverDelay != 1000) {
                buffer.append(".redeliverDelay(").append(redeliverDelay).append(")");
            }
            if (redelivery.isLogStackTrace()) {
                buffer.append(".logStackTrace(true)");
            }

            // render the handled policy
            if (deadLetter.getHandledPolicy() != null) {
                String handledPolicy = deadLetter.getHandledPolicy().toString();
                if (handledPolicy.equals("false")) {
                    buffer.append(".handled(").append(handledPolicy).append(")");
                }
            }

            buffer.append(");");
        }
    }
}
