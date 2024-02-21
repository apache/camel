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
package org.apache.camel;

import org.apache.camel.spi.UnitOfWork;

/**
 * A consumer of message exchanges from an {@link Endpoint}.
 * <p/>
 * Important: Do not do any initialization in the constructor. Instead use
 * {@link org.apache.camel.support.service.ServiceSupport#doInit()} or
 * {@link org.apache.camel.support.service.ServiceSupport#doStart()}.
 */
public interface Consumer extends Service, EndpointAware {

    /**
     * The processor that will process the {@link Exchange} that was consumed.
     */
    Processor getProcessor();

    /**
     * Creates an {@link Exchange} that was consumed.
     * <p/>
     * <b>Important:</b> If the auto release parameter is set to <tt>false</tt> then the consumer is responsible for
     * calling the {@link #releaseExchange(Exchange, boolean)} when the {@link Exchange} is done being routed. This is
     * for advanced consumers that need to have this control in their own hands. For normal use-cases then a consumer
     * can use autoRelease <tt>true</tt> and then Camel will automatic release the exchange after routing.
     *
     * @param autoRelease whether to auto release the exchange when routing is complete via {@link UnitOfWork}
     */
    Exchange createExchange(boolean autoRelease);

    /**
     * Releases the {@link Exchange} when its completed processing and no longer needed.
     *
     * @param exchange    the exchange
     * @param autoRelease whether the exchange was created with auto release
     */
    void releaseExchange(Exchange exchange, boolean autoRelease);

    /**
     * The default callback to use with the consumer when calling the processor using asynchronous routing.
     *
     * This implementation will use {@link org.apache.camel.spi.ExceptionHandler} to handle any exception on the
     * exchange and afterwards release the exchange.
     *
     * @param  exchange    the exchange
     * @param  autoRelease whether the exchange was created with auto release
     * @return             the default callback
     */
    default AsyncCallback defaultConsumerCallback(Exchange exchange, boolean autoRelease) {
        return null;
    }

}
