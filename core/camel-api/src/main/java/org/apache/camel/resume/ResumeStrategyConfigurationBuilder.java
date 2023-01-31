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

package org.apache.camel.resume;

import org.apache.camel.resume.cache.ResumeCache;

/**
 * Provides a basic interface for implementing component-specific configuration builder
 *
 * @param <T> The {@link ResumeStrategyConfigurationBuilder} providing the custom configuration
 * @param <Y> The type of the {@link ResumeStrategyConfiguration} that will be built by the builder
 */
public interface ResumeStrategyConfigurationBuilder<
        T extends ResumeStrategyConfigurationBuilder, Y extends ResumeStrategyConfiguration> {

    /**
     * Sets the {@link org.apache.camel.resume.Cacheable.FillPolicy} for the cache used in the strategy
     *
     * @param  cacheFillPolicy the fill policy to use
     * @return                 this instance
     */
    T withCacheFillPolicy(Cacheable.FillPolicy cacheFillPolicy);

    /**
     * Sets the local resume cache instance to use in the strategy
     *
     * @param  resumeCache the local resume cache instance to use in the strategy
     * @return             this instance
     */
    T withResumeCache(ResumeCache<?> resumeCache);

    /**
     * Builds the resume strategy configuration
     *
     * @return a new instance of the resume strategy configuration
     */
    Y build();
}
