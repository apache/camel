/*
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
package org.apache.camel.component.openfga;

import javax.net.ssl.SSLContext;

import dev.openfga.sdk.api.client.OpenFgaClient;
import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Authorize an Exchange against an OpenFGA relationship graph, and maintain the relationship tuples it is authorized
 * against.
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "openfga", title = "OpenFGA",
             syntax = "openfga:operation", producerOnly = true, category = { Category.SECURITY },
             headersClass = OpenFgaConstants.class)
public class OpenFgaEndpoint extends DefaultEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(OpenFgaEndpoint.class);

    @UriPath(description = "The operation to perform. The operation is taken from the endpoint only: it is deliberately"
                           + " not overridable by a message header, so that an inbound message cannot turn a check into"
                           + " a tuple write, nor a check for one relation into a check for a weaker one.")
    @Metadata(required = true)
    private OpenFgaOperation operation;

    @UriParam
    private OpenFgaConfiguration configuration;

    private volatile OpenFgaAuthorizer authorizer;
    private volatile SSLContext sslContext;

    public OpenFgaEndpoint(final String uri, final Component component, final OpenFgaConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        OpenFgaClient client = configuration.getOpenFgaClient();
        if (client == null) {
            if (ObjectHelper.isEmpty(configuration.getStoreId())) {
                throw new IllegalArgumentException(
                        "storeId is required: it names the OpenFGA store holding the relationship tuples and the"
                                                   + " authorization model, as returned by 'fga store create'");
            }
            warnAboutUnpinnedModel();
            sslContext = createSslContext();
            client = OpenFgaClientFactory.createClient(configuration, sslContext);
        }
        validateOperationOptions();
        authorizer = new OpenFgaAuthorizer(client, configuration, getCamelContext());
    }

    /**
     * Fails fast on an operation that was given nothing to work with, rather than letting the first exchange discover
     * it - a missing {@code relation} on a check would otherwise surface as a deny, which looks exactly like a policy
     * decision and is a thoroughly misleading thing to debug.
     */
    private void validateOperationOptions() {
        switch (operation) {
            case check, batchCheck -> {
                require(configuration.getUser(), "user", "names the subject to authorize");
                require(configuration.getRelation(), "relation", "names the permission to demand");
                if (operation == OpenFgaOperation.check) {
                    require(configuration.getObject(), "object", "names the resource being accessed");
                }
            }
            case listObjects -> {
                require(configuration.getUser(), "user", "names the subject to list objects for");
                require(configuration.getRelation(), "relation", "names the relation to list objects through");
                require(configuration.getType(), "type", "names the object type to enumerate");
            }
            case listRelations -> {
                require(configuration.getUser(), "user", "names the subject to list relations for");
                require(configuration.getObject(), "object", "names the object to list relations on");
                require(configuration.getRelations(), "relations", "lists the relations to ask about");
            }
            case listUsers -> {
                require(configuration.getObject(), "object", "names the object to list users of");
                require(configuration.getRelation(), "relation", "names the relation to list users for");
            }
            default -> {
                // writeTuples and deleteTuples take their tuples from the message body, falling back to the
                // user/relation/object triple, so there is nothing that must be configured up front
            }
        }
    }

    private void require(String value, String option, String purpose) {
        if (ObjectHelper.isEmpty(value)) {
            throw new IllegalArgumentException(
                    option + " is required for the " + operation + " operation: it " + purpose);
        }
    }

    /**
     * An endpoint without a pinned {@code authorizationModelId} evaluates against whatever model the store considers
     * latest, which moves the moment somebody writes a new one. That is a reasonable default for getting started and a
     * poor one in production, and it is invisible in the route, so say it once at startup.
     */
    private void warnAboutUnpinnedModel() {
        if (ObjectHelper.isEmpty(configuration.getAuthorizationModelId())) {
            LOG.warn("authorizationModelId is not set on {}: OpenFGA will evaluate against store {}'s latest"
                     + " authorization model, which changes as soon as a new model is written. Pin the model id in"
                     + " production so that a model rollout is a deliberate configuration change.",
                    getEndpointKey(), configuration.getStoreId());
        }
    }

    /**
     * Resolves the endpoint's TLS configuration, falling back to the context's global one when the component opts in.
     */
    private SSLContext createSslContext() throws Exception {
        SSLContextParameters ssl = configuration.getSslContextParameters();
        if (ssl == null) {
            ssl = getComponent().retrieveGlobalSslContextParameters();
        }
        return ssl != null ? ssl.createSSLContext(getCamelContext()) : null;
    }

    /**
     * The TLS configuration the authorization call resolved to, so the producer's readiness check probes the server the
     * same way rather than failing a handshake the authorization call passes.
     */
    SSLContext getSslContext() {
        return sslContext;
    }

    @Override
    protected void doStop() throws Exception {
        // the SDK holds one HttpClient for the life of the client and, on the Java 17 baseline, HttpClient is not
        // AutoCloseable - so there is nothing to close here, only a reference to drop
        authorizer = null;
        super.doStop();
    }

    @Override
    public OpenFgaComponent getComponent() {
        return (OpenFgaComponent) super.getComponent();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new OpenFgaProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("Consumer not supported");
    }

    public OpenFgaOperation getOperation() {
        return operation;
    }

    public void setOperation(OpenFgaOperation operation) {
        this.operation = operation;
    }

    /**
     * The endpoint configuration.
     */
    public OpenFgaConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(OpenFgaConfiguration configuration) {
        this.configuration = configuration;
    }

    OpenFgaAuthorizer getAuthorizer() {
        return authorizer;
    }
}
