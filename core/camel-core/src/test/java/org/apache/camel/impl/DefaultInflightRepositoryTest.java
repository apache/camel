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
package org.apache.camel.impl;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.impl.engine.DefaultInflightRepository;
import org.apache.camel.spi.InflightRepository;
import org.apache.camel.support.DefaultExchange;
import org.junit.Test;

public class DefaultInflightRepositoryTest extends ContextTestSupport {

    @Test
    public void testDefaultInflightRepository() throws Exception {
        InflightRepository repo = new DefaultInflightRepository();

        assertEquals(0, repo.size());

        Exchange e1 = new DefaultExchange(context);
        repo.add(e1);
        assertEquals(1, repo.size());

        Exchange e2 = new DefaultExchange(context);
        repo.add(e2);
        assertEquals(2, repo.size());

        repo.remove(e2);
        assertEquals(1, repo.size());

        repo.remove(e1);
        assertEquals(0, repo.size());
    }
}
