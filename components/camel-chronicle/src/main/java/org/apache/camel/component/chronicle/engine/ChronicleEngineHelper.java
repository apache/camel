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
package org.apache.camel.component.chronicle.engine;

import java.lang.ref.WeakReference;
import java.util.function.Supplier;

import org.apache.camel.Message;
import org.apache.camel.util.ObjectHelper;

public final class ChronicleEngineHelper {
    private ChronicleEngineHelper() {
    }

    public static Object mandatoryHeader(Message message, String header) {
        return ObjectHelper.notNull(message.getHeader(header), header);
    }

    public static Object mandatoryKey(Message message) {
        return mandatoryHeader(message, ChronicleEngineConstants.KEY);
    }

    public static Object mandatoryBody(Message message) {
        return ObjectHelper.notNull(message.getBody(), ChronicleEngineConstants.VALUE);
    }

    public static final class WeakRef<T> {
        private final Supplier<T> supplier;
        private WeakReference<T> ref;

        public WeakRef(Supplier<T> supplier) {
            this.supplier = supplier;
        }

        public synchronized T get() {
            T rv = ref.get();
            if (rv == null) {
                ref = new WeakReference<>(
                    rv = supplier.get()
                );
            }

            return rv;
        }

        public static <T> WeakRef<T> create(Supplier<T> supplier) {
            return new WeakRef<>(supplier);
        }
    }
}
