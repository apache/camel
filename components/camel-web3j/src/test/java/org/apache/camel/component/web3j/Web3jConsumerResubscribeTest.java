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
package org.apache.camel.component.web3j;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.reactivex.Flowable;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A live subscription that ends with an error (such as a failed eth_getBlockByHash request inside web3j's
 * blockFlowable) is subscribed again, instead of leaving a started consumer that receives nothing.
 */
class Web3jConsumerResubscribeTest extends CamelTestSupport {

    private final Web3j web3j = mock(Web3j.class);

    @EndpointInject("mock:result")
    private MockEndpoint result;

    @EndpointInject("mock:error")
    private MockEndpoint error;

    Web3jConsumerResubscribeTest() {
        // the mocked client is stubbed before the context starts
        testConfiguration().withUseAdviceWith(true);
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("mockWeb3j", web3j);
    }

    private static EthBlock block(String number) {
        EthBlock.Block block = new EthBlock.Block();
        block.setNumber(number);
        EthBlock ethBlock = new EthBlock();
        ethBlock.setResult(block);
        return ethBlock;
    }

    private void startRoute(String operation) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                onException(Exception.class).handled(true).to("mock:error");
                from("web3j://http://127.0.0.1:8545?web3j=#mockWeb3j&operation=" + operation)
                        .routeId("web3j").autoStartup(false)
                        .to("mock:result");
            }
        });
        context.start();
        ((Web3jConsumer) context.getRoute("web3j").getConsumer()).setResubscribeDelay(10);
        context.getRouteController().startRoute("web3j");
    }

    @Test
    void blockSubscriptionEndedByAFailedRequestIsSubscribedAgain() throws Exception {
        when(web3j.blockFlowable(anyBoolean())).thenReturn(
                Flowable.concat(Flowable.just(block("0x1")), Flowable.error(new IOException("Connection reset"))),
                Flowable.concat(Flowable.just(block("0x2")), Flowable.never()));
        result.expectedMessageCount(2);
        error.expectedMessageCount(1);

        startRoute(Web3jConstants.BLOCK_OBSERVABLE);

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
        assertEquals("0x2", result.getReceivedExchanges().get(1).getIn().getBody(EthBlock.Block.class).getNumberRaw());
    }

    @Test
    void failuresWhileSubscribingAgainAreRoutedOnce() throws Exception {
        // the node is unreachable for three attempts, then it answers again
        IOException down = new IOException("Connection refused");
        when(web3j.blockFlowable(anyBoolean())).thenReturn(
                Flowable.error(down), Flowable.error(down), Flowable.error(down),
                Flowable.concat(Flowable.just(block("0x1")), Flowable.never()));
        result.expectedMessageCount(1);
        error.expectedMessageCount(1);

        startRoute(Web3jConstants.BLOCK_OBSERVABLE);

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
        verify(web3j, times(4)).blockFlowable(anyBoolean());
    }

    @Test
    void replayOfARangeIsNotSubscribedAgain() throws Exception {
        // a replay of a block range would route the blocks of the range again
        when(web3j.replayPastBlocksFlowable(any(), any(), anyBoolean()))
                .thenReturn(Flowable.concat(Flowable.just(block("0x1")), Flowable.error(new IOException("Connection reset"))));
        result.expectedMessageCount(1);
        error.expectedMessageCount(1);

        startRoute(Web3jConstants.REPLAY_BLOCKS_OBSERVABLE);

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
        verify(web3j, after(200).times(1)).replayPastBlocksFlowable(any(), any(), anyBoolean());
    }

    @Test
    void logFilterFromAGivenBlockIsNotSubscribedAgain() throws Exception {
        // a new filter from the same block would route the logs from that block again
        when(web3j.ethLogFlowable(any())).thenReturn(Flowable.error(new IOException("Connection refused")));
        error.expectedMessageCount(1);

        startRoute(Web3jConstants.ETH_LOG_OBSERVABLE + "&fromBlock=16");

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
        verify(web3j, after(200).times(1)).ethLogFlowable(any());
    }

    @Test
    void logFilterFromTheLatestBlockIsSubscribedAgain() throws Exception {
        Log log = new Log();
        log.setData("0x01");
        when(web3j.ethLogFlowable(any())).thenReturn(
                Flowable.error(new IOException("Connection refused")),
                Flowable.concat(Flowable.just(log), Flowable.never()));
        result.expectedMessageCount(1);
        error.expectedMessageCount(1);

        startRoute(Web3jConstants.ETH_LOG_OBSERVABLE);

        MockEndpoint.assertIsSatisfied(context, 5, TimeUnit.SECONDS);
        verify(web3j, times(2)).ethLogFlowable(any());
    }

    @Test
    void subscriptionMadeWhileTheConsumerStopsIsDisposed() throws Exception {
        // the second subscription is made while the route stops: the request to the node is slow and is not
        // interrupted by the shutdown of the executor, so it ends after doStop disposed the previous subscription
        CountDownLatch subscribing = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch cancelled = new CountDownLatch(1);
        Flowable<EthBlock> slow = Flowable.defer(() -> {
            subscribing.countDown();
            boolean released = false;
            while (!released) {
                try {
                    released = release.await(10, TimeUnit.SECONDS);
                    if (!released) {
                        throw new IllegalStateException("not released");
                    }
                } catch (InterruptedException e) {
                    // ignore the interrupt of the executor shutdown, as a blocking request may do
                }
            }
            return Flowable.<EthBlock> never();
        }).doOnCancel(cancelled::countDown);
        when(web3j.blockFlowable(anyBoolean())).thenReturn(Flowable.error(new IOException("Connection reset")), slow);
        error.expectedMessageCount(1);

        startRoute(Web3jConstants.BLOCK_OBSERVABLE);
        assertTrue(subscribing.await(5, TimeUnit.SECONDS));
        context.getRouteController().stopRoute("web3j");
        release.countDown();

        assertTrue(cancelled.await(5, TimeUnit.SECONDS), "the subscription made while stopping must be disposed");
        MockEndpoint.assertIsSatisfied(context);
    }
}
