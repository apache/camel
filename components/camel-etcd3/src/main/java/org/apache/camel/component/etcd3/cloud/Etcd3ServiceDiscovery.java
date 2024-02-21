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
package org.apache.camel.component.etcd3.cloud;

import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.options.GetOption;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.etcd3.Etcd3Configuration;
import org.apache.camel.component.etcd3.Etcd3Helper;
import org.apache.camel.impl.cloud.DefaultServiceDiscovery;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.etcd3.Etcd3Helper.toPathPrefix;

/**
 * The root implementation of {@code ServiceDiscovery} fetching the data from etcd.
 */
abstract class Etcd3ServiceDiscovery extends DefaultServiceDiscovery {
    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Etcd3ServiceDiscovery.class);
    /**
     * The object mapper used to unmarshall the service definitions.
     */
    private static final ObjectMapper MAPPER = Etcd3Helper.createObjectMapper();

    /**
     * The path prefix of the key-value pairs containing the service definitions.
     */
    private final String servicePath;
    /**
     * The client to access to etcd.
     */
    private final Client client;
    /**
     * The client to access to the key-value pairs stored into etcd.
     */
    private final KV kvClient;
    /**
     * The charset to use for the keys.
     */
    private final Charset keyCharset;
    /**
     * The charset to use for the keys.
     */
    private final Charset valueCharset;

    /**
     * Construct a {@code Etcd3ServiceDiscovery} with the given configuration.
     *
     * @param configuration the configuration used to set up the service discovery.
     */
    Etcd3ServiceDiscovery(Etcd3Configuration configuration) {
        this.servicePath = ObjectHelper.notNull(configuration.getServicePath(), "servicePath");
        this.client = configuration.createClient();
        this.kvClient = client.getKVClient();
        this.keyCharset = Charset.forName(configuration.getKeyCharset());
        this.valueCharset = Charset.forName(configuration.getValueCharset());
    }

    @Override
    protected void doStop() throws Exception {
        try {
            client.close();
        } finally {
            super.doStop();
        }
    }

    /**
     * @return all the service definitions that could be found.
     */
    protected Etcd3GetServicesResponse findServices() {
        return findServices(s -> true);
    }

    /**
     * @param  filter the filter to apply.
     * @return        all the matching service definitions that could be found.
     */
    protected Etcd3GetServicesResponse findServices(Predicate<Etcd3ServiceDefinition> filter) {
        List<ServiceDefinition> servers = Collections.emptyList();
        long revision = 0;

        if (isRunAllowed()) {
            try {
                final GetResponse response = kvClient.get(
                        ByteSequence.from(toPathPrefix(servicePath), keyCharset),
                        GetOption.newBuilder().isPrefix(true).build()).get();

                revision = response.getHeader().getRevision();
                if (response.getCount() > 0) {
                    servers = response.getKvs().stream()
                            .map(KeyValue::getValue)
                            .filter(ObjectHelper::isNotEmpty)
                            .map(kv -> kv.toString(valueCharset))
                            .map(this::nodeFromString)
                            .filter(Objects::nonNull)
                            .filter(filter)
                            .sorted(Etcd3ServiceDefinition.COMPARATOR)
                            .collect(Collectors.toList());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeCamelException(e);
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            }
        }

        return new Etcd3GetServicesResponse(revision, servers);
    }

    /**
     * Unmarshalls the given value into a service definition.
     *
     * @param  value the json payload representing a service definition.
     * @return       the corresponding service definition if it could be unmarshalled, {@code null} otherwise.
     */
    private Etcd3ServiceDefinition nodeFromString(String value) {
        Etcd3ServiceDefinition server = null;
        try {
            server = MAPPER.readValue(value, Etcd3ServiceDefinition.class);
        } catch (Exception e) {
            LOGGER.warn("Could not parse the json payload {}", value, e);
        }
        return server;
    }
}
