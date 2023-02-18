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
package org.apache.camel.component.properties;

import java.util.Properties;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.PropertiesComponent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PropertiesComponentKeepOnlyChangedTest extends ContextTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testChanged() throws Exception {
        PropertiesComponent pc = context.getPropertiesComponent();
        pc.addInitialProperty("foo", "123");
        pc.addInitialProperty("bar", "true");

        Properties p = new Properties();
        p.setProperty("foo", "123");
        p.setProperty("bar", "false");
        pc.keepOnlyChangeProperties(p);
        Assertions.assertEquals(1, p.size());
        Assertions.assertEquals("false", p.getProperty("bar"));

        p = new Properties();
        p.setProperty("foo", "123");
        p.setProperty("bar", "true");
        pc.keepOnlyChangeProperties(p);
        Assertions.assertEquals(0, p.size());

        p = new Properties();
        p.setProperty("foo", "123");
        p.setProperty("bar", "false");
        p.setProperty("cheese", "gauda");
        pc.keepOnlyChangeProperties(p);
        Assertions.assertEquals(2, p.size());
        Assertions.assertEquals("false", p.getProperty("bar"));
        Assertions.assertEquals("gauda", p.getProperty("cheese"));
    }

}
