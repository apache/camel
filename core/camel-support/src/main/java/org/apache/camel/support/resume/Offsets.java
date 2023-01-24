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

package org.apache.camel.support.resume;

import org.apache.camel.resume.Offset;

/**
 * Offset handling support
 */
public final class Offsets {
    private static class AnonymousOffset<T> implements Offset<T> {
        private T offset;

        public AnonymousOffset(T offset) {
            this.offset = offset;
        }

        @Override
        public void update(T offset) {
            this.offset = offset;
        }

        @Override
        public T getValue() {
            return offset;
        }
    }

    private Offsets() {
    }

    /**
     * Creates a new offset with the given offset value
     *
     * @param  offsetValue the offset value
     * @param  <T>         The type of the offset
     * @return             A new Offset holder with the given offset value
     */
    public static <T> Offset<T> of(T offsetValue) {
        return new AnonymousOffset<>(offsetValue);
    }

    /**
     * Creates a new offset with a default value in case of the offset value is null
     *
     * @param  offsetValue  the offset value
     * @param  defaultValue the default offset value to use if the provided offset value is null
     * @param  <T>          the type of the offset
     * @return              A new Offset holder with the given offset value or the default one if the offset value is
     *                      null
     */
    public static <T> Offset<T> ofNullable(T offsetValue, T defaultValue) {
        if (offsetValue != null) {
            return new AnonymousOffset<>(offsetValue);
        }

        return Offsets.of(defaultValue);
    }
}
