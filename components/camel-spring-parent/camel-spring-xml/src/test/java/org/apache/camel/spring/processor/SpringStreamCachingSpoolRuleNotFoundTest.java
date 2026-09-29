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
package org.apache.camel.spring.processor;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SpringStreamCachingSpoolRuleNotFoundTest {

    @Test
    public void testSpoolRuleNotFound() {
        Exception e = assertThrows(Exception.class, () -> {
            try (ClassPathXmlApplicationContext ac = new ClassPathXmlApplicationContext(
                    "org/apache/camel/spring/processor/SpringStreamCachingSpoolRuleNotFoundTest.xml")) {
                // expected to throw
            }
        });
        Throwable t = e;
        boolean found = false;
        while (t != null && !found) {
            found = t.getMessage() != null && t.getMessage().contains("myUnknownRule");
            t = t.getCause();
        }
        assertTrue(found, "Should report the unknown spool rule");
    }
}
