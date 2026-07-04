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
package org.apache.camel.processor.resume.mongodb;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.Resumable;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.ResumeStrategy;
import org.apache.camel.resume.ResumeStrategyConfiguration;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.spi.StateRepository;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;

@JdkService("mongodb-resume-strategy")
public class MongoDbResumeStrategy implements ResumeStrategy {
    private ResumeAdapter adapter;
    private MongoDbResumeStrategyConfiguration configuration = new MongoDbResumeStrategyConfiguration();
    private boolean stopRepository;

    @Override
    public void start() {
        StateRepository<String, String> repository = configuration.getStateRepository();
        if (repository instanceof ServiceSupport serviceSupport && !serviceSupport.isStarted()) {
            stopRepository = true;
            try {
                ServiceHelper.startService(repository);
            } catch (Exception e) {
                throw new RuntimeCamelException("Unable to start MongoDB resume state repository", e);
            }
        }
    }

    @Override
    public void stop() {
        if (stopRepository) {
            try {
                ServiceHelper.stopAndShutdownService(configuration.getStateRepository());
            } catch (Exception e) {
                throw new RuntimeCamelException("Unable to stop MongoDB resume state repository", e);
            }
        }
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
    public void loadCache() {
        ResumeCache<String> resumeCache = getStringResumeCache();
        StateRepository<String, String> repository = configuration.getStateRepository();

        if (resumeCache == null || repository == null) {
            return;
        }

        if (adapter instanceof org.apache.camel.component.mongodb.MongoDbResumeAdapter mongoDbResumeAdapter) {
            String key = mongoDbResumeAdapter.getResumeTokenKey();
            if (ObjectHelper.isNotEmpty(key)) {
                String value = repository.getState(key);
                if (ObjectHelper.isNotEmpty(value)) {
                    resumeCache.add(key, value);
                }
            }
        }
    }

    @Override
    public <T extends Resumable> void updateLastOffset(T offset) {
        String key = String.valueOf(offset.getOffsetKey().getValue());
        String value = offset.getLastOffset().getValue(String.class);
        updateStoredOffset(key, value);
    }

    @Override
    public <T extends Resumable> void updateLastOffset(T offset, UpdateCallBack updateCallBack) {
        updateLastOffset(offset);
    }

    @Override
    public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offsetValue) {
        String key = String.valueOf(offsetKey.getValue());
        String value = offsetValue.getValue(String.class);
        updateStoredOffset(key, value);
    }

    @Override
    public void updateLastOffset(OffsetKey<?> offsetKey, Offset<?> offset, UpdateCallBack updateCallBack) {
        updateLastOffset(offsetKey, offset);
    }

    @Override
    public void setResumeStrategyConfiguration(ResumeStrategyConfiguration resumeStrategyConfiguration) {
        this.configuration = (MongoDbResumeStrategyConfiguration) resumeStrategyConfiguration;
    }

    @Override
    public ResumeStrategyConfiguration getResumeStrategyConfiguration() {
        return configuration;
    }

    public String getLastOffset(String key) {
        StateRepository<String, String> repository = configuration.getStateRepository();
        if (repository != null) {
            return repository.getState(key);
        }

        ResumeCache<String> resumeCache = getStringResumeCache();
        if (resumeCache != null) {
            return resumeCache.get(key, String.class);
        }

        return null;
    }

    private void updateStoredOffset(String key, String value) {
        if (ObjectHelper.isEmpty(key) || ObjectHelper.isEmpty(value)) {
            return;
        }

        StateRepository<String, String> repository = configuration.getStateRepository();
        if (repository != null) {
            repository.setState(key, value);
        }

        ResumeCache<String> resumeCache = getStringResumeCache();
        if (resumeCache != null) {
            resumeCache.add(key, value);
        }
    }

    @SuppressWarnings("unchecked")
    private ResumeCache<String> getStringResumeCache() {
        return (ResumeCache<String>) configuration.getResumeCache();
    }
}
