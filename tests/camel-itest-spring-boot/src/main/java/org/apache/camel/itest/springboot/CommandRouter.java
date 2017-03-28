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
package org.apache.camel.itest.springboot;

import java.util.concurrent.Future;

import org.springframework.context.ApplicationContext;

import static org.apache.camel.itest.springboot.util.SerializationUtils.marshal;
import static org.apache.camel.itest.springboot.util.SerializationUtils.transferable;
import static org.apache.camel.itest.springboot.util.SerializationUtils.unmarshal;

/**
 * Routes a command coming from another classloader to the appropriate spring bean.
 */
public final class CommandRouter {

    private CommandRouter() {
    }

    public static byte[] execute(String commandStr, byte[] params) {

        try {
            ApplicationContext context = ApplicationContextHolder.getApplicationContext();

            Command command = (Command) context.getBean(commandStr);

            Object[] args = null;
            if (params != null) {
                args = (Object[]) unmarshal(params);
            }

            Future<Object> futResult = command.execute(args);
            Object result = futResult.get();

            return marshal(result);
        } catch (Throwable t) {
            return marshal(transferable(t));
        }
    }

}
