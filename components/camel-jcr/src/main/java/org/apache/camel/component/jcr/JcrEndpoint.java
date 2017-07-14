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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

/**
 * The jcr component allows you to add/read nodes to/from a JCR compliant content repository.
 */
@UriEndpoint(firstVersion = "1.3.0", scheme = "jcr", title = "JCR", syntax = "jcr:host/base", alternativeSyntax = "jcr:username:password@host/base",
        consumerClass = JcrConsumer.class, label = "cms,database")
public class JcrEndpoint extends DefaultEndpoint {

    private Credentials credentials;
    private Repository repository;

    @UriPath @Metadata(required = "true")
    private String host;
    @UriPath
    private String base;
    @UriParam
    private String username;
    @UriParam
    private String password;
    @UriParam
    private int eventTypes;
    @UriParam
    private boolean deep;
    @UriParam
    private String uuids;
    @UriParam
    private String nodeTypeNames;
    @UriParam
    private boolean noLocal;
    @UriParam(defaultValue = "3000")
    private long sessionLiveCheckIntervalOnStart = 3000L;
    @UriParam(defaultValue = "60000")
    private long sessionLiveCheckInterval = 60000L;
    @UriParam
    private String workspaceName;

    protected JcrEndpoint(String endpointUri, JcrComponent component) {
        super(endpointUri, component);
        try {
            URI uri = new URI(endpointUri);
            if (uri.getUserInfo() != null) {
                String[] creds = uri.getUserInfo().split(":");
                this.username = creds[0];
                this.password = creds.length > 1 ? creds[1] : "";
            }
            this.host = uri.getHost();
            this.base = uri.getPath().replaceAll("^/", "");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + endpointUri, e);
        }
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        JcrConsumer answer = new JcrConsumer(this, processor);
        configureConsumer(answer);
        return answer;
    }

    public Producer createProducer() throws Exception {
        return new JcrProducer(this);
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ObjectHelper.notEmpty(host, "host", this);

        this.repository = getCamelContext().getRegistry().lookupByNameAndType(host, Repository.class);
        if (repository == null) {
            throw new RuntimeCamelException("No JCR repository defined under '" + host + "'");
        }
        if (username != null && password != null) {
            this.credentials = new SimpleCredentials(username, password.toCharArray());
        }
    }

    public String getHost() {
        return host;
    }

    /**
     * Name of the {@link javax.jcr.Repository} to lookup from the Camel registry to be used.
     */
    public void setHost(String host) {
        this.host = host;
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
     * Get the base node when accessing the repository
     * 
     * @return the base node
     */
    protected String getBase() {
        return base;
    }

    public void setBase(String base) {
        this.base = base;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Username for login
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password for login
     */
    public void setPassword(String password) {
        this.password = password;
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
     * The workspace to access. If it's not specified then the default one will be used
     */
    public String getWorkspaceName() {
        return workspaceName;
    }

    public void setWorkspaceName(String workspaceName) {
        this.workspaceName = workspaceName;
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
