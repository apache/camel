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
package org.apache.camel.processor.resequencer;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;

//START SNIPPET: example
public class MyFileNameExpression implements Expression {

    public String getFileName(Exchange exchange) {
        return exchange.getIn().getBody(String.class);
    }

    public Object evaluate(Exchange exchange) {
        // parser the file name with YYYYMMDD-DNNN pattern
        String fileName = getFileName(exchange);
        String[] files = fileName.split("-D");
        Long answer = Long.parseLong(files[0]) * 1000 + Long.parseLong(files[1]);
        return answer;
    }

    @Override
    public <T> T evaluate(Exchange exchange, Class<T> type) {
        Object result = evaluate(exchange);
        return exchange.getContext().getTypeConverter().convertTo(type, result);
    }

}
//END SNIPPET: example
