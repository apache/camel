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
package org.apache.camel.component.consul;

import java.util.Optional;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.kiwiproject.consul.Consul;

/**
 * Integrate with <a href="https://www.consul.io/">Consul</a> service discovery and configuration store.
 */
@UriEndpoint(firstVersion = "2.18.0", scheme = "consul", title = "Consul", syntax = "consul:apiEndpoint",
             category = { Category.CLOUD, Category.API }, headersClass = ConsulConstants.class)
@BodyTypes(consumerOut = @BodyType(value = { Carrier.TEXT, Carrier.NULL },
                                   description = "The kv consumer: the value of the key (of each key under it with recursive)"
                                                 + " base64 encoded as Consul stores it, decoded with valueAsString=true, with the"
                                                 + " key, flags and indexes in headers; null for a key without value. The event"
                                                 + " consumer: the payload of the event as text, with its name and id in headers"),
           producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = {
                                          @BodyType.When(option = "action", value = "PUT",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE,
                                                                 Carrier.VALUE, Carrier.NULL }),
                                          @BodyType.When(option = "action", value = "LOCK",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE,
                                                                 Carrier.VALUE, Carrier.NULL }),
                                          @BodyType.When(option = "action", value = "FIRE", carriers = {
                                                  Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.VALUE }),
                                          @BodyType.When(option = "action", value = "REGISTER", carriers = Carrier.POJO),
                                          @BodyType.When(option = "action", value = "CREATE", carriers = Carrier.POJO) },
                                  description = "PUT and LOCK store the body converted to text as the value, and FIRE sends it"
                                                + " as the payload: a Map or a POJO is stored as its toString, so marshal it"
                                                + " first. REGISTER, DEREGISTER of catalog and CREATE take the model object of"
                                                + " the Consul client, such as a Registration or a Session; the session and"
                                                + " prepared query actions read the id from the body when its header is not set;"
                                                + " the other actions take their input from headers"),
           producerOut = @BodyType(value = { Carrier.POJO, Carrier.LIST, Carrier.MAP, Carrier.TEXT, Carrier.UNCHANGED },
                                   byOption = {
                                           @BodyType.When(option = "action", value = "CHECKS",
                                                          carriers = { Carrier.MAP, Carrier.LIST }),
                                           @BodyType.When(option = "action", value = "SERVICES", carriers = Carrier.MAP),
                                           @BodyType.When(option = "action", value = "MEMBERS", carriers = Carrier.LIST),
                                           @BodyType.When(option = "action", value = "AGENT", carriers = Carrier.POJO,
                                                          javaType = "org.kiwiproject.consul.model.agent.Agent"),
                                           @BodyType.When(option = "action", value = "REGISTER", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "DEREGISTER",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "PUT", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "GET_VALUE",
                                                          carriers = { Carrier.POJO, Carrier.TEXT, Carrier.UNCHANGED }),
                                           @BodyType.When(option = "action", value = "GET_VALUES", carriers = Carrier.LIST),
                                           @BodyType.When(option = "action", value = "GET_KEYS", carriers = Carrier.LIST),
                                           @BodyType.When(option = "action", value = "GET_SESSIONS", carriers = Carrier.POJO,
                                                          javaType = "java.util.Optional<java.lang.String>"),
                                           @BodyType.When(option = "action", value = "DELETE_KEY",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "DELETE_KEYS",
                                                          carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "LOCK", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "UNLOCK", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "LIST_DATACENTERS",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "action", value = "LIST_NODES", carriers = Carrier.LIST),
                                           @BodyType.When(option = "action", value = "LIST_SERVICES", carriers = Carrier.MAP),
                                           @BodyType.When(option = "action", value = "GET_SERVICE", carriers = Carrier.LIST),
                                           @BodyType.When(option = "action", value = "GET_NODE", carriers = Carrier.POJO,
                                                          javaType = "org.kiwiproject.consul.model.catalog.CatalogNode"),
                                           @BodyType.When(option = "action", value = "NODE_CHECKS", carriers = Carrier.LIST),
                                           @BodyType.When(option = "action", value = "SERVICE_CHECKS", carriers = Carrier.LIST),
                                           @BodyType.When(option = "action", value = "SERVICE_INSTANCES",
                                                          carriers = Carrier.LIST),
                                           @BodyType.When(option = "action", value = "CREATE",
                                                          carriers = { Carrier.POJO, Carrier.TEXT }),
                                           @BodyType.When(option = "action", value = "DESTROY", carriers = Carrier.UNCHANGED),
                                           @BodyType.When(option = "action", value = "INFO",
                                                          carriers = { Carrier.POJO, Carrier.UNCHANGED }),
                                           @BodyType.When(option = "action", value = "LIST",
                                                          carriers = { Carrier.POJO, Carrier.LIST }),
                                           @BodyType.When(option = "action", value = "RENEW", carriers = Carrier.POJO,
                                                          javaType = "java.util.Optional<org.kiwiproject.consul.model.session.SessionInfo>"),
                                           @BodyType.When(option = "action", value = "GET", carriers = Carrier.POJO,
                                                          javaType = "java.util.Optional<org.kiwiproject.consul.model.query.StoredQuery>"),
                                           @BodyType.When(option = "action", value = "EXECUTE", carriers = Carrier.POJO,
                                                          javaType = "org.kiwiproject.consul.model.query.QueryResults"),
                                           @BodyType.When(option = "action", value = "FIRE", carriers = Carrier.POJO,
                                                          javaType = "org.kiwiproject.consul.model.event.Event"),
                                           @BodyType.When(option = "action", value = "DATACENTERS", carriers = Carrier.LIST),
                                           @BodyType.When(option = "action", value = "NODES", carriers = Carrier.LIST),
                                           @BodyType.When(option = "action", value = "LEADER", carriers = Carrier.TEXT,
                                                          javaType = "java.lang.String"),
                                           @BodyType.When(option = "action", value = "PEERS", carriers = Carrier.LIST) },
                                   description = "The response of Consul as the model objects of the Consul client: a List or a"
                                                 + " Map of them for the list actions, the Value of the key for GET_VALUE (its value"
                                                 + " base64 encoded) or the value as text with valueAsString=true, and the Event"
                                                 + " for FIRE. Unchanged for the actions that only write (PUT, the deletes, LOCK,"
                                                 + " UNLOCK, REGISTER, DEREGISTER, DESTROY) and when nothing is found, with"
                                                 + " the outcome in the CamelConsulResult header"))
public class ConsulEndpoint extends DefaultEndpoint {

    @UriParam
    private final ConsulConfiguration configuration;

    @UriPath(description = "The API endpoint")
    @Metadata(required = true)
    private final String apiEndpoint;

    private final Optional<ConsulFactories.ProducerFactory> producerFactory;
    private final Optional<ConsulFactories.ConsumerFactory> consumerFactory;

    private Consul consul;

    public ConsulEndpoint(String apiEndpoint, String uri, ConsulComponent component, ConsulConfiguration configuration,
                          Optional<ConsulFactories.ProducerFactory> producerFactory,
                          Optional<ConsulFactories.ConsumerFactory> consumerFactory) {

        super(uri, component);

        this.configuration = ObjectHelper.notNull(configuration, "configuration");
        this.apiEndpoint = ObjectHelper.notNull(apiEndpoint, "apiEndpoint");
        this.producerFactory = producerFactory;
        this.consumerFactory = consumerFactory;
    }

    @Override
    public Producer createProducer() throws Exception {
        ConsulFactories.ProducerFactory factory
                = producerFactory.orElseThrow(() -> new IllegalArgumentException("No producer for " + apiEndpoint));

        return factory.create(this, configuration);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        ConsulFactories.ConsumerFactory factory
                = consumerFactory.orElseThrow(() -> new IllegalArgumentException("No consumer for " + apiEndpoint));

        Consumer consumer = factory.create(this, configuration, processor);
        configureConsumer(consumer);
        return consumer;
    }

    // *************************************************************************
    //
    // *************************************************************************

    public ConsulConfiguration getConfiguration() {
        return this.configuration;
    }

    public String getApiEndpoint() {
        return this.apiEndpoint;
    }

    public Consul getConsul() throws Exception {
        lock.lock();
        try {
            if (consul == null && ObjectHelper.isEmpty(getConfiguration().getConsulClient())) {
                consul = configuration.createConsulClient(getCamelContext());
            } else if (ObjectHelper.isNotEmpty(getConfiguration().getConsulClient())) {
                consul = getConfiguration().getConsulClient();
            }

            return consul;
        } finally {
            lock.unlock();
        }
    }
}
