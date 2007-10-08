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
package org.apache.camel.converter.jaxb;

import java.io.IOException;
import java.io.OutputStream;

import javax.xml.bind.JAXBException;

import org.apache.camel.spi.Marshaller;
import org.apache.camel.util.IOHelper;

/**
 * A {@link Marshaller} which uses JAXB2
 *
 * @version $Revision: 1.1 $
 */
public class JaxbMarshaller implements Marshaller {
    javax.xml.bind.Marshaller marshaller;

    public JaxbMarshaller(javax.xml.bind.Marshaller marshaller) {
        this.marshaller = marshaller;
    }

    public void marshal(Object graph, OutputStream stream) throws IOException {
        try {
            marshaller.marshal(graph, stream);
        }
        catch (JAXBException e) {
            throw IOHelper.createIOException(e);
        }
    }
}
