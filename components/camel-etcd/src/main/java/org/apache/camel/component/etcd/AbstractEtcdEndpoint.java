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
package org.apache.camel.component.etcd;

import mousio.etcd4j.EtcdClient;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

public abstract class AbstractEtcdEndpoint extends DefaultEndpoint implements EtcdEndpoint {

    @UriPath(label = "common", description = "The path the endpoint refers to")
    private final String path;
    @UriParam
    private final EtcdConfiguration configuration;

    protected AbstractEtcdEndpoint(String uri, AbstractEtcdComponent component, EtcdConfiguration configuration, String path) {
        super(uri, component);

        this.configuration = configuration;
        this.path = path;
    }

    @Override
    public EtcdConfiguration getConfiguration() {
        return this.configuration;
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
