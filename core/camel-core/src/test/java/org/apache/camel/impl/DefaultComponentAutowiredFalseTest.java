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
package org.apache.camel.impl;

import java.net.ContentHandler;
import java.net.ContentHandlerFactory;
import java.net.CookiePolicy;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Consumer;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.GeneratedPropertyConfigurer;
import org.apache.camel.spi.PropertyConfigurerGetter;
import org.apache.camel.spi.Registry;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.DefaultEndpoint;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DefaultComponentAutowiredFalseTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry reg = super.createRegistry();
        reg.bind("mycomponent-component", new MyComponentConfigurer());
        reg.bind("mycomponent-endpoint-configurer", new MyComponentConfigurer());
        reg.bind("chf", new MyContentHandlerFactory());
        return reg;
    }

    @Test
    public void testAutowiredFalse() throws Exception {
        MyComponent my = new MyComponent(context);
        my.setAutowiredEnabled(false);
        context.addComponent("mycomponent", my);

        my = context.getComponent("mycomponent", MyComponent.class);
        Assertions.assertNotNull(my);

        ContentHandlerFactory chf = (ContentHandlerFactory) context.getRegistry().lookupByName("chf");
        Assertions.assertNotNull(chf);

        // should not be autowired
        Assertions.assertNull(my.getContentHandlerFactory());
        Assertions.assertNull(my.getCookiePolicy());
    }

    @Test
    public void testAutowiredFalseWithEndpointTrue() throws Exception {
        MyComponent my = new MyComponent(context);
        my.setAutowiredEnabled(false);
        context.addComponent("mycomponent", my);

        my = context.getComponent("mycomponent", MyComponent.class);
        Assertions.assertNotNull(my);

        ContentHandlerFactory chf = (ContentHandlerFactory) context.getRegistry().lookupByName("chf");
        Assertions.assertNotNull(chf);

        // should not be autowired
        Assertions.assertNull(my.getContentHandlerFactory());
        Assertions.assertNull(my.getCookiePolicy());

        // endpoint
        MyEndpoint me = context.getEndpoint("mycomponent://test", MyEndpoint.class);
        Assertions.assertNull(me.getContentHandlerFactory());
    }

    @Test
    public void testAutowiredTrue() throws Exception {
        MyComponent my = new MyComponent(context);
        my.setAutowiredEnabled(true);
        context.addComponent("mycomponent", my);

        my = context.getComponent("mycomponent", MyComponent.class);
        Assertions.assertNotNull(my);

        ContentHandlerFactory chf = (ContentHandlerFactory) context.getRegistry().lookupByName("chf");
        Assertions.assertNotNull(chf);

        // should be autowired
        Assertions.assertSame(chf, my.getContentHandlerFactory());
        Assertions.assertNull(my.getCookiePolicy());
    }

    @Component("mycomponent")
    private static final class MyComponent extends DefaultComponent {

        private ContentHandlerFactory contentHandlerFactory;
        private CookiePolicy cookiePolicy;

        private MyComponent(CamelContext context) {
            super(context);
        }

        @Override
        protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
            MyEndpoint me = new MyEndpoint();
            me.setComponent(this);
            return me;
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
                if (target instanceof MyComponent) {
                    MyComponent comp = (MyComponent) target;
                    comp.setContentHandlerFactory((ContentHandlerFactory) value);
                } else {
                    MyEndpoint endp = (MyEndpoint) target;
                    endp.setContentHandlerFactory((ContentHandlerFactory) value);
                }
                return true;
            } else {
                return false;
            }
        }

        @Override
        public Class<?> getOptionType(String name, boolean ignoreCase) {
            if ("contentHandlerFactory".equals(name)) {
                return ContentHandlerFactory.class;
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

    private static final class MyEndpoint extends DefaultEndpoint {
        private ContentHandlerFactory contentHandlerFactory;

        @Override
        public Producer createProducer() throws Exception {
            return null;
        }

        @Override
        public Consumer createConsumer(Processor processor) throws Exception {
            return null;
        }

        @Override
        public boolean isSingleton() {
            return false;
        }

        public ContentHandlerFactory getContentHandlerFactory() {
            return contentHandlerFactory;
        }

        public void setContentHandlerFactory(ContentHandlerFactory contentHandlerFactory) {
            this.contentHandlerFactory = contentHandlerFactory;
        }
    }

}
