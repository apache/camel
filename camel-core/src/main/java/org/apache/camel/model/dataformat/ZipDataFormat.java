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

package org.apache.camel.model.dataformat;


import java.util.zip.Deflater;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.RouteContext;

@XmlRootElement(name = "zip")
@XmlAccessorType(XmlAccessType.FIELD)
public class ZipDataFormat extends DataFormatType {

    @XmlAttribute(required = false)
    private int compressionLevel = Deflater.BEST_SPEED;
    
    public ZipDataFormat() {
    }

    public ZipDataFormat(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }

    @Override
    protected DataFormat createDataFormat(RouteContext routeContext) {
        return new org.apache.camel.impl.ZipDataFormat(compressionLevel);
    }

    public int getCompressionLevel() {
        return compressionLevel;
    }

    public void setCompressionLevel(int compressionLevel) {
        this.compressionLevel = compressionLevel;
    }
}
