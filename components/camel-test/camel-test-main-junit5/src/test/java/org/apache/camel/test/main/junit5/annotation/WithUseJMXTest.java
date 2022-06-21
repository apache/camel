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
package org.apache.camel.test.main.junit5.annotation;

import org.apache.camel.BeanInject;
import org.apache.camel.CamelContext;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.test.main.junit5.CamelMainTest;
import org.apache.camel.test.main.junit5.common.MyMainClass;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * A test class ensuring that JMX can be enabled.
 */
@CamelMainTest(mainClass = MyMainClass.class, useJmx = true)
class WithUseJMXTest {

    @BeanInject
    CamelContext context;

    @Test
    void shouldFindTheManagedCamelContext() {
        assertNotNull(context);
        ManagedCamelContext mc = context.getExtension(ManagedCamelContext.class);
        assertNotNull(mc);
        ManagedCamelContextMBean managedCamelContext = mc.getManagedCamelContext();
        assertNotNull(managedCamelContext);
    }
}
