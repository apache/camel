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
package org.apache.camel.graalvm.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.Producer;
import org.apache.camel.graalvm.CamelRuntime;
import org.apache.camel.graalvm.Reflection;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Language;
import org.apache.xbean.finder.ClassFinder;
import org.graalvm.nativeimage.Feature;
import org.graalvm.nativeimage.RuntimeReflection;

public class CamelFeature implements Feature {

    private void allowInstantiate(Class cl) {
        RuntimeReflection.register(cl);
        for (Constructor c : cl.getConstructors()) {
            RuntimeReflection.register(c);
        }
    }

    private void allowMethods(Class cl) {
        for (Method method : cl.getMethods()) {
            RuntimeReflection.register(method);
        }
    }

    private void allowMethod(Method method) {
        RuntimeReflection.register(method);
    }

    public void beforeAnalysis(BeforeAnalysisAccess access) {
        try {
            ClassFinder finder = new ClassFinder(CamelRuntime.class.getClassLoader());

            finder.findAnnotatedClasses(Reflection.class).forEach(this::allowInstantiate);
            finder.findAnnotatedMethods(Reflection.class).forEach(this::allowMethod);

            finder.findImplementations(Component.class).forEach(this::allowInstantiate);
            finder.findImplementations(Language.class).forEach(this::allowInstantiate);
            finder.findImplementations(DataFormat.class).forEach(this::allowInstantiate);
            finder.findImplementations(Endpoint.class).forEach(this::allowMethods);
            finder.findImplementations(Consumer.class).forEach(this::allowMethods);
            finder.findImplementations(Producer.class).forEach(this::allowMethods);

            allowInstantiate(org.apache.camel.component.file.strategy.GenericFileProcessStrategyFactory.class);
            allowMethods(org.apache.camel.component.file.strategy.GenericFileProcessStrategyFactory.class);

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

}
