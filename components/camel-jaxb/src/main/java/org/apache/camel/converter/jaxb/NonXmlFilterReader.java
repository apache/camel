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
 * This FilterReader will filter out the non-XML characters, see
 * {@link NonXmlCharFilterer} for details.
 */
public class NonXmlFilterReader extends FilterReader {
    NonXmlCharFilterer nonXmlCharFilterer = new NonXmlCharFilterer();

    protected NonXmlFilterReader(Reader in) {
        super(in);
    }

    /**
     * Reads characters into a portion of an array.
     * 
     * @exception IOException
     *                If an I/O error occurs
     */
    public int read(char cbuf[], int off, int len) throws IOException {
        int read = in.read(cbuf, off, len);
        if (read > 0) {
            nonXmlCharFilterer.filter(cbuf, off, read);
        }
        return read;
    }

}
