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
package org.apache.camel.catalog;

import java.lang.management.ManagementFactory;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Deprecated
@Ignore
public class CamelCatalogMBeanExporterTest {

    private CamelCatalogMBeanExporter exporter = new CamelCatalogMBeanExporter();

    @Test
    public void testMBeanExporter() throws Exception {
        exporter.init();

        ObjectName on = exporter.getObjectName();

        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        assertTrue("MBean should be regsistered", mBeanServer.isRegistered(on));

        String schema = (String) mBeanServer.invoke(on, "componentJSonSchema", new Object[]{"docker"}, new String[]{"java.lang.String"});
        assertNotNull(schema);
        assertTrue("Should be docker schema", schema.contains("org.apache.camel.component.docker.DockerComponent"));

        exporter.destroy();
        assertFalse("MBean should be unregsistered", mBeanServer.isRegistered(on));
    }
}
