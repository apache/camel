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
package org.apache.camel.dsl.yaml.validator;

import java.util.List;

/**
 * Where the message body of one route of a YAML file comes from, read from the source: the analysis the body check of
 * {@code camel validate} makes, which {@code camel overview} shows (CAMEL-25200). The routes of one file only: a caller
 * in another file is not followed, and the origin is then {@link #UNKNOWN}.
 *
 * @param index   the position of the route among the routes of the file, from 0
 * @param route   the id of the route, or null
 * @param from    the endpoint the route starts from
 * @param origin  how a message reaches the route: {@link #CONSUMER}, {@link #REST}, {@link #CALLER},
 *                {@link #ERROR_HANDLER}, {@link #ON_COMPLETION}, {@link #SAGA} or {@link #UNKNOWN}
 * @param via     what the origin goes through: the endpoint the callers send to, the REST operation ({@code GET
 *                /stock/{id}}), or the error path ({@code deadLetterChannel}, {@code onException},
 *                {@code onCompletion}, {@code compensation}, {@code completion}); null for a consumer
 * @param callers the routes that send to this one (their id, or the endpoint they start from without its options), or
 *                the route whose error handler or saga reaches it; empty when they are not known
 * @param noBody  whether it is certain that no message reaching the route has a body
 * @param setBy   when there is no body: the first step of the route that may set one, or null
 * @param setAt   the position of that step in the route, from 1; 0 when there is none
 * @param problem the message of the body check, when a step reads the body that is never set; null otherwise
 * @since         4.24
 */
public record BodyOrigin(int index, String route, String from, String origin, String via, List<String> callers,
        boolean noBody, String setBy, int setAt, String problem) {

    /** The route consumes from a component that brings its own message: a timer, an HTTP server, a queue. */
    public static final String CONSUMER = "consumer";
    /** A REST operation of the file, or of an OpenAPI contract the file binds to, calls the route. */
    public static final String REST = "rest";
    /** Other routes of the file call the route, with their message. */
    public static final String CALLER = "caller";
    /** An error handler or an onException sends the failed message to the route. */
    public static final String ERROR_HANDLER = "errorHandler";
    /** An onCompletion sends the completed message to the route. */
    public static final String ON_COMPLETION = "onCompletion";
    /** A saga calls the route to compensate or complete. */
    public static final String SAGA = "saga";
    /** Nothing in the file calls the route: a route of another file may, or code. */
    public static final String UNKNOWN = "unknown";
}
