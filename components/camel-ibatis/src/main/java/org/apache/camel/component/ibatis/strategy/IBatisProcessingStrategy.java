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
package org.apache.camel.component.ibatis.strategy;

import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.component.ibatis.IBatisEndpoint;
import org.apache.camel.component.ibatis.IBatisPollingConsumer;

/**
 * Processing strategy for dealing with IBatis records
 */
public interface IBatisProcessingStrategy {

    /**
     * Called when record is being queried.
     * 
     * @param consumer The Ibatis Polling Consumer
     * @param endpoint The Ibatis Endpoint
     * @return Results of the query as a java.util.List
     * @throws Exception
     */
    List poll(IBatisPollingConsumer consumer, IBatisEndpoint endpoint) throws Exception;

    /**
     * Called if there is a statement to be run after processing
     * 
     * @param endpoint The Ibatis Enpoint
     * @param exchange The exchange after it has been processed
     * @param data The original data delivered to the route
     * @param consumeStatement The update statement to run
     * @throws Exception
     */
    void commit(IBatisEndpoint endpoint, Exchange exchange, Object data, String consumeStatement)
        throws Exception;
}
