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

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StreamCache;

public class FileInputStreamCache extends InputStream implements StreamCache {
    private FileInputStream inputStream;
    private CachedOutputStream cachedOutputStream;
    private File file;

    public FileInputStreamCache(File file, CachedOutputStream cos) throws FileNotFoundException {
        this.file = file;
        cachedOutputStream = cos;
        inputStream = new FileInputStream(file);       
    }
    
    public void close() {
        try {
            inputStream.close();
            cachedOutputStream.close();
        } catch (Exception exception) {
            throw new RuntimeCamelException(exception);
        } 
    }

    public void reset() {            
        try {
            inputStream.close();            
            inputStream = new FileInputStream(file);
        } catch (Exception exception) {
            throw new RuntimeCamelException(exception);
        }            
    }
    
    public int available() throws IOException {
        return inputStream.available();
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }   
    
}   
