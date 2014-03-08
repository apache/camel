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
package org.apache.camel.guice.support;

/**
 * Represents a strategy for closing an object down such as using the @PreDestroy
 * lifecycle from JSR 250, invoking {@link java.io.Closeable#close()} or using
 * the DisposableBean interface from Spring.
 * 
 * @version
 */
public interface Closer {
    /**
     * Closes the given object
     * 
     * @param object
     *            the object to be closed
     * @throws Exception
     *             if the close operation caused some exception to occur
     */
    void close(Object object) throws Throwable;
}
