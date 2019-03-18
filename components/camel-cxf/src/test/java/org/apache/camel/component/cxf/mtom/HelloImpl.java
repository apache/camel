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
package org.apache.camel.component.cxf.mtom;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.xml.ws.Holder;

import org.apache.camel.cxf.mtom_feature.Hello;
import org.junit.Assert;

/**
 * Hello Test Impl class
 */
public class HelloImpl implements Hello {

    public void detail(Holder<byte[]> photo, Holder<Image> image) {
        
        MtomTestHelper.assertEquals(MtomTestHelper.REQ_PHOTO_DATA,  photo.value);      
        Assert.assertNotNull(image.value);
        if (image.value instanceof BufferedImage) {
            Assert.assertEquals(41, ((BufferedImage)image.value).getWidth());
            Assert.assertEquals(39, ((BufferedImage)image.value).getHeight());            
        }

        try {
            image.value = ImageIO.read(getClass().getResource("/Splash.jpg"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        photo.value = MtomTestHelper.RESP_PHOTO_DATA;

    }

    public void echoData(Holder<byte[]> data) {
        throw new UnsupportedOperationException();
    }

}
