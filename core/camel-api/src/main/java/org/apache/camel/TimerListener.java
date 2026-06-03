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
 * Callback interface for objects that need to perform work on a recurring internal timer tick.
 * <p/>
 * Implementations are registered with the {@link org.apache.camel.support.TimerListenerManager}, which fires
 * {@link #onTimer()} at a fixed interval for all registered listeners. This is used internally by components such as
 * the throttler and the load-balancer to periodically update rate-limiter windows or health counters without coupling
 * to a scheduler component.
 *
 * @see org.apache.camel.support.TimerListenerManager
 */
public interface TimerListener {

    /**
     * Notification invoked.
     */
    void onTimer();

}
