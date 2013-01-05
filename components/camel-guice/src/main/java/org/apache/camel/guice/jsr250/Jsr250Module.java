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
package org.apache.camel.guice.jsr250;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import org.apache.camel.guice.support.GuiceyFruitModule;
import org.apache.camel.guice.support.MethodHandler;

/**
 * A module which installs JSR 250 lifecycle and injection using the
 * {@link Resource} annotation.
 * 
 * @version
 */
public class Jsr250Module extends GuiceyFruitModule {

    protected void configure() {
        super.configure();

        bindAnnotationInjector(Resource.class, ResourceMemberProvider.class);

        bindMethodHandler(PostConstruct.class,
                new MethodHandler<Object, Annotation>() {
                    public void afterInjection(Object injectee, Annotation annotation, Method method) 
                        throws InvocationTargetException,
                            IllegalAccessException {

                        method.invoke(injectee);
                    }
                });

        bind(PreDestroyCloser.class);
    }

}
