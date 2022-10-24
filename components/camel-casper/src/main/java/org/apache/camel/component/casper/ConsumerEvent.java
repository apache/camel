package org.apache.camel.component.casper;

/**
 * Consumer events
 * @author mabahma
 *
 */
public enum ConsumerEvent {
	DEPLOY_PROCESSED,
	DEPLOY_ACCEPTED,
	DEPLOY_EXPIRED,
	BLOCK_ADDED,
	FINALITY_SIGNATURE,
	FAULT,
	STEP;
	/**
	 *  findByName
	 * @param name : name to search
	 * @return: ProducerOperation
	 */
	public static ConsumerEvent findByName(String name) {
		ConsumerEvent result = null;
	    for (ConsumerEvent operation : values()) {
	        if (operation.name().equalsIgnoreCase(name)) {
	            result = operation;
	            break;
	        }
	    }
	    return result;
	}
}
