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

import org.apache.camel.resume.Cacheable;
import org.apache.camel.resume.ResumeStrategyConfiguration;
import org.apache.camel.resume.ResumeStrategyConfigurationBuilder;
import org.apache.camel.resume.cache.ResumeCache;

/**
 * This class implements the most basic configuration set used by all resume strategy builders
 *
 * @param <T> The {@link ResumeStrategyConfigurationBuilder} providing the custom configuration
 * @param <Y> The type of the {@link ResumeStrategyConfiguration} that will be built by the builder
 */
public abstract class BasicResumeStrategyConfigurationBuilder<
        T extends BasicResumeStrategyConfigurationBuilder<T, Y>, Y extends ResumeStrategyConfiguration>
        implements ResumeStrategyConfigurationBuilder<T, Y> {
    protected Cacheable.FillPolicy cacheFillPolicy = Cacheable.FillPolicy.MAXIMIZING;
    protected ResumeCache<?> resumeCache;

    @Override
    public T withCacheFillPolicy(Cacheable.FillPolicy fillPolicy) {
        this.cacheFillPolicy = fillPolicy;

        return (T) this;
    }

    @Override
    public T withResumeCache(ResumeCache<?> resumeCache) {
        this.resumeCache = resumeCache;

        return (T) this;
    }

    protected void buildCommonConfiguration(ResumeStrategyConfiguration resumeStrategyConfiguration) {
        resumeStrategyConfiguration.setResumeCache(resumeCache);
        resumeStrategyConfiguration.setCacheFillPolicy(cacheFillPolicy);
    }
}
