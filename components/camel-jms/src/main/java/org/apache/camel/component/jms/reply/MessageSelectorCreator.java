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
package org.apache.camel.component.jms.reply;

import java.util.HashMap;
import java.util.Map;

/**
 * A creator which can build the JMS message selector query string to use
 * with a shared persistent reply-to queue, so we can select the correct messages we expect as replies.
 */
public class MessageSelectorCreator {
    protected Map<String, String> correlationIds;
    protected boolean dirty = true;
    protected StringBuilder expression;

    public MessageSelectorCreator() {
        correlationIds = new HashMap<String, String>();
    }

    public synchronized void addCorrelationID(String id) {
        correlationIds.put(id, id);
        dirty = true;
    }

    public synchronized void removeCorrelationID(String id) {
        correlationIds.remove(id);
        dirty = true;
    }

    public synchronized String get() {
        if (!dirty) {
            return expression.toString();
        }

        expression = new StringBuilder("JMSCorrelationID='");

        if (correlationIds.isEmpty()) {
            // no id's so use a dummy to select nothing
            expression.append("CamelDummyJmsMessageSelector'");
        } else {
            boolean first = true;
            for (Map.Entry<String, String> entry : correlationIds.entrySet()) {
                if (!first) {
                    expression.append(" OR JMSCorrelationID='");
                }
                expression.append(entry.getValue()).append("'");
                if (first) {
                    first = false;
                }
            }
        }

        dirty = false;
        return expression.toString();
    }

}