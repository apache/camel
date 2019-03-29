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
package org.apache.camel.component.bean;

import java.lang.reflect.Method;

/**
 * A key used for caching {@link BeanInfo} by the {@link BeanComponent}
 */
public final class BeanInfoCacheKey {

    private final Class<?> type;
    private final Method explicitMethod;

    public BeanInfoCacheKey(Class<?> type, Method explicitMethod) {
        this.type = type;
        this.explicitMethod = explicitMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BeanInfoCacheKey that = (BeanInfoCacheKey) o;

        if (explicitMethod != null ? !explicitMethod.equals(that.explicitMethod) : that.explicitMethod != null) {
            return false;
        }
        if (!type.equals(that.type)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + (explicitMethod != null ? explicitMethod.hashCode() : 0);
        return result;
    }
}
