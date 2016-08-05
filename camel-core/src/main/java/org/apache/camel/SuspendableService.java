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
 * A {@link Service} which is also capable of suspending and resuming.
 * <p/>
 * This is useable for services which needs more fine grained control at runtime supporting suspension.
 * Other services may select to mimic suspending by just stopping the service.
 * <p/>
 * For example this is use by the JmsConsumer which suspends the Spring JMS listener instead of stopping
 * the consumer totally.
 * <p/>
 * <b>Important:</b> The service should also implement the {@link Suspendable} marker interface to indicate
 * the service supports suspension using custom code logic.
 *
 * @see Suspendable
 */
public interface SuspendableService extends Service {

    /**
     * Suspends the service.
     *
     * @throws Exception is thrown if suspending failed
     */
    void suspend() throws Exception;

    /**
     * Resumes the service.
     *
     * @throws Exception is thrown if resuming failed
     */
    void resume() throws Exception;

    /**
     * Tests whether the service is suspended or not.
     *
     * @return <tt>true</tt> if suspended
     */
    boolean isSuspended();
}
