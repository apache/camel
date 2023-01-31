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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import jakarta.jms.JMSException;
import jakarta.jms.Message;
import jakarta.jms.QueueBrowser;
import jakarta.jms.Session;

import org.apache.camel.Exchange;
import org.springframework.jms.core.BrowserCallback;
import org.springframework.jms.core.JmsOperations;

/**
 * A default implementation of queue browsing using the Spring {@link BrowserCallback}
 */
public class DefaultQueueBrowseStrategy implements QueueBrowseStrategy {

    @Override
    public List<Exchange> browse(JmsOperations template, String queue, final JmsBrowsableEndpoint endpoint) {
        if (endpoint.getSelector() != null) {
            return template.browseSelected(queue, endpoint.getSelector(),
                    (session, browser) -> doBrowse(endpoint, session, browser));
        } else {
            return template.browse(queue, (session, browser) -> doBrowse(endpoint, session, browser));
        }
    }

    private static List<Exchange> doBrowse(JmsBrowsableEndpoint endpoint, Session session, QueueBrowser browser)
            throws JMSException {
        int size = endpoint.getMaximumBrowseSize();
        if (size <= 0) {
            size = Integer.MAX_VALUE;
        }

        // not the best implementation in the world as we have to browse
        // the entire queue, which could be massive
        List<Exchange> answer = new ArrayList<>();
        Enumeration<?> iter = browser.getEnumeration();

        for (int i = 0; i < size && iter.hasMoreElements(); i++) {
            Message message = (Message) iter.nextElement();
            Exchange exchange = endpoint.createExchange(message, session);
            answer.add(exchange);
        }
        return answer;
    }

}
