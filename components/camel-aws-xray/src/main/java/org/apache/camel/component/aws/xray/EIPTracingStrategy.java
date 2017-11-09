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
package org.apache.camel.component.aws.xray;

import java.lang.invoke.MethodHandles;
import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Subsegment;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.DelegateAsyncProcessor;
import org.apache.camel.spi.InterceptStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.aws.xray.XRayTracer.sanitizeName;

public class EIPTracingStrategy implements InterceptStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public Processor wrapProcessorInInterceptors(CamelContext camelContext,
        ProcessorDefinition<?> processorDefinition, Processor target, Processor nextTarget)
        throws Exception {

        String defName = processorDefinition.getShortName();

        return new DelegateAsyncProcessor((Exchange exchange) -> {
            LOG.trace("Creating new subsegment for {} - EIP {}", defName, target);
            Subsegment subsegment = AWSXRay.beginSubsegment(sanitizeName(defName));
            try {
                LOG.trace("Processing EIP {}", target);
                target.process(exchange);
            } catch (Exception ex) {
                LOG.trace("Handling exception thrown by invoked EIP {}", target);
                subsegment.addException(ex);
                throw ex;
            } finally {
                LOG.trace("Closing down subsegment for {}", defName);
                subsegment.close();
            }
        });
    }
}