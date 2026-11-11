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
package org.apache.camel.component.file;

import java.util.function.Predicate;

/**
 * Operations that list a directory one entry at a time, so that a consumer stops reading the directory once it has
 * gathered {@code maxMessagesPerPoll} files, rather than reading the whole directory on every poll.
 *
 * @param <T> the type of the listed entries
 * @see       GenericFileConsumer#canStopListingAtLimit()
 */
public interface GenericFileListingOperations<T> {

    /**
     * Lists the entries of a directory one at a time, in the order of the listing, and stops reading the directory as
     * soon as the visitor returns {@code false}.
     *
     * @param  path                                the directory, or {@code null} for the current directory
     * @param  visitor                             called with each entry, returns {@code false} to stop the listing
     * @throws GenericFileOperationFailedException if the directory cannot be listed
     */
    void listFiles(String path, Predicate<T> visitor) throws GenericFileOperationFailedException;

    /**
     * Whether the visitor may use these operations while the directory is listed, such as to list a sub directory or to
     * resolve a symbolic link. When not, the visitor must stop the listing before it uses them.
     */
    default boolean isReentrantListing() {
        return true;
    }
}
