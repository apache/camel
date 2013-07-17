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
package org.apache.camel.api.management.mbean;

import org.apache.camel.api.management.ManagedAttribute;

public interface ManagedStreamCachingStrategyMBean {

    @ManagedAttribute(description = "Whether stream caching is enabled")
    boolean isEnabled();

    @ManagedAttribute(description = "Directory used when overflow and spooling to disk")
    String getSpoolDirectory();

    @ManagedAttribute(description = "Chiper used if writing with encryption")
    String getSpoolChiper();

    @ManagedAttribute(description = "Threshold in bytes when overflow and spooling to disk instead of keeping in memory")
    void setSpoolThreshold(long threshold);

    @ManagedAttribute(description = "Threshold in bytes when overflow and spooling to disk instead of keeping in memory")
    long getSpoolThreshold();

    @ManagedAttribute(description = "Buffer size in bytes to use when coping between buffers")
    void setBufferSize(int bufferSize);

    @ManagedAttribute(description = "Buffer size in bytes to use when coping between buffers")
    int getBufferSize();

    @ManagedAttribute(description = "Whether to remove spool directory when stopping")
    void setRemoveSpoolDirectoryWhenStopping(boolean remove);

    @ManagedAttribute(description = "Whether to remove spool directory when stopping")
    boolean isRemoveSpoolDirectoryWhenStopping();

}
