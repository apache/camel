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
package org.apache.camel;

/**
 * A {@link Service} which has all the lifecycle events and offers details about its current state.
 */
public interface StatefulService extends SuspendableService, ShutdownableService {

    /**
     * Returns the current status
     *
     * @return the current status
     */
    ServiceStatus getStatus();

    /**
     * Whether the service is started
     *
     * @return true if this service has been started
     */
    boolean isStarted();

    /**
     * Whether the service is starting
     *
     * @return true if this service is being started
     */
    boolean isStarting();

    /**
     * Whether the service is stopping
     *
     * @return true if this service is in the process of stopping
     */
    boolean isStopping();

    /**
     * Whether the service is stopped
     *
     * @return true if this service is stopped
     */
    boolean isStopped();

    /**
     * Whether the service is suspending
     *
     * @return true if this service is in the process of suspending
     */
    boolean isSuspending();

    /**
     * Helper methods so the service knows if it should keep running.
     * Returns <tt>false</tt> if the service is being stopped or is stopped.
     *
     * @return <tt>true</tt> if the service should continue to run.
     */
    boolean isRunAllowed();

    /**
     * Returns the version of this service
     *
     * @return the version
     */
    String getVersion();

}
