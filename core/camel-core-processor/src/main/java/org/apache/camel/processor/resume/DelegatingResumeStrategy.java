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

package org.apache.camel.processor.resume;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import org.apache.camel.ResumeStrategy;
import org.apache.camel.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A delegating strategy that can be used to delegate to and/or abstract resume strategies
 */
public class DelegatingResumeStrategy implements ResumeStrategy {
    private static final Logger LOG = LoggerFactory.getLogger(DelegatingResumeStrategy.class);

    private final List<ResumeStrategy> resumeStrategies;

    public DelegatingResumeStrategy() {
        resumeStrategies = new ArrayList<>();
    }

    protected DelegatingResumeStrategy(List<ResumeStrategy> resumeStrategies) {
        this.resumeStrategies = resumeStrategies;
    }

    public boolean add(ResumeStrategy resumeStrategy) {
        return resumeStrategies.add(resumeStrategy);
    }

    public boolean remove(Object resumeStrategy) {
        return resumeStrategies.remove(resumeStrategy);
    }

    public boolean removeIf(Predicate<? super ResumeStrategy> filter) {
        return resumeStrategies.removeIf(filter);
    }

    @Override
    public void resume() {
        resumeStrategies.forEach(ResumeStrategy::resume);
    }

    @Override
    public void start() {
        resumeStrategies.forEach(Service::start);
    }

    @Override
    public void stop() {
        resumeStrategies.forEach(Service::stop);
    }

    @Override
    public void build() {
        resumeStrategies.forEach(Service::build);
    }

    @Override
    public void init() {
        resumeStrategies.forEach(Service::init);
    }

    private void close(ResumeStrategy resumeStrategy) {
        try {
            resumeStrategy.close();
        } catch (IOException e) {
            LOG.warn("Failed to close resume strategy {}: {}", resumeStrategy.getClass().getSimpleName(), e.getMessage());
        }
    }

    @Override
    public void close() throws IOException {
        resumeStrategies.forEach(r -> close(r));
    }

    @Override
    public String toString() {
        return "DelegatingResumeStrategy{" +
               "resumeStrategies=" + resumeStrategies +
               '}';
    }
}
