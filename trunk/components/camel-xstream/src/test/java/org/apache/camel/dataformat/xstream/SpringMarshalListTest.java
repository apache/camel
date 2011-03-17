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
package org.apache.camel.dataformat.xstream;

import org.apache.camel.CamelContext;
import org.apache.camel.Service;
import org.apache.camel.spring.SpringCamelContext;
import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class SpringMarshalListTest extends MarshalListTest {
    
    protected CamelContext createCamelContext() throws Exception {
        setUseRouteBuilder(false);

        final AbstractXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext("org/apache/camel/dataformat/xstream/SpringMarshalListTest.xml");
        setCamelContextService(new Service() {
            public void start() throws Exception {
                applicationContext.start();

            }

            public void stop() throws Exception {
                applicationContext.stop();
            }
        });

        return SpringCamelContext.springCamelContext(applicationContext);        
    }

}
