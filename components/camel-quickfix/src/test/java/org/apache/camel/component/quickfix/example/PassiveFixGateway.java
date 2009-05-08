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
package org.apache.camel.component.quickfix.example;

import java.io.InputStream;

import org.apache.camel.spring.SpringRouteBuilder;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author Anton Arhipov
 *         <p/>
 *         To use this example, start Banzai and Executor applications from
 *         Quickfix distribution. The PassiveFixGateway example is intended to
 *         receive the messages from Banzai, forward them to Executor, and
 *         respond back to the Banzai with the execution report.
 */
public class PassiveFixGateway extends SpringRouteBuilder {

    public void configure() throws Exception {
        from("quickfix-server:examples/server.cfg").to("quickfix-client:examples/client.cfg");
    }

    public static void main(String[] args) {
        new ClassPathXmlApplicationContext("examples/fix-gateway.xml");
    }

}
