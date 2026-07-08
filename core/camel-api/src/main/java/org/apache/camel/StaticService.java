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
 * Marker interface indicating that a {@link Service} is a "static" service: a singleton within a {@link CamelContext}
 * that is created once at context startup and shared for the context lifetime.
 * <p/>
 * Static services are managed differently from ordinary services: the context starts and stops them as part of its own
 * lifecycle rather than per-route or per-endpoint lifecycle. Examples include
 * {@link org.apache.camel.spi.PropertiesComponent}, {@link org.apache.camel.spi.RestRegistry},
 * {@link org.apache.camel.spi.InflightRepository}, and other context-wide infrastructure services. A service that
 * implements this marker will not be started or stopped multiple times even if multiple routes reference it — the
 * context guarantees exactly one start and one stop across its full lifecycle.
 * <p/>
 * See <a href="https://camel.apache.org/manual/lifecycle.html">Lifecycle</a> in the Camel user manual.
 *
 * @see Service
 */
public interface StaticService extends Service {
}
