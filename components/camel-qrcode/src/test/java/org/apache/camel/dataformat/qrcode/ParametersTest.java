/*
 * Copyright 2014 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.dataformat.qrcode;

import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 * Test class for {@link Parameters}.
 *
 * @author claus.straube
 */
public class ParametersTest {

    public ParametersTest() {
    }

    private final static ImageType TYPE = ImageType.JPG;
    private final static int WIDTH = 200;
    private final static int HEIGHT = 210;
    private final static String ENCODING = "ISO-8859-1";

    @Test
    public void testSingleValueConstructor() {
        Parameters p = new Parameters(TYPE, WIDTH, HEIGHT, ENCODING);
        assertParameters(p, true, true, true, true);
    }

    @Test
    public void testParameterConstructorWithType() {
        Parameters p = new Parameters(this.createHeaders(true, false, false, false), this.createDefaultParameters());
        assertParameters(p, true, false, false, false);
    }

    @Test
    public void testParameterConstructorWithWidth() {
        Parameters p = new Parameters(this.createHeaders(false, true, false, false), this.createDefaultParameters());
        assertParameters(p, false, true, false, false);
    }

    @Test
    public void testParameterConstructorWithHeight() {
        Parameters p = new Parameters(this.createHeaders(false, false, true, false), this.createDefaultParameters());
        assertParameters(p, false, false, true, false);
    }
    
    @Test
    public void testParameterConstructorWithEncoding() {
        Parameters p = new Parameters(this.createHeaders(false, false, false, true), this.createDefaultParameters());
        assertParameters(p, false, false, false, true);
    }
    
    @Test
    public void testParameterConstructorWithAll() {
        Parameters p = new Parameters(this.createHeaders(true, true, true, true), this.createDefaultParameters());
        assertParameters(p, true, true, true, true);
    }
    
    private void assertParameters(Parameters p, boolean type, boolean width, boolean height, boolean encoding) {

        if (type) {
            assertEquals(TYPE, p.getType());
        } else {
            assertEquals(ImageType.PNG, p.getType());
        }

        if (width) {
            assertTrue(p.getWidth() == WIDTH);
        } else {
            assertTrue(p.getWidth() == 100);
        }

        if (height) {
            assertTrue(p.getHeight() == HEIGHT);
        } else {
            assertTrue(p.getHeight() == 100);
        }

        if (encoding) {
            assertEquals(ENCODING, p.getEncoding());
        } else {
            assertEquals("UTF-8", p.getEncoding());
        }

    }

    private Parameters createDefaultParameters() {
        return new Parameters(ImageType.PNG, 100, 100, "UTF-8");
    }

    private Map<String, Object> createHeaders(boolean type, boolean width, boolean height, boolean encoding) {
        Map<String, Object> headers = new HashMap<String, Object>();

        if (type) {
            headers.put(QRCode.IMAGE_TYPE, TYPE);
        }

        if (width) {
            headers.put(QRCode.WIDTH, WIDTH);
        }

        if (height) {
            headers.put(QRCode.HEIGHT, HEIGHT);
        }

        if (encoding) {
            headers.put(QRCode.ENCODING, ENCODING);
        }

        return headers;
    }

}
