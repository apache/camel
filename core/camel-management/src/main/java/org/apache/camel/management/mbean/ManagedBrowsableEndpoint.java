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
package org.apache.camel.management.mbean;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.api.management.mbean.ManagedBrowsableEndpointMBean;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.support.MessageHelper;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;
import org.apache.camel.util.json.Jsoner;

@ManagedResource(description = "Managed BrowsableEndpoint")
public class ManagedBrowsableEndpoint extends ManagedEndpoint implements ManagedBrowsableEndpointMBean {

    public ManagedBrowsableEndpoint(BrowsableEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public BrowsableEndpoint getEndpoint() {
        return (BrowsableEndpoint) super.getEndpoint();
    }

    @Override
    public BrowsableEndpoint getInstance() {
        return getEndpoint();
    }

    /**
     * The exchanges of the endpoint as one snapshot, as the list can change between size() and get(index) (such as a
     * browse limit removing the oldest exchange).
     */
    private Exchange[] snapshot() {
        return getEndpoint().getExchanges().toArray(new Exchange[0]);
    }

    @Override
    public int getBrowseLimit() {
        return getEndpoint().getBrowseLimit();
    }

    @Override
    public int queueSize() {
        return getEndpoint().getExchanges().size();
    }

    @Override
    public String browseExchange(Integer index) {
        Exchange[] exchanges = snapshot();

        if (index >= exchanges.length) {
            return null;
        }
        Exchange exchange = exchanges[index];
        if (exchange == null) {
            return null;
        }
        // must use java type with JMX such as java.lang.String
        return exchange.toString();
    }

    @Override
    public String browseMessageBody(Integer index) {
        Exchange[] exchanges = snapshot();

        if (index >= exchanges.length) {
            return null;
        }
        Exchange exchange = exchanges[index];
        if (exchange == null) {
            return null;
        }

        Message msg = exchange.getMessage();
        return MessageHelper.extractBodyAsString(msg);
    }

    @Override
    public String browseMessageAsXml(Integer index, Boolean includeBody) {
        Exchange[] exchanges = snapshot();

        if (index >= exchanges.length) {
            return null;
        }
        Exchange exchange = exchanges[index];
        if (exchange == null) {
            return null;
        }

        Message msg = exchange.getMessage();
        return MessageHelper.dumpAsXml(msg, includeBody);
    }

    @Override
    public String browseAllMessagesAsXml(Boolean includeBody) {
        return browseRangeMessagesAsXml(0, Integer.MAX_VALUE, includeBody);
    }

    @Override
    public String browseRangeMessagesAsXml(Integer fromIndex, Integer toIndex, Boolean includeBody) {
        if (fromIndex == null) {
            fromIndex = 0;
        }
        if (toIndex == null) {
            toIndex = Integer.MAX_VALUE;
        }
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException(
                    "From index cannot be larger than to index, was: " + fromIndex + " > " + toIndex);
        }

        Exchange[] exchanges = snapshot();
        if (exchanges.length == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<messages>");
        for (int i = fromIndex; i < exchanges.length && i <= toIndex; i++) {
            Exchange exchange = exchanges[i];
            Message msg = exchange.getMessage();
            String xml = MessageHelper.dumpAsXml(msg, includeBody);
            sb.append("\n").append(xml);
        }
        sb.append("\n</messages>");
        return sb.toString();
    }

    @Override
    public String browseMessageAsJSon(Integer index, Boolean includeBody) {
        Exchange[] exchanges = snapshot();

        if (index >= exchanges.length) {
            return null;
        }
        Exchange exchange = exchanges[index];
        if (exchange == null) {
            return null;
        }

        Message msg = exchange.getMessage();
        return MessageHelper.dumpAsJSon(msg, includeBody);
    }

    @Override
    public String browseAllMessagesAsJSon(Boolean includeBody) {
        return browseRangeMessagesAsJSon(0, Integer.MAX_VALUE, includeBody);
    }

    @Override
    public String browseRangeMessagesAsJSon(Integer fromIndex, Integer toIndex, Boolean includeBody) {
        if (fromIndex == null) {
            fromIndex = 0;
        }
        if (toIndex == null) {
            toIndex = Integer.MAX_VALUE;
        }
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException(
                    "From index cannot be larger than to index, was: " + fromIndex + " > " + toIndex);
        }

        Exchange[] exchanges = snapshot();
        if (exchanges.length == 0) {
            return null;
        }

        JsonArray arr = new JsonArray();
        for (int i = fromIndex; i < exchanges.length && i <= toIndex; i++) {
            Exchange exchange = exchanges[i];
            Message msg = exchange.getMessage();
            JsonObject jo = MessageHelper.dumpAsJSonObject(msg, false, false, includeBody, true, true, true, 128 * 1024);
            arr.add(jo);
        }
        String out = arr.toJson();
        return Jsoner.prettyPrint(out);
    }
}
