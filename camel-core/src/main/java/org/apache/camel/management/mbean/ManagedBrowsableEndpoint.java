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
package org.apache.camel.management.mbean;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.management.ManagedOperation;
import org.apache.camel.management.ManagedResource;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.util.MessageHelper;

/**
 * @version 
 */
@ManagedResource(description = "Managed BrowsableEndpoint")
public class ManagedBrowsableEndpoint extends ManagedEndpoint {

    private BrowsableEndpoint endpoint;

    public ManagedBrowsableEndpoint(BrowsableEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public BrowsableEndpoint getEndpoint() {
        return endpoint;
    }

    @ManagedOperation(description = "Current number of Exchanges in Queue")
    public long queueSize() {
        return endpoint.getExchanges().size();
    }

    @ManagedOperation(description = "Get Exchange from queue by index")
    public String browseExchange(Integer index) {
        List<Exchange> exchanges = endpoint.getExchanges();

        if (index >= exchanges.size()) {
            return null;
        }
        Exchange exchange = exchanges.get(index);
        if (exchange == null) {
            return null;
        }
        // must use java type with JMX such as java.lang.String
        return exchange.toString();
    }

    @ManagedOperation(description = "Get message body from queue by index")
    public String browseMessageBody(Integer index) {
        List<Exchange> exchanges = endpoint.getExchanges();

        if (index >= exchanges.size()) {
            return null;
        }
        Exchange exchange = exchanges.get(index);
        if (exchange == null) {
            return null;
        }

        // must use java type with JMX such as java.lang.String
        String body;
        if (exchange.hasOut()) {
            body = exchange.getOut().getBody(String.class);
        } else {
            body = exchange.getIn().getBody(String.class);
        }

        return body;
    }

    /**
     * @deprecated use {@link #browseAllMessagesAsXml(Boolean)} instead
     */
    @ManagedOperation(description = "Get message as XML from queue by index")
    @Deprecated
    public String browseMessageAsXml(Integer index) {
        return browseMessageAsXml(index, true);
    }

    @ManagedOperation(description = "Get message as XML from queue by index")
    public String browseMessageAsXml(Integer index, Boolean includeBody) {
        List<Exchange> exchanges = endpoint.getExchanges();

        if (index >= exchanges.size()) {
            return null;
        }
        Exchange exchange = exchanges.get(index);
        if (exchange == null) {
            return null;
        }

        Message msg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
        String xml = MessageHelper.dumpAsXml(msg, includeBody);

        return xml;
    }

    @ManagedOperation(description = "Gets all the messages as XML from the queue")
    public String browseAllMessagesAsXml(Boolean includeBody) {
        return browseRangeMessagesAsXml(0, Integer.MAX_VALUE, includeBody);
    }

    @ManagedOperation(description = "Gets the range of messages as XML from the queue")
    public String browseRangeMessagesAsXml(Integer fromIndex, Integer toIndex, Boolean includeBody) {
        if (fromIndex == null) {
            fromIndex = 0;
        }
        if (toIndex == null) {
            toIndex = Integer.MAX_VALUE;
        }
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("From index cannot be larger than to index, was: " + fromIndex + " > " + toIndex);
        }

        List<Exchange> exchanges = endpoint.getExchanges();
        if (exchanges.size() == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<messages>");
        for (int i = fromIndex; i < exchanges.size() && i <= toIndex; i++) {
            Exchange exchange = exchanges.get(i);
            Message msg = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
            String xml = MessageHelper.dumpAsXml(msg, includeBody);
            sb.append("\n").append(xml);
        }
        sb.append("\n</messages>");
        return sb.toString();
    }

}
