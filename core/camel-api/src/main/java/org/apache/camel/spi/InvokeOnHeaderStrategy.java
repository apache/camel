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

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;

/**
 * Pluggable strategy for invoking {@link InvokeOnHeader}.
 * <p>
 * Camel provides source code generated strategies via the camel maven tooling.
 *
 * @see InvokeOnHeader
 */
public interface InvokeOnHeaderStrategy {

    /**
     * Invoke the method based on the header key
     *
     * @param  target    the target such as a producer extending HeaderSelectorProducer
     * @param  key       the header key
     * @param  exchange  the exchange
     * @param  callback  the async callback
     * @return           option response from invoking the method, or <tt>null</tt> if the method is void if a value is
     *                   returned, then this value is stored as result on the message body.
     * @throws Exception is thrown if error invoking the method.
     */
    Object invoke(Object target, String key, Exchange exchange, AsyncCallback callback) throws Exception;
}
