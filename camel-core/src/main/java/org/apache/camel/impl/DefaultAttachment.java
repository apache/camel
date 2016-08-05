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

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.activation.DataHandler;
import javax.activation.DataSource;

import org.apache.camel.Attachment;
import org.apache.camel.util.CollectionHelper;

public class DefaultAttachment implements Attachment {
    private Map<String, Object> headers;
    private DataHandler dataHandler;

    public DefaultAttachment(DataHandler dh) {
        dataHandler = dh;
    }

    public DefaultAttachment(DataSource ds) {
        dataHandler = new DataHandler(ds);
    }

    @Override
    public DataHandler getDataHandler() {
        return dataHandler;
    }

    @Override
    public String getHeader(String name) {
        if (headers != null) {
            Object headerObject = headers.get(name);
            if (headerObject instanceof String) {
                return (String)headerObject;
            } else if (headerObject instanceof Collection<?>) {
                return CollectionHelper.collectionAsCommaDelimitedString((Collection<?>)headerObject);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> getHeaderAsList(String name) {
        if (headers != null) {
            Object headerObject = headers.get(name);
            if (headerObject instanceof List<?>) {
                return (List<String>)headerObject;
            } else if (headerObject instanceof String) {
                return Collections.singletonList((String)headerObject);
            }
        }
        return null;
    }

    @Override
    public void addHeader(String headerName, String headerValue) {
        if (headers == null) {
            headers = createHeaders();
        }
        CollectionHelper.appendValue(headers, headerName, headerValue);
    }

    @Override
    public void setHeader(String headerName, String headerValue) {
        if (headers == null) {
            headers = createHeaders();
        }
        headers.put(headerName, headerValue);
    }

    @Override
    public void removeHeader(String headerName) {
        if (headers != null) {
            headers.remove(headerName);
        }
    }

    @Override
    public Collection<String> getHeaderNames() {
        if (headers == null) {
            headers = createHeaders();
        }
        return headers.keySet();
    }

    public void clearHeaders() {
        headers = null;
    }

    private Map<String, Object> createHeaders() {
        return new TreeMap<String, Object>(String.CASE_INSENSITIVE_ORDER);
    }

    public boolean equals(Object other) {
        if (other instanceof Attachment) {
            DataHandler otherDh = ((Attachment)other).getDataHandler();
            return dataHandler.equals(otherDh);
        }
        return false;
    }

    public int hashCode() {
        return dataHandler.hashCode();
    }
}
