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
package org.apache.camel.component.file;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;

/**
 * What to do with malformed input and unmappable characters when the file component reads or writes a file with a
 * charset.
 */
public enum CharsetErrorAction {

    /**
     * Fail with a <tt>MalformedInputException</tt> or an <tt>UnmappableCharacterException</tt> (the default).
     */
    REPORT(CodingErrorAction.REPORT),
    /**
     * Replace the character with the replacement of the charset, such as U+FFFD when reading or '?' when writing
     * ISO-8859-1.
     */
    REPLACE(CodingErrorAction.REPLACE),
    /**
     * Drop the character.
     */
    IGNORE(CodingErrorAction.IGNORE);

    private final CodingErrorAction action;

    CharsetErrorAction(CodingErrorAction action) {
        this.action = action;
    }

    public CodingErrorAction getCodingErrorAction() {
        return action;
    }

    /**
     * A decoder for the charset that handles malformed input and unmappable characters with this action.
     */
    public CharsetDecoder newDecoder(Charset charset) {
        return charset.newDecoder().onMalformedInput(action).onUnmappableCharacter(action);
    }

    /**
     * An encoder for the charset that handles malformed input and unmappable characters with this action.
     */
    public CharsetEncoder newEncoder(Charset charset) {
        return charset.newEncoder().onMalformedInput(action).onUnmappableCharacter(action);
    }
}
