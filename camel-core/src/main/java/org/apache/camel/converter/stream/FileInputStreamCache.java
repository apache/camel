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
package org.apache.camel.converter.stream;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StreamCache;
import org.apache.camel.util.IOHelper;

public class FileInputStreamCache extends InputStream implements StreamCache {
    private InputStream stream;
    private CachedOutputStream cachedOutputStream;
    private File file;

    public FileInputStreamCache() {
    }

    public FileInputStreamCache(File file, CachedOutputStream cos) throws FileNotFoundException {
        this.file = file;
        this.cachedOutputStream = cos;
        this.stream = new FileInputStream(file);
    }
    
    @Override
    public void close() {
        try {
            getInputStream().close();
            if (cachedOutputStream != null) {
                cachedOutputStream.close();
            }
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        } 
    }

    @Override
    public void reset() {
        try {
            getInputStream().close();
            // reset by creating a new stream based on the file
            stream = new FileInputStream(file);
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }            
    }

    public void writeTo(OutputStream os) throws IOException {
        IOHelper.copy(getInputStream(), os);
    }

    @Override
    public int available() throws IOException {
        return getInputStream().available();
    }

    @Override
    public int read() throws IOException {
        return getInputStream().read();
    }

    protected InputStream getInputStream() throws FileNotFoundException {
        if (file != null && stream == null) {
            stream = new FileInputStream(file);
        }
        return stream;
    }

}
