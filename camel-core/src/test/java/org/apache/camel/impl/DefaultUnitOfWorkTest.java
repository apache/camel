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
package org.apache.camel.impl;
import org.apache.camel.CamelContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class DefaultUnitOfWorkTest extends Assert {
    
    private DefaultUnitOfWork unitOfWork;

    @Before
    public void setUp() throws Exception {

        
        CamelContext context = new DefaultCamelContext();
        context.setUuidGenerator(new SimpleUuidGenerator());
        unitOfWork = new DefaultUnitOfWork(new DefaultExchange(context));
    }

    @Test
    public void testGetId() {
        String id = unitOfWork.getId();
        assertNotNull(id);
        assertEquals(id, unitOfWork.getId());
    }
}