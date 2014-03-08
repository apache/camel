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
import com.ibm.as400.access.AS400ConnectionPool;
import com.ibm.as400.access.ConnectionPoolException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.jt400.Jt400DataQueueEndpoint.Format;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pseudo-abstract base JT400 endpoint. This class provides the options that
 * supported by both the {@link Jt400DataQueueEndpoint} and
 * {@link Jt400PgmEndpoint}, and also serves as a factory of connections to the
 * system.
 */
class Jt400Endpoint {
    
    /**
     * Logging tool.
     */
    private static final Logger LOG = LoggerFactory.getLogger(Jt400Endpoint.class);

    /**
     * Constant used to specify that the default system CCSID be used (a
     * negative CCSID is otherwise invalid).
     */
    private static final int DEFAULT_SYSTEM_CCSID = -1;
    
    /**
     * Name of the AS/400 system.
     */
    private final String systemName;
    
    /**
     * ID of the AS/400 user.
     */
    private final String userID;
    
    /**
     * Password of the AS/400 user.
     */
    private final String password;
    
    /**
     * Fully qualified integrated file system path name of the target object of
     * this endpoint (either data queue or program).
     */
    private final String objectPath;
    
    /**
     * Pool from which physical connections to the system are obtained.
     */
    private final AS400ConnectionPool connectionPool;
    
    /**
     * CCSID to use for the connection with the AS/400 system.
     */
    private int ccsid = DEFAULT_SYSTEM_CCSID;
    
    /**
     * Data format for sending messages.
     */
    private Format format = Format.text;
    
    /**
     * Whether AS/400 prompting is enabled in the environment running Camel.
     */
    private boolean guiAvailable;
    
    /**
     * Creates a new endpoint instance for the specified URI, which will use the
     * specified pool for obtaining physical connections to the system.
     * 
     * @param endpointUri URI of the endpoint
     * @param connectionPool pool for obtaining physical connections to the
     *            system
     * @throws URISyntaxException if unable to parse {@code endpointUri}
     * @throws IllegalArgumentException if either {@code endpointUri} or
     *             {@code connectionPool} are null
     */
    Jt400Endpoint(String endpointUri, AS400ConnectionPool connectionPool) throws URISyntaxException {
        ObjectHelper.notNull(endpointUri, "endpointUri", this);
        ObjectHelper.notNull(connectionPool, "connectionPool", this);
        
        URI uri = new URI(endpointUri);
        String[] credentials = uri.getUserInfo().split(":");
        systemName = uri.getHost();
        userID = credentials[0];
        password = credentials[1];
        objectPath = uri.getPath();
        
        this.connectionPool = connectionPool;
    }
    
    /**
     * Returns the name of the AS/400 system.
     * 
     * @return the name of the AS/400 system
     */
    public String getSystemName() {
        return systemName;
    }
    
    /**
     * Returns the ID of the AS/400 user.
     * 
     * @return the ID of the AS/400 user
     */
    public String getUserID() {
        return userID;
    }
    
    /**
     * Returns the password of the AS/400 user.
     * 
     * @return the password of the AS/400 user
     */
    public String getPassword() {
        return password;
    }
    
    /**
     * Returns the fully qualified integrated file system path name of the
     * target object of this endpoint.
     * 
     * @return the fully qualified integrated file system path name of the
     *         target object of this endpoint
     */
    public String getObjectPath() {
        return objectPath;
    }
    
    
    // Options
    
    /**
     * Returns the CCSID to use for the connection with the AS/400 system.
     * Returns -1 if the CCSID to use is the default system CCSID.
     * 
     * @return the CCSID to use for the connection with the AS/400 system, or -1
     *         if that is the default system CCSID
     */
    public int getCssid() {
        return ccsid;
    }
    
    /**
     * Sets the CCSID to use for the connection with the AS/400 system.
     * 
     * @param ccsid the CCSID to use for the connection with the AS/400 system
     */
    public void setCcsid(int ccsid) {
        this.ccsid = (ccsid < 0) ? DEFAULT_SYSTEM_CCSID : ccsid;
    }
    
    /**
     * Returns the data format for sending messages.
     * 
     * @return the data format for sending messages
     */
    public Format getFormat() {
        return format;
    }
    
    /**
     * Sets the data format for sending messages.
     * 
     * @param format the data format for sending messages
     * @throws IllegalArgumentException if {@code format} is null
     */
    public void setFormat(Format format) {
        ObjectHelper.notNull(format, "format", this);
        this.format = format;
    }
    
    /**
     * Returns whether AS/400 prompting is enabled in the environment running
     * Camel.
     * 
     * @return whether AS/400 prompting is enabled in the environment running
     *         Camel
     */
    public boolean isGuiAvailable() {
        return guiAvailable;
    }
    
    /**
     * Sets whether AS/400 prompting is enabled in the environment running
     * Camel.
     * 
     * @param guiAvailable whether AS/400 prompting is enabled in the
     *            environment running Camel
     */
    public void setGuiAvailable(boolean guiAvailable) {
        this.guiAvailable = guiAvailable;
    }
    
    
    // AS400 connections
    
    /**
     * Obtains an {@code AS400} object that connects to this endpoint. Since
     * these objects represent limited resources, clients have the
     * responsibility of {@link #releaseConnection(AS400) releasing them} when
     * done.
     * 
     * @return an {@code AS400} object that connects to this endpoint
     */
    public AS400 getConnection() {
        AS400 system = null;
        try {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Getting an AS400 object for '{}' from {}.", systemName + '/' + userID, connectionPool);
            }
            system = connectionPool.getConnection(systemName, userID, password);
            if (ccsid != DEFAULT_SYSTEM_CCSID) {
                system.setCcsid(ccsid);
            }
            try {
                system.setGuiAvailable(guiAvailable);
            } catch (PropertyVetoException e) {
                LOG.warn("Failed to disable AS/400 prompting in the environment running Camel. This exception will be ignored.", e);
            }
            return system; // Not null here.
        } catch (ConnectionPoolException e) {
            throw new RuntimeCamelException(String.format("Unable to obtain an AS/400 connection for system name '%s' and user ID '%s'", systemName, userID), e);
        } catch (PropertyVetoException e) {
            throw new RuntimeCamelException("Unable to set the CSSID to use with " + system, e);
        }
    }
    
    /**
     * Releases a previously obtained {@code AS400} object from use.
     * 
     * @param connection a previously obtained {@code AS400} object to release
     */
    public void releaseConnection(AS400 connection) {
        ObjectHelper.notNull(connection, "connection", this);
        connectionPool.returnConnectionToPool(connection);
    }

}
