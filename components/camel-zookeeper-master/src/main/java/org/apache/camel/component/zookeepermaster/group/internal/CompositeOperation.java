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
package org.apache.camel.component.zookeepermaster.group.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Operation that aggregates several {@link Operation operations} to be performed inside single task passed to thread
 * executor
 */
public class CompositeOperation implements Operation {

    public static final Logger LOG = LoggerFactory.getLogger(CompositeOperation.class);

    private final Operation[] operations;

    public CompositeOperation(Operation... operations) {
        this.operations = operations;
    }

    @Override
    public void invoke() throws Exception {
        for (Operation op : operations) {
            try {
                op.invoke();
                if (Thread.currentThread().isInterrupted()) {
                    LOG.debug("Interrupting composite operation");
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (InterruptedException e) {
                LOG.debug("Interrupting composite operation");
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.error(e.getMessage(), e);
            }
        }
    }

}
