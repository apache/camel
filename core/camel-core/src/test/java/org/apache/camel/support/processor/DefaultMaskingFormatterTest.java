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
package org.apache.camel.support.processor;

import org.junit.Assert;
import org.junit.Test;

public class DefaultMaskingFormatterTest {

    @Test
    public void testDefaultOption() throws Exception {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter();
        String answer = formatter.format("key=value, myPassword=foo,\n myPassphrase=\"foo bar\", secretKey='!@#$%^&*() -+[]{};:'");
        Assert.assertEquals("key=value, myPassword=\"xxxxx\",\n myPassphrase=\"xxxxx\", secretKey=\"xxxxx\"", answer);

        answer = formatter.format("<xmlPassword>\n foo bar \n</xmlPassword>\n<user password=\"asdf qwert\"/>");
        Assert.assertEquals("<xmlPassword>\n xxxxx \n</xmlPassword>\n<user password=\"xxxxx\"/>", answer);

        answer = formatter.format("{\"key\" : \"value\", \"My Password\":\"foo\", \"My SecretPassphrase\" : \"foo bar\", \"My SecretKey2\" : \"!@#$%^&*() -+[]{};:'\"}");
        Assert.assertEquals("{\"key\" : \"value\", \"My Password\":\"xxxxx\", \"My SecretPassphrase\" : \"xxxxx\", \"My SecretKey2\" : \"xxxxx\"}", answer);
    }

    @Test
    public void testDisableKeyValueMask() throws Exception {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter(false, true, true);
        String answer = formatter.format("key=value, myPassword=foo,\n myPassphrase=\"foo bar\", secretKey='!@#$%^&*() -+[]{};:'");
        Assert.assertEquals("key=value, myPassword=foo,\n myPassphrase=\"foo bar\", secretKey='!@#$%^&*() -+[]{};:'", answer);

        answer = formatter.format("<xmlPassword>\n foo bar \n</xmlPassword>\n<user password=\"asdf qwert\"/>");
        Assert.assertEquals("<xmlPassword>\n xxxxx \n</xmlPassword>\n<user password=\"asdf qwert\"/>", answer);

        answer = formatter.format("{\"key\" : \"value\", \"My Password\":\"foo\", \"My SecretPassphrase\" : \"foo bar\", \"My SecretKey2\" : \"!@#$%^&*() -+[]{};:'\"}");
        Assert.assertEquals("{\"key\" : \"value\", \"My Password\":\"xxxxx\", \"My SecretPassphrase\" : \"xxxxx\", \"My SecretKey2\" : \"xxxxx\"}", answer);
    }

    @Test
    public void testDisableXmlElementMask() throws Exception {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter(true, false, true);
        String answer = formatter.format("key=value, myPassword=foo,\n myPassphrase=\"foo bar\", secretKey='!@#$%^&*() -+[]{};:'");
        Assert.assertEquals("key=value, myPassword=\"xxxxx\",\n myPassphrase=\"xxxxx\", secretKey=\"xxxxx\"", answer);

        answer = formatter.format("<xmlPassword>\n foo bar \n</xmlPassword>\n<user password=\"asdf qwert\"/>");
        Assert.assertEquals("<xmlPassword>\n foo bar \n</xmlPassword>\n<user password=\"xxxxx\"/>", answer);

        answer = formatter.format("{\"key\" : \"value\", \"My Password\":\"foo\", \"My SecretPassphrase\" : \"foo bar\", \"My SecretKey2\" : \"!@#$%^&*() -+[]{};:'\"}");
        Assert.assertEquals("{\"key\" : \"value\", \"My Password\":\"xxxxx\", \"My SecretPassphrase\" : \"xxxxx\", \"My SecretKey2\" : \"xxxxx\"}", answer);
    }

    @Test
    public void testDisableJsonMask() throws Exception {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter(true, true, false);
        String answer = formatter.format("key=value, myPassword=foo,\n myPassphrase=\"foo　bar\", secretKey='!@#$%^&*() -+[]{};:'");
        Assert.assertEquals("key=value, myPassword=\"xxxxx\",\n myPassphrase=\"xxxxx\", secretKey=\"xxxxx\"", answer);

        answer = formatter.format("<xmlPassword>\n foo bar \n</xmlPassword>\n<user password=\"asdf qwert\"/>");
        Assert.assertEquals("<xmlPassword>\n xxxxx \n</xmlPassword>\n<user password=\"xxxxx\"/>", answer);

        answer = formatter.format("{\"key\" : \"value\", \"My Password\":\"foo\", \"My SecretPassphrase\" : \"foo bar\", \"My SecretKey2\" : \"!@#$%^&*() -+[]{};:'\"}");
        Assert.assertEquals("{\"key\" : \"value\", \"My Password\":\"foo\", \"My SecretPassphrase\" : \"foo bar\", \"My SecretKey2\" : \"!@#$%^&*() -+[]{};:'\"}", answer);
    }

    @Test
    public void testCustomMaskString() throws Exception {
        DefaultMaskingFormatter formatter = new DefaultMaskingFormatter();
        formatter.setMaskString("**********");
        String answer = formatter.format("key=value, myPassword=foo,\n myPassphrase=\"foo　bar\", secretKey='!@#$%^&*() -+[]{};:'");
        Assert.assertEquals("key=value, myPassword=\"**********\",\n myPassphrase=\"**********\", secretKey=\"**********\"", answer);

        answer = formatter.format("<xmlPassword>\n foo bar \n</xmlPassword>\n<user password=\"asdf qwert\"/>");
        Assert.assertEquals("<xmlPassword>\n ********** \n</xmlPassword>\n<user password=\"**********\"/>", answer);

        answer = formatter.format("{\"key\" : \"value\", \"My Password\":\"foo\", \"My SecretPassphrase\" : \"foo bar\", \"My SecretKey2\" : \"!@#$%^&*() -+[]{};:'\"}");
        Assert.assertEquals("{\"key\" : \"value\", \"My Password\":\"**********\", \"My SecretPassphrase\" : \"**********\", \"My SecretKey2\" : \"**********\"}", answer);
    }

}
