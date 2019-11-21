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

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.component.platform.http.PlatformHttpEndpoint;

/**
 * An abstraction of an HTTP Server engine on which HTTP endpoints can be deployed.
 */
public interface PlatformHttpEngine {

    /**
     * Creates a new {@link Consumer} for the given {@link PlatformHttpEndpoint}.
     *
     * @param platformHttpEndpoint the {@link PlatformHttpEndpoint} to create a consumer for
     * @param processor the Processor to pass to
     * @return a new {@link Consumer}
     */
    Consumer createConsumer(PlatformHttpEndpoint platformHttpEndpoint, Processor processor);

}
