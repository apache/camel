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

import java.nio.ByteBuffer;

import org.apache.camel.resume.Cacheable;
import org.apache.camel.resume.Deserializable;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.ResumeAction;
import org.apache.camel.resume.ResumeActionAware;
import org.apache.camel.resume.ResumeAdapter;
import org.apache.camel.resume.cache.ResumeCache;
import org.apache.camel.spi.annotations.JdkService;

/**
 * A simple resume adapter that support caching, deserialization and actions. This is usually suitable for supporting
 * resume operations that have simple cache storage requirements, but delegate the resume action to the integrations
 * (i.e.: such as when resuming from database components, where the resume operation can only be determined by the
 * integration itself)
 */
@JdkService(ResumeAdapter.RESUME_ADAPTER_FACTORY)
public class ResumeActionAwareAdapter implements ResumeActionAware, Cacheable, Deserializable {
    private ResumeCache<Object> cache;
    private ResumeAction resumeAction;

    @Override
    public void setResumeAction(ResumeAction resumeAction) {
        this.resumeAction = resumeAction;
    }

    @Override
    public void resume() {
        cache.forEach(resumeAction::evalEntry);
    }

    private boolean add(Object key, Object offset) {
        cache.add(key, offset);

        return true;
    }

    @Override
    public boolean add(OffsetKey<?> key, Offset<?> offset) {
        return add(key.getValue(), offset.getValue());
    }

    @Override
    public void setCache(ResumeCache<?> cache) {
        this.cache = (ResumeCache<Object>) cache;
    }

    @Override
    public ResumeCache<?> getCache() {
        return cache;
    }

    @Override
    public boolean deserialize(ByteBuffer keyBuffer, ByteBuffer valueBuffer) {
        Object key = deserializeKey(keyBuffer);
        Object value = deserializeValue(valueBuffer);

        return add(key, value);
    }
}
