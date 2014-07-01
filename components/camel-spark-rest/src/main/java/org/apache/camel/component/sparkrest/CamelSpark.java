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
