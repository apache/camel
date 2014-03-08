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
package org.apache.camel.dataformat.bindy.util;

/**
 * To help return the char associated to the unicode string
 */
public final class ConverterUtils {

    private ConverterUtils() {
        // helper class
    }

    public static char getCharDelimiter(String separator) {
        if (separator.equals("\\u0001")) {
            return '\u0001';
        } else if (separator.equals("\\t") || separator.equals("\\u0009")) {
            return '\u0009';
        } else if (separator.length() > 1) {
            return separator.charAt(separator.length() - 1);
        } else {
            return separator.charAt(0);
        }
    }

    public static byte[] getByteReturn(String returnCharacter) {
        if (returnCharacter.equals("WINDOWS")) {
            return new byte[] {13, 10};
        } else if (returnCharacter.equals("UNIX")) {
            return new byte[] {10};
        } else if (returnCharacter.equals("MAC")) {
            return new byte[] {13};
        } else {
            return returnCharacter.getBytes();
        }
    }

    public static String getStringCarriageReturn(String returnCharacter) {
        if (returnCharacter.equals("WINDOWS")) {
            return "\r\n";
        } else if (returnCharacter.equals("UNIX")) {
            return "\n";
        } else if (returnCharacter.equals("MAC")) {
            return "\r";
        } else {
            return returnCharacter;
        }
    }
}
