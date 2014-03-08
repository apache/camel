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
package org.apache.camel.component.sjms.jms;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.test.junit4.TestSupport;

import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO Add Class documentation for ObjectPoolTest
 */
public class ObjectPoolTest extends TestSupport {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectPoolTest.class);

    /**
     * Test method for
     * {@link org.apache.camel.component.sjms.jms.ObjectPool#ObjectPool()}.
     * 
     * @throws Exception
     */
    @Test
    public void testObjectPool() throws Exception {
        ObjectPool<MyPooledObject> testPool = new TestPool();
        assertNotNull(testPool);
        testPool.fillPool();
        MyPooledObject pooledObject = testPool.borrowObject();
        assertNotNull(pooledObject);
        assertTrue("Expected a value of 1.  Returned: " + pooledObject.getObjectId(), pooledObject.getObjectId() == 1);

        MyPooledObject nextPooledObject = testPool.borrowObject();
        assertNull(nextPooledObject);

        testPool.returnObject(pooledObject);
        nextPooledObject = testPool.borrowObject();
        assertNotNull(nextPooledObject);
        testPool.drainPool();
    }

    /**
     * Test method for
     * {@link org.apache.camel.component.sjms.jms.ObjectPool#ObjectPool()}.
     */
    @Test
    public void testBadObjectPool() {
        ObjectPool<Object> objectPool = new BadTestPool();

        try {
            objectPool.createObject();
            fail("Should have thrown exception");
        } catch (Exception e) {
            assertIsInstanceOf(IllegalStateException.class, e);
        }
    }

    /**
     * Test method for
     * {@link org.apache.camel.component.sjms.jms.ObjectPool#ObjectPool(int)}.
     * 
     * @throws Exception
     */
    @Test
    public void testObjectPoolInt() throws Exception {
        final int maxPoolObjects = 5;

        ObjectPool<MyPooledObject> testPool = new TestPool(maxPoolObjects);
        testPool.fillPool();

        List<MyPooledObject> poolObjects = new ArrayList<MyPooledObject>();
        for (int i = 0; i < maxPoolObjects; i++) {
            poolObjects.add(testPool.borrowObject());
        }
        for (int i = 0; i < maxPoolObjects; i++) {
            MyPooledObject pooledObject = poolObjects.get(i);
            assertNotNull("MyPooledObject was null for borrow attempt: " + i, pooledObject);
            assertTrue("Expected a value in the range of 1-5.  Returned: " + pooledObject.getObjectId(), pooledObject.getObjectId() > 0 && pooledObject.getObjectId() < 6);
            LOGGER.info("MyPooledObject has an ID of: " + pooledObject.getObjectId());
        }

        assertNull("Pool should be empty", testPool.borrowObject());

        for (MyPooledObject myPooledObject : poolObjects) {
            testPool.returnObject(myPooledObject);
        }

        MyPooledObject pooledObject = testPool.borrowObject();
        assertNotNull(pooledObject);
        assertTrue("Expected a value in the range of 1-5.  Returned: " + pooledObject.getObjectId(), pooledObject.getObjectId() > 0 && pooledObject.getObjectId() < 6);

        testPool.drainPool();
    }

    /**
     * Test method for
     * {@link org.apache.camel.component.sjms.jms.ObjectPool#createObject()}.
     * 
     * @throws Exception
     */
    @Test
    public void testCreateObject() throws Exception {
        ObjectPool<MyPooledObject> testPool = new TestPool();
        assertNotNull(testPool.createObject());
    }

    /**
     * Test method for
     * {@link org.apache.camel.component.sjms.jms.ObjectPool#borrowObject()}.
     * 
     * @throws Exception
     */
    @Test
    public void testBorrowObject() throws Exception {
        ObjectPool<MyPooledObject> testPool = new TestPool();
        testPool.fillPool();
        MyPooledObject pooledObject = testPool.borrowObject();
        assertNotNull(pooledObject);
        assertTrue("Expected a value of 1.  Returned: " + pooledObject.getObjectId(), pooledObject.getObjectId() == 1);

        MyPooledObject nextPooledObject = testPool.borrowObject();
        assertNull("Expected a null as the pool of 1 was already removed", nextPooledObject);
        testPool.drainPool();
    }

    /**
     * Test method for
     * {@link org.apache.camel.component.sjms.jms.ObjectPool#returnObject(java.lang.Object)}
     * .
     * 
     * @throws Exception
     */
    @Test
    public void testReturnObject() throws Exception {
        ObjectPool<MyPooledObject> testPool = new TestPool();
        testPool.fillPool();
        assertNotNull(testPool);
        MyPooledObject pooledObject = testPool.borrowObject();
        MyPooledObject nextPooledObject = testPool.borrowObject();
        testPool.returnObject(pooledObject);
        nextPooledObject = testPool.borrowObject();
        assertNotNull(nextPooledObject);
        testPool.drainPool();
    }

    private static class TestPool extends ObjectPool<MyPooledObject> {

        private final AtomicInteger atomicInteger = new AtomicInteger();

        public TestPool() {
        }

        public TestPool(int poolSize) {
            super(poolSize);
        }

        @Override
        protected MyPooledObject createObject() throws Exception {
            return new MyPooledObject(atomicInteger.incrementAndGet());
        }

        @Override
        protected void destroyObject(MyPooledObject t) throws Exception {
            t = null;
        }

    }

    static class MyPooledObject {
        private int objectId = -1;

        public MyPooledObject(int objectId) {
            this.objectId = objectId;
        }

        /**
         * @return the OBJECT_ID
         */
        public Integer getObjectId() {
            return this.objectId;
        }
    }

    private static class BadTestPool extends ObjectPool<Object> {

        @Override
        protected Object createObject() throws Exception {
            throw new IllegalStateException("I'm a bad ObjectPool impl");
        }

        @Override
        protected void destroyObject(Object t) throws Exception {
            // noop
        }

    }
}
