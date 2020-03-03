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
import org.apache.camel.Endpoint;
import org.apache.camel.support.DefaultProducer;

/**
 * The etcd producer.
 */
public abstract class AbstractEtcdProducer extends DefaultProducer {
    private final EtcdConfiguration configuration;
    private final String path;

    private EtcdClient client;

    protected AbstractEtcdProducer(Endpoint endpoint, EtcdConfiguration configuration, String path) {
        super(endpoint);

        this.configuration = configuration;
        this.path = path;
        this.client = null;
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client.close();
        }

        super.doStop();
    }

    protected EtcdClient getClient() throws Exception {
        if (client == null) {
            client = ((EtcdEndpoint)getEndpoint()).createClient();
        }

        return client;
    }

    protected EtcdConfiguration getConfiguration() {
        return configuration;
    }

    protected String getPath() {
        return this.path;
    }
}
