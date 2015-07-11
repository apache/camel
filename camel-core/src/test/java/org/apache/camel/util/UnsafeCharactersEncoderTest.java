/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.util;

import junit.framework.TestCase;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

public class UnsafeCharactersEncoderTest extends TestCase {

    private void testEncoding(String before, String after) {
        String result = UnsafeUriCharactersEncoder.encode(before);
        assertEquals("Get the wrong encoding result", after, result);
    }

    public void testQnameEncoder() {
        String afterEncoding = "%7Bhttp://www.example.com/test%7DServiceName";
        String beforeEncoding = "{http://www.example.com/test}ServiceName";
        testEncoding(beforeEncoding, afterEncoding);
    }

    public void testNoEncoding() {
        String noEncoding = "http://www.example.com";
        testEncoding(noEncoding, noEncoding);
    }

    public void testUnicodes() {
        String before = "http://test.com/\uFD04";
        String after = "http://test.com/%EF%B4%84";
        testEncoding(before, after);
    }

    public void testPercentEncode() {
        String beforeEncoding = "sql:select * from foo where bar like '%A'";
        String afterEncoding = "sql:select%20*%20from%20foo%20where%20bar%20like%20'%25A'";
        testEncoding(beforeEncoding, afterEncoding);
    }

    public void testPercentEncodeAlready() {
        String beforeEncoding = "sql:select * from foo where bar like '%25A'";
        String afterEncoding = "sql:select%20*%20from%20foo%20where%20bar%20like%20'%25A'";
        testEncoding(beforeEncoding, afterEncoding);
    }

    public void testPercentEncodeDanishChar() {
        String beforeEncoding = "http://localhost:{{port}}/myapp/mytest?columns=claus,s\u00F8ren&username=apiuser";
        String afterEncoding = "http://localhost:%7B%7Bport%7D%7D/myapp/mytest?columns=claus,s%C3%B8ren&username=apiuser";
        testEncoding(beforeEncoding, afterEncoding);
    }

    public void testPercentEncodeDanishCharEncoded() {
        String beforeEncoding = "http://localhost:{{port}}/myapp/mytest?columns=claus,s%C3%B8ren&username=apiuser";
        String afterEncoding = "http://localhost:%7B%7Bport%7D%7D/myapp/mytest?columns=claus,s%C3%B8ren&username=apiuser";
        testEncoding(beforeEncoding, afterEncoding);
    }

    public void testAlreadyEncoded() {
        String beforeEncoding = "http://www.example.com?query=foo%20bar";
        String afterEncoding = "http://www.example.com?query=foo%20bar";
        testEncoding(beforeEncoding, afterEncoding);
    }

    public void testPercentEncodedLast() {
        String beforeEncoding = "http://www.example.com?like=foo%25";
        String afterEncoding = "http://www.example.com?like=foo%25";
        testEncoding(beforeEncoding, afterEncoding);
    }

    public void testPercentEncodingUtf8() throws UnsupportedEncodingException {
        /**
         "When a new URI scheme defines a component that represents textual
         data consisting of characters from the Universal Character Set [UCS],
         the data should first be encoded as octets according to the UTF-8
         character encoding [STD63]; then only those octets that do not
         correspond to characters in the unreserved set should be percent-
         encoded.  For example, the character A would be represented as "A",
         the character LATIN CAPITAL LETTER A WITH GRAVE would be represented
         as "%C3%80", and the character KATAKANA LETTER A would be represented
         as "%E3%82%A2"."

         src: https://tools.ietf.org/html/rfc3986
         */
        testEncodeHttpUri("q= ", "q=%20");
        testEncodeHttpUri("q=€", "q=%E2%82%AC");
        testEncodeHttpUri("q=\u00C0", "q=%C3%80");
        testEncodeHttpUri("q=\u30C4", "q=%E3%83%84");
        testEncodeHttpUri("q=\u0080", "q=%C2%80");
        testEncodeHttpUri("q=\u0081", "q=%C2%81");
    }

    private void testEncodeHttpUri(String before, String after) {
        assertEquals("Got wrong encoding result", after, UnsafeUriCharactersEncoder.encodeHttpURI(before));
    }
}
