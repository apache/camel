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
package org.apache.camel.component.salesforce.api;

import com.thoughtworks.xstream.XStream;

import org.apache.camel.component.salesforce.dto.generated.MSPTest;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class MultiSelectPicklistXmlTest {

    private static final String TEST_XML = "<MSPTest>\n"
        + "  <MspField>Value1;Value2;Value3</MspField>\n"
        + "</MSPTest>";
    private static final String TEST_NULL_XML = "<MSPTest/>";

    private static XStream xStream = new XStream();

    @BeforeClass
    public static void beforeClass() throws Exception {
        xStream = new XStream();
        xStream.processAnnotations(MSPTest.class);
    }


    @Test
    public void testMarshal() throws Exception {
        final MSPTest mspTest = new MSPTest();
        mspTest.setMspField(MSPTest.MSPEnum.values());

        String xml = xStream.toXML(mspTest);
        assertEquals(TEST_XML, xml);

        // test null value
        mspTest.setMspField(null);

        xml = xStream.toXML(mspTest);
        assertEquals(TEST_NULL_XML, xml);
    }

    @Test
    public void testUnmarshal() throws Exception {
        MSPTest mspTest = (MSPTest) xStream.fromXML(TEST_XML);
        assertArrayEquals(MSPTest.MSPEnum.values(), mspTest.getMspField());

        // test null field value
        mspTest = (MSPTest) xStream.fromXML(TEST_NULL_XML);
        assertNull(mspTest.getMspField());
    }

}