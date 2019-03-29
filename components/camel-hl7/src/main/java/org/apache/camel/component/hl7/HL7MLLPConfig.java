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
package org.apache.camel.component.hl7;

import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;

import ca.uhn.hl7v2.DefaultHapiContext;
import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.parser.Parser;

public class HL7MLLPConfig {

    private Charset charset = Charset.defaultCharset();

    private boolean convertLFtoCR;

    // HL7 MLLP start and end markers
    private char startByte = 0x0b; // 11 decimal

    private char endByte1 = 0x1c; // 28 decimal

    private char endByte2 = 0x0d; // 13 decimal

    private HapiContext hapiContext = new DefaultHapiContext();

    private Parser parser = hapiContext.getGenericParser();

    private boolean produceString = true;

    private CodingErrorAction malformedInputErrorAction = CodingErrorAction.REPORT;

    private CodingErrorAction unmappableCharacterErrorAction = CodingErrorAction.REPORT;

    public Charset getCharset() {
        return charset;
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
    }

    public boolean isConvertLFtoCR() {
        return convertLFtoCR;
    }

    public void setConvertLFtoCR(boolean convertLFtoCR) {
        this.convertLFtoCR = convertLFtoCR;
    }

    public char getStartByte() {
        return startByte;
    }

    public void setStartByte(char startByte) {
        this.startByte = startByte;
    }

    public char getEndByte1() {
        return endByte1;
    }

    public void setEndByte1(char endByte1) {
        this.endByte1 = endByte1;
    }

    public char getEndByte2() {
        return endByte2;
    }

    public void setEndByte2(char endByte2) {
        this.endByte2 = endByte2;
    }
    
    public Parser getParser() {
        return parser;
    }

    public void setParser(Parser parser) {
        this.parser = parser;
    }

    public HapiContext getHapiContext() {
        return hapiContext;
    }

    public void setHapiContext(HapiContext hapiContext) {
        this.hapiContext = hapiContext;
        this.parser = hapiContext.getPipeParser();
    }

    public boolean isValidate() {
        return parser.getParserConfiguration().isValidating();
    }

    public void setValidate(boolean validate) {
        parser.getParserConfiguration().setValidating(validate);
    }

    public boolean isProduceString() {
        return produceString;
    }

    public void setProduceString(boolean produceString) {
        this.produceString = produceString;
    }

    public CodingErrorAction getMalformedInputErrorAction() {
        return malformedInputErrorAction;
    }

    public void setMalformedInputErrorAction(CodingErrorAction malformedInputErrorAction) {
        this.malformedInputErrorAction = malformedInputErrorAction;
    }

    public CodingErrorAction getUnmappableCharacterErrorAction() {
        return unmappableCharacterErrorAction;
    }

    public void setUnmappableCharacterErrorAction(CodingErrorAction unmappableCharacterErrorAction) {
        this.unmappableCharacterErrorAction = unmappableCharacterErrorAction;
    }
}
