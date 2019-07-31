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
package org.apache.camel.spi;

/**
 * A vetoable {@link org.apache.camel.spi.Synchronization}.
 * <p/>
 * When using {@link org.apache.camel.spi.Synchronization} they are normally executed
 * when the {@link org.apache.camel.Exchange} complete at the end. If the {@link org.apache.camel.Exchange}
 * is processed asynchronously the {@link org.apache.camel.spi.Synchronization} will be handed
 * over to the next thread. This ensures for example the file consumer will delete the processed file at the very
 * end, when the {@link org.apache.camel.Exchange} has been completed successfully.
 * <p/>
 * However there may be situations where you do not want to handover certain {@link org.apache.camel.spi.Synchronization},
 * such as when doing asynchronously request/reply over SEDA or VM endpoints.
 */
public interface SynchronizationVetoable extends Synchronization {

    /**
     * Whether or not handover this synchronization is allowed.
     * <p/>
     * For example when an {@link org.apache.camel.Exchange} is being routed
     * from one thread to another thread, such as using request/reply over SEDA
     *
     * @return <tt>true</tt> to allow handover, <tt>false</tt> to deny.
     */
    boolean allowHandover();
}
