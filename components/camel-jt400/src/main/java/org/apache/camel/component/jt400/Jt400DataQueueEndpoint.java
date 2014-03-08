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
import java.net.URISyntaxException;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400ConnectionPool;
import com.ibm.as400.access.BaseDataQueue;
import com.ibm.as400.access.DataQueue;
import com.ibm.as400.access.KeyedDataQueue;
import org.apache.camel.CamelException;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

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

    /**
     * Encapsulates the base endpoint options and functionality.
     */
    private final Jt400Endpoint baseEndpoint;

    /**
     * @deprecated Used by {@link #getDataQueue()}, which is deprecated.
     */
    @Deprecated
    private BaseDataQueue dataQueue;

    private boolean keyed;
    private String searchKey;
    private SearchType searchType = SearchType.EQ;

    /**
     * Creates a new AS/400 data queue endpoint using a default connection pool
     * provided by the component.
     * 
     * @throws NullPointerException if {@code component} is null
     */
    protected Jt400DataQueueEndpoint(String endpointUri, Jt400Component component) throws CamelException {
        this(endpointUri, component, component.getConnectionPool());
    }

    /**
     * Creates a new AS/400 data queue endpoint using the specified connection
     * pool.
     */
    protected Jt400DataQueueEndpoint(String endpointUri, Jt400Component component, AS400ConnectionPool connectionPool) throws CamelException {
        super(endpointUri, component);
        ObjectHelper.notNull(connectionPool, "connectionPool");
        try {
            baseEndpoint = new Jt400Endpoint(endpointUri, connectionPool);
        } catch (URISyntaxException e) {
            throw new CamelException("Unable to parse URI for " + URISupport.sanitizeUri(endpointUri), e);
        }
    }

    public void setCcsid(int ccsid) throws PropertyVetoException {
        baseEndpoint.setCcsid(ccsid);
    }

    public void setFormat(Format format) {
        baseEndpoint.setFormat(format);
    }

    public Format getFormat() {
        return baseEndpoint.getFormat();
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
        baseEndpoint.setGuiAvailable(guiAvailable);
    }

    @Override
    public PollingConsumer createPollingConsumer() throws Exception {
        Jt400DataQueueConsumer answer = new Jt400DataQueueConsumer(this);
        configurePollingConsumer(answer);
        return answer;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new Jt400DataQueueProducer(this);
    }

    /**
     * Obtains an {@code AS400} object that connects to this endpoint. Since
     * these objects represent limited resources, clients have the
     * responsibility of {@link #releaseSystem(AS400) releasing them} when done.
     * 
     * @return an {@code AS400} object that connects to this endpoint
     */
    protected AS400 getSystem() {
        return baseEndpoint.getConnection();
    }
    
    /**
     * Releases a previously obtained {@code AS400} object from use.
     * 
     * @param system a previously obtained {@code AS400} object
     */
    protected void releaseSystem(AS400 system) {
        baseEndpoint.releaseConnection(system);
    }

    /**
     * @deprecated This method does not benefit from connection pooling; data
     *             queue instances should be constructed with a connection
     *             obtained by {@link #getSystem()}.
     */
    @Deprecated
    protected BaseDataQueue getDataQueue() {
        if (dataQueue == null) {
            AS400 system = new AS400(baseEndpoint.getSystemName(), baseEndpoint.getUserID(), baseEndpoint.getPassword());
            String objectPath = baseEndpoint.getObjectPath();
            dataQueue = keyed ? new KeyedDataQueue(system, objectPath) : new DataQueue(system, objectPath);
        }
        return dataQueue;
    }
    
    /**
     * Returns the fully qualified integrated file system path name of the data
     * queue of this endpoint.
     * 
     * @return the fully qualified integrated file system path name of the data
     *         queue of this endpoint
     */
    protected String getObjectPath() {
        return baseEndpoint.getObjectPath();
    }

    public boolean isSingleton() {
        return false;
    }

}
