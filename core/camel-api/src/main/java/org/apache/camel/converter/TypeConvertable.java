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

package org.apache.camel.converter;

import java.util.Objects;

public final class TypeConvertable<F, T> {
    private final Class<F> from;
    private final Class<T> to;

    public TypeConvertable(Class<F> from, Class<T> to) {
        this.from = from;
        this.to = to;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        TypeConvertable<?, ?> that = (TypeConvertable<?, ?>) o;
        return Objects.equals(from, that.from) && Objects.equals(to, that.to);
    }

    @Override
    public int hashCode() {
        int result = 1;

        result = 31 * result + (from == null ? 0 : from.hashCode());
        result = 31 * result + (to == null ? 0 : to.hashCode());

        return result;
    }

    public Class<F> getFrom() {
        return from;
    }

    public Class<T> getTo() {
        return to;
    }
}
