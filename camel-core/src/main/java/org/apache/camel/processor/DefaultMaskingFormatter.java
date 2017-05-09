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
package org.apache.camel.processor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.camel.spi.MaskingFormatter;

/**
 * The {@link MaskingFormatter} that searches the specified keywards in the source
 * and replace its value with mask string. By default passphrase, password and secretKey
 * are used as keywards to replace its value.
 */
public class DefaultMaskingFormatter implements MaskingFormatter {

    private static final Set<String> DEFAULT_KEYWORDS = new HashSet<String>(Arrays.asList("passphrase", "password", "secretKey"));
    private Set<String> keywords;
    private boolean maskKeyValue;
    private boolean maskXmlElement;
    private boolean maskJson;
    private String maskString = "xxxxx";
    private Pattern keyValueMaskPattern;
    private Pattern xmlElementMaskPattern;
    private Pattern jsonMaskPattern;

    public DefaultMaskingFormatter() {
        this(DEFAULT_KEYWORDS, true, true, true);
    }

    public DefaultMaskingFormatter(boolean maskKeyValue, boolean maskXml, boolean maskJson) {
        this(DEFAULT_KEYWORDS, maskKeyValue, maskXml, maskJson);
    }

    public DefaultMaskingFormatter(Set<String> keywords, boolean maskKeyValue, boolean maskXmlElement, boolean maskJson) {
        this.keywords = keywords;
        setMaskKeyValue(maskKeyValue);
        setMaskXmlElement(maskXmlElement);
        setMaskJson(maskJson);
    }

    public String format(String source) {
        if (keywords == null || keywords.isEmpty()) {
            return source;
        }

        String answer = source;
        if (maskKeyValue) {
            answer = keyValueMaskPattern.matcher(answer).replaceAll("$1\"" + maskString + "\"");
        }
        if (maskXmlElement) {
            answer = xmlElementMaskPattern.matcher(answer).replaceAll("$1" + maskString + "$3");
        }
        if (maskJson) {
            answer = jsonMaskPattern.matcher(answer).replaceAll("$1\"" + maskString + "\"");
        }
        return answer;
    }

    public boolean isMaskKeyValue() {
        return maskKeyValue;
    }

    public void setMaskKeyValue(boolean maskKeyValue) {
        this.maskKeyValue = maskKeyValue;
        if (maskKeyValue) {
            keyValueMaskPattern = createKeyValueMaskPattern(keywords);
        } else {
            keyValueMaskPattern = null;
        }
    }

    public boolean isMaskXmlElement() {
        return maskXmlElement;
    }

    public void setMaskXmlElement(boolean maskXml) {
        this.maskXmlElement = maskXml;
        if (maskXml) {
            xmlElementMaskPattern = createXmlElementMaskPattern(keywords);
        } else {
            xmlElementMaskPattern = null;
        }
    }

    public boolean isMaskJson() {
        return maskJson;
    }

    public void setMaskJson(boolean maskJson) {
        this.maskJson = maskJson;
        if (maskJson) {
            jsonMaskPattern = createJsonMaskPattern(keywords);
        } else {
            jsonMaskPattern = null;
        }
    }

    public String getMaskString() {
        return maskString;
    }

    public void setMaskString(String maskString) {
        this.maskString = maskString;
    }

    protected Pattern createKeyValueMaskPattern(Set<String> keywords) {
        StringBuilder regex = createOneOfThemRegex(keywords);
        if (regex == null) {
            return null;
        }
        regex.insert(0, "([\\w]*(?:");
        regex.append(")[\\w]*[\\s]*?=[\\s]*?)([\\S&&[^'\",\\}\\]\\)]]+[\\S&&[^,\\}\\]\\)>]]*?|\"[^\"]*?\"|'[^']*?')");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    protected Pattern createXmlElementMaskPattern(Set<String> keywords) {
        StringBuilder regex = createOneOfThemRegex(keywords);
        if (regex == null) {
            return null;
        }
        regex.insert(0, "(<([\\w]*(?:");
        regex.append(")[\\w]*)(?:[\\s]+.+)*?>[\\s]*?)(?:[\\S&&[^<]]+(?:\\s+[\\S&&[^<]]+)*?)([\\s]*?</\\2>)");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    protected Pattern createJsonMaskPattern(Set<String> keywords) {
        StringBuilder regex = createOneOfThemRegex(keywords);
        if (regex == null) {
            return null;
        }
        regex.insert(0, "(\"(?:[^\"]|(?:\\\"))*?(?:");
        regex.append(")(?:[^\"]|(?:\\\"))*?\"\\s*?\\:\\s*?)(?:\"(?:[^\"]|(?:\\\"))*?\")");
        return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    }

    protected StringBuilder createOneOfThemRegex(Set<String> keywords) {
        StringBuilder regex = new StringBuilder();
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }
        String[] strKeywords = keywords.toArray(new String[0]);
        regex.append(Pattern.quote(strKeywords[0]));
        if (strKeywords.length > 1) {
            for (int i = 1; i < strKeywords.length; i++) {
                regex.append('|');
                regex.append(Pattern.quote(strKeywords[i]));
            }
        }
        return regex;
    }
}
