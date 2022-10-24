package org.apache.camel.component.casper;

/**
 * Constants
 * @author mabahma
 */
public class CasperConstants {
	//RPC Calls
	public final static String ENDPOINT_SERVICE = "NETWORK_PEERS, NODE_STATUS,DEPLOY,LAST_BLOCK,BLOCK,LAST_BLOCK_TRANSFERS,BLOCK_TRANSFERS,STATE_ROOT_HASH,ACCOUNT_INFO"
            + ",AUCTION_INFO,ERA_INFO,STATE_ITEM,ACCOUNT_BALANC,ERPC_SCHEMA,GLOBAL_STATE,VALIDATOR_CHANGES,DICTIONARY_ITEM";
   //Consumer events
	public final static String ENDPOINT_EVENTS = "DEPLOY_PROCESSED,DEPLOY_ACCEPTED,BLOCK_ADDED,FINALITY_SIGNATURE,STEP";
    //RPC OPERATION
	public final static String OPERATION = "OPERATION";
    //Query peers
	public final static String NETWORK_PEERS = "NETWORK_PEERS";
    // Get node status
	public final static String NODE_STATUS = "NODE_STATUS";
    //Query deploy
	public final static String DEPLOY = "DEPLOY";
	public final static String DEPLOY_HASH = "DEPLOY_HASH";
    //Query a block
	public final static String LAST_BLOCK = "LAST_BLOCK";
	public final static String BLOCK = "BLOCK";
	public final static String BLOCK_HEIGHT = "BLOCK_HEIGHT";
	public final static String BLOCK_HASH = "BLOCK_HASH";
    //Query transfers : params===>  BLOCK_HASH or  BLOCK_HEIGHT
	public final static String LAST_BLOCK_TRANSFERS = "LAST_BLOCK_TRANSFERS";
	public final static String BLOCK_TRANSFERS = "BLOCK_TRANSFERS";
    //Query state root hash :  params===>  BLOCK_HASH or  BLOCK_HEIGHT
	public  final static String STATE_ROOT_HASH = "STATE_ROOT_HASH";
    //Get account info  :  params===>  BLOCK_HASH or  BLOCK_HEIGHT
	public final static String ACCOUNT_INFO = "ACCOUNT_INFO";
	public final static String PUBLIC_KEY = "PUBLIC_KEY";
    //Get auction info : params===>  BLOCK_HASH or  BLOCK_HEIGHT
	public final static String AUCTION_INFO = "AUCTION_INFO";
    //Get era info :  params===>  BLOCK_HASH or  BLOCK_HEIGHT
	public final static String ERA_INFO = "ERA_INFO";
    //Query stored value :  params===>STATE_ROOT_HASH
	public final static String STATE_ITEM = "STATE_ITEM";
    //Query stored value :  params===>STATE_ROOT_HASH
	public final static String DICTIONARY_ITEM = "DICTIONARY_ITEM";
	public final static String PATH = "PATH";
	public final static String ITEM_KEY = "ITEM_KEY";
    //Account Balance
	public final static String ACCOUNT_BALANCE = "ACCOUNT_BALANCE";
	public final static String PURSE_UREF = "PURSE_UREF";
    //String 	DICTIONNARY KEY parameter
	public final static String DICTIONARY_ITEM_KEY = "DICTIONNARY_ITEM_KEY";
	//String 	SEED_UREF  parameter
	public final static String SEED_UREF = "SEED_UREF";
	
    //Put_Depoy
	public final static String PUT_DEPLOY = "PUT_DEPLOY";
    //validator_changes
	public final static String VALIDATOR_CHANGES="VALIDATOR_CHANGES";
    //global_state
	public final static String GLOBAL_STATE = "GLOBAL_STATE";
    //RPC Schema
	public final static String RPC_SCHEMA = "RPC_SCHEMA";
  
	public final static String NETWORK_VERSION = "NETWORK_VERSION";
	public final static String NETWORK_FEES = "NETWORK_FEES";
    //Consumer valid url paths
	public final static String CONSUMER_PATHS = "/events/main,/events/deploys,/events/sigs";
    //Testnet nodeUrl
	public final static String TESTNET_NODE_URL = "http://65.108.1.10:7777";
	public final static String TESTNET_ENDPOINT_TEST = "casper:" + TESTNET_NODE_URL;
	public final static String ERROR_CAUSE = "ERROR_CAUSE";
	public final static String BLOCK_ADDED="BLOCK_ADDED";
  
}
