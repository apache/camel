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
package org.apache.camel.component.springai.chat;

import java.lang.reflect.Field;

import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tells whether a Spring AI model observes its own calls. Spring AI models record a Micrometer Observation for every
 * model call and tool call when they are built with an ObservationRegistry (Spring Boot does this when observation
 * support is enabled), so Camel does not record them a second time.
 */
final class SpringAiTracing {

    private static final Logger LOG = LoggerFactory.getLogger(SpringAiTracing.class);

    private SpringAiTracing() {
    }

    /**
     * Whether the model holds a non no-op ObservationRegistry. Spring AI models keep it in a private field, which is
     * not public API: when it cannot be read, the model is considered not observed.
     */
    static boolean isObserved(Object model) {
        if (model == null) {
            return false;
        }
        for (Class<?> type = model.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (ObservationRegistry.class.isAssignableFrom(field.getType())) {
                    try {
                        field.setAccessible(true);
                        ObservationRegistry registry = (ObservationRegistry) field.get(model);
                        return registry != null && !registry.isNoop();
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        LOG.debug("Unable to read the ObservationRegistry of {}", type.getName(), e);
                        return false;
                    }
                }
            }
        }
        return false;
    }
}
