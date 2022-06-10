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
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * This contains the input/output file set for resume operations.
 */
public final class DirectoryEntries {
    private final Path directory;
    private Stream<Path> inputFiles;

    public DirectoryEntries(Path directory, Stream<Path> inputFiles) {
        this.directory = directory;
        this.inputFiles = Objects.requireNonNull(inputFiles,
                "A list of input files must be provided for the resume info");
    }

    /**
     * Gets the files that should be resumed
     *
     * @return an stream with the files that should be resumed
     */
    public Stream<Path> resumed() {
        return inputFiles;
    }

    public File getDirectory() {
        return directory.toFile();
    }

    public static void doResume(DirectoryEntries directoryEntries, Predicate<Path> resumableCheck) {
        directoryEntries.inputFiles = directoryEntries.inputFiles.filter(resumableCheck);
    }
}
