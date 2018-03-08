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
package org.apache.camel.component.hbase;

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class HBaseComponentTest {

    @Test
    public void testHBaseConfigurationClassLoaderSetToAppContextClassLoader() throws Exception {
        ClassLoader expectedClassLoader = HBaseComponentTest.class.getClassLoader();

        CamelContext camelContext = new DefaultCamelContext();
        camelContext.setApplicationContextClassLoader(expectedClassLoader);

        HBaseComponent component = new HBaseComponent(new DefaultCamelContext());
        component.doStart();
        component.doStop();

        ClassLoader actualClassLoader = component.getConfiguration().getClassLoader();
        Assert.assertSame(expectedClassLoader, actualClassLoader);
    }

    @Test
    public void testHBaseConfigurationClassLoaderNotOverridden() throws Exception {
        ClassLoader expectedClassLoader = HBaseComponentTest.class.getClassLoader().getParent();

        Configuration configuration = HBaseConfiguration.create();
        configuration.setClassLoader(expectedClassLoader);

        HBaseComponent component = new HBaseComponent(new DefaultCamelContext());
        component.setConfiguration(configuration);
        try {
            component.doStart();
        } catch (IOException e) {
            // Expected because the ClassLoader we set is not the correct one, but it's safe to ignore here
        }
        component.doStop();

        ClassLoader actualClassLoader = component.getConfiguration().getClassLoader();
        Assert.assertSame(expectedClassLoader, actualClassLoader);
    }

    @Test
    public void testHBaseConfigurationClassLoaderSetToDefault() throws Exception {
        ClassLoader expectedClassLoader = HBaseConfiguration.class.getClassLoader();

        HBaseComponent component = new HBaseComponent(new DefaultCamelContext());
        component.doStart();
        component.doStop();

        ClassLoader actualClassLoader = component.getConfiguration().getClassLoader();
        Assert.assertSame(expectedClassLoader, actualClassLoader);
    }
}
