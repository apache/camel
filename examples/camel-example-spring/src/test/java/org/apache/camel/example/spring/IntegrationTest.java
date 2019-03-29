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
package org.apache.camel.example.spring;

import org.apache.camel.spring.Main;
import org.junit.Assert;
import org.junit.Test;

public class IntegrationTest extends Assert {

    @Test
    public void testCamelRulesDeployCorrectlyInSpring() throws Exception {
        // let's boot up the Spring application context for 2 seconds to check that it works OK
        Main.main("-duration", "2s", "-o", "target/site/cameldoc");
    }
}
