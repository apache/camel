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
package org.apache.camel.component.master;

import org.apache.camel.Consumer;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.cluster.CamelClusterView;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Represents an endpoint which only becomes active when the {@link CamelClusterView}
 * has the leadership.
 */
@ManagedResource(description = "Managed Master Endpoint")
@UriEndpoint(
    firstVersion = "2.20.0",
    scheme = "master",
    syntax = "master:namespace:delegateUri",
    consumerOnly = true,
    title = "Master",
    lenientProperties = true,
    label = "clustering")
public class MasterEndpoint extends DefaultEndpoint implements DelegateEndpoint {
    private final Endpoint delegateEndpoint;
    private final CamelClusterService clusterService;

    @UriPath(description = "The name of the cluster namespace to use")
    @Metadata(required = true)
    private final String namespace;

    @UriPath(description = "The endpoint uri to use in master/slave mode")
    @Metadata(required = true)
    private final String delegateUri;

    public MasterEndpoint(String uri, MasterComponent component, CamelClusterService clusterService, String namespace, String delegateUri) {
        super(uri, component);

        this.clusterService = clusterService;
        this.namespace = namespace;
        this.delegateUri = delegateUri;
        this.delegateEndpoint = getCamelContext().getEndpoint(delegateUri);
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Cannot produce from this endpoint");
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        return new MasterConsumer(this, processor, clusterService);
    }

    @Override
    public boolean isLenientProperties() {
        // to allow properties to be propagated to the child endpoint
        return true;
    }

    @ManagedAttribute(description = "The consumer endpoint url to use in master/slave mode", mask = true)
    @Override
    public Endpoint getEndpoint() {
        return delegateEndpoint;
    }

    @ManagedAttribute(description = "The name of the cluster namespace/group to use")
    public String getNamespace() {
        return namespace;
    }
}
