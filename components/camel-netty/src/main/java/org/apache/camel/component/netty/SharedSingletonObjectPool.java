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
package org.apache.camel.component.netty;

import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An {@link org.apache.commons.pool2.ObjectPool} that uses a single shared instance.
 * <p/>
 * This implementation will always return <tt>1</tt> in {@link #getNumActive()} and return <tt>0</tt> in
 * {@link #getNumIdle()}.
 */
public class SharedSingletonObjectPool<T> implements ObjectPool<T> {

    private static final Logger LOG = LoggerFactory.getLogger(SharedSingletonObjectPool.class);
    private final PooledObjectFactory<T> factory;
    private volatile PooledObject<T> t;

    public SharedSingletonObjectPool(PooledObjectFactory<T> factory) {
        this.factory = factory;
    }

    @Override
    public void addObject() throws Exception {
        // noop
    }

    @Override
    public synchronized T borrowObject() throws Exception {
        if (t != null) {
            // ensure the object is validated before we borrow it
            if (!factory.validateObject(t)) {
                invalidateObject(t.getObject());
                LOG.info("Recreating new connection as current connection is invalid: {}", t);
                t = null;
            }
        }
        if (t == null) {
            t = factory.makeObject();
        }
        return t.getObject();
    }

    @Override
    public void clear() throws Exception {
        t = null;
    }

    @Override
    public void close() {
        t = null;
    }

    @Override
    public int getNumActive() {
        return 1;
    }

    @Override
    public int getNumIdle() {
        return 0;
    }

    @Override
    public void invalidateObject(T obj) throws Exception {
        t = null;
    }

    @Override
    public void returnObject(T obj) throws Exception {
        // noop
    }

}
