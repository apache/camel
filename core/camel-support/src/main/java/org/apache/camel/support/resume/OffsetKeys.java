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

import org.apache.camel.resume.OffsetKey;

/**
 * Utility class for handling offset keys
 */
public final class OffsetKeys {
    /**
     * For creating anonymous offset keys
     *
     * @param <T> the type of the offset key
     */
    private static class AnonymousOffsetKey<T> implements OffsetKey<T> {
        private T key;

        public AnonymousOffsetKey() {
        }

        public AnonymousOffsetKey(T key) {
            this.key = key;
        }

        @Override
        public void setValue(T key) {
            this.key = key;
        }

        @Override
        public T getValue() {
            return key;
        }
    }

    /**
     * For creating unmodifiable offset keys
     *
     * @param <T> the type of the offset key
     */
    private static class UnmodifiableOffsetKey<T> implements OffsetKey<T> {
        private final T key;

        public UnmodifiableOffsetKey(T key) {
            this.key = key;
        }

        @Override
        public void setValue(T key) {
            throw new UnsupportedOperationException("This object is unmodifiable");
        }

        @Override
        public T getValue() {
            return key;
        }
    }

    private OffsetKeys() {
    }

    /**
     * Creates a new offset key wrapping the given object
     *
     * @param  object the object to wrap in the offset key
     * @return        a new OffsetKey object that wraps the given object
     * @param  <T>    the type of the object being wrapped
     */
    public static <T> OffsetKey<T> of(T object) {
        return new AnonymousOffsetKey<>(object);
    }

    /**
     * Creates a new unmodifiable offset key wrapping the given object
     *
     * @param  object the object to wrap in the offset key
     * @return        a new OffsetKey object that wraps the given object. The offset key of this object cannot be
     *                updated.
     * @param  <T>    the type of the object being wrapped
     */
    public static <T> OffsetKey<T> unmodifiableOf(T object) {
        return new UnmodifiableOffsetKey<>(object);
    }

    /**
     * Creates new empty OffsetKey object
     *
     * @return an empty OffsetKey object
     */
    public static OffsetKey<?> empty() {
        return new AnonymousOffsetKey<>();
    }
}
