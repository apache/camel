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
package org.apache.camel.management.mbean;

import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.component.seda.SedaEndpoint;

/**
 *
 */
@ManagedResource(description = "Managed SedaEndpoint")
public class ManagedSedaEndpoint extends ManagedBrowsableEndpoint {

    public ManagedSedaEndpoint(SedaEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public SedaEndpoint getInstance() {
        return getEndpoint();
    }

    @Override
    public SedaEndpoint getEndpoint() {
        return (SedaEndpoint) super.getEndpoint();
    }

    @ManagedAttribute(description = "Queue max capacity")
    public int getSize() {
        return getEndpoint().getSize();
    }

    public void setSize(int size) {
        getEndpoint().setSize(size);
    }

    @ManagedAttribute(description = "Current queue size")
    public int getCurrentQueueSize() {
        return getEndpoint().getCurrentQueueSize();
    }

    public void setBlockWhenFull(boolean blockWhenFull) {
        getEndpoint().setBlockWhenFull(blockWhenFull);
    }

    @ManagedAttribute(description = "Whether the caller will block sending to a full queue")
    public boolean isBlockWhenFull() {
        return getEndpoint().isBlockWhenFull();
    }

    public void setConcurrentConsumers(int concurrentConsumers) {
        getEndpoint().setConcurrentConsumers(concurrentConsumers);
    }

    @ManagedAttribute(description = "Number of concurrent consumers")
    public int getConcurrentConsumers() {
        return getEndpoint().getConcurrentConsumers();
    }

    @ManagedAttribute
    public long getTimeout() {
        return getEndpoint().getTimeout();
    }

    public void setTimeout(long timeout) {
        getEndpoint().setTimeout(timeout);
    }

    @ManagedAttribute
    public boolean isMultipleConsumers() {
        return getEndpoint().isMultipleConsumers();
    }

    @ManagedAttribute
    public boolean isMultipleConsumersSupported() {
        return isMultipleConsumers();
    }

    public void setMultipleConsumers(boolean multipleConsumers) {
        getEndpoint().setMultipleConsumers(multipleConsumers);
    }

    /**
     * Purges the queue
     */
    @ManagedOperation(description = "Purges the seda queue")
    public void purgeQueue() {
        getEndpoint().purgeQueue();
    }

}
