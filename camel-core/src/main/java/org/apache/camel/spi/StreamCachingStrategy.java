/**
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
package org.apache.camel.spi;

import java.io.File;

import org.apache.camel.Exchange;
import org.apache.camel.Service;
import org.apache.camel.StreamCache;

/**
 * Strategy for using <a href="http://camel.apache.org/stream-caching.html">stream caching</a>.
 */
public interface StreamCachingStrategy extends Service {

    /**
     * Sets whether the stream caching is enabled.
     * <p/>
     * <b>Notice:</b> This cannot be changed at runtime.
     */
    void setEnabled(boolean enabled);

    boolean isEnabled();

    /**
     * Sets the spool (temporary) directory to use for overflow and spooling to disk.
     * <p/>
     * If no spool directory has been explicit configured, then a temporary directory
     * is created in the <tt>java.io.tmpdir</tt> directory.
     */
    void setSpoolDirectory(File path);

    File getSpoolDirectory();

    void setSpoolDirectory(String path);

    /**
     * Threshold in bytes when overflow to disk is activated.
     * <p/>
     * The default threshold is {@link org.apache.camel.StreamCache#DEFAULT_SPOOL_THRESHOLD} bytes (eg 128kb).
     * Use <tt>-1</tt> to disable overflow to disk.
     */
    void setSpoolThreshold(long threshold);

    long getSpoolThreshold();

    /**
     * Sets the buffer size to use when copying between buffers.
     * <p/>
     * The default size is {@link org.apache.camel.util.IOHelper#DEFAULT_BUFFER_SIZE}
     */
    void setBufferSize(int bufferSize);

    int getBufferSize();

    /**
     * Sets a chiper name to use when spooling to disk to write with encryption.
     * <p/>
     * By default the data is not encrypted.
     */
    void setSpoolChiper(String chiper);

    String getSpoolChiper();

    /**
     * Whether to remove the temporary directory when stopping.
     * <p/>
     * This option is default <tt>true</tt>
     */
    void setRemoveSpoolDirectoryWhenStopping(boolean remove);

    boolean isRemoveSpoolDirectoryWhenStopping();

    /**
     * Caches the body aas a {@link StreamCache}.
     *
     * @param exchange the exchange
     * @return the body cached as a {@link StreamCache}, or <tt>null</tt> if not possible or no need to cache the body
     */
    StreamCache cache(Exchange exchange);

}
