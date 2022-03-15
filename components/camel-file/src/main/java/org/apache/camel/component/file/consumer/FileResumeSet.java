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

package org.apache.camel.component.file.consumer;

import java.io.File;
import java.util.Objects;
import java.util.function.Predicate;

import org.apache.camel.ResumableSet;

/**
 * This contains the input/output file set for resume operations.
 */
public final class FileResumeSet implements ResumableSet<File> {
    private final File[] inputFiles;
    private File[] outputFiles;

    public FileResumeSet(File[] inputFiles) {
        this.inputFiles = Objects.requireNonNull(inputFiles,
                "A list of input files must be provided for the resume info");
    }

    /**
     * Iterates over the set of input files checking if they should be resumed or not
     *
     * @param resumableCheck a checker method that returns true if the file should be resumed or false otherwise
     */
    public void resumeEach(Predicate<File> resumableCheck) {
        this.outputFiles = resumeEach(inputFiles, resumableCheck);
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
}
