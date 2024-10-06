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
package org.apache.camel.impl.event;

import java.io.Serial;

import org.apache.camel.Route;
import org.apache.camel.spi.CamelEvent;

public class RouteRestartingFailureEvent extends AbstractRouteEvent implements CamelEvent.RouteRestartingFailureEvent {
    private static final @Serial long serialVersionUID = 1330257282431407331L;
    private final long attempt;
    private final Throwable cause;
    private final boolean exhausted;

    public RouteRestartingFailureEvent(Route source, long attempt, Throwable cause, boolean exhausted) {
        super(source);
        this.attempt = attempt;
        this.cause = cause;
        this.exhausted = exhausted;
    }

    public long getAttempt() {
        return attempt;
    }

    @Override
    public Throwable getCause() {
        return cause;
    }

    @Override
    public boolean isExhausted() {
        return exhausted;
    }

    @Override
    public String toString() {
        return "Route " + (attempt == 0 ? "starting " : "restarting ") + (exhausted ? "exhausted: " : "failed: ")
               + getRoute().getId() + " (attempt: " + attempt
               + ") due to " + cause.getMessage();
    }
}
