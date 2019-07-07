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
package org.apache.camel.component.file.watch.utils;

import java.nio.file.WatchService;

public class WatchServiceUtils {

    private WatchServiceUtils() {
    }

    /**
     * Check if @param watchService is underlying sun.nio.fs.PollingWatchService
     * This can happen on OS X, AIX and Solaris prior to version 11
     */
    public static boolean isPollingWatchService(WatchService watchService) {
        try {
            // If the WatchService is a PollingWatchService, which it is on OS X, AIX and Solaris prior to version 11
            Class<?> pollingWatchService = Class.forName("sun.nio.fs.PollingWatchService");
            return pollingWatchService.isInstance(watchService);
        } catch (ClassNotFoundException ignored) {
            // This is expected on JVMs where PollingWatchService is not available
            return false;
        }
    }
}
