/* Generated by camel build tools - do NOT edit this file! */
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
package org.apache.camel.builder.component.dsl;

import javax.annotation.processing.Generated;
import org.apache.camel.Component;
import org.apache.camel.builder.component.AbstractComponentBuilder;
import org.apache.camel.builder.component.ComponentBuilder;
import org.apache.camel.component.web3j.Web3jComponent;

/**
 * Interact with Ethereum nodes using web3j client API.
 * 
 * Generated by camel build tools - do NOT edit this file!
 */
@Generated("org.apache.camel.maven.packaging.ComponentDslMojo")
public interface Web3jComponentBuilderFactory {

    /**
     * Web3j Ethereum Blockchain (camel-web3j)
     * Interact with Ethereum nodes using web3j client API.
     * 
     * Category: blockchain
     * Since: 2.22
     * Maven coordinates: org.apache.camel:camel-web3j
     * 
     * @return the dsl builder
     */
    static Web3jComponentBuilder web3j() {
        return new Web3jComponentBuilderImpl();
    }

    /**
     * Builder for the Web3j Ethereum Blockchain component.
     */
    interface Web3jComponentBuilder extends ComponentBuilder<Web3jComponent> {
    
        /**
         * Contract address or a comma separated list of addresses.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: common
         * 
         * @param addresses the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder addresses(java.lang.String addresses) {
            doSetProperty("addresses", addresses);
            return this;
        }
    
        /**
         * Default configuration.
         * 
         * The option is a:
         * &lt;code&gt;org.apache.camel.component.web3j.Web3jConfiguration&lt;/code&gt; type.
         * 
         * Group: common
         * 
         * @param configuration the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder configuration(org.apache.camel.component.web3j.Web3jConfiguration configuration) {
            doSetProperty("configuration", configuration);
            return this;
        }
    
        /**
         * The address the transaction is send from.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: common
         * 
         * @param fromAddress the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder fromAddress(java.lang.String fromAddress) {
            doSetProperty("fromAddress", fromAddress);
            return this;
        }
    
        
        /**
         * The block number, or the string latest for the last mined block or
         * pending, earliest for not yet mined transactions.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Default: latest
         * Group: common
         * 
         * @param fromBlock the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder fromBlock(java.lang.String fromBlock) {
            doSetProperty("fromBlock", fromBlock);
            return this;
        }
    
        
        /**
         * If true it returns the full transaction objects, if false only the
         * hashes of the transactions.
         * 
         * The option is a: &lt;code&gt;boolean&lt;/code&gt; type.
         * 
         * Default: false
         * Group: common
         * 
         * @param fullTransactionObjects the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder fullTransactionObjects(boolean fullTransactionObjects) {
            doSetProperty("fullTransactionObjects", fullTransactionObjects);
            return this;
        }
    
        /**
         * The maximum gas allowed in this block.
         * 
         * The option is a: &lt;code&gt;java.math.BigInteger&lt;/code&gt; type.
         * 
         * Group: common
         * 
         * @param gasLimit the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder gasLimit(java.math.BigInteger gasLimit) {
            doSetProperty("gasLimit", gasLimit);
            return this;
        }
    
        /**
         * A comma separated transaction privateFor nodes with public keys in a
         * Quorum network.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: common
         * 
         * @param privateFor the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder privateFor(java.lang.String privateFor) {
            doSetProperty("privateFor", privateFor);
            return this;
        }
    
        
        /**
         * If true, this will support Quorum API.
         * 
         * The option is a: &lt;code&gt;boolean&lt;/code&gt; type.
         * 
         * Default: false
         * Group: common
         * 
         * @param quorumAPI the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder quorumAPI(boolean quorumAPI) {
            doSetProperty("quorumAPI", quorumAPI);
            return this;
        }
    
        /**
         * The address the transaction is directed to.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: common
         * 
         * @param toAddress the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder toAddress(java.lang.String toAddress) {
            doSetProperty("toAddress", toAddress);
            return this;
        }
    
        
        /**
         * The block number, or the string latest for the last mined block or
         * pending, earliest for not yet mined transactions.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Default: latest
         * Group: common
         * 
         * @param toBlock the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder toBlock(java.lang.String toBlock) {
            doSetProperty("toBlock", toBlock);
            return this;
        }
    
        /**
         * Topics are order-dependent. Each topic can also be a list of topics.
         * Specify multiple topics separated by comma.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: common
         * 
         * @param topics the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder topics(java.lang.String topics) {
            doSetProperty("topics", topics);
            return this;
        }
    
        /**
         * The preconfigured Web3j object.
         * 
         * The option is a: &lt;code&gt;org.web3j.protocol.Web3j&lt;/code&gt;
         * type.
         * 
         * Group: common
         * 
         * @param web3j the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder web3j(org.web3j.protocol.Web3j web3j) {
            doSetProperty("web3j", web3j);
            return this;
        }
    
        
        /**
         * Allows for bridging the consumer to the Camel routing Error Handler,
         * which mean any exceptions (if possible) occurred while the Camel
         * consumer is trying to pickup incoming messages, or the likes, will
         * now be processed as a message and handled by the routing Error
         * Handler. Important: This is only possible if the 3rd party component
         * allows Camel to be alerted if an exception was thrown. Some
         * components handle this internally only, and therefore
         * bridgeErrorHandler is not possible. In other situations we may
         * improve the Camel component to hook into the 3rd party component and
         * make this possible for future releases. By default the consumer will
         * use the org.apache.camel.spi.ExceptionHandler to deal with
         * exceptions, that will be logged at WARN or ERROR level and ignored.
         * 
         * The option is a: &lt;code&gt;boolean&lt;/code&gt; type.
         * 
         * Default: false
         * Group: consumer
         * 
         * @param bridgeErrorHandler the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder bridgeErrorHandler(boolean bridgeErrorHandler) {
            doSetProperty("bridgeErrorHandler", bridgeErrorHandler);
            return this;
        }
    
        /**
         * Contract address.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param address the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder address(java.lang.String address) {
            doSetProperty("address", address);
            return this;
        }
    
        
        /**
         * The block number, or the string latest for the last mined block or
         * pending, earliest for not yet mined transactions.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Default: latest
         * Group: producer
         * 
         * @param atBlock the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder atBlock(java.lang.String atBlock) {
            doSetProperty("atBlock", atBlock);
            return this;
        }
    
        /**
         * Hash of the block where this transaction was in.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param blockHash the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder blockHash(java.lang.String blockHash) {
            doSetProperty("blockHash", blockHash);
            return this;
        }
    
        /**
         * A random hexadecimal(32 bytes) ID identifying the client.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param clientId the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder clientId(java.lang.String clientId) {
            doSetProperty("clientId", clientId);
            return this;
        }
    
        /**
         * The compiled code of a contract OR the hash of the invoked method
         * signature and encoded parameters.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param data the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder data(java.lang.String data) {
            doSetProperty("data", data);
            return this;
        }
    
        /**
         * The local database name.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param databaseName the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder databaseName(java.lang.String databaseName) {
            doSetProperty("databaseName", databaseName);
            return this;
        }
    
        /**
         * The filter id to use.
         * 
         * The option is a: &lt;code&gt;java.math.BigInteger&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param filterId the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder filterId(java.math.BigInteger filterId) {
            doSetProperty("filterId", filterId);
            return this;
        }
    
        /**
         * Gas price used for each paid gas.
         * 
         * The option is a: &lt;code&gt;java.math.BigInteger&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param gasPrice the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder gasPrice(java.math.BigInteger gasPrice) {
            doSetProperty("gasPrice", gasPrice);
            return this;
        }
    
        /**
         * A hexadecimal string representation (32 bytes) of the hash rate.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param hashrate the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder hashrate(java.lang.String hashrate) {
            doSetProperty("hashrate", hashrate);
            return this;
        }
    
        /**
         * The header's pow-hash (256 bits) used for submitting a proof-of-work
         * solution.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param headerPowHash the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder headerPowHash(java.lang.String headerPowHash) {
            doSetProperty("headerPowHash", headerPowHash);
            return this;
        }
    
        /**
         * The transactions/uncle index position in the block.
         * 
         * The option is a: &lt;code&gt;java.math.BigInteger&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param index the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder index(java.math.BigInteger index) {
            doSetProperty("index", index);
            return this;
        }
    
        /**
         * The key name in the database.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param keyName the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder keyName(java.lang.String keyName) {
            doSetProperty("keyName", keyName);
            return this;
        }
    
        
        /**
         * Whether the producer should be started lazy (on the first message).
         * By starting lazy you can use this to allow CamelContext and routes to
         * startup in situations where a producer may otherwise fail during
         * starting and cause the route to fail being started. By deferring this
         * startup to be lazy then the startup failure can be handled during
         * routing messages via Camel's routing error handlers. Beware that when
         * the first message is processed then creating and starting the
         * producer may take a little time and prolong the total processing time
         * of the processing.
         * 
         * The option is a: &lt;code&gt;boolean&lt;/code&gt; type.
         * 
         * Default: false
         * Group: producer
         * 
         * @param lazyStartProducer the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder lazyStartProducer(boolean lazyStartProducer) {
            doSetProperty("lazyStartProducer", lazyStartProducer);
            return this;
        }
    
        /**
         * The mix digest (256 bits) used for submitting a proof-of-work
         * solution.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param mixDigest the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder mixDigest(java.lang.String mixDigest) {
            doSetProperty("mixDigest", mixDigest);
            return this;
        }
    
        /**
         * The nonce found (64 bits) used for submitting a proof-of-work
         * solution.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param nonce the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder nonce(java.lang.String nonce) {
            doSetProperty("nonce", nonce);
            return this;
        }
    
        
        /**
         * Operation to use.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Default: transaction
         * Group: producer
         * 
         * @param operation the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder operation(java.lang.String operation) {
            doSetProperty("operation", operation);
            return this;
        }
    
        /**
         * The transaction index position withing a block.
         * 
         * The option is a: &lt;code&gt;java.math.BigInteger&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param position the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder position(java.math.BigInteger position) {
            doSetProperty("position", position);
            return this;
        }
    
        /**
         * The priority of a whisper message.
         * 
         * The option is a: &lt;code&gt;java.math.BigInteger&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param priority the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder priority(java.math.BigInteger priority) {
            doSetProperty("priority", priority);
            return this;
        }
    
        /**
         * Message to sign by calculating an Ethereum specific signature.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param sha3HashOfDataToSign the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder sha3HashOfDataToSign(java.lang.String sha3HashOfDataToSign) {
            doSetProperty("sha3HashOfDataToSign", sha3HashOfDataToSign);
            return this;
        }
    
        /**
         * The signed transaction data for a new message call transaction or a
         * contract creation for signed transactions.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param signedTransactionData the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder signedTransactionData(java.lang.String signedTransactionData) {
            doSetProperty("signedTransactionData", signedTransactionData);
            return this;
        }
    
        /**
         * The source code to compile.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param sourceCode the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder sourceCode(java.lang.String sourceCode) {
            doSetProperty("sourceCode", sourceCode);
            return this;
        }
    
        /**
         * The information about a transaction requested by transaction hash.
         * 
         * The option is a: &lt;code&gt;java.lang.String&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param transactionHash the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder transactionHash(java.lang.String transactionHash) {
            doSetProperty("transactionHash", transactionHash);
            return this;
        }
    
        /**
         * The time to live in seconds of a whisper message.
         * 
         * The option is a: &lt;code&gt;java.math.BigInteger&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param ttl the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder ttl(java.math.BigInteger ttl) {
            doSetProperty("ttl", ttl);
            return this;
        }
    
        /**
         * The value sent within a transaction.
         * 
         * The option is a: &lt;code&gt;java.math.BigInteger&lt;/code&gt; type.
         * 
         * Group: producer
         * 
         * @param value the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder value(java.math.BigInteger value) {
            doSetProperty("value", value);
            return this;
        }
    
        
        /**
         * Whether autowiring is enabled. This is used for automatic autowiring
         * options (the option must be marked as autowired) by looking up in the
         * registry to find if there is a single instance of matching type,
         * which then gets configured on the component. This can be used for
         * automatic configuring JDBC data sources, JMS connection factories,
         * AWS Clients, etc.
         * 
         * The option is a: &lt;code&gt;boolean&lt;/code&gt; type.
         * 
         * Default: true
         * Group: advanced
         * 
         * @param autowiredEnabled the value to set
         * @return the dsl builder
         */
        default Web3jComponentBuilder autowiredEnabled(boolean autowiredEnabled) {
            doSetProperty("autowiredEnabled", autowiredEnabled);
            return this;
        }
    }

    class Web3jComponentBuilderImpl
            extends AbstractComponentBuilder<Web3jComponent>
            implements Web3jComponentBuilder {
        @Override
        protected Web3jComponent buildConcreteComponent() {
            return new Web3jComponent();
        }
        private org.apache.camel.component.web3j.Web3jConfiguration getOrCreateConfiguration(Web3jComponent component) {
            if (component.getConfiguration() == null) {
                component.setConfiguration(new org.apache.camel.component.web3j.Web3jConfiguration());
            }
            return component.getConfiguration();
        }
        @Override
        protected boolean setPropertyOnComponent(
                Component component,
                String name,
                Object value) {
            switch (name) {
            case "addresses": getOrCreateConfiguration((Web3jComponent) component).setAddresses((java.lang.String) value); return true;
            case "configuration": ((Web3jComponent) component).setConfiguration((org.apache.camel.component.web3j.Web3jConfiguration) value); return true;
            case "fromAddress": getOrCreateConfiguration((Web3jComponent) component).setFromAddress((java.lang.String) value); return true;
            case "fromBlock": getOrCreateConfiguration((Web3jComponent) component).setFromBlock((java.lang.String) value); return true;
            case "fullTransactionObjects": getOrCreateConfiguration((Web3jComponent) component).setFullTransactionObjects((boolean) value); return true;
            case "gasLimit": getOrCreateConfiguration((Web3jComponent) component).setGasLimit((java.math.BigInteger) value); return true;
            case "privateFor": getOrCreateConfiguration((Web3jComponent) component).setPrivateFor((java.lang.String) value); return true;
            case "quorumAPI": getOrCreateConfiguration((Web3jComponent) component).setQuorumAPI((boolean) value); return true;
            case "toAddress": getOrCreateConfiguration((Web3jComponent) component).setToAddress((java.lang.String) value); return true;
            case "toBlock": getOrCreateConfiguration((Web3jComponent) component).setToBlock((java.lang.String) value); return true;
            case "topics": getOrCreateConfiguration((Web3jComponent) component).setTopics((java.lang.String) value); return true;
            case "web3j": getOrCreateConfiguration((Web3jComponent) component).setWeb3j((org.web3j.protocol.Web3j) value); return true;
            case "bridgeErrorHandler": ((Web3jComponent) component).setBridgeErrorHandler((boolean) value); return true;
            case "address": getOrCreateConfiguration((Web3jComponent) component).setAddress((java.lang.String) value); return true;
            case "atBlock": getOrCreateConfiguration((Web3jComponent) component).setAtBlock((java.lang.String) value); return true;
            case "blockHash": getOrCreateConfiguration((Web3jComponent) component).setBlockHash((java.lang.String) value); return true;
            case "clientId": getOrCreateConfiguration((Web3jComponent) component).setClientId((java.lang.String) value); return true;
            case "data": getOrCreateConfiguration((Web3jComponent) component).setData((java.lang.String) value); return true;
            case "databaseName": getOrCreateConfiguration((Web3jComponent) component).setDatabaseName((java.lang.String) value); return true;
            case "filterId": getOrCreateConfiguration((Web3jComponent) component).setFilterId((java.math.BigInteger) value); return true;
            case "gasPrice": getOrCreateConfiguration((Web3jComponent) component).setGasPrice((java.math.BigInteger) value); return true;
            case "hashrate": getOrCreateConfiguration((Web3jComponent) component).setHashrate((java.lang.String) value); return true;
            case "headerPowHash": getOrCreateConfiguration((Web3jComponent) component).setHeaderPowHash((java.lang.String) value); return true;
            case "index": getOrCreateConfiguration((Web3jComponent) component).setIndex((java.math.BigInteger) value); return true;
            case "keyName": getOrCreateConfiguration((Web3jComponent) component).setKeyName((java.lang.String) value); return true;
            case "lazyStartProducer": ((Web3jComponent) component).setLazyStartProducer((boolean) value); return true;
            case "mixDigest": getOrCreateConfiguration((Web3jComponent) component).setMixDigest((java.lang.String) value); return true;
            case "nonce": getOrCreateConfiguration((Web3jComponent) component).setNonce((java.lang.String) value); return true;
            case "operation": getOrCreateConfiguration((Web3jComponent) component).setOperation((java.lang.String) value); return true;
            case "position": getOrCreateConfiguration((Web3jComponent) component).setPosition((java.math.BigInteger) value); return true;
            case "priority": getOrCreateConfiguration((Web3jComponent) component).setPriority((java.math.BigInteger) value); return true;
            case "sha3HashOfDataToSign": getOrCreateConfiguration((Web3jComponent) component).setSha3HashOfDataToSign((java.lang.String) value); return true;
            case "signedTransactionData": getOrCreateConfiguration((Web3jComponent) component).setSignedTransactionData((java.lang.String) value); return true;
            case "sourceCode": getOrCreateConfiguration((Web3jComponent) component).setSourceCode((java.lang.String) value); return true;
            case "transactionHash": getOrCreateConfiguration((Web3jComponent) component).setTransactionHash((java.lang.String) value); return true;
            case "ttl": getOrCreateConfiguration((Web3jComponent) component).setTtl((java.math.BigInteger) value); return true;
            case "value": getOrCreateConfiguration((Web3jComponent) component).setValue((java.math.BigInteger) value); return true;
            case "autowiredEnabled": ((Web3jComponent) component).setAutowiredEnabled((boolean) value); return true;
            default: return false;
            }
        }
    }
}