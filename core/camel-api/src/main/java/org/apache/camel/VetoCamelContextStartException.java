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

import java.util.Objects;

/**
 * Thrown by a {@link org.apache.camel.spi.LifecycleStrategy} to abort the startup of a {@link CamelContext}.
 * <p/>
 * Any {@link org.apache.camel.spi.LifecycleStrategy} registered with the context can veto startup by throwing this
 * exception from its {@code onContextStarting} callback. Whether the exception propagates out of
 * {@link CamelContext#start()} is controlled by {@link #isRethrowException()}: when {@code true} (the default) the
 * exception is re-thrown and the application sees a startup failure; when {@code false} the veto is silent and the
 * context is simply not started.
 *
 * @see CamelContext
 * @see org.apache.camel.spi.LifecycleStrategy
 */
public class VetoCamelContextStartException extends Exception {

    private final CamelContext context;
    private final boolean rethrowException;

    /**
     * @param message the detail message
     * @param context the CamelContext whose start is being vetoed
     */
    public VetoCamelContextStartException(String message, CamelContext context) {
        this(message, context, true);
    }

    /**
     * @param message          the detail message
     * @param context          the CamelContext whose start is being vetoed
     * @param rethrowException whether to rethrow this exception when starting CamelContext
     */
    public VetoCamelContextStartException(String message, CamelContext context, boolean rethrowException) {
        super(Objects.requireNonNull(message, "message"));
        this.context = Objects.requireNonNull(context, "context");
        this.rethrowException = rethrowException;
    }

    /**
     * @param message the detail message
     * @param cause   the cause of the veto
     * @param context the CamelContext whose start is being vetoed
     */
    public VetoCamelContextStartException(String message, Throwable cause, CamelContext context) {
        this(message, cause, context, true);
    }

    /**
     * @param message          the detail message
     * @param cause            the cause of the veto
     * @param context          the CamelContext whose start is being vetoed
     * @param rethrowException whether to rethrow this exception when starting CamelContext
     */
    public VetoCamelContextStartException(String message, Throwable cause, CamelContext context, boolean rethrowException) {
        super(Objects.requireNonNull(message, "message"), Objects.requireNonNull(cause, "cause"));
        this.context = Objects.requireNonNull(context, "context");
        this.rethrowException = rethrowException;
    }

    public CamelContext getContext() {
        return context;
    }

    /**
     * Whether to rethrow this exception when starting CamelContext, to cause an exception to be thrown from the start
     * method.
     * <p/>
     * This option is default <tt>true</tt>.
     */
    public boolean isRethrowException() {
        return rethrowException;
    }

}
