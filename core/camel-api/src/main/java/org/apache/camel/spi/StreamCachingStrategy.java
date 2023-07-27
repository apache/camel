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
package org.apache.camel.spi;

import java.io.File;
import java.util.Collection;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.StaticService;
import org.apache.camel.StreamCache;

/**
 * Strategy for using <a href="http://camel.apache.org/stream-caching.html">stream caching</a>.
 */
public interface StreamCachingStrategy extends StaticService {

    /**
     * Utilization statistics of stream caching.
     */
    interface Statistics {

        /**
         * Gets the counter for number of in-memory {@link StreamCache} created.
         */
        long getCacheMemoryCounter();

        /**
         * Gets the total accumulated number of bytes which has been stream cached for in-memory stream caches.
         */
        long getCacheMemorySize();

        /**
         * Gets the average number of bytes per cached stream for in-memory stream caches.
         */
        long getCacheMemoryAverageSize();

        /**
         * Gets the counter for number of spooled (not in-memory) {@link StreamCache} created.
         */
        long getCacheSpoolCounter();

        /**
         * Gets the total accumulated number of bytes which has been stream cached for spooled stream caches.
         */
        long getCacheSpoolSize();

        /**
         * Gets the average number of bytes per cached stream for spooled (not in-memory) stream caches.
         */
        long getCacheSpoolAverageSize();

        /**
         * Reset the counters
         */
        void reset();

        /**
         * Whether statistics is enabled.
         */
        boolean isStatisticsEnabled();

        /**
         * Sets whether statistics is enabled.
         *
         * @param statisticsEnabled <tt>true</tt> to enable
         */
        void setStatisticsEnabled(boolean statisticsEnabled);
    }

    /**
     * Used for selecting if the memory limit is <tt>committed</tt> or <tt>maximum</tt> heap memory setting.
     */
    enum SpoolUsedHeapMemoryLimit {
        Committed,
        Max
    }

    /**
     * Rule for determine if stream caching should be spooled to disk or kept in-memory.
     */
    interface SpoolRule {

        /**
         * Determines if the stream should be spooled or not. For example if the stream length is over a threshold.
         * <p/>
         * This allows implementations to use custom strategies to determine if spooling is needed or not.
         *
         * @param  length the length of the stream
         * @return        <tt>true</tt> to spool the cache, or <tt>false</tt> to keep the cache in-memory
         */
        boolean shouldSpoolCache(long length);

    }

    /**
     * Sets whether the stream caching is enabled.
     * <p/>
     * <b>Notice:</b> This cannot be changed at runtime.
     */
    void setEnabled(boolean enabled);

    boolean isEnabled();

    /**
     * To filter stream caching of a given set of allowed/denied classes. By default, all classes that are
     * {@link java.io.InputStream} is allowed.
     */
    void setAllowClasses(Class<?>... classes);

    /**
     * To filter stream caching of a given set of allowed/denied classes. By default, all classes that are
     * {@link java.io.InputStream} is allowed. Multiple class names can be separated by comma.
     */
    void setAllowClasses(String names);

    /**
     * To filter stream caching of a given set of allowed/denied classes. By default, all classes that are
     * {@link java.io.InputStream} is allowed.
     */
    Collection<Class<?>> getAllowClasses();

    /**
     * To filter stream caching of a given set of allowed/denied classes. By default, all classes that are
     * {@link java.io.InputStream} is allowed.
     */
    void setDenyClasses(Class<?>... classes);

    /**
     * To filter stream caching of a given set of allowed/denied classes. By default, all classes that are
     * {@link java.io.InputStream} is allowed. Multiple class names can be separated by comma.
     */
    void setDenyClasses(String names);

    /**
     * To filter stream caching of a given set of allowed/denied classes. By default, all classes that are
     * {@link java.io.InputStream} is allowed.
     */
    Collection<Class<?>> getDenyClasses();

    /**
     * Enables spooling to disk.
     * <p/>
     * <b>Notice:</b> This cannot be changed at runtime.
     *
     * Default is disabled.
     */
    void setSpoolEnabled(boolean spoolEnabled);

    /**
     * Is spooling to disk enabled.
     */
    boolean isSpoolEnabled();

    /**
     * Sets the spool (temporary) directory to use for overflow and spooling to disk.
     * <p/>
     * If no spool directory has been explicit configured, then a temporary directory is created in the
     * <tt>java.io.tmpdir</tt> directory.
     */
    void setSpoolDirectory(File path);

    File getSpoolDirectory();

    void setSpoolDirectory(String path);

    /**
     * Threshold in bytes when overflow to disk is activated.
     * <p/>
     * The default threshold is {@link org.apache.camel.StreamCache#DEFAULT_SPOOL_THRESHOLD} bytes (eg 128kb). Use
     * <tt>-1</tt> to disable overflow to disk.
     */
    void setSpoolThreshold(long threshold);

    long getSpoolThreshold();

    /**
     * Sets a percentage (1-99) of used heap memory threshold to activate spooling to disk.
     *
     * @param percentage percentage of used heap memory.
     */
    void setSpoolUsedHeapMemoryThreshold(int percentage);

    int getSpoolUsedHeapMemoryThreshold();

    /**
     * Sets what the upper bounds should be when {@link #setSpoolUsedHeapMemoryThreshold(int)} is in use.
     *
     * @param bounds the bounds
     */
    void setSpoolUsedHeapMemoryLimit(SpoolUsedHeapMemoryLimit bounds);

    SpoolUsedHeapMemoryLimit getSpoolUsedHeapMemoryLimit();

    /**
     * Sets the buffer size to use when allocating in-memory buffers used for in-memory stream caches.
     * <p/>
     * The default size is {@link org.apache.camel.util.IOHelper#DEFAULT_BUFFER_SIZE}
     */
    void setBufferSize(int bufferSize);

    int getBufferSize();

    /**
     * Sets a cipher name to use when spooling to disk to write with encryption.
     * <p/>
     * By default the data is not encrypted.
     */
    void setSpoolCipher(String cipher);

    String getSpoolCipher();

    /**
     * Whether to remove the temporary directory when stopping.
     * <p/>
     * This option is default <tt>true</tt>
     */
    void setRemoveSpoolDirectoryWhenStopping(boolean remove);

    boolean isRemoveSpoolDirectoryWhenStopping();

    /**
     * Sets whether if just any of the {@link org.apache.camel.spi.StreamCachingStrategy.SpoolRule} rules returns
     * <tt>true</tt> then {@link #shouldSpoolCache(long)} returns <tt>true</tt>. If this option is <tt>false</tt>, then
     * <b>all</b> the {@link org.apache.camel.spi.StreamCachingStrategy.SpoolRule} must return <tt>true</tt>.
     * <p/>
     * The default value is <tt>false</tt> which means that all the rules must return <tt>true</tt>.
     */
    void setAnySpoolRules(boolean any);

    boolean isAnySpoolRules();

    /**
     * Gets the utilization statistics.
     */
    Statistics getStatistics();

    /**
     * Adds the {@link org.apache.camel.spi.StreamCachingStrategy.SpoolRule} rule to be used.
     */
    void addSpoolRule(SpoolRule rule);

    /**
     * Determines if the stream should be spooled or not. For example if the stream length is over a threshold.
     * <p/>
     * This allows implementations to use custom strategies to determine if spooling is needed or not.
     *
     * @param  length the length of the stream
     * @return        <tt>true</tt> to spool the cache, or <tt>false</tt> to keep the cache in-memory
     */
    boolean shouldSpoolCache(long length);

    /**
     * Caches the body aas a {@link StreamCache}.
     *
     * @param  exchange the exchange
     * @return          the body cached as a {@link StreamCache}, or <tt>null</tt> if not possible or no need to cache
     *                  the body
     */
    StreamCache cache(Exchange exchange);

    /**
     * Caches the body aas a {@link StreamCache}.
     *
     * @param  message the message
     * @return         the body cached as a {@link StreamCache}, or <tt>null</tt> if not possible or no need to cache
     *                 the body
     */
    StreamCache cache(Message message);

}
