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
package org.apache.camel.component.sjms;

import org.apache.camel.Exchange;

/**
 * Provides a entry point into the transaction
 * {@link org.apache.camel.spi.Synchronization} workflow that will allow a user
 * to control when the {@link javax.jms.Session} commit operation is executed.
 */
public interface TransactionCommitStrategy {

    /**
     * Should returns true to allow the commit to proceed. If false, the commit
     * will be skipped. The default should always be true to avoid messages
     * remaining uncommitted.
     *
     * @param exchange {@link org.apache.camel.Exchange}
     * @return true if the {@link javax.jms.Session} should be committed,
     * otherwise false
     * @throws Exception
     */
    boolean commit(Exchange exchange) throws Exception;

    /**
     * Should returns true to allow the commit to proceed. If false, the commit
     * will be skipped. The default should always be true to avoid messages
     * remaining uncommitted.
     *
     * @param exchange {@link org.apache.camel.Exchange}
     * @return true if the {@link javax.jms.Session} should be committed,
     * otherwise false
     * @throws Exception
     */
    boolean rollback(Exchange exchange) throws Exception;
}
