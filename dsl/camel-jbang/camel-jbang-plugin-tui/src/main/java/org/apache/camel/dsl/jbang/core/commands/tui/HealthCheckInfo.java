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
package org.apache.camel.dsl.jbang.core.commands.tui;

class HealthCheckInfo {
    String group;
    String name;
    String state;
    boolean readiness;
    boolean liveness;
    String message;
    /** When the check went to its state (epoch millis): UP since, DOWN since; 0 when not known. */
    long since;
    /** When the check was last UP (epoch millis), 0 when not known. */
    long lastUp;
    /** How often the check ran, and how often it failed. */
    long invocations;
    long failures;
    /** When it last failed (epoch millis), 0 when it did not. */
    long lastFailure;
}
