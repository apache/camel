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
package org.apache.camel.util.concurrent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.parallel.Isolated;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-24320: {@link java.lang.ScopedValue#orElse} rejects a null fallback via {@code Objects.requireNonNull}, so the
 * ScopedValue-backed {@link ContextValue} must check {@link java.lang.ScopedValue#isBound()} first.
 */
@Isolated
@EnabledForJreRange(min = JRE.JAVA_25)
class ScopedValueContextValueOrElseTest {

    @Test
    void orElseNullWhenUnboundDoesNotThrow() throws Exception {
        Object contextValue = newScopedValueContextValue("testOrElseNull");

        Method orElse = contextValue.getClass().getMethod("orElse", Object.class);
        Object result = orElse.invoke(contextValue, new Object[] { null });

        assertThat(result).isNull();
        assertThat(contextValue.getClass().getMethod("isBound").invoke(contextValue)).isEqualTo(false);
    }

    @Test
    void orElseReturnsDefaultWhenUnbound() throws Exception {
        Object contextValue = newScopedValueContextValue("testOrElseDefault");

        Method orElse = contextValue.getClass().getMethod("orElse", Object.class);
        Object result = orElse.invoke(contextValue, "fallback");

        assertThat(result).isEqualTo("fallback");
    }

    @Test
    void orElseReturnsBoundValue() throws Exception {
        ContextValue<String> routeId = ContextValue.newInstance("boundRoute");
        String result = ContextValue.where(routeId, "myRoute", () -> routeId.orElse(null));

        assertThat(result).isEqualTo("myRoute");
    }

    private static Object newScopedValueContextValue(String name) throws Exception {
        Class<?> svClass = Class.forName("org.apache.camel.util.concurrent.ContextValueFactory$ScopedValueContextValue");
        Constructor<?> ctor = svClass.getDeclaredConstructor(String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(name);
    }
}
