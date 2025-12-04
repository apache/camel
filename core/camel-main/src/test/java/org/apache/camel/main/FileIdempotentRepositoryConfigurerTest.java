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

package org.apache.camel.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.support.processor.idempotent.FileIdempotentRepository;
import org.junit.jupiter.api.Test;

public class FileIdempotentRepositoryConfigurerTest {

    @Test
    public void testReflectionFree() {
        CamelContext context = new DefaultCamelContext();

        BeanIntrospection bi = PluginHelper.getBeanIntrospection(context);
        bi.setExtendedStatistics(true);

        context.start();

        FileIdempotentRepository target = new FileIdempotentRepository();

        boolean hit = PropertyBindingSupport.build()
                .withCamelContext(context)
                .withTarget(target)
                .withReflection(false)
                .withIgnoreCase(true)
                .withProperty("dropOldestFileStore", "123")
                .withProperty("maxFileStoreSize", "2000")
                .withRemoveParameters(true)
                .bind();

        assertTrue(hit);

        assertEquals(123, target.getDropOldestFileStore());
        assertEquals(2000, target.getMaxFileStoreSize());

        // will auto detect generated configurer so no reflection in use
        assertEquals(0, bi.getInvokedCounter());

        context.stop();
    }
}
