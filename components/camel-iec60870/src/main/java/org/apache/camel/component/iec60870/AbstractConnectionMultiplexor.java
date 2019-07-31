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
package org.apache.camel.component.iec60870;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractConnectionMultiplexor {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractConnectionMultiplexor.class);

    public interface Handle {
        void unregister() throws Exception;
    }

    private final class HandleImplementation implements Handle {
        @Override
        public void unregister() throws Exception {
            AbstractConnectionMultiplexor.this.unregister(this);
        }
    }

    private final Set<HandleImplementation> handles = new CopyOnWriteArraySet<>();

    public synchronized Handle register() throws Exception {
        final HandleImplementation handle = new HandleImplementation();

        final boolean needStart = this.handles.isEmpty();
        this.handles.add(handle);

        if (needStart) {
            LOG.info("Calling performStart()");
            performStart();
        }

        return handle;
    }

    private synchronized void unregister(final HandleImplementation handle) throws Exception {
        if (!this.handles.remove(handle)) {
            return;
        }

        if (this.handles.isEmpty()) {
            LOG.info("Calling performStop()");
            performStop();
        }
    }

    public synchronized void dispose() {

        LOG.info("Disposing");
        if (this.handles.isEmpty()) {
            LOG.debug("Disposing - not started");
            return;
        }

        LOG.debug("Disposing - calling performStop()");

        this.handles.clear();
        try {
            performStop();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to stop on dispose", e);
        }
    }

    protected abstract void performStart() throws Exception;

    protected abstract void performStop() throws Exception;
}
