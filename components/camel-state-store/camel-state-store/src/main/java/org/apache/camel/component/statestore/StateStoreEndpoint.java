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
package org.apache.camel.component.statestore;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.KeyValueRepository;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Store, get and delete key-value entries in a pluggable {@link KeyValueRepository}, such as an in-memory store or a
 * shared cache.
 *
 * @since 4.23
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "state-store", title = "State Store",
             syntax = "state-store:storeName", producerOnly = true,
             category = { Category.CACHE },
             headersClass = StateStoreConstants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "The value to store for put and putIfAbsent, as it is, except a stream: an"
                                                + " InputStream or the stream cache of Camel is read into memory and stored as"
                                                + " its bytes, and a Reader as its text, a String. The other operations do not"
                                                + " use the body"),
           producerOut = @BodyType(value = Carrier.UNKNOWN,
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "contains", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Boolean"),
                                           @BodyType.When(option = "operation", value = "keys", carriers = Carrier.LIST,
                                                          javaType = "java.util.Set<java.lang.String>"),
                                           @BodyType.When(option = "operation", value = "size", carriers = Carrier.VALUE,
                                                          javaType = "java.lang.Integer"),
                                           @BodyType.When(option = "operation", value = "clear", carriers = Carrier.NULL) },
                                   description = "By the operation, which the CamelStateStoreOperation header can set: the"
                                                 + " stored value for get, the previous value for put, the value already there"
                                                 + " for putIfAbsent and the removed value for delete, each no body when there"
                                                 + " is none; a Boolean for contains, the Set of the keys for keys, the count"
                                                 + " for size and no body for clear. The value is the object the backend gives"
                                                 + " back: the in-memory one the object stored, another may give a copy; a"
                                                 + " stream was stored as a byte array, and a Reader as a String"))
public class StateStoreEndpoint extends DefaultEndpoint {

    @UriPath(description = "The name of the store. The endpoints with the same store name in one CamelContext share one"
                           + " backend, and the first of them that starts decides which backend it is and whether its"
                           + " keys are namespaced.")
    @Metadata(required = true)
    private String storeName;

    @UriParam(description = "The operation to perform when the message has no CamelStateStoreOperation header. Without"
                            + " either, the producer fails. All operations except keys, size and clear need the key in"
                            + " the CamelStateStoreKey header.",
              enums = "put,putIfAbsent,get,delete,contains,keys,size,clear")
    private StateStoreOperations operation;

    @UriParam(description = "The KeyValueRepository that holds the entries. If not set, the backendFactory of the component"
                            + " creates one for the store name; without a factory, or when it returns null, the single"
                            + " KeyValueRepository in the registry is used, and with none or several of them, a new"
                            + " in-memory store.",
              label = "advanced")
    private KeyValueRepository backend;

    @UriParam(description = "The time to live in milliseconds of the entries that put and putIfAbsent store. 0 or a"
                            + " negative value means that the entries do not expire. The CamelStateStoreTtl header"
                            + " overrides it per message.",
              defaultValue = "0")
    private long ttl;

    @UriParam(description = "Whether to prefix the keys with the store name in a backend set with the backend option or"
                            + " found in the registry, so that stores sharing one repository do not see each other's"
                            + " keys. When false, keys, size and clear act on all the entries of the repository. An"
                            + " in-memory store and a backend from the backendFactory of the component are never"
                            + " prefixed.",
              defaultValue = "true")
    private boolean namespaced = true;

    public StateStoreEndpoint(String endpointUri, StateStoreComponent component) {
        super(endpointUri, component);
    }

    @Override
    public Producer createProducer() {
        return new StateStoreProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("The state-store component does not support consumers");
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        StateStoreComponent comp = (StateStoreComponent) getComponent();
        backend = comp.getOrCreateBackend(storeName, backend, namespaced);
    }

    public String getStoreName() {
        return storeName;
    }

    public void setStoreName(String storeName) {
        this.storeName = storeName;
    }

    public StateStoreOperations getOperation() {
        return operation;
    }

    public void setOperation(StateStoreOperations operation) {
        this.operation = operation;
    }

    public KeyValueRepository getBackend() {
        return backend;
    }

    public void setBackend(KeyValueRepository backend) {
        this.backend = backend;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public boolean isNamespaced() {
        return namespaced;
    }

    public void setNamespaced(boolean namespaced) {
        this.namespaced = namespaced;
    }
}
