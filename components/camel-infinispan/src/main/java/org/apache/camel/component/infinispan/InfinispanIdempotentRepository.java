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
package org.apache.camel.component.infinispan;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.service.ServiceSupport;
import org.infinispan.commons.api.BasicCache;

public abstract class InfinispanIdempotentRepository
        extends ServiceSupport
        implements IdempotentRepository, CamelContextAware {

    private CamelContext camelContext;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    @ManagedOperation(description = "Adds the key to the store")
    public boolean add(String key) {
        // need to check first as put will update the entry lifetime so it can not expire its cache lifespan
        if (getCache().containsKey(key)) {
            // there is already an entry so return false
            return false;
        }

        Boolean put = getCache().put(key, true);
        return put == null;
    }

    @Override
    @ManagedOperation(description = "Does the store contain the given key")
    public boolean contains(String key) {
        return getCache().containsKey(key);
    }

    @Override
    @ManagedOperation(description = "Remove the key from the store")
    public boolean remove(String key) {
        return getCache().remove(key) != null;
    }

    @Override
    @ManagedOperation(description = "Clear the store")
    public void clear() {
        getCache().clear();
    }

    @ManagedAttribute(description = "The processor name")
    public String getCacheName() {
        return getCache().getName();
    }

    @Override
    public boolean confirm(String key) {
        return true;
    }

    protected abstract BasicCache<String, Boolean> getCache();
}
