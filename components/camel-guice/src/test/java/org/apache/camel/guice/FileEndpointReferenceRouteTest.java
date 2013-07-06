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
package org.apache.camel.guice;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.InitialContext;

import com.google.inject.Injector;
import com.google.inject.Provides;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileFilter;
import org.apache.camel.guice.inject.Injectors;
import org.apache.camel.guice.jndi.GuiceInitialContextFactory;
import org.apache.camel.guice.jndi.JndiBind;
import org.junit.Assert;
import org.junit.Test;

public class FileEndpointReferenceRouteTest extends Assert {
    
    public static class RouteInstaller extends RouteBuilder {

        public void configure() throws Exception {
            // lets add some other route builders
            includeRoutes(new MyConfigurableRoute(endpoint("file://src/test/resources?noop=true&filter=#fileFilter"), endpoint("direct:b")));
        }
    }
    
    public static class MyFileFilter<T> implements GenericFileFilter<T> {

        public boolean accept(GenericFile<T> file) {
            // we only want report files 
            return file.getFileName().startsWith("report");
        }
    }

    
    public static class MyModule extends CamelModuleWithRouteTypes {
       
        @SuppressWarnings("unchecked")
        public MyModule() {
            super(RouteInstaller.class);
        }
        
        @Provides
        @JndiBind("fileFilter")
        public GenericFileFilter<?> getfileFilter() {
            return new MyFileFilter<Object>();
        }
        

    }
    
    public static void assertCamelContextRunningThenCloseInjector(Injector injector) throws Exception {
        CamelContext camelContext = injector.getInstance(CamelContext.class);

        org.hamcrest.MatcherAssert.assertThat(camelContext, org.hamcrest.Matchers.is(GuiceCamelContext.class));
        GuiceCamelContext guiceContext = (GuiceCamelContext) camelContext;
        assertTrue("is started!", guiceContext.isStarted());

        Thread.sleep(1000);

        Injectors.close(injector);
    }
    
    @Test
    public void runTest() throws Exception {
        Hashtable<String, Object> env = new Hashtable<String, Object>();
        env.put(Context.PROVIDER_URL, GuiceInitialContextFactory.class.getName());
        env.put(Injectors.MODULE_CLASS_NAMES, MyModule.class.getName());

        InitialContext context = new InitialContext(env);
        Injector injector = (Injector) context.lookup(Injector.class.getName());
        assertNotNull("Found injector", injector);

        Object value = context.lookup("fileFilter");
        assertNotNull("Should have found a value for foo!", value);
        
        assertCamelContextRunningThenCloseInjector(injector);
    }


}
