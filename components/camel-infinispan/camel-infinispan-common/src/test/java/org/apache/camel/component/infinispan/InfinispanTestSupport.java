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
package org.apache.camel.component.infinispan;

import java.util.concurrent.ThreadLocalRandom;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.infinispan.commons.api.BasicCache;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

@TestMethodOrder(MethodOrderer.MethodName.class)
public abstract class InfinispanTestSupport extends CamelTestSupport {
    private static final String TEST_CACHE = "mycache" + ThreadLocalRandom.current().nextInt(1, 100);

    protected BasicCache<Object, Object> getCache() {
        return getCache(getCacheName());
    }

    protected String getCacheName() {
        return TEST_CACHE + "-" + getClass().getSimpleName();
    }

    protected abstract BasicCache<Object, Object> getCache(String name);
}
