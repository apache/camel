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
import java.util.Arrays;
import java.util.Map;
import javax.naming.OperationNotSupportedException;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400ConnectionPool;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelException;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.jt400.Jt400DataQueueEndpoint.Format;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

public class Jt400PgmEndpoint extends DefaultEndpoint {
    /**
     * Encapsulates the base endpoint options and functionality.
     */
    private final Jt400Endpoint baseEndpoint;

    private Integer[] outputFieldsIdxArray;
    private Integer[] outputFieldsLengthArray;

    /**
     * Creates a new AS/400 PGM CALL endpoint using a default connection pool
     * provided by the component.
     * 
     * @throws NullPointerException if {@code component} is null
     */
    protected Jt400PgmEndpoint(String endpointUri, Jt400Component component) throws CamelException {
        this(endpointUri, component, component.getConnectionPool());
    }

    /**
     * Creates a new AS/400 PGM CALL endpoint using the specified connection
     * pool.
     */
    protected Jt400PgmEndpoint(String endpointUri, Jt400Component component, AS400ConnectionPool connectionPool) throws CamelException {
        super(endpointUri, component);
        ObjectHelper.notNull(connectionPool, "connectionPool");
        try {
            baseEndpoint = new Jt400Endpoint(endpointUri, connectionPool);
        } catch (URISyntaxException e) {
            throw new CamelException("Unable to parse URI for " + URISupport.sanitizeUri(endpointUri), e);
        }
    }

    @SuppressWarnings("deprecation")
    public Jt400PgmEndpoint(String endpointUri, String programToExecute, Map<String, Object> parameters,
                            CamelContext camelContext) {
        super(endpointUri, camelContext);
        ObjectHelper.notNull(parameters, "parameters", this);
        if (!parameters.containsKey(Jt400Component.CONNECTION_POOL)) {
            throw new RuntimeCamelException(String.format("parameters must specify '%s'", Jt400Component.CONNECTION_POOL));
        }
        String poolId = parameters.get(Jt400Component.CONNECTION_POOL).toString();
        AS400ConnectionPool connectionPool = EndpointHelper.resolveReferenceParameter(camelContext, poolId, AS400ConnectionPool.class, true);
        try {
            this.baseEndpoint = new Jt400Endpoint(endpointUri, connectionPool);
        } catch (URISyntaxException e) {
            throw new RuntimeCamelException("Unable to parse URI for " + endpointUri, e);
        }
    }

    public Producer createProducer() throws Exception {
        return new Jt400PgmProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new OperationNotSupportedException();
    }

    public boolean isSingleton() {
        // cannot be singleton as we store an AS400 instance on this endpoint
        return false;
    }

    public boolean isFieldIdxForOuput(int idx) {
        return Arrays.binarySearch(outputFieldsIdxArray, idx) >= 0;
    }

    public int getOutputFieldLength(int idx) {
        return outputFieldsLengthArray[idx];
    }

    // getters and setters
    public String getProgramToExecute() {
        return baseEndpoint.getObjectPath();
    }

    /**
     * Obtains an {@code AS400} object that connects to this endpoint. Since
     * these objects represent limited resources, clients have the
     * responsibility of {@link #releaseiSeries(AS400) releasing them} when
     * done.
     * 
     * @return an {@code AS400} object that connects to this endpoint
     */
    public AS400 getiSeries() {
        return baseEndpoint.getConnection();
    }
    
    /**
     * Releases a previously obtained {@code AS400} object from use.
     * 
     * @param iSeries a previously obtained {@code AS400} object
     */
    public void releaseiSeries(AS400 iSeries) {
        baseEndpoint.releaseConnection(iSeries);
    }

    public void setOutputFieldsIdx(String outputFieldsIdx) {
        if (outputFieldsIdx != null) {
            String[] outputArray = outputFieldsIdx.split(",");
            outputFieldsIdxArray = new Integer[outputArray.length];
            for (int i = 0; i < outputArray.length; i++) {
                String str = outputArray[i];
                outputFieldsIdxArray[i] = Integer.parseInt(str);
            }
        }
    }

    public void setFieldsLength(String fieldsLength) {
        if (fieldsLength != null) {
            String[] outputArray = fieldsLength.split(",");
            outputFieldsLengthArray = new Integer[outputArray.length];
            for (int i = 0; i < outputArray.length; i++) {
                String str = outputArray[i];
                outputFieldsLengthArray[i] = Integer.parseInt(str);
            }
        }
    }

    public void setFormat(Format format) {
        baseEndpoint.setFormat(format);
    }

    public Format getFormat() {
        return baseEndpoint.getFormat();
    }

    public void setGuiAvailable(boolean guiAvailable) throws PropertyVetoException {
        baseEndpoint.setGuiAvailable(guiAvailable);
    }

    public boolean isGuiAvailable() {
        return baseEndpoint.isGuiAvailable();
    }

}
