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
package org.apache.camel.spring;

import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CamelGlobalOptionsTest extends SpringTestSupport {

    public void testGlobalOptionsOverrideProperties() {
        assertEquals(3, context.getGlobalOptions().size());
        assertEquals("VALUE_1", context.getGlobalOptions().get("KEY_1"));
        assertEquals("VALUE_2_OVERIDDEN", context.getGlobalOptions().get("KEY_2"));
        assertEquals("VALUE_3", context.getGlobalOptions().get("KEY_3"));
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/spring/camelGlobalOptions.xml");
    }

}
