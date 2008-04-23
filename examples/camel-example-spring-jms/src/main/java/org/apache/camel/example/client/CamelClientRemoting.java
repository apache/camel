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
package org.apache.camel.example.client;

import org.apache.camel.example.server.Multiplier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Requires that the JMS broker is running, as well as CamelServer
 *
 * @author martin.gilday
 */
public final class CamelClientRemoting {

    private CamelClientRemoting() {
        // the main class
    }

    public static void main(final String[] args) {

        ApplicationContext context = new ClassPathXmlApplicationContext("camel-client-remoting.xml");
        Multiplier multiplier = (Multiplier)context.getBean("multiplierProxy");
        int response = multiplier.multiply(22);
        System.out.println("Invoking the multiply with 22, the result is " + response);
        System.exit(0);

    }

}
