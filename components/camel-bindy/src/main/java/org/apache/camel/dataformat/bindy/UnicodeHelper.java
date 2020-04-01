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
package org.apache.camel.dataformat.bindy;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.ibm.icu.text.BreakIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class replicates the essential parts of the String class in order to aid
 * proper work for Unicode chars in the presense of UTF-16. So for all operations 
 * please see {@link String} with the same signature. This class is equally immutable.
 */
public class UnicodeHelper implements Serializable {
    /**
     * Defines how length if a string is defined, i.e how chars are counted.
     */
    public enum Method {
        /**
         * One "char" is one Unicode codepoint, which is the standard case.
         */
        CODEPOINTS,
        
        /**
         * One "char" is one graphem.
         */
        GRAPHEME;
    }
    
    private static final Logger LOG = LoggerFactory.getLogger(UnicodeHelper.class);
    
    private String input;
    
    private List<Integer> splitted;

    private Method method;
    
    /**
     * Create instance.
     * 
     * @param input
     *         String, that is to be wrapped.
     * @param method 
     *         Method, that is used to determin "chars" of string.
     */
    public UnicodeHelper(final String input, final Method method) {
        this.input = input;
        this.method = method;
        this.splitted = null;
    }

    /**
     * For Serialization only!
     */
    protected UnicodeHelper() {
        // Empty
    }

    /**
     * @return
     *         Returns the method used to determining the string length.
     */
    public Method getMethod() {
        return method;
    }

    /**
     * @see String#substring(int)
     */
    public String substring(final int beginIndex) {
        split();
        
        final int beginChar = splitted.get(beginIndex);
        return input.substring(beginChar);
    }
    
    /**
     * @see String#substring(int, int)
     */
    public String substring(final int beginIndex, final int endIndex) {
        split();
        
        final int beginChar = splitted.get(beginIndex);
        final int endChar = splitted.get(endIndex);
        return input.substring(beginChar, endChar);
    }

    /**
     * @see String#length()
     */
    public int length() {
        split();
        
        return splitted.size() - 1;
    }
    
    /**
     * @see String#indexOf(String)
     */
    public int indexOf(final String str) {
        return indexOf(str, 0);
    }   
    
    /**
     * @see String#indexOf(String, int)
     */
    public int indexOf(final String str, final int fromIndex) {
        split();
        
        final int len = new UnicodeHelper(str, method).length();

        for (int index = fromIndex; index + len < length(); index++) {
            if (str.equals(input.substring(splitted.get(index), splitted.get(index + len)))) {
                return index;
            }
        }

        return -1;
    }
    
    private void split() {
        if (this.splitted != null) {
            return;
        }
        
        if (method.equals(Method.CODEPOINTS)) {
            splitCodepoints();
            
        } else /* (method.equals(Method.GRAPHEME)) */ {
            splitGrapheme();
        }
        
        LOG.debug("\"{}\" is splitted into {} ({} {}).", input, splitted, splitted.size() - 1, method);
        if (LOG.isTraceEnabled()) {
            for (int i = 0; i < splitted.size() - 2; i++) {
                LOG.trace("segment [{},{}[=\"{}\".", splitted.get(i), splitted.get(i + 1), input.substring(splitted.get(i), splitted.get(i + 1)));
            }
        }
    }

    private void splitCodepoints() {
        final List<Integer> result = new ArrayList<>();
        
        int i = 0;
        final int len = input.length();
        while (i < len) {
            result.add(i);
            i += (Character.codePointAt(input, i) > 0xffff) ? 2 : 1; 
        }
        result.add(len);
        
        this.splitted = result;
    }

    private void splitGrapheme() {
        final List<Integer> result = new ArrayList<>();

        // 
        // Caution: The BreakIterator of ICU lib (com.ibm.icu.text.BreakIterator; siehe Dependencies) ist used here, 
        //          since the Java builtin one cannot handle modern unicode (Emojis with sex, skin colour, etc.) correctly.
        //
        final BreakIterator bit = BreakIterator.getCharacterInstance();
        bit.setText(input);
        
        result.add(bit.first());
        for (int end = bit.next(); end != BreakIterator.DONE; end = bit.next()) {
            result.add(end);
        }
        this.splitted = result;
    }

    @Override
    public String toString() {
        return "StringHelper [input=" + input + ", splitted=" + splitted + ", method=" + method + "]";
    }
}
