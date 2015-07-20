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
package org.apache.camel.management;

import java.util.concurrent.ThreadPoolExecutor;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.DelegateProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.ErrorHandlerFactory;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.component.log.LogEndpoint;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.management.mbean.ManagedAggregateProcessor;
import org.apache.camel.management.mbean.ManagedBeanProcessor;
import org.apache.camel.management.mbean.ManagedBrowsableEndpoint;
import org.apache.camel.management.mbean.ManagedCamelContext;
import org.apache.camel.management.mbean.ManagedComponent;
import org.apache.camel.management.mbean.ManagedConsumer;
import org.apache.camel.management.mbean.ManagedDelayer;
import org.apache.camel.management.mbean.ManagedDynamicSendProcessor;
import org.apache.camel.management.mbean.ManagedEndpoint;
import org.apache.camel.management.mbean.ManagedErrorHandler;
import org.apache.camel.management.mbean.ManagedEventNotifier;
import org.apache.camel.management.mbean.ManagedIdempotentConsumer;
import org.apache.camel.management.mbean.ManagedProcessor;
import org.apache.camel.management.mbean.ManagedProducer;
import org.apache.camel.management.mbean.ManagedRoute;
import org.apache.camel.management.mbean.ManagedScheduledPollConsumer;
import org.apache.camel.management.mbean.ManagedSendProcessor;
import org.apache.camel.management.mbean.ManagedService;
import org.apache.camel.management.mbean.ManagedSuspendableRoute;
import org.apache.camel.management.mbean.ManagedThreadPool;
import org.apache.camel.management.mbean.ManagedThrottler;
import org.apache.camel.management.mbean.ManagedThroughputLogger;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.Delayer;
import org.apache.camel.processor.DynamicSendProcessor;
import org.apache.camel.processor.ErrorHandler;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.Throttler;
import org.apache.camel.processor.ThroughputLogger;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.processor.idempotent.IdempotentConsumer;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ManagementObjectStrategy;
import org.apache.camel.spi.RouteContext;

/**
 * Default {@link org.apache.camel.spi.ManagementObjectStrategy}.
 */
public class DefaultManagementObjectStrategy implements ManagementObjectStrategy {

    public Object getManagedObjectForCamelContext(CamelContext context) {
        ManagedCamelContext mc = new ManagedCamelContext((ModelCamelContext)context);
        mc.init(context.getManagementStrategy());
        return mc;
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    public Object getManagedObjectForComponent(CamelContext context, Component component, String name) {
        if (component instanceof org.apache.camel.spi.ManagementAware) {
            return ((org.apache.camel.spi.ManagementAware<Component>) component).getManagedObject(component);
        } else {
            ManagedComponent mc = new ManagedComponent(name, component);
            mc.init(context.getManagementStrategy());
            return mc;
        }
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    public Object getManagedObjectForEndpoint(CamelContext context, Endpoint endpoint) {
        // we only want to manage singleton endpoints
        if (!endpoint.isSingleton()) {
            return null;
        }

        if (endpoint instanceof org.apache.camel.spi.ManagementAware) {
            return ((org.apache.camel.spi.ManagementAware<Endpoint>) endpoint).getManagedObject(endpoint);
        } else if (endpoint instanceof BrowsableEndpoint) {
            ManagedBrowsableEndpoint me = new ManagedBrowsableEndpoint((BrowsableEndpoint) endpoint);
            me.init(context.getManagementStrategy());
            return me;
        } else {
            ManagedEndpoint me = new ManagedEndpoint(endpoint);
            me.init(context.getManagementStrategy());
            return me;
        }
    }

    public Object getManagedObjectForErrorHandler(CamelContext context, RouteContext routeContext,
                                                  Processor errorHandler, ErrorHandlerFactory errorHandlerBuilder) {
        ManagedErrorHandler me = new ManagedErrorHandler(routeContext, errorHandler, errorHandlerBuilder);
        me.init(context.getManagementStrategy());
        return me;
    }

    public Object getManagedObjectForRoute(CamelContext context, Route route) {
        ManagedRoute mr;
        if (route.supportsSuspension()) {
            mr = new ManagedSuspendableRoute((ModelCamelContext)context, route);
        } else {
            mr = new ManagedRoute((ModelCamelContext)context, route);
        }
        mr.init(context.getManagementStrategy());
        return mr;
    }

    public Object getManagedObjectForThreadPool(CamelContext context, ThreadPoolExecutor threadPool,
                                                String id, String sourceId, String routeId, String threadPoolProfileId) {
        ManagedThreadPool mtp = new ManagedThreadPool(context, threadPool, id, sourceId, routeId, threadPoolProfileId);
        mtp.init(context.getManagementStrategy());
        return mtp;
    }

    public Object getManagedObjectForEventNotifier(CamelContext context, EventNotifier eventNotifier) {
        ManagedEventNotifier men = new ManagedEventNotifier(context, eventNotifier);
        men.init(context.getManagementStrategy());
        return men;
    }

    public Object getManagedObjectForConsumer(CamelContext context, Consumer consumer) {
        ManagedConsumer mc;
        if (consumer instanceof ScheduledPollConsumer) {
            mc = new ManagedScheduledPollConsumer(context, (ScheduledPollConsumer) consumer);
        } else {
            mc = new ManagedConsumer(context, consumer);
        }
        mc.init(context.getManagementStrategy());
        return mc;
    }

    public Object getManagedObjectForProducer(CamelContext context, Producer producer) {
        ManagedProducer mp = new ManagedProducer(context, producer);
        mp.init(context.getManagementStrategy());
        return mp;
    }

    public Object getManagedObjectForService(CamelContext context, Service service) {
        ManagedService mc = new ManagedService(context, service);
        mc.init(context.getManagementStrategy());
        return mc;
    }

    @SuppressWarnings({"deprecation", "unchecked"})
    public Object getManagedObjectForProcessor(CamelContext context, Processor processor,
                                               ProcessorDefinition<?> definition, Route route) {
        ManagedProcessor answer = null;

        // unwrap delegates as we want the real target processor
        Processor target = processor;
        while (target != null) {

            // skip error handlers
            if (target instanceof ErrorHandler) {
                return false;
            }

            // look for specialized processor which we should prefer to use
            if (target instanceof Delayer) {
                answer = new ManagedDelayer(context, (Delayer) target, definition);
            } else if (target instanceof Throttler) {
                answer = new ManagedThrottler(context, (Throttler) target, definition);
            } else if (target instanceof DynamicSendProcessor) {
                answer = new ManagedDynamicSendProcessor(context, (DynamicSendProcessor) target, definition);
            } else if (target instanceof SendProcessor) {
                SendProcessor sp = (SendProcessor) target;
                // special for sending to throughput logger
                if (sp.getDestination() instanceof LogEndpoint) {
                    LogEndpoint le = (LogEndpoint) sp.getDestination();
                    if (le.getLogger() instanceof ThroughputLogger) {
                        ThroughputLogger tl = (ThroughputLogger) le.getLogger();
                        answer = new ManagedThroughputLogger(context, tl, definition);
                    }
                }
                // regular send processor
                if (answer == null) {
                    answer = new ManagedSendProcessor(context, (SendProcessor) target, definition);
                }
            } else if (target instanceof BeanProcessor) {
                answer = new ManagedBeanProcessor(context, (BeanProcessor) target, definition);
            } else if (target instanceof IdempotentConsumer) {
                answer = new ManagedIdempotentConsumer(context, (IdempotentConsumer) target, definition);
            } else if (target instanceof AggregateProcessor) {
                answer = new ManagedAggregateProcessor(context, (AggregateProcessor) target, (org.apache.camel.model.AggregateDefinition) definition);
            } else if (target instanceof org.apache.camel.spi.ManagementAware) {
                return ((org.apache.camel.spi.ManagementAware<Processor>) target).getManagedObject(processor);
            }

            if (answer != null) {
                // break out as we found an answer
                break;
            }

            // no answer yet, so unwrap any delegates and try again
            if (target instanceof DelegateProcessor) {
                target = ((DelegateProcessor) target).getProcessor();
            } else {
                // no delegate so we dont have any target to try next
                break;
            }
        }

        if (answer == null) {
            // fallback to a generic processor
            answer = new ManagedProcessor(context, target, definition);
        }

        answer.setRoute(route);
        answer.init(context.getManagementStrategy());
        return answer;
    }

}
