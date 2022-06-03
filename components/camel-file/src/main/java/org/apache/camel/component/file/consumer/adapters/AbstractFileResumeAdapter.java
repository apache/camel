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

package org.apache.camel.component.file.consumer.adapters;

import java.io.File;

import org.apache.camel.component.file.consumer.FileResumeAdapter;
import org.apache.camel.resume.Cacheable;
import org.apache.camel.resume.Offset;
import org.apache.camel.resume.OffsetKey;
import org.apache.camel.resume.cache.ResumeCache;

/**
 * Base shared class for the file resume adapters
 */
abstract class AbstractFileResumeAdapter implements FileResumeAdapter, Cacheable {
    protected ResumeCache<File> cache;

    protected AbstractFileResumeAdapter() {
    }

    @SuppressWarnings("unchecked")
    @Override
    public final void setCache(ResumeCache<?> cache) {
        this.cache = (ResumeCache<File>) cache;
    }

    @Override
    public final ResumeCache<?> getCache() {
        return cache;
    }

    @Override
    public final boolean add(OffsetKey<?> key, Offset<?> offset) {
        return add(key.getValue(), offset.getValue());
    }

    protected abstract boolean add(Object key, Object offset);
}
