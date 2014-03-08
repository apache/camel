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
package org.apache.camel.component.stax.model;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;

public final class RecordsUtil {

    private RecordsUtil() {
        // no-op
    }

    public static void createXMLFile() {
        File in = new File("target/in/records.xml");
        if (in.exists()) {
            return;
        } else {
            if (!in.getParentFile().exists() && !in.getParentFile().mkdirs()) {
                throw new RuntimeException("can't create " + in.getParent());
            }
        }

        Records records = new Records();
        for (int i = 0; i < 10; i++) {
            Record record = new Record();
            record.setKey(Integer.toString(i));
            record.setValue("#" + i);
            records.getRecord().add(record);
        }

        Marshaller marshaller;
        try {
            JAXBContext jaxbCtx = JAXBContext.newInstance(Records.class.getPackage().getName());
            marshaller = jaxbCtx.createMarshaller();
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        }

        FileWriter writer = null;
        try {
            writer = new FileWriter(in);
            marshaller.marshal(records, writer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JAXBException e) {
            throw new RuntimeException(e);
        } finally {
            if (writer != null) {
                try {
                    writer.flush();
                    writer.close();
                } catch (IOException e) {
                    // no-op
                }
            }
        }
    }
}
