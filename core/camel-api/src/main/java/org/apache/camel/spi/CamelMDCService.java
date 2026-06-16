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
package org.apache.camel.spi;

import org.apache.camel.CamelContextAware;
import org.apache.camel.Service;

/**
 * SPI that plugs a Mapped Diagnostic Context (MDC) propagation implementation into Camel's exchange processing
 * pipeline.
 * <p/>
 * SLF4J's MDC is thread-local, which means MDC values set on the originating thread are not automatically visible on
 * continuation threads used by Camel's async routing engine. Implementations of this service are responsible for
 * capturing the current MDC state when an exchange begins processing and restoring it on each continuation thread, so
 * that log entries produced across async boundaries carry the same contextual key-value pairs (e.g.,
 * {@code camel.exchangeId}, {@code camel.routeId}, custom breadcrumb values).
 * <p/>
 * Camel discovers the implementation via the service registry; the default implementation is
 * {@code DefaultCamelMDCService} in {@code camel-core-engine}. The {@link org.apache.camel.CamelContext} starts and
 * stops this service as part of its normal lifecycle.
 * <p/>
 * See <a href="https://camel.apache.org/manual/mdc-logging.html">MDC Logging</a> in the Camel user manual.
 *
 * @since 4.15
 */
public interface CamelMDCService extends Service, CamelContextAware {

}
