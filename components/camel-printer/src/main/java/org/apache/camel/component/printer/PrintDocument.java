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
package org.apache.camel.component.printer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.attribute.DocAttributeSet;

class PrintDocument implements Doc {
    private DocFlavor docFlavor;
    private InputStream stream;
    private Reader reader;
    private byte[] buffer;

    PrintDocument(InputStream stream, DocFlavor docFlavor) {
        this.stream = stream;
        this.docFlavor = docFlavor;
    }
   
    @Override
    public DocFlavor getDocFlavor() {
        return docFlavor;
    }

    @Override
    public DocAttributeSet getAttributes() {
        return null;
    }

    @Override
    public Object getPrintData() throws IOException {
        return getStreamForBytes();
    }

    @Override
    public Reader getReaderForText() throws IOException {
        synchronized (this) {
            if (reader != null) {
                return reader;
            } 
            
            if (docFlavor.getMediaType().equalsIgnoreCase("image")) {
                reader = null;
            } else if ((docFlavor.getMediaType().equalsIgnoreCase("text")) 
                || ((docFlavor.getMediaType().equalsIgnoreCase("application")) 
                && (docFlavor.getMediaSubtype().equalsIgnoreCase("xml")))) {
                buffer = new byte[stream.available()];
                int n = stream.available();
                for (int i = 0; i < n; i++) {
                    buffer[i] = (byte)stream.read();
                }
               
                reader = new StringReader(new String(buffer));
                stream = new ByteArrayInputStream(buffer);
            }
            
            return reader;
        }
    }

    @Override
    public InputStream getStreamForBytes() throws IOException {
        return stream; 
    }

}
