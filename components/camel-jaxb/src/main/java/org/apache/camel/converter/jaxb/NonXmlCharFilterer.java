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
package org.apache.camel.converter.jaxb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides filtering of characters that do fall into <a
 * href="http://www.w3.org/TR/2004/REC-xml-20040204/#NT-Char">range defined by
 * XML 1.0 spec</a>. <i>Filtering</i> here means replacement with space char.
 * 
 */
class NonXmlCharFilterer {
    private static final Logger LOG = LoggerFactory.getLogger(FilteringXmlStreamWriter.class);
    private static final char REPLACEMENT_CHAR = ' ';

    /**
     * Determines whether specified character needs to be filtered.
     */
    boolean isFiltered(char c) {
        // Char ::= #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] |
        // [#x10000-#x10FFFF]
        // Won't be checking last interval, as it goes beyond 0xFFFF.
        if (c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c <= 0xD7FF)
                || (c >= 0xE000 && c <= 0xFFFD)) {
            return false;
        }
        return true;
    }

    /**
     * Filter specified char array by replacing non-XML chars with space. Only
     * part of array specified by <code>offset</code> and <code>length</code> is
     * affected.
     * 
     * @return <code>true</code> if <code>content</code> was modified,
     *         <code>false</code> otherwise.
     */
    public boolean filter(char[] content, int offset, int length) {
        if (content == null) {
            return false;
        }

        boolean filtered = false;
        for (int i = offset; i < offset + length; i++) {
            if (isFiltered(content[i])) {
                filtered = true;
                content[i] = REPLACEMENT_CHAR;
            }
        }

        if (filtered) {
            LOG.warn("Identified and replaced non-XML chars");
        }

        return filtered;
    }

    /**
     * Filter specified string by replacing illegal chars with space.
     * 
     * @return filtered string
     */
    public String filter(String original) {
        if (original == null) {
            return null;
        }

        char[] chars = original.toCharArray();
        if (!filter(chars, 0, chars.length)) {
            return original;
        }

        String filtered = new String(chars);
        LOG.warn("Illegal characters were filtered; original => \"" + original
                + "\", filtered => \"" + filtered + "\"");
        return filtered;
    }

}
