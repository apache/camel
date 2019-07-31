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
package org.apache.camel.spring.issues;

import java.util.Arrays;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.junit.Test;

import static org.apache.camel.spring.processor.SpringTestHelper.createSpringCamelContext;

public class SpringInitializationIssueTest extends ContextTestSupport {

    @Override
    protected CamelContext createCamelContext() throws Exception {
        return createSpringCamelContext(this, "org/apache/camel/spring/issues/SpringInitializationIssueTest.xml");
    }

    @Test
    public void testTemp() {
        assertEquals(Arrays.asList(new String[] {"test2a", "test2b", "configured"}), getNamesList("entries2"));
        // Will fail because of wrong bean initialization order caused by SpringCamelContext
        assertEquals(Arrays.asList(new String[] {"test1a", "test1b", "configured"}), getNamesList("entries1"));
    }

    private List<?> getNamesList(String beanName) {
        return context.getRegistry().lookupByNameAndType(beanName, List.class);
    }
}
