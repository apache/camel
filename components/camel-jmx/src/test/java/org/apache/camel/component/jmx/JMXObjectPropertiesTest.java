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
package org.apache.camel.component.jmx;

import java.util.Hashtable;
import java.util.Map;

import org.apache.camel.component.jmx.beans.ISimpleMXBean;
import org.junit.jupiter.api.Test;

/**
 * Tests that the objectName is created with the hashtable of objectProperties
 */
public class JMXObjectPropertiesTest extends SimpleBeanFixture {

    @Test
    public void testObjectProperties() throws Exception {
        ISimpleMXBean bean = getSimpleMXBean();
        bean.touch();
        getMockFixture().waitForMessages();
    }

    @Override
    protected JMXUriBuilder buildFromURI() {
        return new JMXUriBuilder().withObjectDomain(DOMAIN).withObjectPropertiesReference("#myTable");
    }

    @Override
    protected void initRegistry() {
        Map<String, String> ht = new Hashtable<>();
        ht.put("name", "simpleBean");
        getRegistry().bind("myTable", ht);
    }

}
