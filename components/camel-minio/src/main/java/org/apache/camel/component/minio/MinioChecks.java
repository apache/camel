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
package org.apache.camel.component.minio;

import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.camel.RuntimeCamelException;

import static org.apache.camel.util.ObjectHelper.isNotEmpty;

public final class MinioChecks {
    private MinioChecks() {
        // Prevent instantiation of this factory class.
        throw new RuntimeCamelException(
                "Do not instantiate a Factory class! Refer to the class to learn how to properly use this factory implementation.");
    }

    static <C> void checkIfConfigIsNotEmptyAndSetAndConfig(final Supplier<C> getterFn, final Consumer<C> setterFn) {
        if (isNotEmpty(getterFn.get())) {
            setterFn.accept(getterFn.get());
        }
    }

    static <C extends Number> void checkLengthAndSetConfig(final Supplier<C> getterFn, final Consumer<C> setterFn) {
        if (getterFn.get().longValue() > 0) {
            setterFn.accept(getterFn.get());
        }
    }
}
