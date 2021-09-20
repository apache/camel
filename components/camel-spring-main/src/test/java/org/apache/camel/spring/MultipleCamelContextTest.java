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
package org.apache.camel.spring;

import org.apache.camel.CamelContext;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MultipleCamelContextTest {

    @Test
    public void testMain() throws Exception {
        Main main = new Main();
        main.setAllowMultipleCamelContexts(true);
        main.setApplicationContextUri("classpath:org/apache/camel/spring/MultipleCamelContextTest.xml");
        main.start();

        CamelContext camel1 = main.getApplicationContext().getBean("camel1", CamelContext.class);
        CamelContext camel2 = main.getApplicationContext().getBean("camel2", CamelContext.class);

        Assertions.assertNotSame(camel1, camel2);
        Assertions.assertEquals(2, camel1.getRoutesSize());
        Assertions.assertEquals(3, camel2.getRoutesSize());

        main.stop();
    }
}
