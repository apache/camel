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
package org.apache.camel.converter.myconverter;

import java.math.BigDecimal;

import org.apache.camel.Converter;
import org.apache.camel.TypeConverter;
import org.apache.camel.converter.PurchaseOrder;
import org.apache.camel.spi.TypeConverterAware;

/**
 * @version 
 */
@Converter
public class PurchaseOrderConverter implements TypeConverterAware {

    private TypeConverter converter;

    public void setTypeConverter(TypeConverter parentTypeConverter) {
        this.converter = parentTypeConverter;
    }

    @Converter
    public PurchaseOrder toPurchaseOrder(byte[] data) {
        String s = converter.convertTo(String.class, data);

        if (s == null || s.length() < 30) {
            throw new IllegalArgumentException("data is invalid");
        }

        s = s.replaceAll("##START##", "");
        s = s.replaceAll("##END##", "");

        String name = s.substring(0, 9).trim();
        String s2 = s.substring(10, 19).trim();
        String s3 = s.substring(20).trim();

        BigDecimal price = new BigDecimal(s2);
        price = price.setScale(2);

        Integer amount = converter.convertTo(Integer.class, s3);

        PurchaseOrder order = new PurchaseOrder(name, price, amount);
        return order;
    }

}
