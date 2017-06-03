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
package org.apache.camel.component.sparkrest;

import spark.Route;
import spark.Spark;

public final class CamelSpark {

    private CamelSpark() {
    }

    /**
     * Stops the Spark Server
     */
    public static void stop() {
        Spark.stop();
    }

    /**
     * Configures the port number to use
     */
    public static void port(int port) {
        Spark.port(port);
    }

    /**
     * Configures the IP address to use
     */
    public static void ipAddress(String ip) {
        Spark.ipAddress(ip);
    }

    /**
     * Configures the thread pool
     */
    public static void threadPool(int minThreads, int maxThreads, int timeOutMillis) {
        int min = minThreads > 0 ? minThreads : -1;
        int max = maxThreads > 0 ? maxThreads : -1;
        int idle = timeOutMillis > 0 ? timeOutMillis : -1;

        Spark.threadPool(max, min, idle);
    }

    /**
     * Configures connection to be secure
     */
    public static void security(String keystoreFile, String keystorePassword, String truststoreFile, String truststorePassword) {
        Spark.secure(keystoreFile, keystorePassword, truststoreFile, truststorePassword);
    }

    /**
     * Adds a Spark REST verb that routes to the given spark route
     *
     * @param verb   the HTTP verb
     * @param path   the context path
     * @param accept the accept header
     * @param route  the spark route (we call a Camel route from here)
     */
    public static void spark(String verb, String path, String accept, Route route) {
        if ("get".equals(verb)) {
            if (accept != null) {
                Spark.get(path, accept, route);
            } else {
                Spark.get(path, route);
            }
        } else if ("post".equals(verb)) {
            if (accept != null) {
                Spark.post(path, accept, route);
            } else {
                Spark.post(path, route);
            }
        } else if ("put".equals(verb)) {
            if (accept != null) {
                Spark.put(path, accept, route);
            } else {
                Spark.put(path, route);
            }
        } else if ("patch".equals(verb)) {
            if (accept != null) {
                Spark.patch(path, accept, route);
            } else {
                Spark.patch(path, route);
            }
        } else if ("delete".equals(verb)) {
            if (accept != null) {
                Spark.delete(path, accept, route);
            } else {
                Spark.delete(path, route);
            }
        } else if ("head".equals(verb)) {
            if (accept != null) {
                Spark.head(path, accept, route);
            } else {
                Spark.head(path, route);
            }
        } else if ("trace".equals(verb)) {
            if (accept != null) {
                Spark.trace(path, accept, route);
            } else {
                Spark.trace(path, route);
            }
        } else if ("connect".equals(verb)) {
            if (accept != null) {
                Spark.connect(path, accept, route);
            } else {
                Spark.connect(path, route);
            }
        } else if ("options".equals(verb)) {
            if (accept != null) {
                Spark.options(path, accept, route);
            } else {
                Spark.options(path, route);
            }
        }
    }
}
