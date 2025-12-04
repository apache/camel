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

package org.apache.camel.component.jms;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.spi.BrowsableEndpoint;
import org.springframework.jms.core.JmsOperations;

/**
 * Strategy for browsing JMS queues
 */
public interface QueueBrowseStrategy {

    /**
     * Browse the given queue
     */
    List<Exchange> browse(JmsOperations template, String queue, JmsBrowsableEndpoint endpoint, int limit);

    /**
     * Browse quick status of the given queue
     */
    default BrowsableEndpoint.BrowseStatus browseStatus(
            JmsOperations template, String queue, JmsBrowsableEndpoint endpoint, int limit) {
        List<Exchange> list = browse(template, queue, endpoint, limit);
        long ts = 0;
        long ts2 = 0;
        if (!list.isEmpty()) {
            ts = list.get(0).getMessage().getHeader(Exchange.MESSAGE_TIMESTAMP, 0L, long.class);
            ts2 = list.get(list.size() - 1).getMessage().getHeader(Exchange.MESSAGE_TIMESTAMP, 0L, long.class);
        }
        return new BrowsableEndpoint.BrowseStatus(list.size(), ts, ts2);
    }
}
