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
package org.apache.camel.component.jms.reply;

import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.camel.TimeoutMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.TimeoutMap.Listener.Type.*;

/**
 * A creator which can build the JMS message selector query string to use
 * with a shared reply-to queue, so we can select the correct messages we expect as replies.
 */
public class MessageSelectorCreator {
    protected static final Logger LOG = LoggerFactory.getLogger(MessageSelectorCreator.class);
    protected final TimeoutMap<String, ?> timeoutMap;
    protected final ConcurrentSkipListSet<String> correlationIds;
    protected volatile boolean dirty = true;
    protected StringBuilder expression;

    public MessageSelectorCreator(CorrelationTimeoutMap timeoutMap) {
        this.timeoutMap = timeoutMap;
        this.timeoutMap.addListener((type, cid, value) -> timeoutEvent(type, cid));
        // create local set of correlation ids, as its easier to keep track
        // using the listener so we can flag the dirty flag upon changes
        // must support concurrent access
        this.correlationIds = new ConcurrentSkipListSet<>();
    }


    public synchronized String get() {
        if (!dirty) {
            return expression.toString();
        }

        expression = new StringBuilder("JMSCorrelationID='");

        if (correlationIds.size() == 0) {
            // no id's so use a dummy to select nothing
            expression.append("CamelDummyJmsMessageSelector'");
        } else {
            boolean first = true;
            for (String value : correlationIds) {
                if (!first) {
                    expression.append(" OR JMSCorrelationID='");
                }
                expression.append(value).append("'");
                if (first) {
                    first = false;
                }
            }
        }

        String answer = expression.toString();

        dirty = false;
        return answer;
    }

    // Changes to live correlation-ids invalidate existing message selector
    private void timeoutEvent(TimeoutMap.Listener.Type type, String cid) {
        if (type == Put) {
            correlationIds.add(cid);
        } else if (type == Remove || type == Evict) {
            correlationIds.remove(cid);
        }
        dirty = true;
    }

}
