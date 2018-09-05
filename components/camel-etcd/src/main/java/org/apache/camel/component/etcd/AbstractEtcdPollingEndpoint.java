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
package org.apache.camel.component.etcd;

import mousio.etcd4j.EtcdClient;
import org.apache.camel.impl.DefaultPollingEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * The camel etcd component allows you to work with <a href="https://coreos.com/etcd">Etcd</a>, a distributed reliable key-value store.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "etcd", title = "etcd", syntax = "etcd:namespace/path", consumerClass = AbstractEtcdConsumer.class, label = "clustering,database")
public abstract class AbstractEtcdPollingEndpoint extends DefaultPollingEndpoint implements EtcdEndpoint {

    @UriPath(description = "The API namespace to use", enums = "keys,stats,watch")
    @Metadata(required = "true")
    private final EtcdNamespace namespace;
    @UriPath(description = "The path the endpoint refers to")
    @Metadata(required = "false")
    private final String path;
    @UriParam
    private final EtcdConfiguration configuration;

    protected AbstractEtcdPollingEndpoint(String uri, EtcdComponent component, EtcdConfiguration configuration, EtcdNamespace namespace, String path) {
        super(uri, component);

        this.configuration = configuration;
        this.namespace = namespace;
        this.path = path;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public EtcdConfiguration getConfiguration() {
        return this.configuration;
    }

    @Override
    public EtcdNamespace getNamespace() {
        return this.namespace;
    }

    @Override
    public String getPath() {
        return this.path;
    }

    @Override
    public EtcdClient createClient() throws Exception {
        return configuration.createClient();
    }
}
