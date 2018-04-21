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
package org.apache.camel.component.bar;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.ExpressionBuilder;
import org.apache.camel.processor.RemoveHeaderProcessor;
import org.apache.camel.processor.SetHeaderProcessor;
import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.URISupport;

public class BarSendDynamicAware implements SendDynamicAware {

    private String scheme;

    @Override
    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    @Override
    public String getScheme() {
        return scheme;
    }

    @Override
    public Processor createPreProcessor(Exchange exchange, Object recipient) throws Exception {
        // after the ?
        String uri = recipient.toString();
        uri = StringHelper.after(uri, "?");
        if (uri != null) {
            Map<String, Object> map = URISupport.parseQuery(uri);
            if (map.containsKey("drink")) {
                Object value = map.get("drink");
                return new SetHeaderProcessor(ExpressionBuilder.constantExpression(BarConstants.DRINK),
                    ExpressionBuilder.constantExpression(value));
            }
        }

        return null;
    }

    @Override
    public Processor createPostProcessor(Exchange exchange, Object recipient) throws Exception {
        // remove header after use
        return new RemoveHeaderProcessor(BarConstants.DRINK);
    }

    @Override
    public String resolveStaticUri(Exchange exchange, Object recipient) throws Exception {
        // before the ?
        String uri = recipient.toString();
        return StringHelper.before(uri, "?");
    }
}
