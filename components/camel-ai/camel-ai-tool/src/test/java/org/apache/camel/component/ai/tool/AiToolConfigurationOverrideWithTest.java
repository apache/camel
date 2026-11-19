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
package org.apache.camel.component.ai.tool;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Every option of {@link AiToolConfiguration} must be combined by {@link AiToolConfiguration#overrideWith}, so that an
 * option added later (such as one that guards the tool) is not lost when a configuration bean is used.
 */
class AiToolConfigurationOverrideWithTest {

    @Test
    void overrideWithCopiesEveryOption() throws Exception {
        AiToolConfiguration other = new AiToolConfiguration();
        for (Field field : options()) {
            field.set(other, valueFor(field));
        }

        AiToolConfiguration config = new AiToolConfiguration();
        config.overrideWith(other);

        for (Field field : options()) {
            assertThat(field.get(config))
                    .as("option %s is not copied by AiToolConfiguration.overrideWith", field.getName())
                    .isEqualTo(field.get(other));
        }
    }

    @Test
    void overrideWithKeepsTheOptionsTheOtherDoesNotSet() throws Exception {
        AiToolConfiguration config = new AiToolConfiguration();
        for (Field field : options()) {
            field.set(config, valueFor(field));
        }
        AiToolConfiguration before = config.copy();

        config.overrideWith(new AiToolConfiguration());

        for (Field field : options()) {
            assertThat(field.get(config))
                    .as("option %s is cleared by an empty configuration in AiToolConfiguration.overrideWith",
                            field.getName())
                    .isEqualTo(field.get(before));
        }
    }

    private static Field[] options() {
        return Arrays.stream(AiToolConfiguration.class.getDeclaredFields())
                .filter(f -> !Modifier.isStatic(f.getModifiers()))
                .peek(f -> f.setAccessible(true))
                .toArray(Field[]::new);
    }

    private static Object valueFor(Field field) {
        Class<?> type = field.getType();
        if (type == String.class) {
            return "value-of-" + field.getName();
        } else if (type == boolean.class || type == Boolean.class) {
            return Boolean.TRUE;
        } else if (type == Map.class) {
            return Map.of("key-of-" + field.getName(), "value");
        } else if (type.isInterface()) {
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, (proxy, method, args) -> {
                if ("equals".equals(method.getName())) {
                    return proxy == args[0];
                } else if ("hashCode".equals(method.getName())) {
                    return System.identityHashCode(proxy);
                }
                return null;
            });
        }
        return fail("no test value for option %s of type %s: add one here", field.getName(), type.getName());
    }
}
