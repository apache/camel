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
package org.apache.camel.component.jcr;

import java.net.URI;
import java.net.URISyntaxException;

import javax.jcr.Credentials;
import javax.jcr.Repository;
import javax.jcr.SimpleCredentials;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * A JCR endpoint
 */
public class JcrEndpoint extends DefaultEndpoint {

    private Credentials credentials;
    private Repository repository;
    private String base;

    private int eventTypes;
    private boolean deep;
    private String uuids;
    private String nodeTypeNames;
    private boolean noLocal;

    private long sessionLiveCheckIntervalOnStart = 3000L;
    private long sessionLiveCheckInterval = 60000L;

    protected JcrEndpoint(String endpointUri, JcrComponent component) {
        super(endpointUri, component);
        try {
            URI uri = new URI(endpointUri);
            if (uri.getUserInfo() != null) {
                String[] creds = uri.getUserInfo().split(":");
                if (creds != null) {
                    String username = creds[0];
                    String password = creds.length > 1 ? creds[1] : null;
                    this.credentials = new SimpleCredentials(username, password.toCharArray());
                }
            }
            this.repository = component.getCamelContext().getRegistry().lookupByNameAndType(uri.getHost(), Repository.class);
            if (repository == null) {
                throw new RuntimeCamelException("No JCR repository defined under '" + uri.getHost() + "'");
            }
            this.base = uri.getPath().replaceAll("^/", "");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + endpointUri, e);
        }
    }

    /**
     * Currently unsupported
     * @throws RuntimeCamelException
     */
    public Consumer createConsumer(Processor processor) throws Exception {
        return new JcrConsumer(this, processor);
    }

    public Producer createProducer() throws Exception {
        return new JcrProducer(this);
    }

    public boolean isSingleton() {
        return false;
    }

    /**
     * Get the {@link Repository}
     * 
     * @return the repository
     */
    protected Repository getRepository() {
        return repository;
    }

    /**
     * Get the {@link Credentials} for establishing the JCR repository connection
     * 
     * @return the credentials
     */
    protected Credentials getCredentials() {
        return credentials;
    }

    /**
     * Get the base node when accessing the reposititory
     * 
     * @return the base node
     */
    protected String getBase() {
        return base;
    }

    /**
     * <code>eventTypes</code> (a combination of one or more event types encoded
     * as a bit mask value such as javax.jcr.observation.Event.NODE_ADDED, javax.jcr.observation.Event.NODE_REMOVED, etc.).
     * 
     * @return eventTypes
     * @see {@link javax.jcr.observation.Event}
     * @see {@link javax.jcr.observation.ObservationManager#addEventListener(javax.jcr.observation.EventListener, int, String, boolean, String[], String[], boolean)}
     */
    public int getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(int eventTypes) {
        this.eventTypes = eventTypes;
    }

    /**
     * When <code>isDeep</code> is true, events whose associated parent node is at
     * <code>absPath</code> or within its subgraph are received.
     * @return deep
     */
    public boolean isDeep() {
        return deep;
    }

    public void setDeep(boolean deep) {
        this.deep = deep;
    }

    /**
     * When a comma separated uuid list string is set, only events whose associated parent node has one of
     * the identifiers in the comma separated uuid list will be received.
     * @return comma separated uuid list string
     */
    public String getUuids() {
        return uuids;
    }

    public void setUuids(String uuids) {
        this.uuids = uuids;
    }

    /**
     * When a comma separated <code>nodeTypeName</code> list string is set, only events whose associated parent node has
     * one of the node types (or a subtype of one of the node types) in this
     * list will be received.
     * @return
     */
    public String getNodeTypeNames() {
        return nodeTypeNames;
    }

    public void setNodeTypeNames(String nodeTypeNames) {
        this.nodeTypeNames = nodeTypeNames;
    }

    /**
     * If <code>noLocal</code> is <code>true</code>, then events
     * generated by the session through which the listener was registered are
     * ignored. Otherwise, they are not ignored.
     * @return noLocal
     */
    public boolean isNoLocal() {
        return noLocal;
    }

    public void setNoLocal(boolean noLocal) {
        this.noLocal = noLocal;
    }

    /**
     * Interval in milliseconds to wait before the first session live checking.
     * The default value is 3000 ms.
     * 
     * @return sessionLiveCheckIntervalOnStart
     */
    public long getSessionLiveCheckIntervalOnStart() {
        return sessionLiveCheckIntervalOnStart;
    }

    public void setSessionLiveCheckIntervalOnStart(long sessionLiveCheckIntervalOnStart) {
        if (sessionLiveCheckIntervalOnStart <= 0) {
            throw new IllegalArgumentException("sessionLiveCheckIntervalOnStart must be positive value");
        }

        this.sessionLiveCheckIntervalOnStart = sessionLiveCheckIntervalOnStart;
    }

    /**
     * Interval in milliseconds to wait before each session live checking
     * The default value is 60000 ms.
     * 
     * @return
     */
    public long getSessionLiveCheckInterval() {
        return sessionLiveCheckInterval;
    }

    public void setSessionLiveCheckInterval(long sessionLiveCheckInterval) {
        if (sessionLiveCheckInterval <= 0) {
            throw new IllegalArgumentException("sessionLiveCheckInterval must be positive value");
        }

        this.sessionLiveCheckInterval = sessionLiveCheckInterval;
    }

    /**
     * Gets the destination name which was configured from the endpoint uri.
     *
     * @return the destination name resolved from the endpoint uri
     */
    public String getEndpointConfiguredDestinationName() {
        String remainder = ObjectHelper.after(getEndpointKey(), "//");

        if (remainder != null && remainder.contains("@")) {
            remainder = remainder.substring(remainder.indexOf('@'));
        }

        if (remainder != null && remainder.contains("?")) {
            // remove parameters
            remainder = ObjectHelper.before(remainder, "?");
        }

        if (ObjectHelper.isEmpty(remainder)) {
            return remainder;
        }

        return remainder;
    }
}
