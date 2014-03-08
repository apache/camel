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
package org.apache.camel.component.disruptor;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.spi.Synchronization;
import org.apache.camel.util.UnitOfWorkHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractSynchronizedExchange implements SynchronizedExchange {
    private static final Logger LOG = LoggerFactory.getLogger(SynchronizedExchange.class);
    protected final List<Synchronization> synchronizations;
    private final Exchange exchange;

    public AbstractSynchronizedExchange(Exchange exchange) {
        this.exchange = exchange;
        synchronizations = exchange.handoverCompletions();
    }

    @Override
    public Exchange getExchange() {
        return exchange;
    }

    @Override
    public Exchange cancelAndGetOriginalExchange() {
        if (synchronizations != null) {
            for (Synchronization synchronization : synchronizations) {
                exchange.addOnCompletion(synchronization);
            }
        }

        return exchange;
    }

    protected void performSynchronization() {
        //call synchronizations with the result
        UnitOfWorkHelper.doneSynchronizations(getExchange(),
                synchronizations, AbstractSynchronizedExchange.LOG);
    }
}