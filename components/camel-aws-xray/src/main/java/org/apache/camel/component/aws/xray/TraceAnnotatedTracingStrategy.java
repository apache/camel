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
package org.apache.camel.component.aws.xray;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Subsegment;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.ProcessDefinition;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.support.processor.DelegateSyncProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.aws.xray.XRayTracer.sanitizeName;

public class TraceAnnotatedTracingStrategy implements InterceptStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    @Override
    public Processor wrapProcessorInInterceptors(CamelContext camelContext,
                                                 NamedNode processorDefinition,
                                                 Processor target, Processor nextTarget)
        throws Exception {

        Class<?> processorClass = processorDefinition.getClass();
        String shortName = processorDefinition.getShortName();

        if (processorDefinition instanceof BeanDefinition) {
            BeanProcessor beanProcessor = (BeanProcessor) target;
            if (null != beanProcessor && null != beanProcessor.getBean()) {
                processorClass = beanProcessor.getBean().getClass();
            }
        } else if (processorDefinition instanceof ProcessDefinition) {
            DelegateSyncProcessor syncProcessor = (DelegateSyncProcessor) target;
            if (null != syncProcessor && null != syncProcessor.getProcessor()) {
                processorClass = syncProcessor.getProcessor().getClass();
            }
        }

        if (processorClass == null) {
            LOG.trace("Could not identify processor class on target processor {}", target);
            return new DelegateAsyncProcessor(target);
        } else if (!processorClass.isAnnotationPresent(XRayTrace.class)) {
            LOG.trace("{} does not contain an @XRayTrace annotation. Skipping interception",
                    processorClass.getSimpleName());
            return new DelegateAsyncProcessor(target);
        }

        LOG.trace("Wrapping process definition {} of target {} in order for recording its trace",
            processorDefinition, processorClass);

        Annotation annotation = processorClass.getAnnotation(XRayTrace.class);
        XRayTrace trace = (XRayTrace)annotation;

        String metricName = trace.metricName();

        if ("".equals(metricName)) {
            metricName = processorClass.getSimpleName();
        }

        final Class<?> type = processorClass;
        final String name = shortName + ":" + metricName;

        return new DelegateAsyncProcessor((Exchange exchange) -> {
            LOG.trace("Creating new subsegment for {} of type {} - EIP {}", name, type, target);
            Subsegment subsegment = AWSXRay.beginSubsegment(sanitizeName(name));
            try {
                LOG.trace("Processing EIP {}", target);
                target.process(exchange);
            } catch (Exception ex) {
                LOG.trace("Handling exception thrown by invoked EIP {}", target);
                subsegment.addException(ex);
                throw ex;
            } finally {
                LOG.trace("Closing down subsegment for {}", name);
                subsegment.close();
            }
        });
    }
}
