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
import java.util.Optional;

import org.apache.camel.component.file.consumer.GenericFileResumable;
import org.apache.camel.component.file.consumer.GenericFileResumeAdapter;
import org.apache.camel.resume.cache.SingleEntryCache;

/**
 * An implementation of the {@link GenericFileResumeAdapter} that can be used to handle resume operations for file
 * offsets (where the offsets are of Long format).
 */
public class DefaultGenericFileResumeAdapter implements GenericFileResumeAdapter {
    private final SingleEntryCache<File, Long> cache;

    public DefaultGenericFileResumeAdapter(SingleEntryCache<File, Long> cache) {
        this.cache = cache;
    }

    private Optional<Long> getLastOffset(GenericFileResumable<File> resumable) {
        final File addressable = resumable.getAddressable();
        return cache.get(addressable);
    }

    @Override
    public Optional<Long> getLastOffset(File addressable) {
        return cache.get(addressable);
    }

    @Override
    public void resume(GenericFileResumable<File> resumable) {
        final Optional<Long> lastOffsetOpt = getLastOffset(resumable);

        if (!lastOffsetOpt.isPresent()) {
            return;
        }

        final long lastOffset = lastOffsetOpt.get();
        resumable.updateLastOffset(lastOffset);
    }

    @Override
    public void resume() {

    }
}
