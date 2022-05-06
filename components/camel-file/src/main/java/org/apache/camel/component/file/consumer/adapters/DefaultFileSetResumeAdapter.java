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

import org.apache.camel.component.file.consumer.FileResumeSet;
import org.apache.camel.component.file.consumer.FileSetResumeAdapter;
import org.apache.camel.resume.cache.MultiEntryCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An implementation of the {@link FileSetResumeAdapter} that can be used for resume operations for multiple files. For
 * instance, this can be used to manage the resume operations for files within a directory.
 */
public class DefaultFileSetResumeAdapter implements FileSetResumeAdapter {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultFileSetResumeAdapter.class);

    private final MultiEntryCache<File, File> cache;

    public DefaultFileSetResumeAdapter(MultiEntryCache<File, File> cache) {
        this.cache = cache;
    }

    private boolean notProcessed(File file) {
        File key = file.getParentFile();

        // if the file is in the cache, then it's already processed
        boolean ret = !cache.contains(key, file);
        return ret;
    }

    @Override
    public void resume(FileResumeSet resumable) {
        if (resumable != null) {
            resumable.resumeEach(this::notProcessed);
            if (resumable.hasResumables()) {
                LOG.debug("There's {} files to still to be processed", resumable.resumed().length);
            }
        } else {
            LOG.trace("Nothing to resume");
        }
    }

    @Override
    public void resume() {
        // NO-OP
    }
}
