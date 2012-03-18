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
package org.apache.camel.component.jt400;

import java.beans.PropertyVetoException;
import java.net.URI;
import java.net.URISyntaxException;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.BaseDataQueue;
import com.ibm.as400.access.DataQueue;
import com.ibm.as400.access.KeyedDataQueue;
import org.apache.camel.CamelException;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AS/400 Data queue endpoint
 */
public class Jt400DataQueueEndpoint extends DefaultPollingEndpoint {

    public static final String KEY = "KEY";
    public static final String SENDER_INFORMATION = "SENDER_INFORMATION";

    /**
     * SearchTypes for reading from Keyed Data Queues
     */
    public enum SearchType {
        EQ, NE, LT, LE, GT, GE;
    }

    /**
     * Enumeration of supported data formats
     */
    public enum Format {
        /**
         * Using <code>String</code> for transferring data
         */
        text,

        /**
         * Using <code>byte[]</code> for transferring data
         */
        binary;
    }

    private static final transient Logger LOG = LoggerFactory.getLogger(Jt400DataQueueEndpoint.class);

    private final AS400 system;
    private final String objectPath;
    private BaseDataQueue dataQueue;
    private Format format = Format.text;
    private boolean keyed;
    private String searchKey;
    private SearchType searchType = SearchType.EQ;

    /**
     * Creates a new AS/400 data queue endpoint
     */
    protected Jt400DataQueueEndpoint(String endpointUri, Jt400Component component) throws CamelException {
        super(endpointUri, component);
        try {
            URI uri = new URI(endpointUri);
            String[] credentials = uri.getUserInfo().split(":");
            system = new AS400(uri.getHost(), credentials[0], credentials[1]);
            objectPath = uri.getPath();
        } catch (URISyntaxException e) {
            throw new CamelException("Unable to parse URI for " + endpointUri, e);
        }

        try {
            system.setGuiAvailable(false);
        } catch (PropertyVetoException e) {
            LOG.warn("Failed to disable AS/400 prompting in the environment running Camel. This exception will be ignored.", e);
        }
    }

    public void setCcsid(int ccsid) throws PropertyVetoException {
        this.system.setCcsid(ccsid);
    }

    public void setFormat(Format format) {
        this.format = format;
    }

    public Format getFormat() {
        return format;
    }

    public void setKeyed(boolean keyed) {
        this.keyed = keyed;
    }

    public boolean isKeyed() {
        return keyed;
    }

    public void setSearchKey(String searchKey) {
        this.searchKey = searchKey;
    }

    public String getSearchKey() {
        return searchKey;
    }

    public void setSearchType(SearchType searchType) {
        this.searchType = searchType;
    }

    public SearchType getSearchType() {
        return searchType;
    }

    public void setGuiAvailable(boolean guiAvailable) throws PropertyVetoException {
        this.system.setGuiAvailable(guiAvailable);
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        return new Jt400DataQueueConsumer(this);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Jt400DataQueueProducer(this);
    }

    protected AS400 getSystem() {
        return system;
    }

    protected BaseDataQueue getDataQueue() {
        if (dataQueue == null) {
            dataQueue = keyed ? new KeyedDataQueue(system, objectPath) : new DataQueue(system, objectPath);
        }
        return dataQueue;
    }

    public boolean isSingleton() {
        return false;
    }

}
