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
package org.apache.camel.component.raspberrypi;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.component.raspberrypi.RaspberryPiConstants;
import org.junit.Assert;
import org.junit.Test;

public class URLRegex {

    Pattern p = Pattern.compile(RaspberryPiConstants.CAMEL_RBPI_URL_PATTERN);

    @Test
    public void simpleTest() {

        String url = "pin:1";

        Matcher m = p.matcher(url);
        Assert.assertTrue(m.matches());
        Assert.assertEquals("pin", m.group("type"));
        Assert.assertEquals("1", m.group("id"));

    }

    @Test
    public void schemeTest() {

        String url = "raspberrypi://pin:1";
        Matcher m = p.matcher(url);
        Assert.assertTrue(m.matches());
        Assert.assertEquals("pin", m.group("type"));
        Assert.assertEquals("1", m.group("id"));
        Assert.assertEquals("raspberrypi", m.group("scheme"));
    }
    
    @Test
    public void schemeWOSlashTest() {

        String url = "raspberrypi:pin:1";
        Matcher m = p.matcher(url);
        Assert.assertTrue(m.matches());
        Assert.assertEquals("pin", m.group("type"));
        Assert.assertEquals("1", m.group("id"));
        Assert.assertEquals("raspberrypi", m.group("scheme"));
    }

    @Test
    public void simpleTestI2C() {

        String url = "i2c:aze";
        Matcher m = p.matcher(url);
        Assert.assertTrue(m.matches());
        Assert.assertEquals("i2c", m.group("type"));
        Assert.assertEquals("aze", m.group("id"));

    }
    
    @Test
    public void simpleTestRestStyle() {

        String url = "i2c/aze";
        Matcher m = p.matcher(url);
        Assert.assertTrue(m.matches());
        Assert.assertEquals("i2c", m.group("type"));
        Assert.assertEquals("aze", m.group("id"));

    }

}
