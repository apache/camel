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
package org.apache.camel;

import org.jspecify.annotations.Nullable;

/**
 * Abstraction over a file-like object consumed by a file-oriented {@link Consumer} (such as camel-file, camel-ftp, or
 * camel-sftp), providing uniform access to the underlying file handle, its content, and its length.
 * <p/>
 * Implementations wrap different native file types (for example {@code java.io.File} for the local file system, or a
 * library-specific remote file object for FTP/SFTP). The body returned by {@link #getBody()} may be a stream, a byte
 * array, or a string depending on the component configuration.
 *
 * @param <T> the native file type wrapped by this instance
 * @see       Exchange
 */
public interface WrappedFile<T> {

    /**
     * Gets the file.
     *
     * @return the file.
     */
    @Nullable
    T getFile();

    /**
     * Gets the content of the file.
     *
     * @return the content of the file.
     */
    @Nullable
    Object getBody();

    /**
     * Gets the file length in bytes.
     *
     * @return the length of the file in bytes.
     */
    long getFileLength();
}
