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
package org.apache.camel.component.sparkrest;

import spark.Route;
import spark.Service;

public final class CamelSpark {

    private CamelSpark() {
    }

    /**
     * Configures the thread pool
     */
    public static void threadPool(Service sparkInstance, int minThreads, int maxThreads, int timeOutMillis) {
        int min = minThreads > 0 ? minThreads : -1;
        int max = maxThreads > 0 ? maxThreads : -1;
        int idle = timeOutMillis > 0 ? timeOutMillis : -1;
        sparkInstance.threadPool(max, min, idle);
    }

    /**
     * Adds a Spark REST verb that routes to the given spark route
     *
     * @param sparkInstance the SPARK instance
     * @param verb   the HTTP verb
     * @param path   the context path
     * @param accept the accept header
     * @param route  the spark route (we call a Camel route from here)
     */
    public static void spark(Service sparkInstance, String verb, String path, String accept, Route route) {
        if ("get".equals(verb)) {
            if (accept != null) {
                sparkInstance.get(path, accept, route);
            } else {
                sparkInstance.get(path, route);
            }
        } else if ("post".equals(verb)) {
            if (accept != null) {
                sparkInstance.post(path, accept, route);
            } else {
                sparkInstance.post(path, route);
            }
        } else if ("put".equals(verb)) {
            if (accept != null) {
                sparkInstance.put(path, accept, route);
            } else {
                sparkInstance.put(path, route);
            }
        } else if ("patch".equals(verb)) {
            if (accept != null) {
                sparkInstance.patch(path, accept, route);
            } else {
                sparkInstance.patch(path, route);
            }
        } else if ("delete".equals(verb)) {
            if (accept != null) {
                sparkInstance.delete(path, accept, route);
            } else {
                sparkInstance.delete(path, route);
            }
        } else if ("head".equals(verb)) {
            if (accept != null) {
                sparkInstance.head(path, accept, route);
            } else {
                sparkInstance.head(path, route);
            }
        } else if ("trace".equals(verb)) {
            if (accept != null) {
                sparkInstance.trace(path, accept, route);
            } else {
                sparkInstance.trace(path, route);
            }
        } else if ("connect".equals(verb)) {
            if (accept != null) {
                sparkInstance.connect(path, accept, route);
            } else {
                sparkInstance.connect(path, route);
            }
        } else if ("options".equals(verb)) {
            if (accept != null) {
                sparkInstance.options(path, accept, route);
            } else {
                sparkInstance.options(path, route);
            }
        }
    }
}
