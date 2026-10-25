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
package org.apache.camel.language.simple;

import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.annotations.Language;

/**
 * The Camel file language.
 */
@Language(value = "file", functionsClass = FileConstants.class)
@BodyTypes(expressionIn = @BodyType(value = Carrier.ANY,
                                    description = "The body as it is, for ${body}: the file functions read the headers the file"
                                                  + " consumers set"),
           expressionOut = @BodyType(value = Carrier.UNKNOWN,
                                     description = "A text with functions gives text, such as a file name. A single function"
                                                   + " gives its value as it is: ${file:size}, ${file:length} and"
                                                   + " ${file:modified} a Long, ${file:absolute} a Boolean, the other file"
                                                   + " functions text, and null without the headers of a file"))
public class FileLanguage extends SimpleLanguage {

    public FileLanguage() {
        // do not allow escaping, as Windows uses \ as path separator
        allowEscape = false;
    }

}
