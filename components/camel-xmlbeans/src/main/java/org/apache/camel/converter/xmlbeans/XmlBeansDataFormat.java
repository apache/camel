/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.converter.xmlbeans;

import java.io.InputStream;
import java.io.OutputStream;

import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.util.ExchangeHelper;
import org.apache.xmlbeans.XmlObject;

/**
 * A <a href="http://activemq.apache.org/camel/data-format.html">data format</a>
 * ({@link DataFormat}) using XmlBeans to marshal to and from XML
 *
 * @version $Revision: 1.1 $
 */
public class XmlBeansDataFormat implements DataFormat{

    public void marshal(Exchange exchange, Object body, OutputStream stream) throws Exception {
        XmlObject object = ExchangeHelper.convertToMandatoryType(exchange, XmlObject.class, body);
        object.save(stream);
    }

    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        return XmlObject.Factory.parse(stream);
    }
}
