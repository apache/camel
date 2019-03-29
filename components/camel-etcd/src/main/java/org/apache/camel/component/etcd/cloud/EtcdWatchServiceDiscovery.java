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
package org.apache.camel.component.etcd.cloud;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import mousio.client.promises.ResponsePromise;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.component.etcd.EtcdConfiguration;
import org.apache.camel.component.etcd.EtcdHelper;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EtcdWatchServiceDiscovery
        extends EtcdServiceDiscovery
        implements ResponsePromise.IsSimplePromiseResponseHandler<EtcdKeysResponse> {

    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdWatchServiceDiscovery.class);
    private final AtomicReference<List<ServiceDefinition>> serversRef;
    private final AtomicLong index;
    private final String servicePath;

    public EtcdWatchServiceDiscovery(EtcdConfiguration configuration) throws Exception {
        super(configuration);

        this.serversRef = new AtomicReference<>();
        this.index = new AtomicLong(0);
        this.servicePath = ObjectHelper.notNull(configuration.getServicePath(), "servicePath");
    }

    @Override
    public List<ServiceDefinition> getServices(String name) {
        List<ServiceDefinition> servers = serversRef.get();
        if (servers == null) {
            serversRef.set(getServices());
            watch();
        }

        return serversRef.get().stream()
            .filter(s -> name.equalsIgnoreCase(s.getName()))
            .collect(Collectors.toList());
    }

    // *************************************************************************
    // Watch
    // *************************************************************************

    @Override
    public void onResponse(ResponsePromise<EtcdKeysResponse> promise) {
        if (!isRunAllowed()) {
            return;
        }

        Throwable throwable = promise.getException();
        if (throwable instanceof EtcdException) {
            EtcdException exception = (EtcdException) throwable;
            if (EtcdHelper.isOutdatedIndexException(exception)) {
                LOGGER.debug("Outdated index, key={}, cause={}", servicePath, exception.etcdCause);
                index.set(exception.index + 1);
            }
        } else {
            try {
                EtcdKeysResponse response = promise.get();
                EtcdHelper.setIndex(index, response);

                serversRef.set(getServices());
            } catch (TimeoutException e) {
                LOGGER.debug("Timeout watching for {}", getConfiguration().getServicePath());
                throwable = null;
            } catch (Exception e) {
                throwable = e;
            }
        }

        if (throwable == null) {
            watch();
        } else {
            throw new RuntimeCamelException(throwable);
        }
    }

    private void watch() {
        if (!isRunAllowed()) {
            return;
        }

        try {
            getClient().get(servicePath)
                .recursive()
                .waitForChange(index.get())
                .timeout(1, TimeUnit.SECONDS)
                .send()
                .addListener(this);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }
}
