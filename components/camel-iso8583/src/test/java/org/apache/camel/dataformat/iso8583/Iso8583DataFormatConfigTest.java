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
package org.apache.camel.dataformat.iso8583;

import java.io.File;
import java.io.FileNotFoundException;

import com.solab.iso8583.IsoMessage;
import org.apache.camel.CamelExecutionException;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The configFile is loaded with the Camel resource loader, and a missing ISO-Type fails with a clear message.
 */
class Iso8583DataFormatConfigTest extends CamelTestSupport {

    @Test
    void unmarshalWithConfigFileFromFileSystem() {
        IsoMessage iso = template.requestBody("direct:file", new File("src/test/resources/parse1.txt"), IsoMessage.class);

        assertEquals(0x210, iso.getType());
        assertEquals("30.00", iso.getObjectValue(4).toString());
    }

    @Test
    void configFileNotFoundFailsAtStart() {
        Iso8583DataFormat dataFormat = new Iso8583DataFormat();
        dataFormat.setCamelContext(context);
        dataFormat.setConfigFile("file:target/does-not-exist/j8583-config.xml");

        Exception e = assertThrows(Exception.class, dataFormat::init);
        assertInstanceOf(FileNotFoundException.class, e.getCause());
    }

    @Test
    void unmarshalWithoutIsoType() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:noIsoType", new File("src/test/resources/parse1.txt")));

        IllegalArgumentException cause = assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(cause.getMessage().contains("isoType"), cause.getMessage());
        assertTrue(cause.getMessage().contains(Iso8583Constants.ISO_TYPE), cause.getMessage());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                Iso8583DataFormat fromFile = new Iso8583DataFormat();
                fromFile.setConfigFile("file:src/test/resources/j8583-config.xml");
                fromFile.setIsoType("0210");
                // a factory in the registry would be auto-wired instead of loading the configFile
                fromFile.setAllowAutoWiredMessageFormat(false);
                from("direct:file").unmarshal(fromFile);

                from("direct:noIsoType").unmarshal().iso8583();
            }
        };
    }
}
