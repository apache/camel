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
package org.apache.camel.spring.remoting;

import static junit.framework.TestCase.assertEquals;
import org.apache.camel.Consume;

public class MyMultiArgumentService implements MyMultiArgumentServiceInterface {

    @Override
    @Consume(uri = "direct:myargs")
    public void doSomething(String arg1, String arg2, Long arg3) {
        assertEquals("Hello World 1", arg1);
        assertEquals("Hello World 2", arg2);
        assertEquals(new Long(3), arg3);
    }

}
