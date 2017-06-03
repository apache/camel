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
package org.apache.camel.loanbroker;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

//START SNIPPET: client
public final class Client {

    private Client() {
    }

    public static void main(String args[]) throws Exception {

        AbstractApplicationContext applicationContext = new ClassPathXmlApplicationContext("META-INF/spring/client.xml");
        CamelContext context = applicationContext.getBean("camel", CamelContext.class);
        context.start();

        ProducerTemplate template = context.createProducerTemplate();

        String out = template.requestBodyAndHeader("jms:queue:loan", null, Constants.PROPERTY_SSN, "Client-A", String.class);
        System.out.println(out);

        template.stop();
        context.stop();
    }

}
// END SNIPPET: client
