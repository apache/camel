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

import org.apache.camel.Processor;

/**
 * Implemented by a {@link Processor} to control whether it may be wrapped by an {@link InterceptStrategy}.
 * <p/>
 * Most processors are interceptable, but some EIPs (such as try/catch/finally) cannot be intercepted safely;
 * {@link #canIntercept()} returns {@code false} for those so Camel skips wrapping them.
 *
 * @see   InterceptStrategy
 * @since 4.7
 */
public interface InterceptableProcessor {

    /**
     * Whether the processor can be intercepted or not.
     *
     * @return true to allow intercepting, false to skip.
     */
    boolean canIntercept();

}
