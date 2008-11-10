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
package org.apache.camel.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

/**
 * The default implementation of {@link Message}
 *
 * @version $Revision$
 */
public class DefaultMessage extends MessageSupport {
    private Map<String, Object> headers;
    private Map<String, DataHandler> attachments;

    @Override
    public String toString() {
        return "Message: " + getBody();
    }

    public Object getHeader(String name) {
        return getHeaders().get(name);
    }

    public <T> T getHeader(String name, Class<T> type) {
        Object value = getHeader(name);
        Exchange e = getExchange();
        return e.getContext().getTypeConverter().convertTo(type, e, value);
    }

    public void setHeader(String name, Object value) {
        if (headers == null) {
            headers = createHeaders();
        }
        headers.put(name, value);
    }

    public Object removeHeader(String name) {
        if (headers != null) {
            return headers.remove(name);
        } else {
            return null;
        }
    }

    public Map<String, Object> getHeaders() {
        if (headers == null) {
            headers = createHeaders();
        }
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        this.headers = headers;
    }

    public DefaultMessage newInstance() {
        return new DefaultMessage();
    }

    /**
     * A factory method to lazily create the headers to make it easy to create
     * efficient Message implementations which only construct and populate the
     * Map on demand
     *
     * @return return a newly constructed Map possibly containing headers from
     *         the underlying inbound transport
     */
    protected Map<String, Object> createHeaders() {
        HashMap<String, Object> map = new HashMap<String, Object>();
        populateInitialHeaders(map);
        return map;
    }

    /**
     * A strategy method populate the initial set of headers on an inbound
     * message from an underlying binding
     *
     * @param map is the empty header map to populate
     */
    protected void populateInitialHeaders(Map<String, Object> map) {
    }

    /**
     * A factory method to lazily create the attachments to make it easy to
     * create efficient Message implementations which only construct and
     * populate the Map on demand
     *
     * @return return a newly constructed Map
     */
    protected Map<String, DataHandler> createAttachments() {
        HashMap<String, DataHandler> map = new HashMap<String, DataHandler>();
        populateInitialAttachments(map);
        return map;
    }

    /**
     * A strategy method populate the initial set of attachments on an inbound
     * message from an underlying binding
     *
     * @param map is the empty attachment map to populate
     */
    protected void populateInitialAttachments(Map<String, DataHandler> map) {
    }

    public void addAttachment(String id, DataHandler content) {
        if (attachments == null) {
            attachments = createAttachments();
        }
        attachments.put(id, content);
    }

    public DataHandler getAttachment(String id) {
        return getAttachments().get(id);
    }

    public Set<String> getAttachmentNames() {
        if (attachments == null) {
            attachments = createAttachments();
        }
        return attachments.keySet();
    }

    public void removeAttachment(String id) {
        if (attachments != null && attachments.containsKey(id)) {
            attachments.remove(id);
        }
    }

    public Map<String, DataHandler> getAttachments() {
        if (attachments == null) {
            attachments = createAttachments();
        }
        return attachments;
    }

    public void setAttachments(Map<String, DataHandler> attachments) {
        this.attachments = attachments;
    }

    public boolean hasAttachments() {
        if (attachments == null) {
            attachments = createAttachments();
        }
        return this.attachments != null && this.attachments.size() > 0;
    }

    /**
     * Returns true if the headers have been mutated in some way
     */
    protected boolean hasPopulatedHeaders() {
        return headers != null;
    }
    
}
