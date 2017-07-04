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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.activation.DataHandler;

import org.apache.camel.Attachment;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.HeadersMapFactory;
import org.apache.camel.util.AttachmentMap;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * The default implementation of {@link org.apache.camel.Message}
 * <p/>
 * This implementation uses a {@link org.apache.camel.util.CaseInsensitiveMap} storing the headers.
 * This allows us to be able to lookup headers using case insensitive keys, making it easier for end users
 * as they do not have to be worried about using exact keys.
 * See more details at {@link org.apache.camel.util.CaseInsensitiveMap}.
 * The implementation of the map can be configured by the {@link HeadersMapFactory} which can be set
 * on the {@link CamelContext}. The default implementation uses the {@link org.apache.camel.util.CaseInsensitiveMap CaseInsensitiveMap}.
 *
 * @version 
 */
public class DefaultMessage extends MessageSupport {
    private boolean fault;
    private Map<String, Object> headers;
    private Map<String, DataHandler> attachments;
    private Map<String, Attachment> attachmentObjects;

    /**
     * @deprecated use {@link #DefaultMessage(CamelContext)}
     */
    @Deprecated
    public DefaultMessage() {
    }

    public DefaultMessage(CamelContext camelContext) {
        setCamelContext(camelContext);
    }

    public boolean isFault() {
        return fault;
    }

    public void setFault(boolean fault) {
        this.fault = fault;
    }

    public Object getHeader(String name) {
        if (hasHeaders()) {
            return getHeaders().get(name);
        } else {
            return null;
        }
    }

    public Object getHeader(String name, Object defaultValue) {
        Object answer = getHeaders().get(name);
        return answer != null ? answer : defaultValue;
    }

    public Object getHeader(String name, Supplier<Object> defaultValueSupplier) {
        ObjectHelper.notNull(name, "name");
        ObjectHelper.notNull(defaultValueSupplier, "defaultValueSupplier");
        Object answer = getHeaders().get(name);
        return answer != null ? answer : defaultValueSupplier.get();
    }

    @SuppressWarnings("unchecked")
    public <T> T getHeader(String name, Class<T> type) {
        Object value = getHeader(name);
        if (value == null) {
            // lets avoid NullPointerException when converting to boolean for null values
            if (boolean.class == type) {
                return (T) Boolean.FALSE;
            }
            return null;
        }

        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already same type
        if (type.isInstance(value)) {
            return type.cast(value);
        }

        Exchange e = getExchange();
        if (e != null) {
            return e.getContext().getTypeConverter().convertTo(type, e, value);
        } else {
            return type.cast(value);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getHeader(String name, Object defaultValue, Class<T> type) {
        Object value = getHeader(name, defaultValue);
        if (value == null) {
            // lets avoid NullPointerException when converting to boolean for null values
            if (boolean.class == type) {
                return (T) Boolean.FALSE;
            }
            return null;
        }

        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already same type
        if (type.isInstance(value)) {
            return type.cast(value);
        }

        Exchange e = getExchange();
        if (e != null) {
            return e.getContext().getTypeConverter().convertTo(type, e, value);
        } else {
            return type.cast(value);
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getHeader(String name, Supplier<Object> defaultValueSupplier, Class<T> type) {
        ObjectHelper.notNull(name, "name");
        ObjectHelper.notNull(type, "type");
        ObjectHelper.notNull(defaultValueSupplier, "defaultValueSupplier");
        Object value = getHeader(name, defaultValueSupplier);
        if (value == null) {
            // lets avoid NullPointerException when converting to boolean for null values
            if (boolean.class == type) {
                return (T) Boolean.FALSE;
            }
            return null;
        }

        // eager same instance type test to avoid the overhead of invoking the type converter
        // if already same type
        if (type.isInstance(value)) {
            return type.cast(value);
        }

        Exchange e = getExchange();
        if (e != null) {
            return e.getContext().getTypeConverter().convertTo(type, e, value);
        } else {
            return type.cast(value);
        }
    }

    public void setHeader(String name, Object value) {
        if (headers == null) {
            headers = createHeaders();
        }
        headers.put(name, value);
    }

    public Object removeHeader(String name) {
        if (!hasHeaders()) {
            return null;
        }
        return headers.remove(name);
    }

    public boolean removeHeaders(String pattern) {
        return removeHeaders(pattern, (String[]) null);
    }

    public boolean removeHeaders(String pattern, String... excludePatterns) {
        if (!hasHeaders()) {
            return false;
        }

        boolean matches = false;
        // must use a set to store the keys to remove as we cannot walk using entrySet and remove at the same time
        // due concurrent modification error
        Set<String> toRemove = new HashSet<String>();
        for (Map.Entry<String, Object> entry : headers.entrySet()) {
            String key = entry.getKey();
            if (EndpointHelper.matchPattern(key, pattern)) {
                if (excludePatterns != null && isExcludePatternMatch(key, excludePatterns)) {
                    continue;
                }
                matches = true;
                toRemove.add(entry.getKey());
            }
        }
        for (String key : toRemove) {
            headers.remove(key);
        }

        return matches;
    }

    public Map<String, Object> getHeaders() {
        if (headers == null) {
            headers = createHeaders();
        }
        return headers;
    }

    public void setHeaders(Map<String, Object> headers) {
        ObjectHelper.notNull(getCamelContext(), "CamelContext", this);

        if (getCamelContext().getHeadersMapFactory().isInstanceOf(headers)) {
            this.headers = headers;
        } else {
            // create a new map
            this.headers = getCamelContext().getHeadersMapFactory().newMap(headers);
        }
    }

    public boolean hasHeaders() {
        if (!hasPopulatedHeaders()) {
            // force creating headers
            getHeaders();
        }
        return headers != null && !headers.isEmpty();
    }

    public DefaultMessage newInstance() {
        ObjectHelper.notNull(getCamelContext(), "CamelContext", this);

        return new DefaultMessage(getCamelContext());
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
        ObjectHelper.notNull(getCamelContext(), "CamelContext", this);

        Map<String, Object> map = getCamelContext().getHeadersMapFactory().newMap();
        populateInitialHeaders(map);
        return map;
    }

    /**
     * A factory method to lazily create the attachmentObjects to make it easy to
     * create efficient Message implementations which only construct and
     * populate the Map on demand
     *
     * @return return a newly constructed Map
     */
    protected Map<String, Attachment> createAttachments() {
        Map<String, Attachment> map = new LinkedHashMap<String, Attachment>();
        populateInitialAttachments(map);
        return map;
    }

    /**
     * A strategy method populate the initial set of headers on an inbound
     * message from an underlying binding
     *
     * @param map is the empty header map to populate
     */
    protected void populateInitialHeaders(Map<String, Object> map) {
        // do nothing by default
    }

    /**
     * A strategy method populate the initial set of attachmentObjects on an inbound
     * message from an underlying binding
     *
     * @param map is the empty attachment map to populate
     */
    protected void populateInitialAttachments(Map<String, Attachment> map) {
        // do nothing by default
    }

    /**
     * A strategy for component specific messages to determine whether the
     * message is redelivered or not.
     * <p/>
     * <b>Important: </b> It is not always possible to determine if the transacted is a redelivery
     * or not, and therefore <tt>null</tt> is returned. Such an example would be a JDBC message.
     * However JMS brokers provides details if a transacted message is redelivered.
     *
     * @return <tt>true</tt> if redelivered, <tt>false</tt> if not, <tt>null</tt> if not able to determine
     */
    protected Boolean isTransactedRedelivered() {
        // return null by default
        return null;
    }

    public void addAttachment(String id, DataHandler content) {
        addAttachmentObject(id, new DefaultAttachment(content));
    }

    public void addAttachmentObject(String id, Attachment content) {
        if (attachmentObjects == null) {
            attachmentObjects = createAttachments();
        }
        attachmentObjects.put(id, content);
    }

    public DataHandler getAttachment(String id) {
        Attachment att = getAttachmentObject(id);
        if (att == null) {
            return null;
        } else {
            return att.getDataHandler();
        }
    }

    @Override
    public Attachment getAttachmentObject(String id) {
        return getAttachmentObjects().get(id);
    }

    public Set<String> getAttachmentNames() {
        if (attachmentObjects == null) {
            attachmentObjects = createAttachments();
        }
        return attachmentObjects.keySet();
    }

    public void removeAttachment(String id) {
        if (attachmentObjects != null && attachmentObjects.containsKey(id)) {
            attachmentObjects.remove(id);
        }
    }

    public Map<String, DataHandler> getAttachments() {
        if (attachments == null) {
            attachments = new AttachmentMap(getAttachmentObjects());
        }
        return attachments;
    }

    public Map<String, Attachment> getAttachmentObjects() {
        if (attachmentObjects == null) {
            attachmentObjects = createAttachments();
        }
        return attachmentObjects;
    }
    
    public void setAttachments(Map<String, DataHandler> attachments) {
        if (attachments == null) {
            this.attachmentObjects = null;
        } else if (attachments instanceof AttachmentMap) {
            // this way setAttachments(getAttachments()) will tunnel attachment headers
            this.attachmentObjects = ((AttachmentMap)attachments).getOriginalMap();
        } else {
            this.attachmentObjects = new LinkedHashMap<String, Attachment>();
            for (Map.Entry<String, DataHandler> entry : attachments.entrySet()) {
                this.attachmentObjects.put(entry.getKey(), new DefaultAttachment(entry.getValue()));
            }
        }
        this.attachments = null;
    }

    public void setAttachmentObjects(Map<String, Attachment> attachments) {
        this.attachmentObjects = attachments;
        this.attachments = null;
    }

    public boolean hasAttachments() {
        // optimized to avoid calling createAttachments as that creates a new empty map
        // that we 99% do not need (only camel-mail supports attachments), and we have
        // then ensure camel-mail always creates attachments to remedy for this
        return this.attachmentObjects != null && this.attachmentObjects.size() > 0;
    }

    /**
     * Returns true if the headers have been mutated in some way
     */
    protected boolean hasPopulatedHeaders() {
        return headers != null;
    }

    public String createExchangeId() {
        return null;
    }

    private static boolean isExcludePatternMatch(String key, String... excludePatterns) {
        for (String pattern : excludePatterns) {
            if (EndpointHelper.matchPattern(key, pattern)) {
                return true;
            }
        }
        return false;
    }

}
