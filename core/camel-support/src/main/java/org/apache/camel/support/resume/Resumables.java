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

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.function.Predicate;

import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.Resumable;

/**
 * A wrapper for resumable entities
 */
public final class Resumables {

    /**
     * An anonymous resumable type
     *
     * @param <K> the type of the key, name or object that can be addressed by the given offset (aka addressable)
     * @param <V> the type of offset
     */
    private static class AnonymousResumable<K, V> implements Resumable {
        private final K addressable;
        private V offset;

        /**
         * Creates a new anonymous resumable type
         *
         * @param addressable the key, name or object that can be addressed by the given offset
         */
        public AnonymousResumable(K addressable) {
            this.addressable = addressable;
        }

        /**
         * Creates a new anonymous resumable type
         *
         * @param addressable the key, name or object that can be addressed by the given offset
         * @param offset      the offset value
         */
        public AnonymousResumable(K addressable, V offset) {
            this.addressable = addressable;
            this.offset = offset;
        }

        @Override
        public Offset<V> getLastOffset() {
            return Offsets.of(offset);
        }

        @Override
        public OffsetKey<K> getOffsetKey() {
            return new OffsetKey<>() {
                private final K key = addressable;

                @Override
                public void setValue(K key) {
                    throw new UnsupportedOperationException("Setting offset keys for anonymous resumables is unsupported");
                }

                @Override
                public K getValue() {
                    return key;
                }
            };
        }
    }

    private Resumables() {

    }

    /**
     * Creates a new resumable for an addressable
     *
     * @param  addressable the key, name or object that can be addressed by the given offset
     * @param  offset      the offset value
     * @param  <K>         the type of the key, name or object that can be addressed by the given offset (aka
     *                     addressable)
     * @param  <V>         the type of offset
     * @return             A new resumable entity for the given addressable with the given offset value
     */
    public static <K, V> Resumable of(K addressable, V offset) {
        return new AnonymousResumable<>(addressable, offset);
    }

    /**
     * Iterates over the set of input checking if they should be resumed or not.
     *
     * @param  input          the input array to check for resumables
     * @param  resumableCheck a checker method that returns true if a single entry of the input should be resumed or
     *                        false otherwise. For instance: given a set A, B and C, where B has already been processed,
     *                        then a test for A and C returns true, whereas a test for B returns false.
     * @return                a new array containing the elements that still need to be processed
     */
    public static <T> T[] resumeEach(T[] input, Predicate<T> resumableCheck) {
        @SuppressWarnings("unchecked")
        T[] tmp = (T[]) Array.newInstance(input.getClass().getComponentType(), input.length);
        int count = 0;

        for (T entry : input) {
            if (resumableCheck.test(entry)) {
                tmp[count] = entry;
                count++;
            }
        }

        if (count != input.length) {
            return Arrays.copyOf(tmp, count);
        }

        return input;
    }

}
