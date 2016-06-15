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
package org.apache.camel.itest.springboot.util;

import java.lang.reflect.Method;

import org.apache.camel.itest.springboot.ITestConfig;

/**
 * Provide access to testing methods defined in the Spring-Boot context (that uses a different classloader).
 */
public class SpringBootContainerFacade {

    private Class<?> delegateClass;

    public SpringBootContainerFacade(ClassLoader springClassloader) {
        try {
            this.delegateClass = springClassloader.loadClass("org.apache.camel.itest.springboot.CommandRouter");
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Exception while loading target class", e);
        }
    }

    public Object executeTest(String test, ITestConfig config, String component) throws Exception {
        Object resObj = execute(test, config, component);
        return resObj;
    }

    private Object execute(String command, Object... args) throws Exception {
        Method method = delegateClass.getMethod("execute", String.class, byte[].class);
        byte[] argsSer = null;
        if (args != null) {
            argsSer = SerializationUtils.marshal(args);
        }

        byte[] resByte = (byte[]) method.invoke(null, command, argsSer);
        if (resByte != null) {
            Object res = SerializationUtils.unmarshal(resByte);
            if (res instanceof Exception) {
                throw (Exception) res;
            } else if (res instanceof Throwable) {
                throw new RuntimeException((Throwable) res);
            } else {
                return res;
            }
        }

        return null;
    }

}
