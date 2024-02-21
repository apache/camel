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

import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Various tests for the uri builder
 */
public class JMXUriBuilderTest {

    @Test
    public void defaultsToPlatform() {
        assertEquals("jmx:platform", new JMXUriBuilder().toString());
    }

    @Test
    public void remote() {
        assertEquals("jmx:service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi",
                new JMXUriBuilder("service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi").toString());
    }

    @Test
    public void withServerName() {
        assertEquals("jmx:service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi",
                new JMXUriBuilder().withServerName("service:jmx:rmi:///jndi/rmi://localhost:1099/jmxrmi").toString());
    }

    @Test
    public void format() {
        assertEquals("jmx:platform?format=raw", new JMXUriBuilder().withFormat("raw").toString());
    }

    @Test
    public void credentials() {
        assertEquals("jmx:platform?user=me&password=pass", new JMXUriBuilder().withUser("me").withPassword("pass").toString());
    }

    @Test
    public void objectName() {
        assertEquals("jmx:platform?objectDomain=myDomain&objectName=oname",
                new JMXUriBuilder().withObjectDomain("myDomain").withObjectName("oname").toString());
    }

    @Test
    public void notificationFilter() {
        assertEquals("jmx:platform?notificationFilter=#foo", new JMXUriBuilder().withNotificationFilter("#foo").toString());
    }

    @Test
    public void handback() {
        assertEquals("jmx:platform?handback=#hb", new JMXUriBuilder().withHandback("#hb").toString());
    }

    @Test
    public void objectProperties() {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        map.put("one", "1");
        map.put("two", "2");
        assertEquals("jmx:platform?key.one=1&key.two=2", new JMXUriBuilder().withObjectProperties(map).toString());
    }

    @Test
    public void withObjectPropertiesReference() {
        assertEquals("jmx:platform?objectProperties=#op", new JMXUriBuilder().withObjectPropertiesReference("#op").toString());
    }

    @Test
    public void withObjectPropertiesReferenceSansHashmark() {
        assertEquals("jmx:platform?objectProperties=#op", new JMXUriBuilder().withObjectPropertiesReference("op").toString());
    }
}
