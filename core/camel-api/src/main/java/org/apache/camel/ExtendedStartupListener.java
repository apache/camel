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

/**
 * A {@link StartupListener} variant whose callback is guaranteed to fire exactly once, after all route consumers are
 * fully started and the {@link CamelContext} is considered completely running.
 * <p/>
 * Unlike the base {@link StartupListener} (which fires twice during startup: once before consumers start and once
 * after), {@code ExtendedStartupListener} provides a single, unambiguous signal that the context is fully operational.
 * This makes it the correct hook for code that must run after every route is reachable, such as adding and starting
 * additional routes programmatically.
 *
 * @see StartupListener
 */
public interface ExtendedStartupListener extends StartupListener {

}
