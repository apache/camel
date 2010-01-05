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
package org.apache.camel.converter.jaxb;

import java.io.FilterReader;
import java.io.IOException;
import java.io.Reader;

/**
 * This FilterReader will skip the ISO control character and others
 *  
 */
public class JaxbFilterReader extends FilterReader {
    
    protected JaxbFilterReader(Reader in) {
        super(in);
    }
    
    /**
     * Reads a single character.
     *
     * @exception  IOException  If an I/O error occurs
     */
    public int read() throws IOException {
        char ch = (char) in.read();
        while (isFiltered(ch)) {
            // Skip the character that need to be filtered.
            ch = (char) in.read();
        }
        return ch;
    }

    /**
     * Reads characters into a portion of an array.
     *
     * @exception  IOException  If an I/O error occurs
     */
    public int read(char cbuf[], int off, int len) throws IOException {
        char buffer[] = new char[len];
        int readed = in.read(buffer, 0, len - off);
        if (readed >= 0) {
            int copyed = 0;
            for (int i = 0; i < readed; i++) {
                if (!isFiltered(buffer[i])) {
                    cbuf[off + copyed] = buffer[i];
                    copyed++;
                } else {
                    // Skip the character that need to be filtered.
                }
            }
            return copyed;
        } else {
            return readed;
        }
    }
    
    // According to http://www.w3.org/TR/2004/REC-xml-20040204/#NT-Char,
    // we filter these Chars
    protected boolean isFiltered(char ch) {
        return Character.isISOControl(ch) || ((int)ch >= 0xFDD0 && (int)ch <= 0xFDDF);
    }
    
    
    
    

}
