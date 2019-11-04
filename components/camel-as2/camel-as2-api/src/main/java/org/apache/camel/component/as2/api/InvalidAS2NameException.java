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
package org.apache.camel.component.as2.api;

/**
 * Thrown to indicated a given AS2 name is invalid.
 */
public class InvalidAS2NameException extends Exception {

    private static final long serialVersionUID = -6284079291785073089L;

    private final String name;

    private final int index;

    /**
     * Constructs an <code>InvalidAS2NameException</code> for the
     * specified name and index.
     *
     * @param name - the AS2 name that is invalid.
     * @param index - the index in the <code>name</code> of the invalid character
     */
    public InvalidAS2NameException(String name, int index) {
        this.name = name;
        this.index = index;
    }

    /* (non-Javadoc)
     * @see java.lang.Throwable#getMessage()
     */
    @Override
    public String getMessage() {
        char character = name.charAt(index);
        String invalidChar = "" + character;
        if (Character.isISOControl(character)) {
            invalidChar = String.format("\\u%04x", (int) character);
        }
        return "Invalid character '" + invalidChar + "' at index " + index;
    }

    /**
     * Returns the invalid AS2 name
     *
     * @return the invalid AS2 name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the index of the invalid character in <code>name</code>
     *
     * @return the index of the invalid character in <code>name</code>
     */
    public int getIndex() {
        return index;
    }
}
