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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchResponse;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.etcd3.Etcd3Configuration;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.etcd3.Etcd3Helper.toPathPrefix;

/**
 * An implementation of a {@link Etcd3ServiceDiscovery} that retrieves all the service definitions from etcd at first
 * call, then refresh the list when a change has been detected.
 */
public class Etcd3WatchServiceDiscovery extends Etcd3ServiceDiscovery
        implements Watch.Listener {

    /**
     * The logger.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(Etcd3WatchServiceDiscovery.class);

    /**
     * The current list of service definitions found.
     */
    private volatile List<ServiceDefinition> allServices;
    /**
     * The revision of the last update of the list of service definitions.
     */
    private final AtomicLong revision;
    /**
     * The path prefix of the key-value pairs containing the service definitions.
     */
    private final String servicePath;
    /**
     * The client to access to etcd.
     */
    private final Client client;
    /**
     * The client to watch key-value pairs stored into etcd corresponding to the service definitions.
     */
    private final Watch watch;
    /**
     * The charset to use for the keys.
     */
    private final Charset keyCharset;
    /**
     * The current watcher used to watch the changes of the service definitions.
     */
    private final AtomicReference<Watch.Watcher> watcher = new AtomicReference<>();
    /**
     * The mutex used to prevent concurrent load of the list of service definitions.
     */
    private final Object mutex = new Object();

    /**
     * Construct a {@code Etcd3WatchServiceDiscovery} with the given configuration.
     *
     * @param configuration the configuration used to set up the service discovery.
     */
    public Etcd3WatchServiceDiscovery(Etcd3Configuration configuration) {
        super(configuration);
        this.revision = new AtomicLong();
        this.servicePath = ObjectHelper.notNull(configuration.getServicePath(), "servicePath");
        this.client = configuration.createClient();
        this.watch = client.getWatchClient();
        this.keyCharset = Charset.forName(configuration.getKeyCharset());
    }

    @Override
    protected void doStop() throws Exception {
        try {
            client.close();
        } finally {
            super.doStop();
        }
    }

    @Override
    public List<ServiceDefinition> getServices(String name) {
        List<ServiceDefinition> servers = allServices;
        if (servers == null) {
            synchronized (mutex) {
                servers = allServices;
                if (servers == null) {
                    servers = reloadServices();
                    doWatch();
                }
            }
        }

        return servers.stream()
                .filter(s -> name.equalsIgnoreCase(s.getName()))
                .collect(Collectors.toList());
    }

    /**
     * Gets the list of service definitions from the etcd then update the current list.
     *
     * @return list of all service definitions that could be found.
     */
    private List<ServiceDefinition> reloadServices() {
        Etcd3GetServicesResponse response = findServices();
        revision.getAndUpdate(r -> Math.max(r, response.getRevision() + 1));
        this.allServices = response.getServices();
        return response.getServices();
    }

    /**
     * If allowed, starts to watch the changes on the service definitions.
     */
    private void doWatch() {
        if (!isRunAllowed()) {
            return;
        }
        watcher.getAndUpdate(w -> {
            if (w != null) {
                w.close();
            }
            return watch.watch(
                    ByteSequence.from(toPathPrefix(servicePath), keyCharset),
                    WatchOption.newBuilder().isPrefix(true).withRevision(revision.get()).build(),
                    this);
        });
    }

    @Override
    public void onNext(WatchResponse response) {
        // A change has been received let's reload the list
        reloadServices();
    }

    @Override
    public void onError(Throwable throwable) {
        LOGGER.debug("Cloud not fetch the index, key={}, cause={}", servicePath, throwable);
    }

    @Override
    public void onCompleted() {
        doWatch();
    }
}
