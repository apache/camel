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
package org.apache.camel.component.ignite;

import org.apache.camel.Component;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.ignite.Ignite;

/**
 * Base class for all Ignite endpoints. 
 */
public abstract class AbstractIgniteEndpoint extends DefaultEndpoint {

    protected AbstractIgniteComponent component;

    @UriParam(defaultValue = "true")
    private boolean propagateIncomingBodyIfNoReturnValue = true;

    @UriParam
    private boolean treatCollectionsAsCacheObjects;

    public AbstractIgniteEndpoint(String endpointUri, Component component) {
        super(endpointUri, component);
    }

    @Override
    public boolean isSingleton() {
        return false;
    }

    protected AbstractIgniteComponent igniteComponent() {
        if (component == null) {
            component = (AbstractIgniteComponent) getComponent();
        }
        return component;
    }

    protected Ignite ignite() {
        return igniteComponent().getIgnite();
    }

    /**
     * Gets whether to propagate the incoming body if the return type of the underlying 
     * Ignite operation is void.
     * 
     * @return
     */
    public boolean isPropagateIncomingBodyIfNoReturnValue() {
        return propagateIncomingBodyIfNoReturnValue;
    }

    /**
     * Sets whether to propagate the incoming body if the return type of the underlying 
     * Ignite operation is void.
     * 
     * @param propagateIncomingBodyIfNoReturnValue
     */
    public void setPropagateIncomingBodyIfNoReturnValue(boolean propagateIncomingBodyIfNoReturnValue) {
        this.propagateIncomingBodyIfNoReturnValue = propagateIncomingBodyIfNoReturnValue;
    }

    /**
     * Gets whether to treat Collections as cache objects or as Collections of items to 
     * insert/update/compute, etc.
     * 
     * @return
     */
    public boolean isTreatCollectionsAsCacheObjects() {
        return treatCollectionsAsCacheObjects;
    }

    /**
     * Sets whether to treat Collections as cache objects or as Collections of items to 
     * insert/update/compute, etc.
     * 
     * @param treatCollectionsAsCacheObjects
     */
    public void setTreatCollectionsAsCacheObjects(boolean treatCollectionsAsCacheObjects) {
        this.treatCollectionsAsCacheObjects = treatCollectionsAsCacheObjects;
    }

}
