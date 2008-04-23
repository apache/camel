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
package org.apache.camel.example.server;

import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author martin.gilday
 */
public final class CamelServer {
    private CamelServer() {
        // the main class
    }

    /**
     * @param args
     */
    public static void main(final String[] args) {
        JmsBroker broker = new JmsBroker();

        try {
            broker.start();
            ApplicationContext context = new ClassPathXmlApplicationContext("META-INF/spring/camel-server.xml");
            Thread.sleep(5 * 60 * 1000);
        } catch (Exception e) {
            // get the exception
            e.printStackTrace();
        } finally {
            try {
                broker.stop();
            } catch (Exception e) {
                // do nothing here
            }
            System.exit(0);
        }

    }

}
