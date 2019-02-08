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
package org.apache.camel;

/**
 * An exception to veto starting {@link CamelContext}.
 * <p/>
 * The option rethrowException can be used to control whether to rethrow this exception
 * when starting CamelContext or not.
 *
 * @see org.apache.camel.spi.LifecycleStrategy
 */
public class VetoCamelContextStartException extends Exception {
    private static final long serialVersionUID = 8046489554418284257L;
    private final CamelContext context;
    private final boolean rethrowException;

    public VetoCamelContextStartException(String message, CamelContext context) {
        this(message, context, true);
    }

    public VetoCamelContextStartException(String message, CamelContext context, boolean rethrowException) {
        super(message);
        this.context = context;
        this.rethrowException = rethrowException;
    }

    public VetoCamelContextStartException(String message, Throwable cause, CamelContext context) {
        this(message, cause, context, true);
    }

    public VetoCamelContextStartException(String message, Throwable cause, CamelContext context, boolean rethrowException) {
        super(message, cause);
        this.context = context;
        this.rethrowException = rethrowException;
    }

    public CamelContext getContext() {
        return context;
    }

    /**
     * Whether to rethrow this exception when starting CamelContext, to cause an exception
     * to be thrown from the start method.
     * <p/>
     * This option is default <tt>true</tt>.
     */
    public boolean isRethrowException() {
        return rethrowException;
    }

}
