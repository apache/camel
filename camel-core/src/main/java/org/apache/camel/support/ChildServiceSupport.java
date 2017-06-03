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
package org.apache.camel.support;

import java.util.LinkedHashSet;
import java.util.Set;

import org.apache.camel.util.ServiceHelper;

/**
 * Base class to control lifecycle for a set of child {@link org.apache.camel.Service}s.
 */
public abstract class ChildServiceSupport extends ServiceSupport {
    private Set<Object> childServices;
    
    public void start() throws Exception {
        start(true);
    }

    public void start(boolean startChildren) throws Exception {
        if (!started.get()) {
            if (starting.compareAndSet(false, true)) {
                boolean childrenStarted = false;
                Exception ex = null;
                try {
                    if (childServices != null && startChildren) {
                        ServiceHelper.startServices(childServices);
                    }
                    childrenStarted = true;
                    doStart();
                } catch (Exception e) {
                    ex = e;
                } finally {
                    if (ex != null) {
                        try {
                            stop(childrenStarted);
                        } catch (Exception e) {
                            // Ignore exceptions as we want to show the original exception
                        }
                        throw ex;
                    } else {
                        started.set(true);
                        starting.set(false);
                        stopping.set(false);
                        stopped.set(false);
                        suspending.set(false);
                        suspended.set(false);
                        shutdown.set(false);
                        shuttingdown.set(false);
                    }
                }
            }
        }
    }
    
    private void stop(boolean childrenStarted) throws Exception {
        if (stopping.compareAndSet(false, true)) {
            try {
                try {
                    starting.set(false);
                    suspending.set(false);
                    if (childrenStarted) {
                        doStop();
                    }
                } finally {
                    started.set(false);
                    suspended.set(false);
                    if (childServices != null) {
                        ServiceHelper.stopServices(childServices);
                    }
                }
            } finally {
                stopped.set(true);
                stopping.set(false);
                starting.set(false);
                started.set(false);
                suspending.set(false);
                suspended.set(false);
                shutdown.set(false);
                shuttingdown.set(false);
            }
        }
    }

    public void stop() throws Exception {
        if (!stopped.get()) {
            stop(true);
        }
    }
    
    public void shutdown() throws Exception {
        // ensure we are stopped first
        stop();

        if (shuttingdown.compareAndSet(false, true)) {
            try {
                try {
                    doShutdown();
                } finally {
                    if (childServices != null) {
                        ServiceHelper.stopAndShutdownServices(childServices);
                    }
                }
            } finally {
                // shutdown is also stopped so only set shutdown flags
                shutdown.set(true);
                shuttingdown.set(false);
            }
        }
    }
    
    protected void addChildService(Object childService) {
        synchronized (this) {
            if (childServices == null) {
                childServices = new LinkedHashSet<Object>();
            }
        }
        childServices.add(childService);
    }

    protected boolean removeChildService(Object childService) {
        return childServices != null && childServices.remove(childService);
    }

}
