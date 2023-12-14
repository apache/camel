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

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;

import javax.sql.DataSource;

import org.apache.camel.BeanInject;
import org.apache.camel.BindToRegistry;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.CamelBeanPostProcessor;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DefaultCamelBeanPostProcessorComplexFieldFirstTest extends ContextTestSupport {

    private CamelBeanPostProcessor postProcessor;

    @Test
    public void testPostProcessor() throws Exception {
        FooService foo = new FooService();

        context.getRegistry().bind("myDS", new DummyDataSource());

        postProcessor.postProcessBeforeInitialization(foo, "foo");
        postProcessor.postProcessAfterInitialization(foo, "foo");

        // should register the beans in the registry via @BindRegistry
        Object bean = context.getRegistry().lookupByName("myCoolBean");
        assertNotNull(bean);
        MySerialBean msb = assertIsInstanceOf(MySerialBean.class, bean);

        assertEquals(123, msb.getId());
        assertEquals(DummyDataSource.class.getName(), msb.getName());
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        postProcessor = PluginHelper.getBeanPostProcessor(context);
    }

    @BindToRegistry
    public static class FooService {

        @BeanInject
        private DataSource ctx;

        @BindToRegistry("myCoolBean")
        public MySerialBean myBean() {
            MySerialBean myBean = new MySerialBean();
            myBean.setId(123);
            myBean.setName(ctx.getClass().getName());
            return myBean;
        }

    }

    private class DummyDataSource implements DataSource {

        @Override
        public Connection getConnection() throws SQLException {
            return null;
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return null;
        }

        @Override
        public PrintWriter getLogWriter() throws SQLException {
            return null;
        }

        @Override
        public void setLogWriter(PrintWriter out) throws SQLException {

        }

        @Override
        public void setLoginTimeout(int seconds) throws SQLException {

        }

        @Override
        public int getLoginTimeout() throws SQLException {
            return 0;
        }

        @Override
        public Logger getParentLogger() throws SQLFeatureNotSupportedException {
            return null;
        }

        @Override
        public <T> T unwrap(Class<T> iface) throws SQLException {
            return null;
        }

        @Override
        public boolean isWrapperFor(Class<?> iface) throws SQLException {
            return false;
        }
    }
}
