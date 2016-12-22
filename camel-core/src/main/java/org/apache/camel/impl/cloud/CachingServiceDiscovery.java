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

package org.apache.camel.impl.cloud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.apache.camel.cloud.ServiceDefinition;
import org.apache.camel.cloud.ServiceDiscovery;
import org.apache.camel.util.ObjectHelper;

public class CachingServiceDiscovery implements ServiceDiscovery {
    private final ServiceDiscovery delegate;
    private List<ServiceDefinition> services;
    private long lastUpdate;
    private long timeout;

    public CachingServiceDiscovery(ServiceDiscovery delegate) {
        this.delegate = ObjectHelper.notNull(delegate, "delegate");
        this.lastUpdate = 0;
        this.services = Collections.emptyList();
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

    public CachingServiceDiscovery timeout(long timeout) {
        setTimeout(timeout);
        return this;
    }

    public CachingServiceDiscovery timeout(long timeout, TimeUnit unit) {
        setTimeout(timeout, unit);
        return this;
    }

    @Override
    public List<ServiceDefinition> getInitialListOfServices(String name) {
        return delegate.getInitialListOfServices(name);
    }

    @Override
    public List<ServiceDefinition> getUpdatedListOfServices(String name) {
        long now = System.currentTimeMillis();

        if (lastUpdate == 0 || now > lastUpdate + timeout) {
            List<ServiceDefinition> updatedList = delegate.getUpdatedListOfServices(name);
            if (updatedList.isEmpty()) {
                services = Collections.emptyList();
            } else {
                // List is copied as the delegated ServiceCallServiceDiscovery
                // may update the list
                services = Collections.unmodifiableList(new ArrayList<>(updatedList));
            }

            lastUpdate = now;
        }

        return services;
    }

    // **********************
    // Helpers
    // **********************

    public static CachingServiceDiscovery wrap(ServiceDiscovery delegate) {
        return new CachingServiceDiscovery(delegate);
    }

    public static CachingServiceDiscovery wrap(ServiceDiscovery delegate, long timeout) {
        return new CachingServiceDiscovery(delegate).timeout(timeout);
    }

    public static CachingServiceDiscovery wrap(ServiceDiscovery delegate, long timeout, TimeUnit unit) {
        return new CachingServiceDiscovery(delegate).timeout(timeout, unit);
    }
}
