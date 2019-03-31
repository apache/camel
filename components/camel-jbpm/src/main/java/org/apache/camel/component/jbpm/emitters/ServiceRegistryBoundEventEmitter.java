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
package org.apache.camel.component.jbpm.emitters;

import java.util.Collection;

import org.jbpm.persistence.api.integration.EventCollection;
import org.jbpm.persistence.api.integration.EventEmitter;
import org.jbpm.persistence.api.integration.InstanceView;
import org.jbpm.services.api.service.ServiceRegistry;

public class ServiceRegistryBoundEventEmitter implements EventEmitter {
    
    private EventEmitter delegate;
    
    public ServiceRegistryBoundEventEmitter() {
        this.delegate = (EventEmitter) ServiceRegistry.get().service("CamelEventEmitter");
    }

    @Override
    public void deliver(Collection<InstanceView<?>> data) {
        delegate.deliver(data);
        
    }

    @Override
    public void apply(Collection<InstanceView<?>> data) {
        delegate.apply(data);
    }

    @Override
    public void drop(Collection<InstanceView<?>> data) {
        delegate.drop(data);
        
    }

    @Override
    public EventCollection newCollection() {
        return delegate.newCollection();
    }

    @Override
    public void close() {
 
    }

}
