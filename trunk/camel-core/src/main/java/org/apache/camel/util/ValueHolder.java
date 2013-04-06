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
package org.apache.camel.util;

/**
 * Holder object for a given value.
 */
public class ValueHolder<V> {
    private V value;

    /**
     * @deprecated should be immutable, will be removed in Camel 3.0
     */
    @Deprecated
    public ValueHolder() {
    }

    public ValueHolder(V val) {
        value = val;
    }

    public V get() {
        return value;
    }

    /**
     * @deprecated should be immutable, will be removed in Camel 3.0
     */
    @Deprecated
    public void set(V val) {
        value = val;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ValueHolder<?> that = (ValueHolder<?>) o;

        if (value != null ? !value.equals(that.value) : that.value != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }
}
