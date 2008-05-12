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
import org.apache.camel.impl.DefaultExchange;

/**
 * A JCR endpoint
 */
public class JcrEndpoint extends DefaultEndpoint<DefaultExchange> {

    private Credentials credentials;
    private Repository repository;
    private String base;

    @SuppressWarnings("unchecked")
    protected JcrEndpoint(String endpointUri, JcrComponent component) {
        super(endpointUri, component);
        try {
            URI uri = new URI(endpointUri);
            if (uri.getUserInfo() != null && uri.getAuthority() != null) {
                this.credentials = new SimpleCredentials(uri.getUserInfo(), uri.getAuthority().toCharArray());
            }
            this.repository = (Repository) component.getCamelContext().getRegistry().lookup(uri.getHost());
            if (repository == null) {
                throw new RuntimeCamelException("No JCR repository defined under '" + uri.getHost() + "'");
            }
            this.base = uri.getPath().replaceAll("^/", "");
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URI: " + endpointUri, e);
        }
    }

    public JcrEndpoint(String endpointUri, String base, Credentials credentials, Repository repository) {
        super(endpointUri);
        this.base = base;
        this.credentials = credentials;
        this.repository = repository;
    }

    /**
     * Currently unsupported
     * @throws RuntimeCamelException
     */
    public Consumer<DefaultExchange> createConsumer(Processor processor) throws Exception {
        throw new RuntimeCamelException("No consumer endpoint support for JCR available");
    }

    /**
     * Creates a new {@link Producer} 
     */
    public Producer<DefaultExchange> createProducer() throws Exception {
        return new JcrProducer(this);
    }

    /**
     * {@inheritDoc}
     */
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

}
