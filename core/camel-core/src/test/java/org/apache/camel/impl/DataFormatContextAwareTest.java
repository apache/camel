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

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.Test;

/**
 *
 */
public class DataFormatContextAwareTest extends ContextTestSupport {

    private MyDataFormat my = new MyDataFormat();

    @Override
    protected Registry createRegistry() throws Exception {
        Registry registry = super.createRegistry();
        registry.bind("my", my);
        return registry;
    }

    @Test
    public void testLanguageCamelContextAware() throws Exception {
        DataFormat df = context.resolveDataFormat("my");
        assertNotNull(df);

        MyDataFormat me = assertIsInstanceOf(MyDataFormat.class, df);
        assertNotNull(me.getCamelContext());
    }

    private static class MyDataFormat extends ServiceSupport implements DataFormat, CamelContextAware {

        private CamelContext camelContext;

        @Override
        public CamelContext getCamelContext() {
            return camelContext;
        }

        @Override
        public void setCamelContext(CamelContext camelContext) {
            this.camelContext = camelContext;
        }

        @Override
        public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        }

        @Override
        public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
            return null;
        }

        @Override
        protected void doStart() throws Exception {
            // noop
        }

        @Override
        protected void doStop() throws Exception {
            // noop
        }
    }
}
