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

import java.util.Map;

import org.apache.camel.Message;

/**
 * SPI factory that creates the {@link java.util.Map} implementation used to store headers on a {@link Message}.
 * <p/>
 * The factory is discovered via the service key {@link #FACTORY}. The default implementation
 * ({@code DefaultHeadersMapFactory} in {@code camel-base-engine}) returns a case-insensitive linked hash map, which is
 * important for HTTP-style protocols where header names such as {@code content-type} and {@code Content-Type} must be
 * treated as equal. An alternative implementation ({@code FastHeadersMapFactory} in {@code camel-headersmap}) provides
 * higher-throughput concurrent access. Custom implementations must correctly implement
 * {@link #isInstanceOf(java.util.Map)} (so Camel can detect whether a given map was created by this factory) and
 * {@link #newMap(java.util.Map)} (defensive copy semantics for {@link Message#setHeaders}).
 * <p/>
 * See <a href="https://camel.apache.org/manual/exchange.html">Exchange</a> in the Camel user manual.
 *
 * @see Message
 */
public interface HeadersMapFactory {

    /**
     * Service factory key.
     */
    String FACTORY = "headers-map-factory";

    /**
     * Creates a new empty {@link Map}
     *
     * @return new empty map
     */
    Map<String, Object> newMap();

    /**
     * Creates a new {@link Map} and copies over all the content from the existing map.
     * <p/>
     * The copy of the content should use defensive copy, so the returned map can add/remove/change the content without
     * affecting the existing map.
     *
     * @param  map existing map to copy over (must use defensive copy)
     * @return     new map with the content from the existing map
     */
    Map<String, Object> newMap(Map<String, Object> map);

    /**
     * Whether the given {@link Map} implementation is created by this factory?
     *
     * @return <tt>true</tt> if created from this factory, <tt>false</tt> if not
     */
    boolean isInstanceOf(Map<String, Object> map);

    /**
     * Whether the created {@link Map} are case insensitive or not.
     * <p/>
     * Important: When using case sensitive (this method return false). Then the map is case sensitive which means
     * headers such as <tt>content-type</tt> and <tt>Content-Type</tt> are two different keys which can be a problem for
     * some protocols such as HTTP based, which rely on case insensitive headers. However case sensitive implementations
     * can yield faster performance. Therefore use case sensitive implementation with care.
     */
    boolean isCaseInsensitive();

}
