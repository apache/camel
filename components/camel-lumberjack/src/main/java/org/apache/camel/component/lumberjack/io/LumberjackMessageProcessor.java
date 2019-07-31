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
package org.apache.camel.component.lumberjack.io;

/**
 * This functional interface defines a processor that will be called when a lumberjack message is received
 */
@FunctionalInterface
public interface LumberjackMessageProcessor {
    /**
     * Called when a message is received. The {@code callback} must be called with the status of the processing
     *
     * @param payload Lumberjack message payload
     * @param callback Callback to call when the processing is complete
     */
    void onMessageReceived(Object payload, Callback callback);

    /**
     * This functional interface defines the callback to call when the processing of a Lumberjack message is complete
     */
    @FunctionalInterface
    interface Callback {
        /**
         * Called when the processing is complete.
         *
         * @param success {@code true} is the processing is a success, {@code false} otherwise
         */
        void onComplete(boolean success);
    }
}
