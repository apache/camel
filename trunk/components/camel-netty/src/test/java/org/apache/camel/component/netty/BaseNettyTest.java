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
package org.apache.camel.component.netty;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.component.properties.PropertiesComponent;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.AfterClass;
import org.junit.BeforeClass;

/**
 *
 */
public class BaseNettyTest extends CamelTestSupport {
    private static volatile int port;

    @BeforeClass
    public static void initPort() throws Exception {
        File file = new File("target/nettyport.txt");

        if (!file.exists()) {
            // start from somewhere in the 25xxx range
            port = AvailablePortFinder.getNextAvailable(25000);
        } else {
            // read port number from file
            String s = IOConverter.toString(file, null);
            port = Integer.parseInt(s);
            // use next free port
            port = AvailablePortFinder.getNextAvailable(port + 1);
        }

    }

    @AfterClass
    public static void savePort() throws Exception {
        File file = new File("target/nettyport.txt");

        // save to file, do not append
        FileOutputStream fos = new FileOutputStream(file, false);
        try {
            fos.write(String.valueOf(port).getBytes());
        } finally {
            fos.close();
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.addComponent("properties", new PropertiesComponent("ref:prop"));
        return context;
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        Properties prop = new Properties();
        prop.setProperty("port", "" + getPort());
        jndi.bind("prop", prop);

        return jndi;
    }

    protected int getNextPort() {
        port = AvailablePortFinder.getNextAvailable(port + 1);
        return port;
    }

    protected int getPort() {
        return port;
    }

}
