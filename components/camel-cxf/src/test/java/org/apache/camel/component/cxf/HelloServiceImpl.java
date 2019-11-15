/*
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
package org.apache.camel.component.cxf;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HelloServiceImpl implements HelloService {
    private static final Logger LOG = LoggerFactory.getLogger(HelloServiceImpl.class);
    private int invocationCount;
    
    private String name;
    
    public HelloServiceImpl(String name) {
        this.name = name;
    }
    
    public HelloServiceImpl() {
        name = "";
    }
 
    @Override
    public String echo(String text) {
        LOG.info("call for echo with " + text);
        return "echo " + text;
    }

    @Override
    public void ping() {
        invocationCount++;
        LOG.info("call for oneway ping");
    }

    @Override
    public int getInvocationCount() {
        return invocationCount;
    }

    @Override
    public String sayHello() {
        return "hello" + name;
    }

    @Override
    public Boolean echoBoolean(Boolean bool) {
        return bool;
    }

    @Override
    public String complexParameters(List<String> par1, List<String> par2) {
        String result = "param";
        if (par1 != null && par2 != null) {
            result = result + ":" + par1.get(0) + par2.get(0);
        }
        return result;
    }


}


