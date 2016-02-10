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

import java.net.URI;

import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.EtcdSecurityContext;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriPath;
import org.apache.camel.util.ObjectHelper;

/**
 * Represents a etcd endpoint.
 */
@UriEndpoint(scheme = "etcd", title = "etcd", syntax = "etcd:namespace", consumerClass = AbstractEtcdConsumer.class, label = "etcd")
abstract class AbstractEtcdEndpoint<C extends EtcdConfiguration> extends DefaultEndpoint {

    @UriPath(description = "The namespace")
    @Metadata(required = "true")
    private final EtcdNamespace namespace;
    private final C configuration;
    private final String path;

    protected AbstractEtcdEndpoint(String uri, EtcdComponent component, C configuration, EtcdNamespace namespace, String path) {
        super(uri, component);

        this.configuration = configuration;
        this.namespace = namespace;
        this.path = path;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public C getConfiguration() {
        return this.configuration;
    }

    public EtcdNamespace getActionNamespace() {
        return this.namespace;
    }

    public EtcdClient createClient() throws Exception {

        String[] uris = EtcdConstants.ETCD_DEFAULT_URIS;
        if (configuration.hasUris()) {
            uris = configuration.getUris().split(",");
        }

        URI[] etcdUriList = new URI[uris.length];

        int i = 0;
        for (String uri : uris) {
            etcdUriList[i++] = URI.create(getCamelContext().resolvePropertyPlaceholders(uri));
        }

        return new EtcdClient(
            new EtcdSecurityContext(
                configuration.createSslContext(),
                configuration.getUserName(),
                configuration.getPassword()),
            etcdUriList
        );
    }

    public String getPath() {
        return this.path;
    }

    public String getRemainingPath(String defaultPath) {
        String path = getPath().substring(namespace.path().length());
        if (ObjectHelper.isEmpty(path)) {
            path = defaultPath;
        }

        return path;
    }
}
