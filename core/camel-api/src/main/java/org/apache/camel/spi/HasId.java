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

/**
 * Marker for an object that exposes a unique id, useful for referencing it in REST, JMX, and tooling APIs.
 * <p/>
 * Many Camel runtime objects (routes, processors, services) carry an id so they can be looked up, managed, and
 * correlated back to their model definition. {@link IdAware} extends this to also allow the id to be set.
 *
 * @see IdAware
 * @see HasGroup
 */
public interface HasId {

    /**
     * Returns the id
     *
     * @return the id
     */
    String getId();
}
