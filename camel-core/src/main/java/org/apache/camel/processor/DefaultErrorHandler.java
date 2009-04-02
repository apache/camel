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
import org.apache.camel.Processor;
import org.apache.camel.impl.converter.AsyncProcessorTypeConverter;
import org.apache.camel.util.ServiceHelper;

/**
 * Default error handler
 *
 * @version $Revision$
 */
public class DefaultErrorHandler extends ErrorHandlerSupport implements AsyncProcessor {

    private Processor output;
    private AsyncProcessor outputAsync;

    public DefaultErrorHandler(Processor output) {
        this.output = output;
        this.outputAsync = AsyncProcessorTypeConverter.convert(output);
    }

    @Override
    public String toString() {
        return "DefaultErrorHandler[" + output + "]";
    }

    public void process(Exchange exchange) throws Exception {
        output.process(exchange);
    }

    public boolean process(Exchange exchange, final AsyncCallback callback) {
        return outputAsync.process(exchange, new AsyncCallback() {
            public void done(boolean sync) {
                callback.done(sync);
            }
        });
    }

    /**
     * Returns the output processor
     */
    public Processor getOutput() {
        return output;
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(output);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(output);
    }

}
