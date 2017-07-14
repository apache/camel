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

import junit.framework.TestCase;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spring.example.MyProcessor;

/**
 * @version 
 */
public class MainExampleTest extends TestCase {

    public void testMain() throws Exception {
        Main main = new Main();
        main.addRouteBuilder(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file://src/test/data?initialDelay=0&delay=10&noop=true").process(new MyProcessor()).to("file://target/mainTest");
            }
        });
        main.start();

        // run for 100 millis
        main.setDuration(100);

        main.stop();
    }
    
    public void testFileApplicationContextUri() throws Exception {
        Main main = new Main();
        main.setFileApplicationContextUri("src/test/resources/org/apache/camel/spring/routingUsingProcessor.xml");
        main.start();

        // run for 100 millis
        main.setDuration(100);

        main.stop();
    }

}