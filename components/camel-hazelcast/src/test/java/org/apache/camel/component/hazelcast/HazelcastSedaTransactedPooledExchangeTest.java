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
package org.apache.camel.component.hazelcast;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.engine.PooledExchangeFactory;

/**
 * The same transacted routes with pooled exchanges: a pooled exchange is reset when it is done, so the consumer must
 * read the outcome of the exchange before it is released, or a failed exchange would be committed.
 */
class HazelcastSedaTransactedPooledExchangeTest extends HazelcastSedaTransactedAsyncRouteTest {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getCamelContextExtension().setExchangeFactory(new PooledExchangeFactory());
        return context;
    }
}
