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
package org.apache.camel.component.ai.resource;

/**
 * Listener notified when resource specifications are registered to or deregistered from an {@link AiResourceRegistry}.
 * <p>
 * Registration events are driven by the {@code ai-resource} consumer lifecycle: a resource is registered when its route
 * starts or resumes, and deregistered when its route stops or suspends. A resource endpoint declaring multiple tags
 * fires one event per tag.
 * <p>
 * Callbacks are invoked outside the registry lock, on the thread performing the (de)registration. Implementations must
 * be thread-safe and non-blocking; a callback that throws is logged and does not affect the registration itself or
 * other listeners.
 *
 * @since 4.23
 */
public interface AiResourceRegistryListener {

    /**
     * Called after a resource specification has been registered.
     *
     * @param tag  the tag the resource was registered under, or {@code null} for the default (untagged) pool
     * @param spec the registered resource specification
     */
    void resourceRegistered(String tag, AiResourceSpec spec);

    /**
     * Called after a resource specification has been deregistered.
     *
     * @param tag  the tag the resource was deregistered from, or {@code null} for the default (untagged) pool
     * @param spec the deregistered resource specification
     */
    void resourceDeregistered(String tag, AiResourceSpec spec);

    /**
     * Called after a resource-template specification has been registered. The default implementation is a no-op so
     * adapters that only understand fixed resources remain source and binary compatible.
     *
     * @param tag  the tag the template was registered under, or {@code null} for the default pool
     * @param spec the registered resource-template specification
     * @since      4.24
     */
    default void resourceTemplateRegistered(String tag, AiResourceTemplateSpec spec) {
    }

    /**
     * Called after a resource-template specification has been deregistered.
     *
     * @param tag  the tag the template was deregistered from, or {@code null} for the default pool
     * @param spec the deregistered resource-template specification
     * @since      4.24
     */
    default void resourceTemplateDeregistered(String tag, AiResourceTemplateSpec spec) {
    }
}
