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
package org.apache.camel.support.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.camel.Channel;
import org.apache.camel.Navigate;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.ShutdownableService;
import org.apache.camel.StatefulService;
import org.apache.camel.Suspendable;
import org.apache.camel.SuspendableService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A collection of helper methods for working with {@link Service} objects.
 */
public final class ServiceHelper {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceHelper.class);

    /**
     * Utility classes should not have a public constructor.
     */
    private ServiceHelper() {
    }

    /**
     * Initializes the given {@code value} if it's a {@link Service} or a collection of it.
     * <p/>
     * Calling this method has no effect if {@code value} is {@code null}.
     */
    public static void initService(Object value) {
        if (value instanceof Service) {
            ((Service) value).init();
        } else if (value instanceof Iterable) {
            for (Object o : (Iterable) value) {
                initService(o);
            }
        }
    }

    /**
     * Initializes each element of the given {@code services} if {@code services} itself is
     * not {@code null}, otherwise this method would return immediately.
     *
     * @see #initService(Object)
     */
    public static void initService(Object... services) {
        if (services != null) {
            for (Object o : services) {
                initService(o);
            }
        }
    }

    /**
     * Starts the given {@code value} if it's a {@link Service} or a collection of it.
     * <p/>
     * Calling this method has no effect if {@code value} is {@code null}.
     */
    public static void startService(Object value) {
        if (value instanceof Service) {
            ((Service) value).start();
        } else if (value instanceof Iterable) {
            for (Object o : (Iterable) value) {
                startService(o);
            }
        }
    }
    
    /**
     * Starts each element of the given {@code services} if {@code services} itself is
     * not {@code null}, otherwise this method would return immediately.
     * 
     * @see #startService(Object)
     */
    public static void startService(Object... services) {
        if (services != null) {
            for (Object o : services) {
                startService(o);
            }
        }
    }

    /**
     * Stops each element of the given {@code services} if {@code services} itself is
     * not {@code null}, otherwise this method would return immediately.
     * <p/>
     * If there's any exception being thrown while stopping the elements one after the
     * other this method would rethrow the <b>first</b> such exception being thrown.
     * 
     * @see #stopService(Collection)
     */
    public static void stopService(Object... services) {
        if (services != null) {
            for (Object o : services) {
                stopService(o);
            }
        }
    }

    /**
     * Stops the given {@code value}, rethrowing the first exception caught.
     * <p/>
     * Calling this method has no effect if {@code value} is {@code null}.
     * 
     * @see Service#stop()
     * @see #stopService(Collection)
     */
    public static void stopService(Object value) {
        if (value instanceof Service) {
            ((Service) value).stop();
        } else if (value instanceof Iterable) {
            for (Object o : (Iterable) value) {
                stopService(o);
            }
        }
    }

    /**
     * Stops each element of the given {@code services} if {@code services} itself is
     * not {@code null}, otherwise this method would return immediately.
     * <p/>
     * If there's any exception being thrown while stopping the elements one after the
     * other this method would rethrow the <b>first</b> such exception being thrown.
     * 
     * @see #stopService(Object)
     */
    public static void stopService(Collection<?> services) {
        if (services == null) {
            return;
        }
        RuntimeException firstException = null;
        for (Object value : services) {
            try {
                stopService(value);
            } catch (RuntimeException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Caught exception stopping service: {}", value, e);
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
     * Stops and shutdowns each element of the given {@code services} if {@code services} itself is
     * not {@code null}, otherwise this method would return immediately.
     * <p/>
     * If there's any exception being thrown while stopping/shutting down the elements one after
     * the other this method would rethrow the <b>first</b> such exception being thrown.
     * 
     * @see #stopAndShutdownServices(Collection)
     */
    public static void stopAndShutdownServices(Object... services) {
        if (services == null) {
            return;
        }
        List<Object> list = Arrays.asList(services);
        stopAndShutdownServices(list);
    }

    /**
     * Stops and shutdowns the given {@code service}, rethrowing the first exception caught.
     * <p/>
     * Calling this method has no effect if {@code value} is {@code null}.
     * 
     * @see #stopService(Object)
     * @see ShutdownableService#shutdown()
     */
    public static void stopAndShutdownService(Object value) {
        stopService(value);

        // then try to shutdown
        if (value instanceof ShutdownableService) {
            ShutdownableService service = (ShutdownableService)value;
            LOG.trace("Shutting down service {}", value);
            service.shutdown();
        }
    }

    /**
     * Stops and shutdowns each element of the given {@code services} if {@code services}
     * itself is not {@code null}, otherwise this method would return immediately.
     * <p/>
     * If there's any exception being thrown while stopping/shutting down the elements one after
     * the other this method would rethrow the <b>first</b> such exception being thrown.
     * 
     * @see #stopService(Object)
     * @see ShutdownableService#shutdown()
     */
    public static void stopAndShutdownServices(Collection<?> services) {
        if (services == null) {
            return;
        }
        RuntimeException firstException = null;

        for (Object value : services) {

            try {
                // must stop it first
                stopService(value);

                // then try to shutdown
                if (value instanceof ShutdownableService) {
                    ShutdownableService service = (ShutdownableService)value;
                    LOG.trace("Shutting down service: {}", service);
                    service.shutdown();
                }
            } catch (RuntimeException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Caught exception shutting down service: {}", value, e);
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
     * Resumes each element of the given {@code services} if {@code services} itself is
     * not {@code null}, otherwise this method would return immediately.
     * <p/>
     * If there's any exception being thrown while resuming the elements one after the
     * other this method would rethrow the <b>first</b> such exception being thrown.
     * 
     * @see #resumeService(Object)
     */
    public static void resumeServices(Collection<?> services) {
        if (services == null) {
            return;
        }
        RuntimeException firstException = null;
        for (Object value : services) {
            if (value instanceof Service) {
                Service service = (Service)value;
                try {
                    resumeService(service);
                } catch (RuntimeException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Caught exception resuming service: {}", service, e);
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
     * Resumes the given {@code service}.
     * <p/>
     * If {@code service} is both {@link org.apache.camel.Suspendable} and {@link org.apache.camel.SuspendableService} then
     * its {@link org.apache.camel.SuspendableService#resume()} is called but
     * <b>only</b> if {@code service} is already {@link #isSuspended(Object)
     * suspended}.
     * <p/>
     * If {@code service} is <b>not</b> a
     * {@link org.apache.camel.Suspendable} and {@link org.apache.camel.SuspendableService} then its
     * {@link org.apache.camel.Service#start()} is called.
     * <p/>
     * Calling this method has no effect if {@code service} is {@code null}.
     * 
     * @param service the service
     * @return <tt>true</tt> if either <tt>resume</tt> method or
     *         {@link #startService(Object)} was called, <tt>false</tt>
     *         otherwise.
     * @throws Exception is thrown if error occurred
     * @see #startService(Object)
     */
    public static boolean resumeService(Object service) {
        if (service instanceof Suspendable && service instanceof SuspendableService) {
            SuspendableService ss = (SuspendableService) service;
            if (ss.isSuspended()) {
                LOG.debug("Resuming service {}", service);
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

    /**
     * Suspends each element of the given {@code services} if {@code services} itself is
     * not {@code null}, otherwise this method would return immediately.
     * <p/>
     * If there's any exception being thrown while suspending the elements one after the
     * other this method would rethrow the <b>first</b> such exception being thrown.
     * 
     * @see #suspendService(Object)
     */
    public static void suspendServices(Collection<?> services) {
        if (services == null) {
            return;
        }
        RuntimeException firstException = null;
        for (Object value : services) {
            if (value instanceof Service) {
                Service service = (Service)value;
                try {
                    suspendService(service);
                } catch (RuntimeException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Caught exception suspending service: {}", service, e);
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
     * Suspends the given {@code service}.
     * <p/>
     * If {@code service} is both {@link org.apache.camel.Suspendable} and {@link org.apache.camel.SuspendableService} then
     * its {@link org.apache.camel.SuspendableService#suspend()} is called but
     * <b>only</b> if {@code service} is <b>not</b> already
     * {@link #isSuspended(Object) suspended}.
     * <p/>
     * If {@code service} is <b>not</b> a
     * {@link org.apache.camel.Suspendable} and {@link org.apache.camel.SuspendableService} then its
     * {@link org.apache.camel.Service#stop()} is called.
     * <p/>
     * Calling this method has no effect if {@code service} is {@code null}.
     * 
     * @param service the service
     * @return <tt>true</tt> if either the <tt>suspend</tt> method or
     *         {@link #stopService(Object)} was called, <tt>false</tt>
     *         otherwise.
     * @throws Exception is thrown if error occurred
     * @see #stopService(Object)
     */
    public static boolean suspendService(Object service) {
        if (service instanceof Suspendable && service instanceof SuspendableService) {
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
     * Is the given service stopping or already stopped?
     *
     * @return <tt>true</tt> if stopping or already stopped, <tt>false</tt> otherwise
     * @see StatefulService#isStopping()
     * @see StatefulService#isStopped()
     */
    public static boolean isStopped(Object value) {
        if (value instanceof StatefulService) {
            StatefulService service = (StatefulService) value;
            if (service.isStopping() || service.isStopped()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Is the given service starting or already started?
     *
     * @return <tt>true</tt> if starting or already started, <tt>false</tt> otherwise
     * @see StatefulService#isStarting()
     * @see StatefulService#isStarted()
     */
    public static boolean isStarted(Object value) {
        if (value instanceof StatefulService) {
            StatefulService service = (StatefulService) value;
            if (service.isStarting() || service.isStarted()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Is the given service suspending or already suspended?
     *
     * @return <tt>true</tt> if suspending or already suspended, <tt>false</tt> otherwise
     * @see StatefulService#isSuspending()
     * @see StatefulService#isSuspended()
     */
    public static boolean isSuspended(Object value) {
        if (value instanceof StatefulService) {
            StatefulService service = (StatefulService) value;
            if (service.isSuspending() || service.isSuspended()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gathers all child services by navigating the service to recursively gather all child services.
     * <p/>
     * The returned set does <b>not</b> include the children being error handler.
     *
     * @param service the service
     * @return the services, including the parent service, and all its children
     */
    public static Set<Service> getChildServices(Service service) {
        return getChildServices(service, false);
    }

    /**
     * Gathers all child services by navigating the service to recursively gather all child services.
     *
     * @param service the service
     * @param includeErrorHandler whether to include error handlers
     * @return the services, including the parent service, and all its children
     */
    public static Set<Service> getChildServices(Service service, boolean includeErrorHandler) {
        Set<Service> answer = new LinkedHashSet<>();
        doGetChildServices(answer, service, includeErrorHandler);
        return answer;
    }

    private static void doGetChildServices(Set<Service> services, Service service, boolean includeErrorHandler) {
        services.add(service);
        if (service instanceof Navigate) {
            Navigate<?> nav = (Navigate<?>) service;
            if (nav.hasNext()) {
                List<?> children = nav.next();
                for (Object child : children) {
                    if (child instanceof Channel) {
                        if (includeErrorHandler) {
                            // special for error handler as they are tied to the Channel
                            Processor errorHandler = ((Channel) child).getErrorHandler();
                            if (errorHandler instanceof Service) {
                                services.add((Service) errorHandler);
                            }
                        }
                        Processor next = ((Channel) child).getNextProcessor();
                        if (next instanceof Service) {
                            services.add((Service) next);
                        }
                    }
                    if (child instanceof Service) {
                        doGetChildServices(services, (Service) child, includeErrorHandler);
                    }
                }
            }
        }
    }
    
}
