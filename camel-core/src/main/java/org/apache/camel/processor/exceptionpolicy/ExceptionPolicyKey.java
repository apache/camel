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

import org.apache.camel.model.WhenType;

/**
 * Exception policy key is a compound key for storing:
 * <b>exception class</b> + <b>when</b> => <b>exception type</b>.
 * <p/>
 * This is used by Camel to store the onException types configued that has or has not predicates attached (when).
 */
public final class ExceptionPolicyKey {

    private final Class exceptionClass;
    private final WhenType when;

    public ExceptionPolicyKey(Class exceptionClass, WhenType when) {
        this.exceptionClass = exceptionClass;
        this.when = when;
    }

    public Class getExceptionClass() {
        return exceptionClass;
    }

    public WhenType getWhen() {
        return when;
    }

    public static ExceptionPolicyKey newInstance(Class exceptionClass) {
        return new ExceptionPolicyKey(exceptionClass, null);
    }

    public static ExceptionPolicyKey newInstance(Class exceptionClass, WhenType when) {
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

        if (!exceptionClass.equals(that.exceptionClass)) {
            return false;
        }
        if (when != null ? !when.equals(that.when) : that.when != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = exceptionClass.hashCode();
        result = 31 * result + (when != null ? when.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ExceptionPolicyKey[" + exceptionClass + (when != null ? " " + when : "") + "]";
    }
}
