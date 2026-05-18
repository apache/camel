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
package org.apache.camel.component.vertx.websocket;

import org.apache.camel.support.DefaultHeaderFilterStrategy;

/**
 * Default header filter strategy for Vert.x WebSocket endpoints.
 * <p>
 * Filters out Camel internal headers (starting with "Camel" or "camel") in both directions to prevent external
 * WebSocket clients from injecting internal Camel headers via query or path parameters.
 */
public class VertxWebsocketHeaderFilterStrategy extends DefaultHeaderFilterStrategy {

    public VertxWebsocketHeaderFilterStrategy() {
        setLowerCase(true);
        setOutFilterStartsWith(CAMEL_FILTER_STARTS_WITH);
        setInFilterStartsWith(CAMEL_FILTER_STARTS_WITH);
    }
}
