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

package org.apache.camel.component.file.azure.strategy;

import java.util.Map;
import java.util.function.Consumer;

import org.apache.camel.LoggingLevel;
import org.apache.camel.component.file.strategy.GenericFileRenameExclusiveReadLockStrategy;

final class StrategyUtil {

    private StrategyUtil() {

    }

    static <T> void ifNotEmpty(Map<String, Object> params, String key, Class<T> clazz, Consumer<T> consumer) {
        Object o = params.get(key);

        if (o != null) {
            consumer.accept(clazz.cast(o));
        }
    }

    static void setup(GenericFileRenameExclusiveReadLockStrategy<?> readLockStrategy, Map<String, Object> params) {
        ifNotEmpty(params, "readLockTimeout", Long.class, readLockStrategy::setTimeout);
        ifNotEmpty(params, "readLockCheckInterval", Long.class, readLockStrategy::setCheckInterval);
        ifNotEmpty(params, "readLockMarkerFile", Boolean.class, readLockStrategy::setMarkerFiler);
        ifNotEmpty(params, "readLockLoggingLevel", LoggingLevel.class, readLockStrategy::setReadLockLoggingLevel);
    }

    static void setup(FilesChangedExclusiveReadLockStrategy readLockStrategy, Map<String, Object> params) {
        ifNotEmpty(params, "readLockTimeout", Long.class, readLockStrategy::setTimeout);
        ifNotEmpty(params, "readLockCheckInterval", Long.class, readLockStrategy::setCheckInterval);
        ifNotEmpty(params, "readLockMinLength", Long.class, readLockStrategy::setMinLength);
        ifNotEmpty(params, "readLockMinAge", Long.class, readLockStrategy::setMinAge);
        ifNotEmpty(params, "fastExistsCheck", Boolean.class, readLockStrategy::setFastExistsCheck);
        ifNotEmpty(params, "readLockMarkerFile", Boolean.class, readLockStrategy::setMarkerFiler);
        ifNotEmpty(params, "readLockLoggingLevel", LoggingLevel.class, readLockStrategy::setReadLockLoggingLevel);
    }

}
