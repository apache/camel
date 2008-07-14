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
package org.apache.camel.component.file;

import java.io.File;

import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.util.jndi.JndiContext;

/**
 * Unit test that we can chain bean and file producer.
 */
public class BeanToFileTest extends ContextTestSupport {

    public void testBeanToFile() throws Exception {
        template.sendBody("direct:in", "World");

        // give Camel time to create the file
        Thread.sleep(1000);

        File file = new File("target/BeanToFileTest.txt");
        file = file.getAbsoluteFile();
        assertEquals("Bye World", IOConverter.toString(file));
    }

    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("myBean", new MyBean());
        return answer;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in").
                    to("bean:myBean").
                    setHeader(FileComponent.HEADER_FILE_NAME, "BeanToFileTest.txt").
                    to("file://target/?append=false");
            }
        };
    }

    public static class MyBean {
        public String doSomething(String input) {
            return "Bye " + input;
        }
    }

}
