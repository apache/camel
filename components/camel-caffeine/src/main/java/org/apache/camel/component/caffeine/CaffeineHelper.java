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
package org.apache.camel.component.caffeine;

import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.camel.util.ObjectHelper;

public final class CaffeineHelper {

    private CaffeineHelper() {
    }

    public static void defineBuilder(Caffeine<?, ?> builder, CaffeineConfiguration configuration) {
        if (configuration.getEvictionType() == EvictionType.SIZE_BASED) {
            if (configuration.getInitialCapacity() != null) {
                builder.initialCapacity(configuration.getInitialCapacity());
            }
            if (configuration.getMaximumSize() != null) {
                builder.maximumSize(configuration.getMaximumSize());
            }
        } else if (configuration.getEvictionType() == EvictionType.TIME_BASED) {
            builder.expireAfterAccess(configuration.getExpireAfterAccessTime(), TimeUnit.SECONDS);
            builder.expireAfterWrite(configuration.getExpireAfterWriteTime(), TimeUnit.SECONDS);
        }
        if (configuration.isStatsEnabled()) {
            if (ObjectHelper.isEmpty(configuration.getStatsCounter())) {
                builder.recordStats();
            } else {
                builder.recordStats(configuration::getStatsCounter);
            }
        }
        if (ObjectHelper.isNotEmpty(configuration.getRemovalListener())) {
            builder.removalListener(configuration.getRemovalListener());
        }
    }

}
