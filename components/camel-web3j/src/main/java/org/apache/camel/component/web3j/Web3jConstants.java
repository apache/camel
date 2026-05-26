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

import org.apache.camel.spi.Metadata;

public interface Web3jConstants {
    String WEB3_CLIENT_VERSION = "WEB3_CLIENT_VERSION";
    String WEB3_SHA3 = "WEB3_SHA3";
    String NET_VERSION = "NET_VERSION";
    String NET_LISTENING = "NET_LISTENING";
    String NET_PEER_COUNT = "NET_PEER_COUNT";
    String ETH_PROTOCOL_VERSION = "ethProtocolVersion";
    String ETH_COINBASE = "ETH_COINBASE";
    String ETH_SYNCING = "ETH_SYNCING";
    String ETH_MINING = "ETH_MINING";
    @Metadata(label = "producer", description = "A hexadecimal string representation (32 bytes) of the hash rate.",
              javaType = "String")
    String ETH_HASHRATE = "CamelWeb3jEthHashrate";
    String ETH_GAS_PRICE = "ETH_GAS_PRICE";
    String ETH_ACCOUNTS = "ETH_ACCOUNTS";
    String ETH_BLOCK_NUMBER = "ETH_BLOCK_NUMBER";
    String ETH_GET_BALANCE = "ETH_GET_BALANCE";
    String ETH_GET_STORAGE_AT = "ETH_GET_STORAGE_AT";
    String ETH_GET_TRANSACTION_COUNT = "ETH_GET_TRANSACTION_COUNT";
    String ETH_GET_BLOCK_TRANSACTION_COUNT_BY_HASH = "ETH_GET_BLOCK_TRANSACTION_COUNT_BY_HASH";
    String ETH_GET_BLOCK_TRANSACTION_COUNT_BY_NUMBER = "ETH_GET_BLOCK_TRANSACTION_COUNT_BY_NUMBER";
    String ETH_GET_UNCLE_COUNT_BY_BLOCK_HASH = "ETH_GET_UNCLE_COUNT_BY_BLOCK_HASH";
    String ETH_GET_UNCLE_COUNT_BY_BLOCK_NUMBER = "ETH_GET_UNCLE_COUNT_BY_BLOCK_NUMBER";
    String ETH_GET_CODE = "ETH_GET_CODE";
    String ETH_SIGN = "ETH_SIGN";
    String ETH_SEND_TRANSACTION = "ETH_SEND_TRANSACTION";
    String ETH_SEND_RAW_TRANSACTION = "ETH_SEND_RAW_TRANSACTION";
    String ETH_CALL = "ETH_CALL";
    String ETH_ESTIMATE_GAS = "ETH_ESTIMATE_GAS";
    String ETH_GET_BLOCK_BY_HASH = "ETH_GET_BLOCK_BY_HASH";
    String ETH_GET_BLOCK_BY_NUMBER = "ETH_GET_BLOCK_BY_NUMBER";
    String ETH_GET_TRANSACTION_BY_HASH = "ETH_GET_TRANSACTION_BY_HASH";
    String ETH_GET_TRANSACTION_BY_BLOCK_HASH_AND_INDEX = "ETH_GET_TRANSACTION_BY_BLOCK_HASH_AND_INDEX";
    String ETH_GET_TRANSACTION_BY_BLOCK_NUMBER_AND_INDEX = "ETH_GET_TRANSACTION_BY_BLOCK_NUMBER_AND_INDEX";
    String ETH_GET_TRANSACTION_RECEIPT = "ETH_GET_TRANSACTION_RECEIPT";
    String ETH_GET_UNCLE_BY_BLOCK_HASH_AND_INDEX = "ETH_GET_UNCLE_BY_BLOCK_HASH_AND_INDEX";
    String ETH_GET_UNCLE_BY_BLOCK_NUMBER_AND_INDEX = "ETH_GET_UNCLE_BY_BLOCK_NUMBER_AND_INDEX";
    String ETH_GET_COMPILERS = "ETH_GET_COMPILERS";
    String ETH_COMPILE_LLL = "ETH_COMPILE_LLL";
    String ETH_COMPILE_SOLIDITY = "ETH_COMPILE_SOLIDITY";
    String ETH_COMPILE_SERPENT = "ETH_COMPILE_SERPENT";
    String ETH_NEW_FILTER = "ETH_NEW_FILTER";
    String ETH_NEW_BLOCK_FILTER = "ETH_NEW_BLOCK_FILTER";
    String ETH_NEW_PENDING_TRANSACTION_FILTER = "ETH_NEW_PENDING_TRANSACTION_FILTER";
    String ETH_UNINSTALL_FILTER = "ETH_UNINSTALL_FILTER";
    String ETH_GET_FILTER_CHANGES = "ETH_GET_FILTER_CHANGES";
    String ETH_GET_FILTER_LOGS = "ETH_GET_FILTER_LOGS";
    String ETH_GET_LOGS = "ETH_GET_LOGS";
    String ETH_GET_WORK = "ETH_GET_WORK";
    String ETH_SUBMIT_WORK = "ETH_SUBMIT_WORK";
    String ETH_SUBMIT_HASHRATE = "ETH_SUBMIT_HASHRATE";
    String DB_PUT_STRING = "DB_PUT_STRING";
    String DB_GET_STRING = "DB_GET_STRING";
    String DB_PUT_HEX = "DB_PUT_HEX";
    String DB_GET_HEX = "DB_GET_HEX";
    String SHH_VERSION = "SHH_VERSION";
    String SHH_POST = "SHH_POST";
    String SHH_NEW_IDENTITY = "SHH_NEW_IDENTITY";
    String SHH_HAS_IDENTITY = "SHH_HAS_IDENTITY";
    String SHH_NEW_GROUP = "SHH_NEW_GROUP";
    String SHH_ADD_TO_GROUP = "SHH_ADD_TO_GROUP";
    String SHH_NEW_FILTER = "SHH_NEW_FILTER";
    String SHH_UNINSTALL_FILTER = "SHH_UNINSTALL_FILTER";
    String SHH_GET_FILTER_CHANGES = "SHH_GET_FILTER_CHANGES";
    String SHH_GET_MESSAGES = "SHH_GET_MESSAGES";

    String QUORUM_ETH_SEND_TRANSACTION = "QUORUM_ETH_SEND_TRANSACTION";
    String QUORUM_NODE_INFO = "QUORUM_NODE_INFO";
    String QUORUM_GET_PRIVATE_PAYLOAD = "QUORUM_GET_PRIVATE_PAYLOAD";

    String ETH_LOG_OBSERVABLE = "ETH_LOG_OBSERVABLE";
    String ETH_BLOCK_HASH_OBSERVABLE = "ETH_BLOCK_HASH_OBSERVABLE";
    String ETH_PENDING_TRANSACTION_HASH_OBSERVABLE = "ETH_PENDING_TRANSACTION_HASH_OBSERVABLE";
    String TRANSACTION_OBSERVABLE = "TRANSACTION_OBSERVABLE";
    String PENDING_TRANSACTION_OBSERVABLE = "PENDING_TRANSACTION_OBSERVABLE";
    String BLOCK_OBSERVABLE = "BLOCK_OBSERVABLE";
    String REPLAY_BLOCKS_OBSERVABLE = "REPLAY_BLOCKS_OBSERVABLE";
    String REPLAY_TRANSACTIONS_OBSERVABLE = "REPLAY_TRANSACTIONS_OBSERVABLE";

    @Metadata(label = "producer", description = "The id", javaType = "Long")
    String ID = "CamelWeb3jId";
    @Metadata(label = "producer", description = "The operation to perform", javaType = "String")
    String OPERATION = "CamelWeb3jOperation";
    String TRANSACTION = "TRANSACTION";
    /**
     * The block number, or the string "latest" for the last mined block or "pending", "earliest" for not yet mined
     * transactions.
     */
    @Metadata(label = "producer", javaType = "String")
    String AT_BLOCK = "CamelWeb3jAtBlock";
    @Metadata(label = "producer", description = "Contract address.", javaType = "String")
    String ADDRESS = "CamelWeb3jAddress";
    @Metadata(label = "producer", description = "Contract address or a list of addresses.", javaType = "List<String>")
    String ADDRESSES = "CamelWeb3jAddresses";
    @Metadata(label = "producer", description = "The address the transaction is send from", javaType = "String")
    String FROM_ADDRESS = "CamelWeb3jFromAddress";
    @Metadata(label = "producer", description = "The address the transaction is directed to", javaType = "String")
    String TO_ADDRESS = "CamelWeb3jToAddress";
    @Metadata(label = "producer", description = "The transaction index position withing a block.", javaType = "String")
    String POSITION = "CamelWeb3jPosition";
    @Metadata(label = "producer", description = "Hash of the block where this transaction was in.", javaType = "String")
    String BLOCK_HASH = "CamelWeb3jBlockHash";
    @Metadata(label = "producer", description = "The information about a transaction requested by transaction hash.",
              javaType = "String")
    String TRANSACTION_HASH = "CamelWeb3jTransactionHash";
    @Metadata(label = "producer", description = "Message to sign by calculating an Ethereum specific signature.",
              javaType = "String")
    String SHA3_HASH_OF_DATA_TO_SIGN = "CamelWeb3jSha3HashOfDataToSign";
    @Metadata(label = "producer",
              description = "The signed transaction data for a new message call transaction or a contract creation for signed transactions.",
              javaType = "String")
    String SIGNED_TRANSACTION_DATA = "CamelWeb3jSignedTransactionData";
    @Metadata(label = "producer",
              description = "If true it returns the full transaction objects, if false only the hashes of the transactions.",
              javaType = "Boolean")
    String FULL_TRANSACTION_OBJECTS = "CamelWeb3jFullTransactionObjects";
    @Metadata(label = "producer", description = "The transactions/uncle index position in the block.", javaType = "String")
    String INDEX = "CamelWeb3jIndex";
    @Metadata(label = "producer", description = "The source code to compile.", javaType = "String")
    String SOURCE_CODE = "CamelWeb3jSourceCode";
    @Metadata(label = "producer", description = "The filter id to use.", javaType = "java.math.BigInteger")
    String FILTER_ID = "CamelWeb3jFilterId";
    @Metadata(label = "producer", description = "The local database name.", javaType = "String")
    String DATABASE_NAME = "CamelWeb3jDatabaseName";
    @Metadata(label = "producer", description = "The key name in the database.", javaType = "String")
    String KEY_NAME = "CamelWeb3jKeyName";
    @Metadata(label = "producer", description = "The nonce found (64 bits) used for submitting a proof-of-work solution.",
              javaType = "java.math.BigInteger")
    String NONCE = "CamelWeb3jNonce";
    @Metadata(label = "producer",
              description = "The header's pow-hash (256 bits) used for submitting a proof-of-work solution.",
              javaType = "String")
    String HEADER_POW_HASH = "CamelWeb3jHeaderPowHash";
    @Metadata(label = "producer", description = "The mix digest (256 bits) used for submitting a proof-of-work solution.",
              javaType = "String")
    String MIX_DIGEST = "CamelWeb3jMixDigest";
    @Metadata(label = "producer", description = "A random hexadecimal(32 bytes) ID identifying the client.",
              javaType = "String")
    String CLIENT_ID = "CamelWeb3jClientId";
    @Metadata(label = "producer", description = "Gas price used for each paid gas.", javaType = "java.math.BigInteger")
    String GAS_PRICE = "CamelWeb3jGasPrice";
    @Metadata(label = "producer", description = "The maximum gas allowed in this block.", javaType = "java.math.BigInteger")
    String GAS_LIMIT = "CamelWeb3jGasLimit";
    @Metadata(label = "producer", description = "The value sent within a transaction.", javaType = "java.math.BigInteger")
    String VALUE = "CamelWeb3jValue";
    @Metadata(label = "producer",
              description = "The compiled code of a contract OR the hash of the invoked method signature and encoded parameters.",
              javaType = "String")
    String DATA = "CamelWeb3jData";
    /**
     * The block number, or the string "latest" for the last mined block or "pending", "earliest" for not yet mined
     * transactions.
     */
    @Metadata(label = "producer", javaType = "String")
    String FROM_BLOCK = "CamelWeb3jFromBlock";
    /**
     * The block number, or the string "latest" for the last mined block or "pending", "earliest" for not yet mined
     * transactions.
     */
    @Metadata(label = "producer", javaType = "String")
    String TO_BLOCK = "CamelWeb3jToBlock";
    @Metadata(label = "producer", description = "Topics are order-dependent. Each topic can also be a list of topics. " +
                                                "Specify multiple topics separated by comma.",
              javaType = "List<String>")
    String TOPICS = "CamelWeb3jTopics";
    @Metadata(label = "producer", description = "The priority of a whisper message.", javaType = "java.math.BigInteger")
    String PRIORITY = "CamelWeb3jPriority";
    @Metadata(label = "producer", description = "The time to live in seconds of a whisper message.",
              javaType = "java.math.BigInteger")
    String TTL = "CamelWeb3jTtl";
    @Metadata(label = "producer", description = "A transaction privateFor nodes with public keys in a Quorum network",
              javaType = "List<String>")
    String PRIVATE_FOR = "CamelWeb3jPrivateFor";
    @Metadata(label = "producer", description = "A transaction privateFrom",
              javaType = "String")
    String PRIVATE_FROM = "CamelWeb3jPrivateFrom";

    @Metadata(label = "producer", description = "The error code", javaType = "int")
    String ERROR_CODE = "CamelWeb3jErrorCode";
    @Metadata(label = "producer", description = "The error data", javaType = "String")
    String ERROR_DATA = "CamelWeb3jErrorData";
    @Metadata(label = "producer", description = "The error message", javaType = "String")
    String ERROR_MESSAGE = "CamelWeb3jErrorMessage";

    @Metadata(label = "consumer", description = "The status of the operation", javaType = "String")
    String HEADER_STATUS = "CamelWeb3jStatus";
    @Metadata(label = "consumer", description = "The operation", javaType = "String")
    String HEADER_OPERATION = "CamelWeb3jHeaderOperation";
}
