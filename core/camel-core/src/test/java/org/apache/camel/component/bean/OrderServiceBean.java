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
package org.apache.camel.component.bean;

import org.w3c.dom.Document;

import org.apache.camel.TypeConverter;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.util.FileUtil;

public class OrderServiceBean {

    private TypeConverter converter;

    public void setConverter(TypeConverter converter) {
        this.converter = converter;
    }

    public String handleCustom(GenericFile<?> file) {
        String content = converter.convertTo(String.class, file.getBody());
        String orderId = FileUtil.stripExt(file.getFileNameOnly());

        StringBuilder sb = new StringBuilder();
        sb.append(orderId);
        sb.append(",");
        sb.append(content);

        return sb.toString();
    }

    public String handleXML(Document doc) {
        if (doc == null) {
            return null;
        }

        converter.convertTo(String.class, doc);

        Integer orderId = 77889;
        Integer customerId = 667;
        Integer confirmId = 457;

        StringBuilder sb = new StringBuilder();
        sb.append(orderId);
        sb.append(",");
        sb.append(customerId);
        sb.append(",");
        sb.append(confirmId);

        return sb.toString();
    }

}
