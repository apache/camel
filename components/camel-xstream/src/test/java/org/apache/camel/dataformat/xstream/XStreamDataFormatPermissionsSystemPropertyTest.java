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
package org.apache.camel.dataformat.xstream;

import com.thoughtworks.xstream.XStream;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

public class XStreamDataFormatPermissionsSystemPropertyTest extends XStreamDataFormatPermissionsTest {

    @BeforeClass
    public static void setup() {
        // clear the default permissions system property
        // see AbstractXStreamWrapper.PERMISSIONS_PROPERTY_DEFAULT
        XStreamTestUtils.setPermissionSystemProperty("*");
    }

    @AfterClass
    public static void cleanup() {
        XStreamTestUtils.revertPermissionSystemProperty();
    }

    @Test
    @Override
    public void testNone() {
        XStreamDataFormat xStreamDataFormat = new XStreamDataFormat();
        XStream xStream = xStreamDataFormat.createXStream(context.getClassResolver(), context.getApplicationContextClassLoader());
        
        Object po = xStream.fromXML(XML_PURCHASE_ORDER);
        assertNotNull(po);
    }
}
