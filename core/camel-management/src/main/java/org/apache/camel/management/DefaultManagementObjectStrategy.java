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
import org.apache.camel.management.mbean.ManagedConvertHeader;
import org.apache.camel.management.mbean.ManagedConvertVariable;
import org.apache.camel.management.mbean.ManagedCustomLoadBalancer;
import org.apache.camel.management.mbean.ManagedDataFormat;
import org.apache.camel.management.mbean.ManagedDataTypeTransformer;
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
import org.apache.camel.management.mbean.ManagedPoll;
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
import org.apache.camel.management.mbean.ManagedRemoveVariable;
import org.apache.camel.management.mbean.ManagedResequencer;
import org.apache.camel.management.mbean.ManagedRollback;
import org.apache.camel.management.mbean.ManagedRoundRobinLoadBalancer;
import org.apache.camel.management.mbean.ManagedRoute;
import org.apache.camel.management.mbean.ManagedRouteController;
import org.apache.camel.management.mbean.ManagedRouteGroup;
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
import org.apache.camel.management.mbean.ManagedSetHeaders;
import org.apache.camel.management.mbean.ManagedSetProperty;
import org.apache.camel.management.mbean.ManagedSetVariable;
import org.apache.camel.management.mbean.ManagedSetVariables;
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
import org.apache.camel.model.ExpressionNode;
import org.apache.camel.model.LoadBalanceDefinition;
import org.apache.camel.model.ProcessDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RecipientListDefinition;
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
import org.apache.camel.processor.PollProcessor;
import org.apache.camel.processor.RecipientList;
import org.apache.camel.processor.RemoveHeaderProcessor;
import org.apache.camel.processor.RemoveHeadersProcessor;
import org.apache.camel.processor.RemovePropertiesProcessor;
import org.apache.camel.processor.RemovePropertyProcessor;
import org.apache.camel.processor.RemoveVariableProcessor;
import org.apache.camel.processor.Resequencer;
import org.apache.camel.processor.RollbackProcessor;
import org.apache.camel.processor.RoutingSlip;
import org.apache.camel.processor.SamplingThrottler;
import org.apache.camel.processor.ScriptProcessor;
import org.apache.camel.processor.SendDynamicProcessor;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.SetBodyProcessor;
import org.apache.camel.processor.SetHeaderProcessor;
import org.apache.camel.processor.SetHeadersProcessor;
import org.apache.camel.processor.SetPropertyProcessor;
import org.apache.camel.processor.SetVariableProcessor;
import org.apache.camel.processor.SetVariablesProcessor;
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
import org.apache.camel.support.processor.ConvertHeaderProcessor;
import org.apache.camel.support.processor.ConvertVariableProcessor;
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
    public Object getManagedObjectForComponent(CamelContext context, Component component, String name) {
        ManagedComponent mc = new ManagedComponent(name, component);
        mc.init(context.getManagementStrategy());
        return mc;
    }

    @Override
    public Object getManagedObjectForDataFormat(CamelContext context, DataFormat dataFormat) {
        ManagedDataFormat md = new ManagedDataFormat(context, dataFormat);
        md.init(context.getManagementStrategy());
        return md;
    }

    @Override
    public Object getManagedObjectForEndpoint(CamelContext context, Endpoint endpoint) {
        // we only want to manage singleton endpoints
        if (!endpoint.isSingleton()) {
            return null;
        }

        if (endpoint instanceof BrowsableEndpoint browsableEndpoint) {
            ManagedBrowsableEndpoint me = new ManagedBrowsableEndpoint(browsableEndpoint);
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
        if (routeController instanceof SupervisingRouteController supervisingRouteController) {
            mrc = new ManagedSupervisingRouteController(context, supervisingRouteController);
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
    public Object getManagedObjectForRouteGroup(CamelContext context, String group) {
        if (group == null) {
            return null;
        }
        ManagedRouteGroup mr = new ManagedRouteGroup(context, group);
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
        if (consumer instanceof ScheduledPollConsumer scheduledPollConsumer) {
            mc = new ManagedScheduledPollConsumer(context, scheduledPollConsumer);
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
    public Object getManagedObjectForProcessor(
            CamelContext context, Processor processor,
            NamedNode node, Route route) {
        ManagedProcessor answer = null;

        ProcessorDefinition<?> definition = (ProcessorDefinition<?>) node;

        if (definition instanceof RecipientListDefinition && processor instanceof Pipeline pipeline) {
            // special for RecipientListDefinition, as the processor is wrapped in a pipeline as last
            for (Processor value : pipeline.next()) {
                processor = value;
            }
        }

        // unwrap delegates as we want the real target processor
        Processor target = processor;
        while (target != null) {

            // skip error handlers
            if (target instanceof ErrorHandler) {
                return false;
            }

            if (target instanceof ConvertBodyProcessor cbp) {
                answer = new ManagedConvertBody(context, cbp, definition);
            } else if (target instanceof ConvertHeaderProcessor chp) {
                answer = new ManagedConvertHeader(context, chp, definition);
            } else if (target instanceof ConvertVariableProcessor cvp) {
                answer = new ManagedConvertVariable(context, cvp, definition);
            } else if (target instanceof ChoiceProcessor cp) {
                answer = new ManagedChoice(context, cp, definition);
            } else if (target instanceof ClaimCheckProcessor ccp) {
                answer = new ManagedClaimCheck(context, ccp, definition);
            } else if (target instanceof Delayer d) {
                answer = new ManagedDelayer(context, d, definition);
            } else if (target instanceof DisabledProcessor dp) {
                answer = new ManagedDisabled(context, dp, definition);
            } else if (target instanceof TryProcessor tryProc) {
                answer = new ManagedDoTry(context, tryProc, cast(definition));
            } else if (target instanceof CatchProcessor catchProc) {
                answer = new ManagedDoCatch(context, catchProc, cast(definition));
            } else if (target instanceof FinallyProcessor fp) {
                answer = new ManagedDoFinally(context, fp, cast(definition));
            } else if (target instanceof Throttler t) {
                answer = new ManagedThrottler(context, t, definition);
            } else if (target instanceof DynamicRouter dr) {
                answer = new ManagedDynamicRouter(context, dr, cast(definition));
            } else if (target instanceof RoutingSlip rs) {
                answer = new ManagedRoutingSlip(context, rs, cast(definition));
            } else if (target instanceof FilterProcessor fp) {
                answer = new ManagedFilter(context, fp, (ExpressionNode) definition);
            } else if (target instanceof LogProcessor lp) {
                answer = new ManagedLog(context, lp, definition);
            } else if (target instanceof LoopProcessor loopProc) {
                answer = new ManagedLoop(context, loopProc, cast(definition));
            } else if (target instanceof MarshalProcessor mp) {
                answer = new ManagedMarshal(context, mp, cast(definition));
            } else if (target instanceof UnmarshalProcessor up) {
                answer = new ManagedUnmarshal(context, up, cast(definition));
            } else if (target instanceof FailOverLoadBalancer folb) {
                answer = new ManagedFailoverLoadBalancer(context, folb, cast(definition));
            } else if (target instanceof RandomLoadBalancer rlb) {
                answer = new ManagedRandomLoadBalancer(context, rlb, cast(definition));
            } else if (target instanceof RoundRobinLoadBalancer rrlb) {
                answer = new ManagedRoundRobinLoadBalancer(context, rrlb, cast(definition));
            } else if (target instanceof StickyLoadBalancer slb) {
                answer = new ManagedStickyLoadBalancer(context, slb, cast(definition));
            } else if (target instanceof TopicLoadBalancer tlb) {
                answer = new ManagedTopicLoadBalancer(context, tlb, cast(definition));
            } else if (target instanceof WeightedLoadBalancer wlb) {
                answer = new ManagedWeightedLoadBalancer(context, wlb, cast(definition));
            } else if (target instanceof RecipientList rl) {
                answer = new ManagedRecipientList(context, rl, cast(definition));
            } else if (target instanceof Splitter s) {
                answer = new ManagedSplitter(context, s, cast(definition));
            } else if (target instanceof MulticastProcessor mcp) {
                answer = new ManagedMulticast(context, mcp, definition);
            } else if (target instanceof SamplingThrottler st) {
                answer = new ManagedSamplingThrottler(context, st, definition);
            } else if (target instanceof Resequencer r) {
                answer = new ManagedResequencer(context, r, definition);
            } else if (target instanceof RollbackProcessor rp) {
                answer = new ManagedRollback(context, rp, definition);
            } else if (target instanceof StreamResequencer sr) {
                answer = new ManagedResequencer(context, sr, definition);
            } else if (target instanceof SetBodyProcessor sbp) {
                answer = new ManagedSetBody(context, sbp, cast(definition));
            } else if (target instanceof RemoveHeaderProcessor rhp) {
                answer = new ManagedRemoveHeader(context, rhp, definition);
            } else if (target instanceof RemoveHeadersProcessor rhsp) {
                answer = new ManagedRemoveHeaders(context, rhsp, definition);
            } else if (target instanceof SetHeaderProcessor shp) {
                answer = new ManagedSetHeader(context, shp, cast(definition));
            } else if (target instanceof SetHeadersProcessor shsp) {
                answer = new ManagedSetHeaders(context, shsp, cast(definition));
            } else if (target instanceof SetVariableProcessor svp) {
                answer = new ManagedSetVariable(context, svp, cast(definition));
            } else if (target instanceof SetVariablesProcessor svsp) {
                answer = new ManagedSetVariables(context, svsp, cast(definition));
            } else if (target instanceof RemovePropertyProcessor rpp) {
                answer = new ManagedRemoveProperty(context, rpp, definition);
            } else if (target instanceof RemovePropertiesProcessor rpsp) {
                answer = new ManagedRemoveProperties(context, rpsp, definition);
            } else if (target instanceof RemoveVariableProcessor rvp) {
                answer = new ManagedRemoveVariable(context, rvp, definition);
            } else if (target instanceof SetPropertyProcessor spp) {
                answer = new ManagedSetProperty(context, spp, cast(definition));
            } else if (target instanceof ExchangePatternProcessor epp) {
                answer = new ManagedSetExchangePattern(context, epp, definition);
            } else if (target instanceof ScriptProcessor scrp) {
                answer = new ManagedScript(context, scrp, cast(definition));
            } else if (target instanceof StepProcessor stepp) {
                answer = new ManagedStep(context, stepp, definition);
            } else if (target instanceof StopProcessor stopp) {
                answer = new ManagedStop(context, stopp, definition);
            } else if (target instanceof ThreadsProcessor tp) {
                answer = new ManagedThreads(context, tp, definition);
            } else if (target instanceof ThrowExceptionProcessor tep) {
                answer = new ManagedThrowException(context, tep, definition);
            } else if (target instanceof TransformProcessor) {
                answer = new ManagedTransformer(context, target, cast(definition));
            } else if (target instanceof DataTypeProcessor) {
                answer = new ManagedDataTypeTransformer(context, target, cast(definition));
            } else if (target instanceof PredicateValidatingProcessor pvp) {
                answer = new ManagedValidate(context, pvp, cast(definition));
            } else if (target instanceof WireTapProcessor wtp) {
                answer = new ManagedWireTapProcessor(context, wtp, definition);
            } else if (target instanceof SendDynamicProcessor sdp) {
                answer = new ManagedSendDynamicProcessor(context, sdp, definition);
            } else if (target instanceof SendProcessor sp) {
                // special for sending to throughput logger
                if (sp.getDestination() instanceof LogEndpoint le && le.getLogger() instanceof ThroughputLogger tl) {
                    answer = new ManagedThroughputLogger(context, tl, definition);
                }
                // regular send processor
                if (answer == null) {
                    answer = new ManagedSendProcessor(context, sp, definition);
                }
            } else if (target instanceof BeanProcessor bp) {
                answer = new ManagedBeanProcessor(context, bp, definition);
            } else if (target instanceof IdempotentConsumer ic) {
                answer = new ManagedIdempotentConsumer(context, ic, cast(definition));
            } else if (target instanceof AggregateProcessor ap) {
                answer = new ManagedAggregateProcessor(context, ap, cast(definition));
            } else if (target instanceof Enricher e) {
                answer = new ManagedEnricher(context, e, cast(definition));
            } else if (target instanceof PollProcessor pp) {
                answer = new ManagedPoll(context, pp, cast(definition));
            } else if (target instanceof PollEnricher pe) {
                answer = new ManagedPollEnricher(context, pe, cast(definition));
            }

            // special for custom load balancer
            if (definition instanceof LoadBalanceDefinition lb) {
                if (lb.getLoadBalancerType() instanceof CustomLoadBalancerDefinition
                        && target instanceof LoadBalancer loadBalancer) {
                    answer = new ManagedCustomLoadBalancer(context, loadBalancer, lb);
                }
            }

            if (answer != null) {
                // break out as we found an answer
                break;
            }

            // no answer yet, so unwrap any delegates and try again
            if (target instanceof DelegateProcessor delegateProcessor) {
                target = delegateProcessor.getProcessor();
            } else {
                // no delegate so we dont have any target to try next
                break;
            }
        }

        if (answer == null && definition instanceof ProcessDefinition pd) {
            answer = new ManagedProcess(context, target, pd);
        } else if (answer == null) {
            // fallback to a generic processor
            answer = new ManagedProcessor(context, target, definition);
        }

        answer.setRoute(route);
        answer.init(context.getManagementStrategy());
        return answer;
    }

    @SuppressWarnings("unchecked")
    private <T extends ProcessorDefinition<?>> T cast(ProcessorDefinition<?> definition) {
        return (T) definition;
    }

}
