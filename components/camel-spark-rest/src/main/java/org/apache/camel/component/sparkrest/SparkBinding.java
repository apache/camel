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
package org.apache.camel.component.sparkrest;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import spark.Request;
import spark.Response;

public interface SparkBinding {

    /**
     * Binds from Spark {@link Request} to Camel {@link org.apache.camel.Message}.
     *
     * @param request       the Spark request
     * @param exchange      the exchange that should contain the returned message.
     * @param configuration configuration
     * @return the message to store on the given exchange
     * @throws Exception is thrown if error during binding
     */
    Message toCamelMessage(Request request, Exchange exchange, SparkConfiguration configuration) throws Exception;

    /**
     * Binds from Spark {@link Request} to Camel headers as a {@link Map}.
     *
     * @param request       the Spark request
     * @param headers       the Camel headers that should be populated
     * @param exchange      the exchange that should contain the returned message.
     * @param configuration the endpoint configuration
     * @throws Exception is thrown if error during binding
     */
    void populateCamelHeaders(Request request, Map<String, Object> headers, Exchange exchange, SparkConfiguration configuration) throws Exception;

    /**
     * Binds from Camel {@link Message} to Spark {@link Response}.
     *
     * @param message       the Camel message
     * @param response      the Spark response to bind to
     * @param configuration the endpoint configuration
     * @throws Exception is thrown if error during binding
     */
    void toSparkResponse(Message message, Response response, SparkConfiguration configuration) throws Exception;

}
