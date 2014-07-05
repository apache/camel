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
package org.apache.camel.dataformat.zipfile;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultMessage;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Iterator which can go through the ZipInputStream according to ZipEntry
 * Based on the thread <a href="http://camel.465427.n5.nabble.com/zip-file-best-practices-td5713437.html">zip file best practices</a>
 */
public class ZipIterator implements Iterator<Message>, Closeable {
    static final Logger LOGGER = LoggerFactory.getLogger(ZipIterator.class);
    
    private final Message inputMessage;
    private ZipInputStream zipInputStream;
    private Message parent;
    
    public ZipIterator(Message inputMessage) {
        this.inputMessage = inputMessage;
        InputStream inputStream = inputMessage.getBody(InputStream.class);
        if (inputStream instanceof ZipInputStream) {
            zipInputStream = (ZipInputStream)inputStream;
        } else {
            zipInputStream = new ZipInputStream(new BufferedInputStream(inputStream));
        }
        parent = null;
    }
    
    @Override
    public boolean hasNext() {
        try {
            if (zipInputStream == null) {
                return false;
            }
            boolean availableDataInCurrentEntry = zipInputStream.available() == 1;
            if (!availableDataInCurrentEntry) {
                // advance to the next entry.
                parent = getNextElement();
                // check if there are more data.
                availableDataInCurrentEntry = zipInputStream.available() == 1;
                // if there are not more data, close the stream.
                if (!availableDataInCurrentEntry) {
                    zipInputStream.close();
                }                
            }
            return availableDataInCurrentEntry;            
        } catch (IOException exception) {
            //Just wrap the IOException as CamelRuntimeException
            throw new RuntimeCamelException(exception);      
        }
    }

    @Override
    public Message next() {
        if (parent == null) {
            parent = getNextElement();
        }
        Message answer = parent;
        parent = null;
        checkNullAnswer(answer);

        return answer;
    }
    
    private Message getNextElement() {
        Message answer = null;
        
        if (zipInputStream != null) {
            try {
                ZipEntry current = getNextEntry();

                if (current != null) {
                    LOGGER.debug("read zipEntry {}", current.getName());
                    answer = new DefaultMessage();
                    answer.getHeaders().putAll(inputMessage.getHeaders());
                    answer.setHeader("zipFileName", current.getName());
                    answer.setHeader(Exchange.FILE_NAME, current.getName());
                    answer.setBody(new ZipInputStreamWrapper(zipInputStream));
                    return answer;
                } else {
                    LOGGER.trace("close zipInputStream");
                }
            } catch (IOException exception) {
                //Just wrap the IOException as CamelRuntimeException
                throw new RuntimeCamelException(exception);
            }
        }        
        
        return answer;
    }

    public void checkNullAnswer(Message answer) {
        if (answer == null && zipInputStream != null) {
            IOHelper.close(zipInputStream);
            zipInputStream = null;
        }
    }

    private ZipEntry getNextEntry() throws IOException {
        ZipEntry entry;

        while ((entry = zipInputStream.getNextEntry()) != null) {
            if (!entry.isDirectory()) {
                return entry;
            }
        }

        return null;
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        if (zipInputStream != null) {
            zipInputStream.close();
        }
    }
}