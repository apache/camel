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
package org.apache.camel.builder;

import org.apache.camel.ErrorHandlerFactory;

/**
 * A builder of a <a href="http://camel.apache.org/error-handler.html">Error
 * Handler</a>
 */
public interface ErrorHandlerBuilder extends ErrorHandlerFactory {

    /**
     * Whether this error handler supports transacted exchanges.
     */
    boolean supportTransacted();

    /**
     * Clones this builder so each {@link RouteBuilder} has its private builder
     * to use, to avoid changes from one {@link RouteBuilder} to influence the
     * others.
     * <p/>
     * This is needed by the current Camel 2.x architecture.
     *
     * @return a clone of this {@link ErrorHandlerBuilder}
     */
    ErrorHandlerBuilder cloneBuilder();

}
