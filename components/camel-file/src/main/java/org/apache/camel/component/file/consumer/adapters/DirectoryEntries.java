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
import java.util.Objects;
import java.util.function.Predicate;

import org.apache.camel.support.resume.Resumables;

/**
 * This contains the input/output file set for resume operations.
 */
public final class DirectoryEntries {
    private final File directory;
    private final File[] inputFiles;
    private File[] outputFiles;

    public DirectoryEntries(File directory, File[] inputFiles) {
        this.directory = directory;
        this.inputFiles = Objects.requireNonNull(inputFiles,
                "A list of input files must be provided for the resume info");
    }

    /**
     * Gets the files that should be resumed
     *
     * @return an array with the files that should be resumed
     */
    public File[] resumed() {
        return outputFiles;
    }

    /**
     * Whether there are resumable files to process
     *
     * @return true if there are resumable files or false otherwise
     */
    public boolean hasResumables() {
        if (outputFiles != inputFiles) {
            return true;
        }

        return false;
    }

    public File getDirectory() {
        return directory;
    }

    public void setOutputFiles(File[] resumed) {
        this.outputFiles = resumed;
    }

    public static void doResume(DirectoryEntries directoryEntries, Predicate<File> resumableCheck) {
        File[] processed = Resumables.resumeEach(directoryEntries.inputFiles,
                resumableCheck);
        directoryEntries.setOutputFiles(processed);
    }
}
