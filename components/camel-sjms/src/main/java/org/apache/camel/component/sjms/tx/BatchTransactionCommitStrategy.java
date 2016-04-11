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
package org.apache.camel.component.sjms.tx;

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Exchange;
import org.apache.camel.component.sjms.TransactionCommitStrategy;

/**
 * Provides a thread safe counter to track the number of {@link Exchange}
 * objects that have been been processed.
 */
public class BatchTransactionCommitStrategy implements TransactionCommitStrategy {

    private final AtomicInteger current = new AtomicInteger(0);
    private final int count;

    public BatchTransactionCommitStrategy(int count) {
        this.count = count;
    }

    @Override
    public boolean commit(Exchange exchange) throws Exception {
        boolean answer = false;
        int currentVal = current.incrementAndGet();
        if (currentVal >= count) {
            answer = true;
            current.set(0);
        }

        return answer;
    }

    @Override
    public boolean rollback(Exchange exchange) throws Exception {
        current.set(0);
        return true;
    }

    public void reset() {
        current.set(0);
    }

}
