/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.graalvm;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.FileComponent;
import org.apache.camel.component.file.FileConsumer;
import org.apache.camel.component.file.FileEndpoint;
import org.apache.camel.component.file.FileOperations;
import org.apache.camel.component.file.strategy.GenericFileProcessStrategySupport;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.SimpleRegistry;
import org.apache.camel.util.IntrospectionSupport;

public abstract class CamelRuntime extends RouteBuilder {

    private SimpleRegistry registry = new SimpleRegistry();
    private CamelContext context = new FastCamelContext(registry);

    public void start() throws Exception {
        context.addRoutes(this);

//        FileEndpoint e = (FileEndpoint) new FileComponent(context).createEndpoint("file:target/orders");
//        FileConsumer c = new FileConsumer(e, ex -> {
//        }, new FileOperations(e), new GenericFileProcessStrategySupport<File>() {
//        });
//
//        Set<Method> methods = IntrospectionSupport.findSetterMethods(c.getClass(), "initialDelay", true);
//        System.err.println("Setter founds: " + methods.size());
//        for (Method method : methods) {
//            System.err.println(method.toGenericString());
//        }
//        System.err.println("Methods: " + Arrays.toString(c.getClass().getMethods()));
//        System.err.println("Methods: " + Arrays.toString(c.getClass().getSuperclass().getMethods()));
//
//
//        IntrospectionSupport.setProperty(context, c, "initialDelay", "1000");

        context.start();
    }

    public void bind(String name, Object object) {
        registry.put(name, object);
    }

}
