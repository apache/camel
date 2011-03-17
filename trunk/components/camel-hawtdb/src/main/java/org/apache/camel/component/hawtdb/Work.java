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
package org.apache.camel.component.hawtdb;

import org.fusesource.hawtdb.api.Transaction;

/**
 * Demarcates the statements that need to be performed as a 
 * HawtDB transactional unit of work.
 */
interface Work<T> {

    /**
     * Executes the work within the bounds of the given transaction
     *
     * @param transaction the transaction
     * @return result of the work, can be <tt>null</tt> if no result to return.
     */
    T execute(Transaction transaction);

}