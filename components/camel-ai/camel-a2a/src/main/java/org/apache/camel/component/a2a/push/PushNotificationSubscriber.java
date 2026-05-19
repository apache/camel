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
package org.apache.camel.component.a2a.push;

import org.apache.camel.component.a2a.model.StreamResponse;
import org.apache.camel.component.a2a.state.A2ATaskSubscriber;

/**
 * Bridges the task subscriber SPI to the push notification dispatcher. Registered as a global subscriber on each task
 * that has push notification configs, so the store doesn't need direct knowledge of push dispatch.
 */
public class PushNotificationSubscriber implements A2ATaskSubscriber {

    private final PushNotificationDispatcher dispatcher;

    public PushNotificationSubscriber(PushNotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public void onEvent(String taskId, StreamResponse event) {
        dispatcher.dispatch(taskId, event);
    }
}
