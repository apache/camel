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
package org.apache.camel.spi;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;

/**
 * Used for components that can optimise the usage of {@link org.apache.camel.processor.SendDynamicProcessor} (toD)
 * to reuse a static {@link org.apache.camel.Endpoint} and {@link Producer} that supports
 * using headers to provide the dynamic parts. For example many of the HTTP components supports this.
 */
public interface SendDynamicAware {

    /**
     * Sets the component name.
     *
     * @param scheme  name of the component
     */
    void setScheme(String scheme);

    /**
     * Gets the component name
     */
    String getScheme();

    /**
     * Creates the pre {@link Processor} that will prepare the {@link Exchange}
     * with dynamic details from the given recipient.
     *
     * @param exchange    the exchange
     * @param recipient   the uri of the recipient
     * @return the processor, or <tt>null</tt> to not let toD use this optimisation.
     * @throws Exception is thrown if error creating the pre processor.
     */
    Processor createPreProcessor(Exchange exchange, Object recipient) throws Exception;

    /**
     * Creates an optional post {@link Processor} that will be executed afterwards
     * when the message has been sent dynamic.
     *
     * @param exchange    the exchange
     * @param recipient   the uri of the recipient
     * @return the post processor, or <tt>null</tt> if no post processor is needed.
     * @throws Exception is thrown if error creating the post processor.
     */
    Processor createPostProcessor(Exchange exchange, Object recipient) throws Exception;

    /**
     * Resolves the static part of the uri that are used for creating a single {@link org.apache.camel.Endpoint}
     * and {@link Producer} that will be reused for processing the optimised toD.
     *
     * @param exchange    the exchange
     * @param recipient   the uri of the recipient
     * @return the static uri, or <tt>null</tt> to not let toD use this optimisation.
     * @throws Exception is thrown if error resolving the static uri.
     */
    String resolveStaticUri(Exchange exchange, Object recipient) throws Exception;

}
