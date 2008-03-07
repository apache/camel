/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.QueueBrowser;
import javax.jms.Session;

import org.apache.camel.Exchange;
import org.apache.camel.spi.BrowsableEndpoint;
import org.springframework.jms.core.BrowserCallback;
import org.springframework.jms.core.JmsOperations;

/**
 * An endpoint for a JMS Queue which is also browsable
 *
 * @version $Revision: 1.1 $
 */
public class JmsQueueEndpoint extends JmsEndpoint implements BrowsableEndpoint<JmsExchange> {
    private int maximumBrowseSize = -1;

    public JmsQueueEndpoint(String uri, JmsComponent component, String destination, JmsConfiguration configuration) {
        super(uri, component, destination, false, configuration);
    }

    public int getMaximumBrowseSize() {
        return maximumBrowseSize;
    }

    /**
     * If a number is set > 0 then this limits the number of messages that are returned when browsing the queue
     */
    public void setMaximumBrowseSize(int maximumBrowseSize) {
        this.maximumBrowseSize = maximumBrowseSize;
    }

    public List<Exchange> getExchanges() {
        String queue = getDestination();
        JmsOperations template = getConfiguration().createInOnlyTemplate(false, queue);

        // TODO not the best implementation in the world as we have to browse the entire queue, which could be massive
        final List<Exchange> answer = new ArrayList<Exchange>();
        template.browse(queue, new BrowserCallback() {
            public Object doInJms(Session session, QueueBrowser browser) throws JMSException {
                Enumeration iter = browser.getEnumeration();
                while (iter.hasMoreElements()) {
                    Message message = (Message) iter.nextElement();
                    JmsExchange exchange = createExchange(message);
                    answer.add(exchange);
                }
                return answer;
            }
        });
        return answer;
    }
}
