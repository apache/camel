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

/**
 * A store of state, such as an {@link IdempotentRepository} or an {@link AggregationRepository}, that tells whether it
 * keeps its data only in the memory of this JVM. Such data is lost when the application stops: after a restart an
 * idempotent consumer lets the same messages through again, a file consumer with noop=true reads the same files again,
 * and an aggregator has lost the groups it was building.
 * <p/>
 * Only true counts: false means that the data is kept elsewhere, or that the store cannot tell, such as a cache whose
 * provider does not say where it keeps its entries. A store that can be configured either way, such as a cache with or
 * without a disk tier, or a store that delegates to another store, answers for the configuration in use.
 *
 * @since 4.24
 */
public interface InMemoryStrategy {

    /**
     * Whether the data is kept only in the memory of this JVM, so it is lost when the application stops; false when the
     * data is kept elsewhere, or when the store cannot tell.
     */
    boolean isInMemory();
}
