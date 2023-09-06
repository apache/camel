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
package org.apache.camel.management;

import java.util.Iterator;
import java.util.concurrent.ThreadPoolExecutor;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.DelegateProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Route;
import org.apache.camel.Service;
import org.apache.camel.cluster.CamelClusterService;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.component.log.LogEndpoint;
import org.apache.camel.health.HealthCheckRegistry;
import org.apache.camel.management.mbean.ManagedAggregateProcessor;
import org.apache.camel.management.mbean.ManagedBeanProcessor;
import org.apache.camel.management.mbean.ManagedBrowsableEndpoint;
import org.apache.camel.management.mbean.ManagedCamelContext;
import org.apache.camel.management.mbean.ManagedCamelHealth;
import org.apache.camel.management.mbean.ManagedChoice;
import org.apache.camel.management.mbean.ManagedClaimCheck;
import org.apache.camel.management.mbean.ManagedClusterService;
import org.apache.camel.management.mbean.ManagedComponent;
import org.apache.camel.management.mbean.ManagedConsumer;
import org.apache.camel.management.mbean.ManagedConvertBody;
import org.apache.camel.management.mbean.ManagedCustomLoadBalancer;
import org.apache.camel.management.mbean.ManagedDataFormat;
import org.apache.camel.management.mbean.ManagedDelayer;
import org.apache.camel.management.mbean.ManagedDisabled;
import org.apache.camel.management.mbean.ManagedDoCatch;
import org.apache.camel.management.mbean.ManagedDoFinally;
import org.apache.camel.management.mbean.ManagedDoTry;
import org.apache.camel.management.mbean.ManagedDynamicRouter;
import org.apache.camel.management.mbean.ManagedEndpoint;
import org.apache.camel.management.mbean.ManagedEnricher;
import org.apache.camel.management.mbean.ManagedEventNotifier;
import org.apache.camel.management.mbean.ManagedFailoverLoadBalancer;
import org.apache.camel.management.mbean.ManagedFilter;
import org.apache.camel.management.mbean.ManagedIdempotentConsumer;
import org.apache.camel.management.mbean.ManagedLog;
import org.apache.camel.management.mbean.ManagedLoop;
import org.apache.camel.management.mbean.ManagedMarshal;
import org.apache.camel.management.mbean.ManagedMulticast;
import org.apache.camel.management.mbean.ManagedPollEnricher;
import org.apache.camel.management.mbean.ManagedProcess;
import org.apache.camel.management.mbean.ManagedProcessor;
import org.apache.camel.management.mbean.ManagedProducer;
import org.apache.camel.management.mbean.ManagedRandomLoadBalancer;
import org.apache.camel.management.mbean.ManagedRecipientList;
import org.apache.camel.management.mbean.ManagedRemoveHeader;
import org.apache.camel.management.mbean.ManagedRemoveHeaders;
import org.apache.camel.management.mbean.ManagedRemoveProperties;
import org.apache.camel.management.mbean.ManagedRemoveProperty;
import org.apache.camel.management.mbean.ManagedResequencer;
import org.apache.camel.management.mbean.ManagedRollback;
import org.apache.camel.management.mbean.ManagedRoundRobinLoadBalancer;
import org.apache.camel.management.mbean.ManagedRoute;
import org.apache.camel.management.mbean.ManagedRouteController;
import org.apache.camel.management.mbean.ManagedRoutingSlip;
import org.apache.camel.management.mbean.ManagedSamplingThrottler;
import org.apache.camel.management.mbean.ManagedScheduledPollConsumer;
import org.apache.camel.management.mbean.ManagedScript;
import org.apache.camel.management.mbean.ManagedSendDynamicProcessor;
import org.apache.camel.management.mbean.ManagedSendProcessor;
import org.apache.camel.management.mbean.ManagedService;
import org.apache.camel.management.mbean.ManagedSetBody;
import org.apache.camel.management.mbean.ManagedSetExchangePattern;
import org.apache.camel.management.mbean.ManagedSetHeader;
import org.apache.camel.management.mbean.ManagedSetProperty;
import org.apache.camel.management.mbean.ManagedSplitter;
import org.apache.camel.management.mbean.ManagedStep;
import org.apache.camel.management.mbean.ManagedStickyLoadBalancer;
import org.apache.camel.management.mbean.ManagedStop;
import org.apache.camel.management.mbean.ManagedSupervisingRouteController;
import org.apache.camel.management.mbean.ManagedSuspendableRoute;
import org.apache.camel.management.mbean.ManagedThreadPool;
import org.apache.camel.management.mbean.ManagedThreads;
import org.apache.camel.management.mbean.ManagedThrottler;
import org.apache.camel.management.mbean.ManagedThroughputLogger;
import org.apache.camel.management.mbean.ManagedThrowException;
import org.apache.camel.management.mbean.ManagedTopicLoadBalancer;
import org.apache.camel.management.mbean.ManagedTransformer;
import org.apache.camel.management.mbean.ManagedUnmarshal;
import org.apache.camel.management.mbean.ManagedValidate;
import org.apache.camel.management.mbean.ManagedWeightedLoadBalancer;
import org.apache.camel.management.mbean.ManagedWireTapProcessor;
import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.DynamicRouterDefinition;
import org.apache.camel.model.EnrichDefinition;
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.FinallyDefinition;
import org.apache.camel.model.IdempotentConsumerDefinition;
import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.LoopDefinition;
import org.apache.camel.model.MarshalDefinition;
import org.apache.camel.model.PollEnrichDefinition;
import org.apache.camel.model.ProcessDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RecipientListDefinition;
import org.apache.camel.model.RoutingSlipDefinition;
import org.apache.camel.model.ScriptDefinition;
import org.apache.camel.model.SetBodyDefinition;
import org.apache.camel.model.SetHeaderDefinition;
import org.apache.camel.model.SetPropertyDefinition;
import org.apache.camel.model.SplitDefinition;
import org.apache.camel.model.TransformDefinition;
import org.apache.camel.model.TryDefinition;
import org.apache.camel.model.UnmarshalDefinition;
import org.apache.camel.model.ValidateDefinition;
import org.apache.camel.model.loadbalancer.CustomLoadBalancerDefinition;
import org.apache.camel.processor.CatchProcessor;
import org.apache.camel.processor.ChoiceProcessor;
import org.apache.camel.processor.ClaimCheckProcessor;
import org.apache.camel.processor.Delayer;
import org.apache.camel.processor.DisabledProcessor;
import org.apache.camel.processor.DynamicRouter;
import org.apache.camel.processor.Enricher;
import org.apache.camel.processor.ExchangePatternProcessor;
import org.apache.camel.processor.FilterProcessor;
import org.apache.camel.processor.FinallyProcessor;
import org.apache.camel.processor.LogProcessor;
import org.apache.camel.processor.LoopProcessor;
import org.apache.camel.processor.MulticastProcessor;
import org.apache.camel.processor.Pipeline;
import org.apache.camel.processor.PollEnricher;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.RemoveHeaderProcessor;
import org.apache.camel.processor.RemoveHeadersProcessor;
import org.apache.camel.processor.RemovePropertiesProcessor;
import org.apache.camel.processor.RemovePropertyProcessor;
import org.apache.camel.processor.Resequencer;
import org.apache.camel.processor.RollbackProcessor;
import org.apache.camel.processor.RoutingSlip;
import org.apache.camel.processor.SamplingThrottler;
import org.apache.camel.processor.ScriptProcessor;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.SetBodyProcessor;
import org.apache.camel.processor.SetHeaderProcessor;
import org.apache.camel.processor.SetPropertyProcessor;
import org.apache.camel.processor.Splitter;
import org.apache.camel.processor.StepProcessor;
import org.apache.camel.processor.StopProcessor;
import org.apache.camel.processor.StreamResequencer;
import org.apache.camel.processor.ThreadsProcessor;
import org.apache.camel.processor.Throttler;
import org.apache.camel.processor.ThrowExceptionProcessor;
import org.apache.camel.processor.TransformProcessor;
import org.apache.camel.processor.TryProcessor;
import org.apache.camel.processor.WireTapProcessor;
import org.apache.camel.processor.aggregate.AggregateProcessor;
import org.apache.camel.processor.idempotent.IdempotentConsumer;
import org.apache.camel.processor.loadbalancer.FailOverLoadBalancer;
import org.apache.camel.processor.loadbalancer.LoadBalancer;
import org.apache.camel.processor.loadbalancer.RandomLoadBalancer;
import org.apache.camel.processor.loadbalancer.RoundRobinLoadBalancer;
import org.apache.camel.processor.loadbalancer.StickyLoadBalancer;
import org.apache.camel.processor.loadbalancer.TopicLoadBalancer;
import org.apache.camel.processor.loadbalancer.WeightedLoadBalancer;
import org.apache.camel.processor.transformer.DataTypeProcessor;
import org.apache.camel.spi.BrowsableEndpoint;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.ErrorHandler;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ManagementObjectStrategy;
import org.apache.camel.spi.RouteController;
import org.apache.camel.spi.SupervisingRouteController;
import org.apache.camel.support.ScheduledPollConsumer;
import org.apache.camel.support.processor.ConvertBodyProcessor;
import org.apache.camel.support.processor.MarshalProcessor;
import org.apache.camel.support.processor.PredicateValidatingProcessor;
import org.apache.camel.support.processor.ThroughputLogger;
import org.apache.camel.support.processor.UnmarshalProcessor;

/**
 * Default {@link org.apache.camel.spi.ManagementObjectStrategy}.
 */
public class DefaultManagementObjectStrategy implements ManagementObjectStrategy {

    @Override
    public Object getManagedObjectForCamelContext(CamelContext context) {
        ManagedCamelContext mc = new ManagedCamelContext(context);
        mc.init(context.getManagementStrategy());
        return mc;
    }

    @Override
    public Object getManagedObjectForCamelHealth(CamelContext context, HealthCheckRegistry healthCheckRegistry) {
        ManagedCamelHealth mch = new ManagedCamelHealth(context, healthCheckRegistry);
        mch.init(context.getManagementStrategy());
        return mch;
    }

    @Override
    @SuppressWarnings({ "deprecation", "unchecked" })
    public Object getManagedObjectForComponent(CamelContext context, Component component, String name) {
        ManagedComponent mc = new ManagedComponent(name, component);
        mc.init(context.getManagementStrategy());
        return mc;
    }

    @Override
    @SuppressWarnings({ "deprecation", "unchecked" })
    public Object getManagedObjectForDataFormat(CamelContext context, DataFormat dataFormat) {
        ManagedDataFormat md = new ManagedDataFormat(context, dataFormat);
        md.init(context.getManagementStrategy());
        return md;
    }

    @Override
    @SuppressWarnings({ "deprecation", "unchecked" })
    public Object getManagedObjectForEndpoint(CamelContext context, Endpoint endpoint) {
        // we only want to manage singleton endpoints
        if (!endpoint.isSingleton()) {
            return null;
        }

        if (endpoint instanceof BrowsableEndpoint) {
            ManagedBrowsableEndpoint me = new ManagedBrowsableEndpoint((BrowsableEndpoint) endpoint);
            me.init(context.getManagementStrategy());
            return me;
        } else {
            ManagedEndpoint me = new ManagedEndpoint(endpoint);
            me.init(context.getManagementStrategy());
            return me;
        }
    }

    @Override
    public Object getManagedObjectForRouteController(CamelContext context, RouteController routeController) {
        ManagedService mrc;
        if (routeController instanceof SupervisingRouteController) {
            mrc = new ManagedSupervisingRouteController(context, (SupervisingRouteController) routeController);
        } else {
            mrc = new ManagedRouteController(context, routeController);
        }
        mrc.init(context.getManagementStrategy());
        return mrc;
    }

    @Override
    public Object getManagedObjectForRoute(CamelContext context, Route route) {
        ManagedRoute mr;
        if (route.supportsSuspension()) {
            mr = new ManagedSuspendableRoute(context, route);
        } else {
            mr = new ManagedRoute(context, route);
        }
        mr.init(context.getManagementStrategy());
        return mr;
    }

    @Override
    public Object getManagedObjectForThreadPool(
            CamelContext context, ThreadPoolExecutor threadPool,
            String id, String sourceId, String routeId, String threadPoolProfileId) {
        ManagedThreadPool mtp = new ManagedThreadPool(context, threadPool, id, sourceId, routeId, threadPoolProfileId);
        mtp.init(context.getManagementStrategy());
        return mtp;
    }

    @Override
    public Object getManagedObjectForEventNotifier(CamelContext context, EventNotifier eventNotifier) {
        ManagedEventNotifier men = new ManagedEventNotifier(context, eventNotifier);
        men.init(context.getManagementStrategy());
        return men;
    }

    @Override
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

    @Override
    public Object getManagedObjectForProducer(CamelContext context, Producer producer) {
        ManagedProducer mp = new ManagedProducer(context, producer);
        mp.init(context.getManagementStrategy());
        return mp;
    }

    @Override
    public Object getManagedObjectForService(CamelContext context, Service service) {
        ManagedService mc = new ManagedService(context, service);
        mc.init(context.getManagementStrategy());
        return mc;
    }

    @Override
    public Object getManagedObjectForClusterService(CamelContext context, CamelClusterService service) {
        ManagedClusterService mcs = new ManagedClusterService(context, service);
        mcs.init(context.getManagementStrategy());
        return mcs;
    }

    @Override
    @SuppressWarnings({ "deprecation", "unchecked" })
    public Object getManagedObjectForProcessor(
            CamelContext context, Processor processor,
            NamedNode node, Route route) {
        ManagedProcessor answer = null;

        ProcessorDefinition<?> definition = (ProcessorDefinition<?>) node;

        if (definition instanceof RecipientListDefinition) {
            // special for RecipientListDefinition, as the processor is wrapped in a pipeline as last
            Pipeline pipeline = (Pipeline) processor;
            Iterator<Processor> it = pipeline.next().iterator();
            while (it.hasNext()) {
                processor = it.next();
            }
        }

        // unwrap delegates as we want the real target processor
        Processor target = processor;
        while (target != null) {

            // skip error handlers
            if (target instanceof ErrorHandler) {
                return false;
            }

            if (target instanceof ConvertBodyProcessor) {
                answer = new ManagedConvertBody(context, (ConvertBodyProcessor) target, definition);
            } else if (target instanceof ChoiceProcessor) {
                answer = new ManagedChoice(context, (ChoiceProcessor) target, definition);
            } else if (target instanceof ClaimCheckProcessor) {
                answer = new ManagedClaimCheck(context, (ClaimCheckProcessor) target, definition);
            } else if (target instanceof Delayer) {
                answer = new ManagedDelayer(context, (Delayer) target, definition);
            } else if (target instanceof DisabledProcessor) {
                answer = new ManagedDisabled(context, (DisabledProcessor) target, definition);
            } else if (target instanceof TryProcessor) {
                answer = new ManagedDoTry(context, (TryProcessor) target, (TryDefinition) definition);
            } else if (target instanceof CatchProcessor) {
                answer = new ManagedDoCatch(context, (CatchProcessor) target, (CatchDefinition) definition);
            } else if (target instanceof FinallyProcessor) {
                answer = new ManagedDoFinally(context, (FinallyProcessor) target, (FinallyDefinition) definition);
            } else if (target instanceof Throttler) {
                answer = new ManagedThrottler(context, (Throttler) target, definition);
            } else if (target instanceof DynamicRouter) {
                answer = new ManagedDynamicRouter(context, (DynamicRouter) target, (DynamicRouterDefinition) definition);
            } else if (target instanceof RoutingSlip) {
                answer = new ManagedRoutingSlip(context, (RoutingSlip) target, (RoutingSlipDefinition) definition);
            } else if (target instanceof FilterProcessor) {
                answer = new ManagedFilter(context, (FilterProcessor) target, (ExpressionNode) definition);
            } else if (target instanceof LogProcessor) {
                answer = new ManagedLog(context, (LogProcessor) target, definition);
            } else if (target instanceof LoopProcessor) {
                answer = new ManagedLoop(context, (LoopProcessor) target, (LoopDefinition) definition);
            } else if (target instanceof MarshalProcessor) {
                answer = new ManagedMarshal(context, (MarshalProcessor) target, (MarshalDefinition) definition);
            } else if (target instanceof UnmarshalProcessor) {
                answer = new ManagedUnmarshal(context, (UnmarshalProcessor) target, (UnmarshalDefinition) definition);
            } else if (target instanceof FailOverLoadBalancer) {
                answer = new ManagedFailoverLoadBalancer(
                        context, (FailOverLoadBalancer) target, (LoadBalanceDefinition) definition);
            } else if (target instanceof RandomLoadBalancer) {
                answer = new ManagedRandomLoadBalancer(
                        context, (RandomLoadBalancer) target, (LoadBalanceDefinition) definition);
            } else if (target instanceof RoundRobinLoadBalancer) {
                answer = new ManagedRoundRobinLoadBalancer(
                        context, (RoundRobinLoadBalancer) target, (LoadBalanceDefinition) definition);
            } else if (target instanceof StickyLoadBalancer) {
                answer = new ManagedStickyLoadBalancer(
                        context, (StickyLoadBalancer) target, (LoadBalanceDefinition) definition);
            } else if (target instanceof TopicLoadBalancer) {
                answer = new ManagedTopicLoadBalancer(context, (TopicLoadBalancer) target, (LoadBalanceDefinition) definition);
            } else if (target instanceof WeightedLoadBalancer) {
                answer = new ManagedWeightedLoadBalancer(
                        context, (WeightedLoadBalancer) target, (LoadBalanceDefinition) definition);
            } else if (target instanceof RecipientList) {
                answer = new ManagedRecipientList(context, (RecipientList) target, (RecipientListDefinition) definition);
            } else if (target instanceof Splitter) {
                answer = new ManagedSplitter(context, (Splitter) target, (SplitDefinition) definition);
            } else if (target instanceof MulticastProcessor) {
                answer = new ManagedMulticast(context, (MulticastProcessor) target, definition);
            } else if (target instanceof SamplingThrottler) {
                answer = new ManagedSamplingThrottler(context, (SamplingThrottler) target, definition);
            } else if (target instanceof Resequencer) {
                answer = new ManagedResequencer(context, (Resequencer) target, definition);
            } else if (target instanceof RollbackProcessor) {
                answer = new ManagedRollback(context, (RollbackProcessor) target, definition);
            } else if (target instanceof StreamResequencer) {
                answer = new ManagedResequencer(context, (StreamResequencer) target, definition);
            } else if (target instanceof SetBodyProcessor) {
                answer = new ManagedSetBody(context, (SetBodyProcessor) target, (SetBodyDefinition) definition);
            } else if (target instanceof RemoveHeaderProcessor) {
                answer = new ManagedRemoveHeader(context, (RemoveHeaderProcessor) target, definition);
            } else if (target instanceof RemoveHeadersProcessor) {
                answer = new ManagedRemoveHeaders(context, (RemoveHeadersProcessor) target, definition);
            } else if (target instanceof SetHeaderProcessor) {
                answer = new ManagedSetHeader(context, (SetHeaderProcessor) target, (SetHeaderDefinition) definition);
            } else if (target instanceof RemovePropertyProcessor) {
                answer = new ManagedRemoveProperty(context, (RemovePropertyProcessor) target, definition);
            } else if (target instanceof RemovePropertiesProcessor) {
                answer = new ManagedRemoveProperties(context, (RemovePropertiesProcessor) target, definition);
            } else if (target instanceof SetPropertyProcessor) {
                answer = new ManagedSetProperty(context, (SetPropertyProcessor) target, (SetPropertyDefinition) definition);
            } else if (target instanceof ExchangePatternProcessor) {
                answer = new ManagedSetExchangePattern(context, (ExchangePatternProcessor) target, definition);
            } else if (target instanceof ScriptProcessor) {
                answer = new ManagedScript(context, (ScriptProcessor) target, (ScriptDefinition) definition);
            } else if (target instanceof StepProcessor) {
                answer = new ManagedStep(context, (StepProcessor) target, definition);
            } else if (target instanceof StopProcessor) {
                answer = new ManagedStop(context, (StopProcessor) target, definition);
            } else if (target instanceof ThreadsProcessor) {
                answer = new ManagedThreads(context, (ThreadsProcessor) target, definition);
            } else if (target instanceof ThrowExceptionProcessor) {
                answer = new ManagedThrowException(context, (ThrowExceptionProcessor) target, definition);
            } else if (target instanceof TransformProcessor) {
                answer = new ManagedTransformer(context, target, (TransformDefinition) definition);
            } else if (target instanceof DataTypeProcessor && definition instanceof TransformDefinition) {
                answer = new ManagedTransformer(context, target, (TransformDefinition) definition);
            } else if (target instanceof PredicateValidatingProcessor) {
                answer = new ManagedValidate(context, (PredicateValidatingProcessor) target, (ValidateDefinition) definition);
            } else if (target instanceof WireTapProcessor) {
                answer = new ManagedWireTapProcessor(context, (WireTapProcessor) target, definition);
            } else if (target instanceof SendDynamicProcessor) {
                answer = new ManagedSendDynamicProcessor(context, (SendDynamicProcessor) target, definition);
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
                answer = new ManagedIdempotentConsumer(
                        context, (IdempotentConsumer) target, (IdempotentConsumerDefinition) definition);
            } else if (target instanceof AggregateProcessor) {
                answer = new ManagedAggregateProcessor(context, (AggregateProcessor) target, (AggregateDefinition) definition);
            } else if (target instanceof Enricher) {
                answer = new ManagedEnricher(context, (Enricher) target, (EnrichDefinition) definition);
            } else if (target instanceof PollEnricher) {
                answer = new ManagedPollEnricher(context, (PollEnricher) target, (PollEnrichDefinition) definition);
            }

            // special for custom load balancer
            if (definition instanceof LoadBalanceDefinition) {
                LoadBalanceDefinition lb = (LoadBalanceDefinition) definition;
                if (lb.getLoadBalancerType() instanceof CustomLoadBalancerDefinition) {
                    answer = new ManagedCustomLoadBalancer(context, (LoadBalancer) target, (LoadBalanceDefinition) definition);
                }
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

        if (answer == null && definition instanceof ProcessDefinition) {
            answer = new ManagedProcess(context, target, (ProcessDefinition) definition);
        } else if (answer == null) {
            // fallback to a generic processor
            answer = new ManagedProcessor(context, target, definition);
        }

        answer.setRoute(route);
        answer.init(context.getManagementStrategy());
        return answer;
    }

}
