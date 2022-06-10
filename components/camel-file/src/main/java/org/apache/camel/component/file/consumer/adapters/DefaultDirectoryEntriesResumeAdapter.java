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

import org.apache.camel.component.file.consumer.DirectoryEntriesResumeAdapter;
import org.apache.camel.component.file.consumer.FileResumeAdapter;

/**
 * An implementation of the {@link FileResumeAdapter} that can be used for resume operations for the file component.
 * This one can be used to manage the resume operations for files within a directory.
 */
class DefaultDirectoryEntriesResumeAdapter extends AbstractFileResumeAdapter implements DirectoryEntriesResumeAdapter {
    private DirectoryEntries fileSet;

    private boolean notProcessed(File directory, File file) {
        FileSet cached = cache.get(directory, FileSet.class);
        if (cached == null) {
            return true;
        }

        return !cached.contains(file);
    }

    @Override
    public void setResumePayload(DirectoryEntries fileSet) {
        assert fileSet != null;

        this.fileSet = fileSet;
    }

    protected boolean add(Object key, Object offset) {
        if (offset instanceof File) {
            FileSet fileSet = (FileSet) cache.computeIfAbsent((File) key, k -> new FileSet());

            fileSet.update((File) offset);
        } else {
            throw new UnsupportedOperationException("This adapter cannot be used for file offsets");
        }

        // For this one it's safe to always continue processing
        return true;
    }

    private void resumeDirectoryEntries() {
        DirectoryEntries.doResume(fileSet, path -> notProcessed(fileSet.getDirectory(), path.toFile()));
    }

    @Override
    public void resume() {
        resumeDirectoryEntries();
    }

    public void deserializeFileEntry(File keyObj, File valueObj) {
        FileSet fileSet = (FileSet) cache.computeIfAbsent(keyObj, obj -> new FileSet());

        fileSet.update(valueObj);
    }
}
