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
 * Basic configuration holder for resume strategies
 */
public abstract class ResumeStrategyConfiguration {
    private Cacheable.FillPolicy cacheFillPolicy;
    private ResumeCache<?> resumeCache;

    /**
     * Gets the {@link org.apache.camel.resume.Cacheable.FillPolicy} for the cache used in the strategy
     *
     * @return the fill policy to use
     */
    public Cacheable.FillPolicy getCacheFillPolicy() {
        return cacheFillPolicy;
    }

    /**
     * Sets the {@link org.apache.camel.resume.Cacheable.FillPolicy} for the cache used in the strategy
     *
     * @param cacheFillPolicy the fill policy to use
     */
    public void setCacheFillPolicy(Cacheable.FillPolicy cacheFillPolicy) {
        this.cacheFillPolicy = cacheFillPolicy;
    }

    /**
     * Allows the implementation to provide custom strategy service factories. It binds to service name provided in the
     * {@link org.apache.camel.spi.annotations.JdkService} strategy .This allows the strategy to be resolved
     * automatically in runtime while also allowing fallback to manually constructed strategies when necessary
     *
     * @return
     */
    public abstract String resumeStrategyService();

    public ResumeCache<?> getResumeCache() {
        return resumeCache;
    }

    public void setResumeCache(ResumeCache<?> resumeCache) {
        this.resumeCache = resumeCache;
    }
}
