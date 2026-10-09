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

import java.util.concurrent.RejectedExecutionException;

/**
 * An exchange cut off because its route, or the {@link CamelContext}, is being stopped while the exchange is in flight:
 * a route stop, a route reload in dev mode, or the graceful shutdown timing out. Nothing in the route failed; a
 * consumer that rolls back, such as file, delivers the message again.
 * <p/>
 * It extends {@link RejectedExecutionException}, which Camel used for this before, so an
 * {@code onException(RejectedExecutionException.class)} or a catch of it still matches. A real rejection (a thread pool
 * or a queue that is full) stays a plain {@link RejectedExecutionException}.
 */
public class RouteStoppingException extends RejectedExecutionException {

    public RouteStoppingException(String message) {
        super(message);
    }

    public RouteStoppingException(String message, Throwable cause) {
        super(message, cause);
    }
}
