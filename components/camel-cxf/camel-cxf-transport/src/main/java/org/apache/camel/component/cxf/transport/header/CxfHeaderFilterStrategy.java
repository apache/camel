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
package org.apache.camel.component.cxf.transport.header;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.headers.Header;
import org.apache.cxf.message.Message;
import org.apache.cxf.service.model.BindingInfo;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The default CXF header filter strategy.
 */
public class CxfHeaderFilterStrategy extends DefaultHeaderFilterStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(CxfHeaderFilterStrategy.class);
    private Map<String, MessageHeaderFilter> messageHeaderFiltersMap;

    private List<MessageHeaderFilter> messageHeaderFilters;

    private boolean relayHeaders = true;
    private boolean allowFilterNamespaceClash;
    private boolean relayAllMessageHeaders;

    public CxfHeaderFilterStrategy() {
        initialize();
    }

    protected void initialize() {
        //filter the operationName and operationName
        getOutFilter().add(CxfConstants.OPERATION_NAME.toLowerCase());
        getOutFilter().add(CxfConstants.OPERATION_NAMESPACE.toLowerCase());

        // Request and response context Maps will be passed to CXF Client APIs
        getOutFilter().add(Client.REQUEST_CONTEXT.toLowerCase());
        getOutFilter().add(Client.RESPONSE_CONTEXT.toLowerCase());

        // protocol headers are stored as a Map.  DefaultCxfBinding
        // read the Map and send each entry to the filter.  Therefore,
        // we need to filter the header of this name.
        getOutFilter().add(Message.PROTOCOL_HEADERS.toLowerCase());
        getInFilter().add(Message.PROTOCOL_HEADERS.toLowerCase());

        // Add the filter for the Generic Message header
        // http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.5
        getOutFilter().add("cache-control");
        getOutFilter().add("connection");
        getOutFilter().add("date");
        getOutFilter().add("pragma");
        getOutFilter().add("trailer");
        getOutFilter().add("transfer-encoding");
        getOutFilter().add("upgrade");
        getOutFilter().add("via");
        getOutFilter().add("warning");

        // Since CXF can take the content-type from the protocol header
        // we need to filter this header of this name.
        getOutFilter().add("Content-Type".toLowerCase());

        // Filter out Content-Length since it can fool Jetty (HttpGenerator) to
        // close response output stream prematurely.  (It occurs when the
        // message size (e.g. with attachment) is large and response content length
        // is bigger than request content length.)
        getOutFilter().add("Content-Length".toLowerCase());

        // Filter Content-Length as it will cause some trouble when the message
        // is passed to the other endpoint
        getInFilter().add("content-length".toLowerCase());

        setLowerCase(true);

        // initialize message header filter map with default SOAP filter
        messageHeaderFiltersMap = new HashMap<>();
        addToMessageHeaderFilterMap(new SoapMessageHeaderFilter());

        // filter headers begin with "Camel" or "org.apache.camel"
        setOutFilterStartsWith(CAMEL_FILTER_STARTS_WITH);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected boolean extendedFilter(Direction direction, String key, Object value, Exchange exchange) {
        // Currently only handles Header.HEADER_LIST message header relay/filter
        if (!Header.HEADER_LIST.equals(key) || value == null) {
            return false;
        }

        if (!relayHeaders) {
            // not propagating Header.HEADER_LIST at all
            return true;
        }

        if (relayAllMessageHeaders) {
            // all message headers will be relayed (no filtering)
            return false;
        }

        // get filter
        MessageHeaderFilter messageHeaderfilter = getMessageHeaderFilter(exchange);
        if (messageHeaderfilter == null) {
            LOG.debug("No CXF Binding namespace can be resolved.  Message headers are intact.");
            return false;
        }

        LOG.trace("messageHeaderfilter = {}", messageHeaderfilter);

        try {
            messageHeaderfilter.filter(direction, (List<Header>) value);
        } catch (Exception t) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Failed to cast value to Header<List> due to {}", t.toString(), t);
            }
        }

        // return false since the header list (which has been filtered) should be propagated
        return false;
    }

    private void addToMessageHeaderFilterMap(MessageHeaderFilter filter) {
        for (String ns : filter.getActivationNamespaces()) {
            if (messageHeaderFiltersMap.containsKey(ns)
                    && !allowFilterNamespaceClash) {
                throw new IllegalArgumentException(
                        "More then one MessageHeaderRelay activates "
                                                   + "for the same namespace: " + ns);
            }
            messageHeaderFiltersMap.put(ns, filter);
        }
    }

    private MessageHeaderFilter getMessageHeaderFilter(Exchange exchange) {
        BindingOperationInfo boi = exchange.getProperty(BindingOperationInfo.class.getName(),
                BindingOperationInfo.class);
        String ns = null;
        if (boi != null) {
            BindingInfo b = boi.getBinding();
            if (b != null) {
                ns = b.getBindingId();
            }
        }

        MessageHeaderFilter answer = null;
        if (ns != null) {
            answer = messageHeaderFiltersMap.get(ns);
        }

        return answer;
    }

    /**
     * @param messageHeaderFilters the messageHeaderFilters to set
     */
    public void setMessageHeaderFilters(List<MessageHeaderFilter> messageHeaderFilters) {
        this.messageHeaderFilters = messageHeaderFilters;
        // clear the amp to allow removal of default filter
        messageHeaderFiltersMap.clear();
        for (MessageHeaderFilter filter : messageHeaderFilters) {
            addToMessageHeaderFilterMap(filter);
        }
    }

    /**
     * @return the messageHeaderFilters
     */
    public List<MessageHeaderFilter> getMessageHeaderFilters() {
        return messageHeaderFilters;
    }

    /**
     * @return the allowFilterNamespaceClash
     */
    public boolean isAllowFilterNamespaceClash() {
        return allowFilterNamespaceClash;
    }

    /**
     * @param allowFilterNamespaceClash the allowFilterNamespaceClash to set
     */
    public void setAllowFilterNamespaceClash(boolean allowFilterNamespaceClash) {
        this.allowFilterNamespaceClash = allowFilterNamespaceClash;
    }

    /**
     * @return the messageHeaderFiltersMap
     */
    public Map<String, MessageHeaderFilter> getMessageHeaderFiltersMap() {
        return messageHeaderFiltersMap;
    }

    /**
     * @param relayHeaders the relayHeaders to set
     */
    public void setRelayHeaders(boolean relayHeaders) {
        this.relayHeaders = relayHeaders;
    }

    /**
     * @return the relayHeaders
     */
    public boolean isRelayHeaders() {
        return relayHeaders;
    }

    /**
     * @return the relayAllMessageHeaders
     */
    public boolean isRelayAllMessageHeaders() {
        return relayAllMessageHeaders;
    }

    /**
     * @param relayAllMessageHeaders the relayAllMessageHeaders to set
     */
    public void setRelayAllMessageHeaders(boolean relayAllMessageHeaders) {
        this.relayAllMessageHeaders = relayAllMessageHeaders;
    }

}
