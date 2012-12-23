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
package org.apache.camel.component.netty;

import java.util.NoSuchElementException;

import org.apache.commons.pool.ObjectPool;
import org.apache.commons.pool.PoolableObjectFactory;

/**
 * An {@link org.apache.commons.pool.ObjectPool} that uses a single shared instance.
 * <p/>
 * This implementation will always return <tt>1</tt> in {@link #getNumActive()} and
 * return <tt>0</tt> in {@link #getNumIdle()}.
 */
public class SharedSingletonObjectPool<T> implements ObjectPool<T> {

    private final PoolableObjectFactory<T> factory;
    private volatile T t;

    public SharedSingletonObjectPool(PoolableObjectFactory<T> factory) {
        this.factory = factory;
    }

    @Override
    public synchronized T borrowObject() throws Exception, NoSuchElementException, IllegalStateException {
        if (t == null) {
            t = factory.makeObject();
        }
        return t;
    }

    @Override
    public void returnObject(T obj) throws Exception {
        // noop
    }

    @Override
    public void invalidateObject(T obj) throws Exception {
        t = null;
    }

    @Override
    public void addObject() throws Exception, IllegalStateException, UnsupportedOperationException {
        // noop
    }

    @Override
    public int getNumIdle() throws UnsupportedOperationException {
        return 0;
    }

    @Override
    public int getNumActive() throws UnsupportedOperationException {
        return 1;
    }

    @Override
    public void clear() throws Exception, UnsupportedOperationException {
        t = null;
    }

    @Override
    public void close() throws Exception {
        t = null;
    }

    @Override
    public void setFactory(PoolableObjectFactory<T> factory) throws IllegalStateException, UnsupportedOperationException {
        // noop
    }
}
