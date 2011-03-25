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
package org.apache.camel.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.apache.camel.Service;
import org.apache.camel.ShutdownableService;
import org.apache.camel.SuspendableService;
import org.apache.camel.impl.ServiceSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A collection of helper methods for working with {@link Service} objects
 *
 * @version 
 */
public final class ServiceHelper {
    private static final transient Logger LOG = LoggerFactory.getLogger(ServiceHelper.class);

    /**
     * Utility classes should not have a public constructor.
     */
    private ServiceHelper() {
    }

    /**
     * Starts all of the given services
     */
    public static void startService(Object value) throws Exception {
        if (isStarted(value)) {
            // only start service if not already started
            LOG.trace("Service already started: {}", value);
            return;
        }
        if (value instanceof Service) {
            Service service = (Service)value;
            LOG.trace("Starting service: {}", service);
            service.start();
        } else if (value instanceof Collection) {
            startServices((Collection<?>)value);
        }
    }

    /**
     * Starts all of the given services
     */
    public static void startServices(Object... services) throws Exception {
        if (services == null) {
            return;
        }
        for (Object value : services) {
            startService(value);
        }
    }

    /**
     * Starts all of the given services
     */
    public static void startServices(Collection<?> services) throws Exception {
        if (services == null) {
            return;
        }
        for (Object value : services) {
            startService(value);
        }
    }

    /**
     * Stops all of the given services, throwing the first exception caught
     */
    public static void stopServices(Object... services) throws Exception {
        if (services == null) {
            return;
        }
        List<Object> list = Arrays.asList(services);
        stopServices(list);
    }

    /**
     * Stops all of the given services, throwing the first exception caught
     */
    public static void stopService(Object value) throws Exception {
        if (isStopped(value)) {
            // only stop service if not already stopped
            LOG.trace("Service already stopped: {}", value);
            return;
        }
        if (value instanceof Service) {
            Service service = (Service)value;
            LOG.trace("Stopping service {}", value);
            service.stop();
        } else if (value instanceof Collection) {
            stopServices((Collection<?>)value);
        }
    }

    /**
     * Stops all of the given services, throwing the first exception caught
     */
    public static void stopServices(Collection<?> services) throws Exception {
        if (services == null) {
            return;
        }
        Exception firstException = null;
        for (Object value : services) {
            try {
                stopService(value);
            } catch (Exception e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Caught exception stopping service: " + value, e);
                }
                if (firstException == null) {
                    firstException = e;
                }
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }

    /**
     * Stops and shutdowns all of the given services, throwing the first exception caught
     */
    public static void stopAndShutdownServices(Object... services) throws Exception {
        if (services == null) {
            return;
        }
        List<Object> list = Arrays.asList(services);
        stopAndShutdownServices(list);
    }

    /**
     * Stops and shutdowns all of the given services, throwing the first exception caught
     */
    public static void stopAndShutdownService(Object value) throws Exception {
        if (value instanceof Service) {
            // must stop it first
            stopService(value);
        }

        // then try to shutdown
        if (value instanceof ShutdownableService) {
            ShutdownableService service = (ShutdownableService)value;
            LOG.trace("Shutting down service {}", value);
            service.shutdown();
        } else if (value instanceof Collection) {
            stopAndShutdownServices((Collection<?>)value);
        }
    }

    /**
     * Stops and shutdowns all of the given services, throwing the first exception caught
     */
    public static void stopAndShutdownServices(Collection<?> services) throws Exception {
        if (services == null) {
            return;
        }
        Exception firstException = null;

        for (Object value : services) {

            // must stop it first
            stopService(value);

            // then try to shutdown
            if (value instanceof ShutdownableService) {
                ShutdownableService service = (ShutdownableService)value;
                try {
                    LOG.trace("Shutting down service: {}", service);
                    service.shutdown();
                } catch (Exception e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Caught exception shutting down service: " + service, e);
                    }
                    if (firstException == null) {
                        firstException = e;
                    }
                }
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }

    public static void resumeServices(Collection<?> services) throws Exception {
        if (services == null) {
            return;
        }
        Exception firstException = null;
        for (Object value : services) {
            if (value instanceof Service) {
                Service service = (Service)value;
                try {
                    resumeService(service);
                } catch (Exception e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Caught exception resuming service: " + service, e);
                    }
                    if (firstException == null) {
                        firstException = e;
                    }
                }
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }

    /**
     * Resumes the given service.
     * <p/>
     * If the service is a {@link org.apache.camel.SuspendableService} then the <tt>resume</tt>
     * operation is <b>only</b> invoked if the service is suspended.
     * <p/>
     * If the service is a {@link org.apache.camel.impl.ServiceSupport} then the <tt>start</tt>
     * operation is <b>only</b> invoked if the service is startable.
     * <p/>
     * Otherwise the service is started.
     *
     * @param service the service
     * @return <tt>true</tt> if either <tt>resume</tt> or <tt>start</tt> was invoked,
     * <tt>false</tt> if the service is already in the desired state.
     * @throws Exception is thrown if error occurred
     */
    public static boolean resumeService(Object service) throws Exception {
        if (service instanceof SuspendableService) {
            SuspendableService ss = (SuspendableService) service;
            if (ss.isSuspended()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Resuming service " + service);
                }
                ss.resume();
                return true;
            } else {
                return false;
            }
        } else {
            startService(service);
            return true;
        }
    }

    public static void suspendServices(Collection<?> services) throws Exception {
        if (services == null) {
            return;
        }
        Exception firstException = null;
        for (Object value : services) {
            if (value instanceof Service) {
                Service service = (Service)value;
                try {
                    suspendService(service);
                } catch (Exception e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Caught exception suspending service: " + service, e);
                    }
                    if (firstException == null) {
                        firstException = e;
                    }
                }
            }
        }
        if (firstException != null) {
            throw firstException;
        }
    }

    /**
     * Suspends the given service.
     * <p/>
     * If the service is a {@link org.apache.camel.SuspendableService} then the <tt>suspend</tt>
     * operation is <b>only</b> invoked if the service is <b>not</b> suspended.
     * <p/>
     * If the service is a {@link org.apache.camel.impl.ServiceSupport} then the <tt>stop</tt>
     * operation is <b>only</b> invoked if the service is stoppable.
     * <p/>
     * Otherwise the service is stopped.
     *
     * @param service the service
     * @return <tt>true</tt> if either <tt>suspend</tt> or <tt>stop</tt> was invoked,
     * <tt>false</tt> if the service is already in the desired state.
     * @throws Exception is thrown if error occurred
     */
    public static boolean suspendService(Service service) throws Exception {
        if (service instanceof SuspendableService) {
            SuspendableService ss = (SuspendableService) service;
            if (!ss.isSuspended()) {
                LOG.trace("Suspending service {}", service);
                ss.suspend();
                return true;
            } else {
                return false;
            }
        } else {
            stopService(service);
            return true;
        }
    }

    /**
     * Is the given service stopping or stopped?
     *
     * @return <tt>true</tt> if already stopped, otherwise <tt>false</tt>
     */
    public static boolean isStopped(Object value) {
        if (value instanceof ServiceSupport) {
            ServiceSupport service = (ServiceSupport) value;
            if (service.isStopping() || service.isStopped()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Is the given service starting or started?
     *
     * @return <tt>true</tt> if already started, otherwise <tt>false</tt>
     */
    public static boolean isStarted(Object value) {
        if (value instanceof ServiceSupport) {
            ServiceSupport service = (ServiceSupport) value;
            if (service.isStarting() || service.isStarted()) {
                return true;
            }
        }
        return false;
    }

}
