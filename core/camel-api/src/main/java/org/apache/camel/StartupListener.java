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
package org.apache.camel;

/**
 * Allows objects to be notified when {@link CamelContext} has almost done all work when starting.
 * <p/>
 * The state of {@link CamelContext} may still be in <tt>starting</tt> when this callback is invoked, this is by design.
 * The callback is invoked during the routes startup procedure when starting {@link CamelContext}.
 * <p/>
 * This can be used to perform any custom work when the entire {@link CamelContext} has been initialized and <b>almost</b>
 * started. This callback is invoked twice during starting the Camel routes, once before the route consumers are started,
 * and once again after the route consumer has just been started. This is by design to allow Camel components
 * to react accordingly and for example to register custom startup listeners during starting consumers.
 * <p/>
 * If you want to have only one callback after the route consumers has been fully started then use the {@link ExtendedStartupListener} instead.
 * <p/>
 * For example the QuartzComponent leverages this to ensure the Quartz scheduler does not start until after all the
 * Camel routes and services have already been started.
 * <p/>
 * <b>Important:</b> You cannot use this listener to add and start new routes to the {@link CamelContext} as this is not
 * supported by design, as this listener plays a role during starting up routes. Instead you can use an {@link org.apache.camel.spi.EventNotifier}
 * and listen on the {@link org.apache.camel.spi.CamelEvent.CamelContextStartedEvent} event and then add and start new routes from there.
 * Instead use the {@link ExtendedStartupListener} if you wish to add new routes.
 *
 * @see ExtendedStartupListener
 */
public interface StartupListener {

    /**
     * Callback invoked when the {@link CamelContext} is being started.
     *
     * @param context        the Camel context
     * @param alreadyStarted whether or not the {@link CamelContext} already has been started. For example the context
     *                       could already have been started, and then a service is added/started later which still
     *                       triggers this callback to be invoked.
     * @throws Exception     can be thrown in case of errors to fail the startup process and have the application
     *                       fail on startup.
     */
    default void onCamelContextStarting(CamelContext context, boolean alreadyStarted) throws Exception {
    }

    /**
     * Callback invoked when the {@link CamelContext} is about to be fully started (not started yet).
     * Yes we are aware of the method name, but we can all have a bad-naming day.
     *
     * @param context        the Camel context
     * @param alreadyStarted whether or not the {@link CamelContext} already has been started. For example the context
     *                       could already have been started, and then a service is added/started later which still
     *                       triggers this callback to be invoked.
     * @throws Exception     can be thrown in case of errors to fail the startup process and have the application
     *                       fail on startup.
     */
    void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception;

    /**
     * Callback invoked when the {@link CamelContext} has been fully started.
     *
     * @param context        the Camel context
     * @param alreadyStarted whether or not the {@link CamelContext} already has been started. For example the context
     *                       could already have been started, and then a service is added/started later which still
     *                       triggers this callback to be invoked.
     * @throws Exception     can be thrown in case of errors to fail the startup process and have the application
     *                       fail on startup.
     */
    default void onCamelContextFullyStarted(CamelContext context, boolean alreadyStarted) throws Exception {
    }

}
