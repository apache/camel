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
package org.apache.camel.example.pojo_messaging;

import java.io.File;

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class CamelContextTest extends CamelSpringTestSupport {
    
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/messages");
        super.setUp();
    }
       
    @Test
    public void testCheckFiles() throws Exception {
        // wait a little for the files to be picked up and processed
        Thread.sleep(5000);

        File file = new File("target/messages/emea/hr_pickup");
        assertTrue("The pickup folder should exists", file.exists());
        assertEquals("There should be 1 dumped files", 1, file.list().length);
        file = new File("target/messages/amer/hr_pickup");
        assertTrue("The pickup folder should exists", file.exists());
        assertEquals("There should be 2 dumped files", 2, file.list().length);    
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {        
        return new ClassPathXmlApplicationContext("META-INF/spring/camel-context.xml");
    }

}
