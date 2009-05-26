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
 * A service pool is like a connection pool.
 *
 * @version $Revision$
 */
public interface ServicePool<Key, Service> extends org.apache.camel.Service {

    /**
     * Acquires the given service. If absent in pool the service
     * is added to the pool.
     *
     * @param key the key
     * @param service the service
     * @return the acquired service, is newer <tt>null</tt>
     */
    Service acquireIfAbsent(Key key, Service service);

    /**
     * Tries to acquire the servie with the given key
     * @param key the key
     * @return the acquired service, or <tt>null</tt> if no free in pool
     */
    Service acquire(Key key);

    /**
     * Releases the service back to the pool
     *
     * @param key  the key
     * @param service the service
     */
    void release(Key key, Service service);

}
