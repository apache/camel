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
package org.apache.camel.impl.remote;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.spi.ServiceCallServer;
import org.apache.camel.spi.ServiceCallServerListStrategy;
import org.apache.camel.util.ObjectHelper;

public class CachingServiceCallServiceListStrategy<T extends ServiceCallServer> implements ServiceCallServerListStrategy<T> {
    private final ServiceCallServerListStrategy<T> delegate;
    private List<T> servers;
    private long lastUpdate;
    private long timeout;

    public CachingServiceCallServiceListStrategy(ServiceCallServerListStrategy<T> delegate) {
        this.delegate = ObjectHelper.notNull(delegate, "delegate");
        this.lastUpdate = 0;
        this.servers = Collections.emptyList();
        this.timeout = 60 * 1000; // 1 min;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public void setTimeout(long timeout, TimeUnit unit) {
        this.timeout = unit.toMillis(timeout);
    }

    public long getTimeout() {
        return timeout;
    }

    public CachingServiceCallServiceListStrategy<T> timeout(long timeout) {
        setTimeout(timeout);
        return this;
    }

    public CachingServiceCallServiceListStrategy<T> timeout(long timeout, TimeUnit unit) {
        setTimeout(timeout, unit);
        return this;
    }

    @Override
    public List<T> getInitialListOfServers(String name) {
        return delegate.getInitialListOfServers(name);
    }

    @Override
    public List<T> getUpdatedListOfServers(String name) {
        long now = System.currentTimeMillis();

        if (lastUpdate == 0 || now > lastUpdate + timeout) {
            List<T> updatedList = delegate.getUpdatedListOfServers(name);
            if (updatedList.isEmpty()) {
                servers = Collections.emptyList();
            } else {
                // List is copied as the delegated ServiceCallServerListStrategy
                // may update the list
                servers = Collections.unmodifiableList(new ArrayList<>(updatedList));
            }

            lastUpdate = now;
        }

        return servers;
    }

    // **********************
    // Helpers
    // **********************

    public static <S extends ServiceCallServer> CachingServiceCallServiceListStrategy<S> wrap(ServiceCallServerListStrategy<S> delegate) {
        return new CachingServiceCallServiceListStrategy<>(delegate);
    }

    public static <S extends ServiceCallServer> CachingServiceCallServiceListStrategy<S> wrap(ServiceCallServerListStrategy<S> delegate, long timeout) {
        return new CachingServiceCallServiceListStrategy<>(delegate)
            .timeout(timeout);
    }

    public static <S extends ServiceCallServer> CachingServiceCallServiceListStrategy<S> wrap(ServiceCallServerListStrategy<S> delegate, long timeout, TimeUnit unit) {
        return new CachingServiceCallServiceListStrategy<>(delegate)
            .timeout(timeout, unit);
    }
}
