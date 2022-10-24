package org.apache.camel.component.casper;

/**
 * Producer operations (RPC calls to casper node)
 * @author mabahma
 *
 */
public enum ProducerOperation {
	NETWORK_PEERS,
	NODE_STATUS,
	DEPLOY,
	LAST_BLOCK,
	BLOCK,
	LAST_BLOCK_TRANSFERS,
	BLOCK_TRANSFERS,
	STATE_ROOT_HASH,
	ACCOUNT_INFO,
	AUCTION_INFO,
	ERA_INFO,
	STATE_ITEM,
	ACCOUNT_BALANCE,
	PUT_DEPLOY,
	DICTIONARY_ITEM
	;

	/**
	 * findByName
	 * @param name : name to search
	 * @return: ProducerOperation
	 */
	public static ProducerOperation findByName(String name) {
		ProducerOperation result = null;
	    for (ProducerOperation operation : values()) {
	        if (operation.name().equalsIgnoreCase(name)) {
	            result = operation;
	            break;
	        }
	    }
	    return result;
	}
}
