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
package org.apache.camel.component.etcd.processor.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import mousio.etcd4j.EtcdClient;
import org.apache.camel.component.etcd.EtcdConfiguration;
import org.apache.camel.component.etcd.EtcdHelper;
import org.apache.camel.impl.remote.DefaultServiceCallServerListStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EtcdServiceCallServerListStrategy extends DefaultServiceCallServerListStrategy<EtcdServiceCallServer> {
    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdServiceCallServerListStrategy.class);
    private static final ObjectMapper MAPPER = EtcdHelper.createObjectMapper();

    private final EtcdConfiguration configuration;
    private EtcdClient client;

    public EtcdServiceCallServerListStrategy(EtcdConfiguration configuration) {
        this.configuration = configuration;
        this.client = null;
    }

    @Override
    protected void doStart() throws Exception {
        if (client == null) {
            client = configuration.createClient();
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    protected EtcdConfiguration getConfiguration() {
        return this.configuration;
    }

    protected EtcdClient getClient() {
        return this.client;
    }

    protected EtcdServiceCallServer nodeFromString(String value) {
        EtcdServiceCallServer server = null;

        try {
            server = MAPPER.readValue(value, EtcdServiceCallServer.class);
        } catch (Exception e) {
            LOGGER.warn("", e);
        }

        return server;
    }
}
