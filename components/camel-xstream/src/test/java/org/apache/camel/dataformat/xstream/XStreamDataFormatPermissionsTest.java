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
package org.apache.camel.dataformat.xstream;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.ForbiddenClassException;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class XStreamDataFormatPermissionsTest extends CamelTestSupport {
    protected static final String XML_PURCHASE_ORDER = 
        "<org.apache.camel.dataformat.xstream.PurchaseOrder>"
        + "<name>foo</name>"
        + "<price>10.0</price>"
        + "<amount>1.0</amount>"
        + "</org.apache.camel.dataformat.xstream.PurchaseOrder>";
    protected static final String XML_PURCHASE_ORDERS_LIST = 
        "<list>"
        + "<org.apache.camel.dataformat.xstream.PurchaseOrder>"
        + "<name>foo</name>"
        + "<price>10.0</price>"
        + "<amount>1.0</amount>"
        + "</org.apache.camel.dataformat.xstream.PurchaseOrder>"
        + "<org.apache.camel.dataformat.xstream.PurchaseOrder>"
        + "<name>bar</name>"
        + "<price>9.0</price>"
        + "<amount>2.0</amount>"
        + "</org.apache.camel.dataformat.xstream.PurchaseOrder>"
        + "</list>";
    
    @Test
    public void testNone() {
        XStreamDataFormat xStreamDataFormat = new XStreamDataFormat();
        XStream xStream = xStreamDataFormat.createXStream(context.getClassResolver(), context.getApplicationContextClassLoader());
        
        try {
            xStream.fromXML(XML_PURCHASE_ORDER);
            fail("should fail to unmarshall");
        } catch (ForbiddenClassException e) {
            // OK
        }
    }
    
    
    @Test
    public void testDeny() {
        XStreamDataFormat xStreamDataFormat = new XStreamDataFormat();
        xStreamDataFormat.setPermissions("-org.apache.camel.dataformat.xstream.PurchaseOrder");

        XStream xStream = xStreamDataFormat.createXStream(context.getClassResolver(), context.getApplicationContextClassLoader());
        
        try {
            xStream.fromXML(XML_PURCHASE_ORDER);
            fail("should fail to unmarshall");
        } catch (ForbiddenClassException e) {
            // OK
        }
    }

    @Test
    public void testAllow() {
        XStreamDataFormat xStreamDataFormat = new XStreamDataFormat();
        xStreamDataFormat.setPermissions("org.apache.camel.dataformat.xstream.PurchaseOrder");

        XStream xStream = xStreamDataFormat.createXStream(context.getClassResolver(), context.getApplicationContextClassLoader());
        
        Object po = xStream.fromXML(XML_PURCHASE_ORDER);
        assertNotNull(po);
        
        po = xStream.fromXML(XML_PURCHASE_ORDERS_LIST);
        assertNotNull(po);
    }

    @Test
    public void testAllowAndDeny() {
        XStreamDataFormat xStreamDataFormat = new XStreamDataFormat();
        xStreamDataFormat.setPermissions("org.apache.camel.dataformat.xstream.PurchaseOrder,-org.apache.camel.dataformat.xstream.*");

        XStream xStream = xStreamDataFormat.createXStream(context.getClassResolver(), context.getApplicationContextClassLoader());
        
        try {
            xStream.fromXML(XML_PURCHASE_ORDER);
            fail("should fail to unmarshall");
        } catch (ForbiddenClassException e) {
            // OK
        }
    }

    @Test
    public void testDenyAndAllowDeny() {
        XStreamDataFormat xStreamDataFormat = new XStreamDataFormat();
        xStreamDataFormat.setPermissions("-org.apache.camel.dataformat.xstream.*,org.apache.camel.dataformat.xstream.PurchaseOrder");

        XStream xStream = xStreamDataFormat.createXStream(context.getClassResolver(), context.getApplicationContextClassLoader());
        
        Object po = xStream.fromXML(XML_PURCHASE_ORDER);
        assertNotNull(po);

        po = xStream.fromXML(XML_PURCHASE_ORDERS_LIST);
        assertNotNull(po);
    }

    @Test
    public void testAllowAny() {
        XStreamDataFormat xStreamDataFormat = new XStreamDataFormat();
        xStreamDataFormat.setPermissions("*");

        XStream xStream = xStreamDataFormat.createXStream(context.getClassResolver(), context.getApplicationContextClassLoader());
        
        Object po = xStream.fromXML(XML_PURCHASE_ORDER);
        assertNotNull(po);

        po = xStream.fromXML(XML_PURCHASE_ORDERS_LIST);
        assertNotNull(po);
    }

    @Test
    public void testAllowAnyAndDeny() {
        XStreamDataFormat xStreamDataFormat = new XStreamDataFormat();
        xStreamDataFormat.setPermissions("*,-org.apache.camel.dataformat.xstream.PurchaseOrder");

        XStream xStream = xStreamDataFormat.createXStream(context.getClassResolver(), context.getApplicationContextClassLoader());
        
        try {
            xStream.fromXML(XML_PURCHASE_ORDER);
            fail("should fail to unmarshall");
        } catch (ForbiddenClassException e) {
            // OK
        }
    }

    @Test
    public void testDenyAny() {
        XStreamDataFormat xStreamDataFormat = new XStreamDataFormat();
        xStreamDataFormat.setPermissions("-*");

        XStream xStream = xStreamDataFormat.createXStream(context.getClassResolver(), context.getApplicationContextClassLoader());
        
        try {
            xStream.fromXML(XML_PURCHASE_ORDER);
            fail("should fail to unmarshall");
        } catch (ForbiddenClassException e) {
            // OK
        }
    }

    @Test
    public void testDenyAnyAndAllow() {
        XStreamDataFormat xStreamDataFormat = new XStreamDataFormat();
        xStreamDataFormat.setPermissions("-*,org.apache.camel.dataformat.xstream.PurchaseOrder");

        XStream xStream = xStreamDataFormat.createXStream(context.getClassResolver(), context.getApplicationContextClassLoader());
        
        Object po = xStream.fromXML(XML_PURCHASE_ORDER);
        assertNotNull(po);

        try {
            xStream.fromXML(XML_PURCHASE_ORDERS_LIST);
            fail("should fail to unmarshall");
        } catch (ForbiddenClassException e) {
            // OK
        }
    }
}
