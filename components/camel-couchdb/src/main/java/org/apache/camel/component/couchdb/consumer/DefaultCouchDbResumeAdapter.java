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

package org.apache.camel.component.couchdb.consumer;

import java.nio.ByteBuffer;

import org.apache.camel.resume.Cacheable;
import org.apache.camel.resume.Deserializable;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.ResumeAction;
import org.apache.camel.resume.cache.ResumeCache;

public class DefaultCouchDbResumeAdapter implements CouchDbResumeAdapter, Cacheable, Deserializable {
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
        Object key = deserializeObject(keyBuffer);
        Object value = deserializeObject(valueBuffer);

        return add(key, value);
    }
}
