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
package org.apache.camel.dataformat.bindy.format.factories;

import java.text.ParseException;

import org.apache.camel.dataformat.bindy.Format;
import org.apache.camel.dataformat.bindy.FormattingOptions;

public class CharacterFormatFactory extends AbstractFormatFactory {

    private final CharacterFormat characterFormat = new CharacterFormat();

    {
        supportedClasses.add(char.class);
        supportedClasses.add(Character.class);
    }

    @Override
    public Format<?> build(FormattingOptions formattingOptions) {
        return characterFormat;
    }

    private static class CharacterFormat implements Format<Character> {

        @Override
        public String format(Character object) throws Exception {
            return object.toString();
        }

        @Override
        public Character parse(String string) throws Exception {
            if (string.length() > 1) {
                throw new ParseException("The string \"" + string + "\" cannot be parsed to a character (size > 1).", 1);
            }
            return string.charAt(0);
        }

    }
}
