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
package org.apache.camel.processor;

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;

public class ExchangePatternProcessor implements AsyncProcessor {
    private ExchangePattern exchangePattern = ExchangePattern.InOnly;
    
    public ExchangePatternProcessor() {
        
    }
    
    public ExchangePatternProcessor(ExchangePattern ep) {
        setExchangePattern(ep);
    }
    
    public void setExchangePattern(ExchangePattern ep) {
        exchangePattern = ep;
    }
   
    public void process(Exchange exchange) throws Exception {
        exchange.setPattern(exchangePattern);        
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        exchange.setPattern(exchangePattern);
        callback.done(true);
        return true;
    }

}
