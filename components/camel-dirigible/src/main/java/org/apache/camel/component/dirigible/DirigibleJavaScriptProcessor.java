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
package org.apache.camel.component.dirigible;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.support.CamelContextHelper;

class DirigibleJavaScriptProcessor implements Processor {

    private final String javaScriptPath;

    DirigibleJavaScriptProcessor(String javaScriptPath) {
        this.javaScriptPath = javaScriptPath;
    }

    @Override
    public void process(Exchange exchange) {
        DirigibleJavaScriptInvoker invoker = getInvoker(exchange.getContext());
        Message message = exchange.getMessage();

        invoker.invoke(message, javaScriptPath);
    }

    private DirigibleJavaScriptInvoker getInvoker(CamelContext camelContext) {
        try {
            DirigibleJavaScriptInvoker invoker
                    = CamelContextHelper.findSingleByType(camelContext, DirigibleJavaScriptInvoker.class);
            if (invoker == null) {
                invoker = camelContext.getInjector()
                        .newInstance(DirigibleJavaScriptInvoker.class);
            }
            if (invoker == null) {
                throw new DirigibleJavaScriptException("Cannot get instance of " + DirigibleJavaScriptInvoker.class);
            }

            return invoker;
        } catch (RuntimeException ex) {
            throw new DirigibleJavaScriptException("Cannot get instance of " + DirigibleJavaScriptInvoker.class, ex);
        }
    }

}
