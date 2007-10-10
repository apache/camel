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
package org.apache.camel.util;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.util.BitSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


public class UnsafeUriCharactersEncoder {
    private static final transient Log LOG = LogFactory.getLog(UnsafeUriCharactersEncoder.class);
    static BitSet unsafeCharacters;
    static {
        unsafeCharacters = new BitSet(256);
        unsafeCharacters.set(' ');
        unsafeCharacters.set('"');
        unsafeCharacters.set('<');
        unsafeCharacters.set('>');
        unsafeCharacters.set('#');
        unsafeCharacters.set('%');
        unsafeCharacters.set('{');
        unsafeCharacters.set('}');
        unsafeCharacters.set('|');
        unsafeCharacters.set('\\');
        unsafeCharacters.set('^');
        unsafeCharacters.set('~'); 
        unsafeCharacters.set('[');
        unsafeCharacters.set(']');
        unsafeCharacters.set('`');        
    }
    
    
    private UnsafeUriCharactersEncoder() {
        // util class 
    }
    
    public static String encode(String s) {
        int n = s.length();
        if (n == 0)
            return s;
        
        // First check whether we actually need to encode
        
        try {
            byte[] bytes = s.getBytes("UTF8");        
            for (int i = 0;;) {
                if (unsafeCharacters.get(bytes[i]))
                    break;
                if (++i >= bytes.length)
                    return s;
            }
            
            StringBuffer sb = new StringBuffer();
            for (byte b : bytes) {            
                if (unsafeCharacters.get(b)) {
                    appendEscape(sb, (byte)b);
                }    
                else
                    sb.append((char)b);
            }
            return sb.toString();
        } catch (UnsupportedEncodingException e) {
            LOG.error("Can't encoding the uri: ", e);            
            return null;
        }    
    }
    
    private static void appendEscape(StringBuffer sb, byte b) {
        sb.append('%');
        sb.append(hexDigits[(b >> 4) & 0x0f]);
        sb.append(hexDigits[(b >> 0) & 0x0f]);
    }
    
    private final static char[] hexDigits = {
                                        '0', '1', '2', '3', '4', '5', '6', '7',
                                        '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'
                                         };
    
      

}
