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

import io.reactivex.Flowable;
import org.apache.camel.EndpointInject;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class Web3jConsumerTest extends CamelTestSupport {

    private final Web3j web3j = mock(Web3j.class);

    @EndpointInject("mock:result")
    private MockEndpoint result;

    Web3jConsumerTest() {
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

    private void startRoute(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("web3j://http://127.0.0.1:8545?web3j=#mockWeb3j" + options)
                        .routeId("web3j").autoStartup(false)
                        .to("mock:result");
            }
        });
        context.start();
        context.getRouteController().startRoute("web3j");
    }

    @Test
    void consumerWithoutOperationFailsToStart() {
        Exception e = assertThrows(Exception.class, () -> startRoute(""));
        Throwable cause = e;
        while (!(cause instanceof IllegalArgumentException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertTrue(cause.getMessage().startsWith(
                "Unsupported operation transaction for the web3j consumer: set the operation option to one of"
                                                 + " ETH_LOG_OBSERVABLE, ETH_BLOCK_HASH_OBSERVABLE"),
                cause.getMessage());
    }

    @Test
    void operationInLowerCase() throws Exception {
        when(web3j.blockFlowable(anyBoolean())).thenReturn(Flowable.concat(Flowable.just(block("0x1")), Flowable.never()));
        result.expectedMessageCount(1);

        startRoute("&operation=block_observable");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void replayOfBlocksFromAndToTheLatestBlockByDefault() throws Exception {
        when(web3j.replayPastBlocksFlowable(eq(DefaultBlockParameterName.LATEST), eq(DefaultBlockParameterName.LATEST),
                anyBoolean())).thenReturn(Flowable.just(block("0x1")));
        // the block, then the message that ends the subscription
        result.expectedMessageCount(2);

        startRoute("&operation=" + Web3jConstants.REPLAY_BLOCKS_OBSERVABLE);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    void logFilterFromAndToTheLatestBlockByDefault() throws Exception {
        ArgumentCaptor<EthFilter> filter = ArgumentCaptor.forClass(EthFilter.class);
        when(web3j.ethLogFlowable(filter.capture())).thenReturn(Flowable.<Log> never());

        startRoute("&operation=" + Web3jConstants.ETH_LOG_OBSERVABLE);

        verify(web3j).ethLogFlowable(filter.getValue());
        assertEquals(DefaultBlockParameterName.LATEST, filter.getValue().getFromBlock());
        assertEquals(DefaultBlockParameterName.LATEST, filter.getValue().getToBlock());
    }
}
