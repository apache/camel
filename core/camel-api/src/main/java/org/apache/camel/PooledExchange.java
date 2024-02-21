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

import org.apache.camel.spi.ExchangeFactory;

/**
 * Pooled {@link Exchange} which contains the methods and APIs that are not intended for Camel end users but used
 * internally by Camel for optimizing memory footprint by reusing exchanges created by {@link Consumer}s via
 * {@link ExchangeFactory}.
 */
public interface PooledExchange extends Exchange {

    /**
     * Task to execute when the exchange is done.
     */
    @FunctionalInterface
    interface OnDoneTask {
        void onDone(Exchange exchange);
    }

    /**
     * Registers a task to run when this exchange is done.
     * <p/>
     * <b>Important:</b> This API is NOT intended for Camel end users, but used internally by Camel itself.
     */
    void onDone(OnDoneTask task);

    /**
     * When the exchange is done being used.
     * <p/>
     * <b>Important:</b> This API is NOT intended for Camel end users, but used internally by Camel itself.
     */
    void done();

    /**
     * Resets the exchange for reuse with the given created timestamp;
     * <p/>
     * <b>Important:</b> This API is NOT intended for Camel end users, but used internally by Camel itself.
     */
    @Deprecated
    void reset(long created);

    /**
     * Whether this exchange was created to auto release when its unit of work is done
     * <p/>
     * <b>Important:</b> This API is NOT intended for Camel end users, but used internally by Camel itself.
     */
    void setAutoRelease(boolean autoRelease);

    /**
     * Whether this exchange was created to auto release when its unit of work is done
     * <p/>
     * <b>Important:</b> This API is NOT intended for Camel end users, but used internally by Camel itself.
     */
    boolean isAutoRelease();

}
