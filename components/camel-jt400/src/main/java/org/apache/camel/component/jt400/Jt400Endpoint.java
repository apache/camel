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

import java.net.URISyntaxException;
import java.util.Arrays;
import javax.naming.OperationNotSupportedException;
import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400ConnectionPool;
import org.apache.camel.CamelException;
import org.apache.camel.Consumer;
import org.apache.camel.MultipleConsumersSupport;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.ScheduledPollEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * The jt400 component allows you to exchanges messages with an AS/400 system using data queues or program call.
 */
@UriEndpoint(firstVersion = "1.5.0", scheme = "jt400", title = "JT400", syntax = "jt400:userID:password/systemName/objectPath.type", consumerClass = Jt400DataQueueConsumer.class, label = "messaging")
public class Jt400Endpoint extends ScheduledPollEndpoint implements MultipleConsumersSupport {

    public static final String KEY = "KEY";
    public static final String SENDER_INFORMATION = "SENDER_INFORMATION";

    @UriParam
    private final Jt400Configuration configuration;

    /**
     * Creates a new AS/400 data queue endpoint using a default connection pool
     * provided by the component.
     * 
     * @throws NullPointerException if {@code component} is null
     */
    protected Jt400Endpoint(String endpointUri, Jt400Component component) throws CamelException {
        this(endpointUri, component, component.getConnectionPool());
    }

    /**
     * Creates a new AS/400 data queue endpoint using the specified connection
     * pool.
     */
    protected Jt400Endpoint(String endpointUri, Jt400Component component, AS400ConnectionPool connectionPool) throws CamelException {
        super(endpointUri, component);
        ObjectHelper.notNull(connectionPool, "connectionPool");
        try {
            configuration = new Jt400Configuration(endpointUri, connectionPool);
        } catch (URISyntaxException e) {
            throw new CamelException("Unable to parse URI for " + URISupport.sanitizeUri(endpointUri), e);
        }
    }

    @Override
    public Producer createProducer() throws Exception {
        if (Jt400Type.DTAQ == configuration.getType()) {
            return new Jt400DataQueueProducer(this);
        } else {
            return new Jt400PgmProducer(this);
        }
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        if (Jt400Type.DTAQ == configuration.getType()) {
            Consumer consumer = new Jt400DataQueueConsumer(this, processor);
            configureConsumer(consumer);
            return consumer;
        } else {
            throw new OperationNotSupportedException();
        }
    }

    public boolean isSingleton() {
        // cannot be singleton as we store an AS400 instance on the configuration
        return false;
    }

    /**
     * Obtains an {@code AS400} object that connects to this endpoint. Since
     * these objects represent limited resources, clients have the
     * responsibility of {@link #releaseSystem(AS400) releasing them} when done.
     * 
     * @return an {@code AS400} object that connects to this endpoint
     */
    protected AS400 getSystem() {
        return configuration.getConnection();
    }
    
    /**
     * Releases a previously obtained {@code AS400} object from use.
     * 
     * @param system a previously obtained {@code AS400} object
     */
    protected void releaseSystem(AS400 system) {
        configuration.releaseConnection(system);
    }

    /**
     * Returns the fully qualified integrated file system path name of the data
     * queue of this endpoint.
     * 
     * @return the fully qualified integrated file system path name of the data
     *         queue of this endpoint
     */
    protected String getObjectPath() {
        return configuration.getObjectPath();
    }

    public Jt400Type getType() {
        return configuration.getType();
    }

    public void setType(Jt400Type type) {
        configuration.setType(type);
    }

    public String getSearchKey() {
        return configuration.getSearchKey();
    }

    public boolean isKeyed() {
        return configuration.isKeyed();
    }

    public Integer[] getOutputFieldsIdxArray() {
        return configuration.getOutputFieldsIdxArray();
    }

    public int getCcsid() {
        return configuration.getCcsid();
    }

    public void setOutputFieldsIdxArray(Integer[] outputFieldsIdxArray) {
        configuration.setOutputFieldsIdxArray(outputFieldsIdxArray);
    }

    public void setSearchKey(String searchKey) {
        configuration.setSearchKey(searchKey);
    }

    public void setOutputFieldsIdx(String outputFieldsIdx) {
        configuration.setOutputFieldsIdx(outputFieldsIdx);
    }

    public void setKeyed(boolean keyed) {
        configuration.setKeyed(keyed);
    }

    public Integer[] getOutputFieldsLengthArray() {
        return configuration.getOutputFieldsLengthArray();
    }

    public void setSearchType(Jt400Configuration.SearchType searchType) {
        configuration.setSearchType(searchType);
    }

    public boolean isGuiAvailable() {
        return configuration.isGuiAvailable();
    }

    public void setFormat(Jt400Configuration.Format format) {
        configuration.setFormat(format);
    }

    public void setFieldsLength(String fieldsLength) {
        configuration.setFieldsLength(fieldsLength);
    }

    public Jt400Configuration.Format getFormat() {
        return configuration.getFormat();
    }

    public void setOutputFieldsLengthArray(Integer[] outputFieldsLengthArray) {
        configuration.setOutputFieldsLengthArray(outputFieldsLengthArray);
    }

    public int getCssid() {
        return configuration.getCssid();
    }

    public String getUserID() {
        return configuration.getUserID();
    }

    public Jt400Configuration.SearchType getSearchType() {
        return configuration.getSearchType();
    }

    public void setCcsid(int ccsid) {
        configuration.setCcsid(ccsid);
    }

    public void setGuiAvailable(boolean guiAvailable) {
        configuration.setGuiAvailable(guiAvailable);
    }

    public String getPassword() {
        return configuration.getPassword();
    }

    public String getSystemName() {
        return configuration.getSystemName();
    }

    public boolean isFieldIdxForOuput(int idx) {
        return Arrays.binarySearch(getOutputFieldsIdxArray(), idx) >= 0;
    }

    public int getOutputFieldLength(int idx) {
        return configuration.getOutputFieldsLengthArray()[idx];
    }

    public void setObjectPath(String objectPath) {
        configuration.setObjectPath(objectPath);
    }

    public void setPassword(String password) {
        configuration.setPassword(password);
    }

    public void setUserID(String userID) {
        configuration.setUserID(userID);
    }

    public void setSystemName(String systemName) {
        configuration.setSystemName(systemName);
    }

    public void setSecured(boolean secured) {
        configuration.setSecured(secured);
    }

    public boolean isSecured() {
        return configuration.isSecured();
    }

    public int getReadTimeout() {
        return configuration.getReadTimeout();
    }

    public void setReadTimeout(int readTimeout) {
        configuration.setReadTimeout(readTimeout);
    }

    public void setProcedureName(String procedureName) {
        configuration.setProcedureName(procedureName);
    }

    public String getProcedureName() {
        return configuration.getProcedureName();
    }

    @Override
    public boolean isMultipleConsumersSupported() {
        return true;
    }

}
