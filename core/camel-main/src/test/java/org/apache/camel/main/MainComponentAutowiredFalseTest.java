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
package org.apache.camel.main;

import java.net.ContentHandler;
import java.net.ContentHandlerFactory;
import java.net.CookiePolicy;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.support.DefaultComponent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class MainComponentAutowiredFalseTest {

    @Test
    public void testAutowiredFalse() throws Exception {
        Main main = new Main();
        main.bind("mycomponent-component", new MyComponentConfigurer());
        main.bind("chf", new MyContentHandlerFactory());
        main.bind("mycomponent", new MyComponent());

        main.addProperty("camel.component.mycomponent.hello", "World");
        main.addProperty("camel.component.mycomponent.autowired-enabled", "false");

        main.start();

        MyComponent my = main.getCamelContext().getComponent("mycomponent", MyComponent.class);
        // should bind the string value
        Assertions.assertEquals("World", my.getHello());
        // should not be autowired
        Assertions.assertNull(my.getContentHandlerFactory());
        Assertions.assertNull(my.getCookiePolicy());

        main.stop();
    }

    @Test
    public void testAutowiredTrue() throws Exception {
        Main main = new Main();
        main.bind("mycomponent-component", new MyComponentConfigurer());
        main.bind("chf", new MyContentHandlerFactory());
        main.bind("mycomponent", new MyComponent());

        main.addProperty("camel.component.mycomponent.hello", "World");
        main.addProperty("camel.component.mycomponent.autowired-enabled", "true");

        main.start();

        MyComponent my = main.getCamelContext().getComponent("mycomponent", MyComponent.class);
        // should bind the string value
        Assertions.assertEquals("World", my.getHello());
        // should be autowired
        Assertions.assertNotNull(my.getContentHandlerFactory());
        // this option is not autowire capable
        Assertions.assertNull(my.getCookiePolicy());

        main.stop();
    }

    private static final class MyComponent extends DefaultComponent {

        private ContentHandlerFactory contentHandlerFactory;
        private CookiePolicy cookiePolicy;
        private String hello;

        public MyComponent() {
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            return null;
        }

        public ContentHandlerFactory getContentHandlerFactory() {
            return contentHandlerFactory;
        }

        public void setContentHandlerFactory(ContentHandlerFactory contentHandlerFactory) {
            this.contentHandlerFactory = contentHandlerFactory;
        }

        public CookiePolicy getCookiePolicy() {
            return cookiePolicy;
        }

        public void setCookiePolicy(CookiePolicy cookiePolicy) {
            this.cookiePolicy = cookiePolicy;
        }

        public String getHello() {
            return hello;
        }

        public void setHello(String hello) {
            this.hello = hello;
        }
    }

    private static class MyComponentConfigurer extends org.apache.camel.support.component.PropertyConfigurerSupport
            implements GeneratedPropertyConfigurer, PropertyConfigurerGetter {

        @Override
        public String[] getAutowiredNames() {
            return new String[] { "contentHandlerFactory" };
        }

        @Override
        public boolean configure(CamelContext camelContext, Object target, String name, Object value, boolean ignoreCase) {
            if ("contentHandlerFactory".equals(name)) {
                MyComponent comp = (MyComponent) target;
                comp.setContentHandlerFactory((ContentHandlerFactory) value);
                return true;
            } else if ("hello".equals(name)) {
                MyComponent comp = (MyComponent) target;
                comp.setHello((String) value);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Class<?> getOptionType(String name, boolean ignoreCase) {
            if ("contentHandlerFactory".equals(name)) {
                return ContentHandlerFactory.class;
            } else if ("hello".equals(name)) {
                return String.class;
            } else {
                return null;
            }
        }

        @Override
        public Object getOptionValue(Object target, String name, boolean ignoreCase) {
            return null;
        }
    }

    private static class MyContentHandlerFactory implements ContentHandlerFactory {

        @Override
        public ContentHandler createContentHandler(String mimetype) {
            return null;
        }
    }

}
