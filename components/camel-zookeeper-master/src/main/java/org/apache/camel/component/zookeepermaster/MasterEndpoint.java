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
package org.apache.camel.component.zookeepermaster;

import org.apache.camel.Consumer;
import org.apache.camel.DelegateEndpoint;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;

/**
 * Represents an endpoint which only becomes active when it obtains the master lock
 */
@UriEndpoint(firstVersion = "2.19.0", scheme = "zookeeper-master", syntax = "zookeeper-master:name:endpoint", consumerClass = MasterConsumer.class, consumerOnly = true,
    title = "ZooKeeper Master", lenientProperties = true, label = "clustering")
public class MasterEndpoint extends DefaultEndpoint implements DelegateEndpoint {

    private final MasterComponent component;

    private final Endpoint childEndpoint;

    @UriPath(name = "name", description = "The name of the cluster group to use")
    @Metadata(required = "true")
    private final String singletonId;

    @UriPath(name = "endpoint", description = "The Camel endpoint to use in master/slave mode")
    @Metadata(required = "true")
    private final String child;

    public MasterEndpoint(String uri, MasterComponent component, String singletonId, String child) {
        super(uri, component);
        this.component = component;
        this.singletonId = singletonId;
        this.child = child;
        this.childEndpoint = getCamelContext().getEndpoint(child);
    }

    public String getSingletonId() {
        return singletonId;
    }

    @Override
    public Producer createProducer() throws Exception {
        throw new UnsupportedOperationException("Cannot produce from this endpoint");
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new MasterConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    public boolean isLenientProperties() {
        // to allow properties to be propagated to the child endpoint
        return true;
    }

    // Properties
    //-------------------------------------------------------------------------
    public MasterComponent getComponent() {
        return component;
    }

    public String getChild() {
        return child;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    Endpoint getChildEndpoint() {
        return childEndpoint;
    }

    public Endpoint getEndpoint() {
        return getChildEndpoint();
    }
}
