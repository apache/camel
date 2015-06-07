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
import javax.activation.DataHandler;

import org.apache.camel.Exchange;
import org.apache.camel.util.CaseInsensitiveMap;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.MessageHelper;

/**
 * The default implementation of {@link org.apache.camel.Message}
 * <p/>
 * This implementation uses a {@link org.apache.camel.util.CaseInsensitiveMap} storing the headers.
 * This allows us to be able to lookup headers using case insensitive keys, making it easier for end users
 * as they do not have to be worried about using exact keys.
 * See more details at {@link org.apache.camel.util.CaseInsensitiveMap}.
 *
 * @version 
 */
public class DefaultMessage extends MessageSupport {
    private boolean fault;
    private Map<String, Object> headers;
    private Map<String, DataHandler> attachments;

    @Override
    public String toString() {
        return MessageHelper.extractBodyForLogging(this);
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

    @SuppressWarnings("unchecked")
    public <T> T getHeader(String name, Class<T> type) {
        Object value = getHeader(name);
        if (value == null) {
            // lets avoid NullPointerException when converting to boolean for null values
            if (boolean.class.isAssignableFrom(type)) {
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
            if (boolean.class.isAssignableFrom(type)) {
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
        if (headers instanceof CaseInsensitiveMap) {
            this.headers = headers;
        } else {
            // wrap it in a case insensitive map
            this.headers = new CaseInsensitiveMap(headers);
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
        Map<String, Object> map = new CaseInsensitiveMap();
        populateInitialHeaders(map);
        return map;
    }

    /**
     * A factory method to lazily create the attachments to make it easy to
     * create efficient Message implementations which only construct and
     * populate the Map on demand
     *
     * @return return a newly constructed Map
     */
    protected Map<String, DataHandler> createAttachments() {
        Map<String, DataHandler> map = new LinkedHashMap<String, DataHandler>();
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
     * A strategy method populate the initial set of attachments on an inbound
     * message from an underlying binding
     *
     * @param map is the empty attachment map to populate
     */
    protected void populateInitialAttachments(Map<String, DataHandler> map) {
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
        // optimized to avoid calling createAttachments as that creates a new empty map
        // that we 99% do not need (only camel-mail supports attachments), and we have
        // then ensure camel-mail always creates attachments to remedy for this
        return this.attachments != null && this.attachments.size() > 0;
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
