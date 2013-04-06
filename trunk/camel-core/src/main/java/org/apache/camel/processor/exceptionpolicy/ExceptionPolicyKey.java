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
package org.apache.camel.processor.exceptionpolicy;

import org.apache.camel.model.WhenDefinition;

/**
 * Exception policy key is a compound key for storing:
 * <b>route id </b> + <b>exception class</b> + <b>when</b> => <b>exception type</b>.
 * <p/>
 * This is used by Camel to store the onException types configured that has or has not predicates attached (when).
 */
public final class ExceptionPolicyKey {

    private final String routeId;
    private final Class<? extends Throwable> exceptionClass;
    private final WhenDefinition when;

    /**
     * @deprecated will be removed in the near future, use the other constructor
     */
    @Deprecated
    public ExceptionPolicyKey(Class<? extends Throwable> exceptionClass, WhenDefinition when) {
        this(null, exceptionClass, when);
    }

    /**
     * Key for exception clause
     *
     * @param routeId          the route, or use <tt>null</tt> for a global scoped
     * @param exceptionClass   the exception class
     * @param when             optional predicate when the exception clause should trigger
     */
    public ExceptionPolicyKey(String routeId, Class<? extends Throwable> exceptionClass, WhenDefinition when) {
        this.routeId = routeId;
        this.exceptionClass = exceptionClass;
        this.when = when;
    }

    public Class<?> getExceptionClass() {
        return exceptionClass;
    }

    public WhenDefinition getWhen() {
        return when;
    }

    public String getRouteId() {
        return routeId;
    }

    /**
     * @deprecated will be removed in the near future. Use the constructor instead.
     */
    @Deprecated
    public static ExceptionPolicyKey newInstance(Class<? extends Throwable> exceptionClass) {
        return new ExceptionPolicyKey(exceptionClass, null);
    }

    /**
     * @deprecated will be removed in the near future. Use the constructor instead.
     */
    @Deprecated
    public static ExceptionPolicyKey newInstance(Class<? extends Throwable> exceptionClass, WhenDefinition when) {
        return new ExceptionPolicyKey(exceptionClass, when);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ExceptionPolicyKey that = (ExceptionPolicyKey) o;

        if (exceptionClass != null ? !exceptionClass.equals(that.exceptionClass) : that.exceptionClass != null) {
            return false;
        }
        if (routeId != null ? !routeId.equals(that.routeId) : that.routeId != null) {
            return false;
        }
        if (when != null ? !when.equals(that.when) : that.when != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = routeId != null ? routeId.hashCode() : 0;
        result = 31 * result + (exceptionClass != null ? exceptionClass.hashCode() : 0);
        result = 31 * result + (when != null ? when.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ExceptionPolicyKey[route: " + (routeId != null ? routeId : "<global>") + ", " + exceptionClass + (when != null ? " " + when : "") + "]";
    }
}
