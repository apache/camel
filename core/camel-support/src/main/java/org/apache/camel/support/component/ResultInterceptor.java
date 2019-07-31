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
package org.apache.camel.support.component;

import org.apache.camel.Exchange;

/**
 * Intercepts API method invocation result Exchange.
 */
public interface ResultInterceptor {

    /**
     * Split a complex result into result elements.
     * @param result API method invocation result
     * @return either the same result if it cannot be split, an array or collection object with split results
     */
    Object splitResult(Object result);

    /**
     * Do additional result exchange processing, for example, adding custom headers.
     * @param result result of API method invocation.
     * @param resultExchange result as a Camel exchange, may be a split result from Arrays or Collections.
     */
    void interceptResult(Object result, Exchange resultExchange);
}
