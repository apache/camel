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
package org.apache.camel.cdi;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.impl.DefaultCamelContextNameStrategy;
import org.apache.camel.spi.CamelContextNameStrategy;

/**
 * A {@link CamelContextNameStrategy} for Camel contexts that are managed by Camel CDI.
 *
 * As opposed to {@link org.apache.camel.impl.DefaultCamelContextNameStrategy},
 * this implementation does not increment the suffix for proxies that are created
 * each time a contextual reference to a normal-scoped bean is retrieved.
 *
 * It is used by Camel CDI for custom Camel context beans that do not override
 * the context name nor the naming strategy.
 *
 * @see CamelContextNameStrategy
 */
@Vetoed
final class CdiCamelContextNameStrategy extends DefaultCamelContextNameStrategy implements CamelContextNameStrategy {

    private static final AtomicInteger CONTEXT_COUNTER = new AtomicInteger(0);

    @Override
    public String getNextName() {
        return "camel" + "-" + CONTEXT_COUNTER.incrementAndGet();
    }
}
