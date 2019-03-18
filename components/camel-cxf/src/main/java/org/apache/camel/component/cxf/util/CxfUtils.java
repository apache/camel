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
package org.apache.camel.component.cxf.util;

import java.io.InputStream;

import org.apache.camel.support.UnitOfWorkHelper;
import org.apache.cxf.helpers.IOUtils;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;

public final class CxfUtils {
    
    private CxfUtils() {
        // helper class
    }
    
    public static String getStringFromInputStream(InputStream in) throws Exception {
        CachedOutputStream bos = new CachedOutputStream();
        IOUtils.copy(in, bos);
        in.close();
        bos.close();
        return bos.getOut().toString();
    }
    
    public static void closeCamelUnitOfWork(Message message) {
        Exchange cxfExchange;
        if ((cxfExchange = message.getExchange()) != null) {
            org.apache.camel.Exchange exchange = cxfExchange.get(org.apache.camel.Exchange.class);
            if (exchange != null) {
                UnitOfWorkHelper.doneUow(exchange.getUnitOfWork(), exchange);
            }
        }
    }
}
