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
package org.apache.camel.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UnsafeCharactersEncoderTest {

    private void testEncoding(String before, String after) {
        String result = UnsafeUriCharactersEncoder.encode(before);
        assertEquals(after, result, "Get the wrong encoding result");
    }

    @Test
    public void testQnameEncoder() {
        String afterEncoding = "%7Bhttp://www.example.com/test%7DServiceName";
        String beforeEncoding = "{http://www.example.com/test}ServiceName";
        testEncoding(beforeEncoding, afterEncoding);
    }

    @Test
    public void testNoEncoding() {
        String noEncoding = "http://www.example.com";
        testEncoding(noEncoding, noEncoding);
    }

    @Test
    public void testUnicodes() {
        String noEncoding = "http://test.com/\uFD04";
        testEncoding(noEncoding, noEncoding);
    }

    @Test
    public void testPercentEncode() {
        String beforeEncoding = "sql:select * from foo where bar like '%A'";
        String afterEncoding = "sql:select%20*%20from%20foo%20where%20bar%20like%20'%25A'";
        testEncoding(beforeEncoding, afterEncoding);
    }

    @Test
    public void testPercentEncodeAlready() {
        String beforeEncoding = "sql:select * from foo where bar like '%25A'";
        String afterEncoding = "sql:select%20*%20from%20foo%20where%20bar%20like%20'%25A'";
        testEncoding(beforeEncoding, afterEncoding);
    }

    @Test
    public void testPercentEncodeDanishChar() {
        String beforeEncoding = "http://localhost:{{port}}/myapp/mytest?columns=claus,s\u00F8ren&username=apiuser";
        String afterEncoding = "http://localhost:%7B%7Bport%7D%7D/myapp/mytest?columns=claus,s\u00F8ren&username=apiuser";
        testEncoding(beforeEncoding, afterEncoding);
    }

    @Test
    public void testPercentEncodeDanishCharEncoded() {
        String beforeEncoding = "http://localhost:{{port}}/myapp/mytest?columns=claus,s%C3%B8ren&username=apiuser";
        String afterEncoding = "http://localhost:%7B%7Bport%7D%7D/myapp/mytest?columns=claus,s%C3%B8ren&username=apiuser";
        testEncoding(beforeEncoding, afterEncoding);
    }

    @Test
    public void testAlreadyEncoded() {
        String beforeEncoding = "http://www.example.com?query=foo%20bar";
        String afterEncoding = "http://www.example.com?query=foo%20bar";
        testEncoding(beforeEncoding, afterEncoding);
    }

    @Test
    public void testPercentEncodedLast() {
        String beforeEncoding = "http://www.example.com?like=foo%25";
        String afterEncoding = "http://www.example.com?like=foo%25";
        testEncoding(beforeEncoding, afterEncoding);
    }

    @Test
    public void testPlusInQuery() {
        String beforeEncoding = "http://www.example.com?param1=%2B447777111222";
        String afterEncoding = "http://www.example.com?param1=%2B447777111222";
        testEncoding(beforeEncoding, afterEncoding);

        beforeEncoding = "http://www.example.com?param1=+447777111222";
        afterEncoding = "http://www.example.com?param1=+447777111222";
        testEncoding(beforeEncoding, afterEncoding);
    }

    @Test
    public void testPasswordEncodingInRawMode() {
        String password = "RAW(%j#7%c6i)";
        String result = UnsafeUriCharactersEncoder.encode(password, true);
        // remove RAW
        result = result.substring(4);
        result = result.substring(0, result.length() - 1);
        String expected = "%25j%237%25c6i";

        assertEquals(result, expected, "Get the wrong encoding result");
    }

}
