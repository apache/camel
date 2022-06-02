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

import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.ResumeStrategy;

/**
 * A resume strategy that keeps all the resume strategy information in memory. This is hardly useful for production
 * level implementations, but can be useful for testing the resume strategies
 */
public class TransientResumeStrategy implements ResumeStrategy {
    private final ResumeAdapter resumeAdapter;

    public TransientResumeStrategy(ResumeAdapter resumeAdapter) {
        this.resumeAdapter = resumeAdapter;
    }

    @Override
    public void setAdapter(ResumeAdapter adapter) {

    }

    @Override
    public ResumeAdapter getAdapter() {
        return resumeAdapter;
    }

    @Override
    public <T extends ResumeAdapter> T getAdapter(Class<T> clazz) {
        return ResumeStrategy.super.getAdapter(clazz);
    }

    @Override
    public void start() {
        // this is NO-OP
    }

    @Override
    public void stop() {
        // this is NO-OP
    }
}
