/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.testng;

import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import org.apache.camel.spring.SpringCamelContext;

/**
 * A helper base class for running Camel based test cases using TestNG which makes it easy to overload
 * system properties before the spring application context is initialised; to allow a single spring XML to be reused
 * with some properties being overloaded.
 *
 * @version $Revision$
 */
public class SpringRunner {
    private Properties oldSystemProperties;
    private AbstractXmlApplicationContext applicationContext;
    private SpringCamelContext camelContext;

    protected void assertApplicationContextStarts(String applicationContextLocations, Properties properties) throws Exception {
        // lets overload the system properties
        Set<Map.Entry<Object, Object>> entries = properties.entrySet();
        for (Map.Entry<Object, Object> entry : entries) {
            System.setProperty(entry.getKey().toString(), entry.getValue().toString());
        }

        // now lets load the context
        applicationContext = new ClassPathXmlApplicationContext(applicationContextLocations);
        applicationContext.start();

        camelContext = SpringCamelContext.springCamelContext(applicationContext);
    }

    protected SpringCamelContext getCamelContext() {
        return camelContext;
    }

    /**
     * Creates a properties object with the given key and value 
     */
    protected Properties createProperties(String name, String value) {
        Properties properties = new Properties();
        properties.setProperty(name, value);
        return properties;
    }

    @BeforeTest
    protected void setUp() {
        oldSystemProperties = new Properties(System.getProperties());
    }

    @AfterTest
    protected void tearDown() {
        if (applicationContext != null) {
            applicationContext.close();
        }
        System.setProperties(oldSystemProperties);
    }

}