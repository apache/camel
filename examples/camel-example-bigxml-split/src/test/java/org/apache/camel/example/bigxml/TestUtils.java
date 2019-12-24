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
package org.apache.camel.example.bigxml;

import java.io.File;
import java.io.FileOutputStream;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class TestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(TestUtils.class);
    private static final String BASEPATH = System.getProperty("user.dir") + "/target/data";
    private static final int NUM_RECORDS = 40000;
    private static final int MAX_WAIT_TIME = 5000;
    
    private TestUtils() {
    }

    public static String getBasePath() {
        return BASEPATH;
    }

    public static int getNumOfRecords() {
        return NUM_RECORDS;
    }

    public static int getMaxWaitTime() {
        return MAX_WAIT_TIME;
    }

    public static void buildTestXml() throws Exception {
        new File(BASEPATH).mkdir();
        File f = new File(BASEPATH + "/test.xml");
        if (!f.exists()) {
            LOG.info("Building test XML file...");
            XMLOutputFactory xof = XMLOutputFactory.newInstance();
            XMLStreamWriter xsw = xof.createXMLStreamWriter(new FileOutputStream(f), "UTF-8");
            try {
                xsw.writeStartDocument("UTF-8", "1.0");
                xsw.writeStartElement("records");
                xsw.writeAttribute("xmlns", "http://fvaleri.it/records");
                for (int i = 0; i < NUM_RECORDS; i++) {
                    xsw.writeStartElement("record");
                    xsw.writeStartElement("key");
                    xsw.writeCharacters("" + i);
                    xsw.writeEndElement();
                    xsw.writeStartElement("value");
                    xsw.writeCharacters("The quick brown fox jumps over the lazy dog");
                    xsw.writeEndElement();
                    xsw.writeEndElement();
                }
                xsw.writeEndElement();
                xsw.writeEndDocument();
            } finally {
                LOG.info("Test XML file ready (size: {} kB)", f.length() / 1024);
                xsw.flush();
                xsw.close();
            }
        }
    }

}
