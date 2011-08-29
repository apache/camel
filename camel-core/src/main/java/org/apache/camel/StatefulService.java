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


public interface StatefulService extends Service, ShutdownableService {

    void suspend() throws Exception;

    void resume() throws Exception;

    /**
     * Returns the current status
     */
    ServiceStatus getStatus();

    /**
     * @return true if this service has been started
     */
    boolean isStarted();

    /**
     * @return true if this service is being started
     */
    boolean isStarting();

    /**
     * @return true if this service is in the process of stopping
     */
    boolean isStopping();

    /**
     * @return true if this service is stopped
     */
    boolean isStopped();

    /**
     * @return true if this service is in the process of suspending
     */
    boolean isSuspending();

    /**
     * @return true if this service is suspended
     */
    boolean isSuspended();

    /**
     * Helper methods so the service knows if it should keep running.
     * Returns <tt>false</tt> if the service is being stopped or is stopped.
     *
     * @return <tt>true</tt> if the service should continue to run.
     */
    boolean isRunAllowed();

    /**
     * Returns the version of this service
     */
    String getVersion();

}
