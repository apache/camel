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
package org.apache.camel.component.aws2.kinesis.consumer;

import org.apache.camel.resume.Cacheable;
import org.apache.camel.resume.ResumeStrategyConfiguration;
import org.apache.camel.resume.ResumeStrategyConfigurationBuilder;
import org.apache.camel.resume.cache.ResumeCache;

public class KinesisResumeStrategyConfiguration extends ResumeStrategyConfiguration {

    private KinesisResumeStrategyConfiguration() {
    }

    @Override
    public String resumeStrategyService() {
        return "kinesis-resume-strategy";
    }

    public static KinesisResumeStrategyConfigurationBuilder builder() {
        return new KinesisResumeStrategyConfigurationBuilder();
    }

    public static class KinesisResumeStrategyConfigurationBuilder
            implements
            ResumeStrategyConfigurationBuilder<KinesisResumeStrategyConfigurationBuilder, KinesisResumeStrategyConfiguration> {

        private KinesisResumeStrategyConfigurationBuilder() {
        }

        private Cacheable.FillPolicy fillPolicy = Cacheable.FillPolicy.MAXIMIZING;
        private ResumeCache resumeCache;

        @Override
        public KinesisResumeStrategyConfigurationBuilder withCacheFillPolicy(Cacheable.FillPolicy cacheFillPolicy) {
            this.fillPolicy = cacheFillPolicy;
            return this;
        }

        @Override
        public KinesisResumeStrategyConfigurationBuilder withResumeCache(ResumeCache<?> resumeCache) {
            this.resumeCache = resumeCache;
            return this;
        }

        @Override
        public KinesisResumeStrategyConfiguration build() {
            KinesisResumeStrategyConfiguration result = new KinesisResumeStrategyConfiguration();
            result.setResumeCache(resumeCache);
            result.setCacheFillPolicy(fillPolicy);
            return result;
        }
    }
}
