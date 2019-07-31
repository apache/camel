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
package org.apache.camel.component.mybatis;

import java.util.List;

import org.apache.camel.Exchange;

/**
 * Processing strategy for dealing with MyBatis when consuming.
 */
public interface MyBatisProcessingStrategy {

    /**
     * Called when record is being queried.
     *
     * @param consumer the consumer
     * @param endpoint the endpoint
     * @return Results of the query as a {@link List}
     * @throws Exception can be thrown in case of error
     */
    List<?> poll(MyBatisConsumer consumer, MyBatisEndpoint endpoint) throws Exception;

    /**
     * Commit callback if there are a statements to be run after processing.
     *
     * @param endpoint          the endpoint
     * @param exchange          The exchange after it has been processed
     * @param data              The original data delivered to the route
     * @param consumeStatements Name of the statement(s) to run, will use SQL update. Use comma to provide multiple statements to run.
     * @throws Exception can be thrown in case of error
     */
    void commit(MyBatisEndpoint endpoint, Exchange exchange, Object data, String consumeStatements) throws Exception;

}
