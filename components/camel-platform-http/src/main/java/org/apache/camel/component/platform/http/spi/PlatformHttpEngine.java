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

package org.apache.camel.component.platform.http.spi;

import org.apache.camel.Processor;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;
import org.apache.camel.spi.EmbeddedHttpService;

/**
 * An abstraction of an HTTP Server engine on which HTTP endpoints can be deployed.
 */
public interface PlatformHttpEngine extends EmbeddedHttpService {

    /**
     * Creates a new {@link PlatformHttpConsumer} for the given {@link PlatformHttpEndpoint}.
     *
     * @param  platformHttpEndpoint the {@link PlatformHttpEndpoint} to create a consumer for
     * @param  processor            the Processor to pass to
     * @return                      a new {@link PlatformHttpConsumer}
     */
    PlatformHttpConsumer createConsumer(PlatformHttpEndpoint platformHttpEndpoint, Processor processor);

    @Override
    default int getServerPort() {
        return 0;
    }

    @Override
    default String getScheme() {
        String scheme = "http";
        int port = getServerPort();
        if (port == 443 || port == 8443) {
            // it's common to use 8443 for SSL on spring-boot
            scheme = "https";
        }
        return scheme;
    }
}
