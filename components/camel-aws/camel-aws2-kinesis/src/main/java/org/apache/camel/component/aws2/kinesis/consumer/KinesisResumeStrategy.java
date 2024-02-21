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

import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.Resumable;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.resume.ResumeStrategyConfiguration;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.spi.annotations.JdkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JdkService("kinesis-resume-strategy")
public class KinesisResumeStrategy implements ResumeStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(KinesisResumeStrategy.class);

    private ResumeStrategyConfiguration configuration = KinesisResumeStrategyConfiguration.builder().build();
    private ResumeCache resumeCache;
    private ResumeAdapter adapter;

    @Override
    public void start() {
        LOG.info("start");
        this.resumeCache = configuration.getResumeCache();
    }

    @Override
    public void stop() {
        LOG.info("stop");
    }

    @Override
    public void setAdapter(ResumeAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public ResumeAdapter getAdapter() {
        return adapter;
    }

    @Override
    public <T extends Resumable> void updateLastOffset(T offset) {
        resumeCache.add(offset.getOffsetKey().getValue().toString(),
                new KinesisOffset(offset.getLastOffset().getValue(String.class)));
    }

    @Override
    public <T extends Resumable> void updateLastOffset(T offset, UpdateCallBack updateCallBack) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offsetValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offset, UpdateCallBack updateCallBack) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setResumeStrategyConfiguration(ResumeStrategyConfiguration resumeStrategyConfiguration) {
        this.configuration = resumeStrategyConfiguration;
    }

    @Override
    public ResumeStrategyConfiguration getResumeStrategyConfiguration() {
        return configuration;
    }
}
