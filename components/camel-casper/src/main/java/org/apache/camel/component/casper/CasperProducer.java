package org.apache.camel.component.casper;

import java.io.IOException;
import java.util.Arrays;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Message;
import org.apache.camel.spi.InvokeOnHeader;
import org.apache.camel.support.HeaderSelectorProducer;
import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

import com.syntifi.casper.sdk.identifier.block.HashBlockIdentifier;
import com.syntifi.casper.sdk.identifier.block.HeightBlockIdentifier;
import com.syntifi.casper.sdk.identifier.dictionary.URefDictionaryIdentifier;
import com.syntifi.casper.sdk.identifier.dictionary.URefSeed;
import com.syntifi.casper.sdk.model.account.AccountData;
import com.syntifi.casper.sdk.model.auction.AuctionData;
import com.syntifi.casper.sdk.model.balance.BalanceData;
import com.syntifi.casper.sdk.model.block.JsonBlock;
import com.syntifi.casper.sdk.model.deploy.Deploy;
import com.syntifi.casper.sdk.model.deploy.DeployData;
import com.syntifi.casper.sdk.model.deploy.DeployResult;
import com.syntifi.casper.sdk.model.dictionary.DictionaryData;
import com.syntifi.casper.sdk.model.era.EraInfoData;
import com.syntifi.casper.sdk.model.peer.PeerData;
import com.syntifi.casper.sdk.model.stateroothash.StateRootHashData;
import com.syntifi.casper.sdk.model.status.StatusData;
import com.syntifi.casper.sdk.model.storedvalue.StoredValueData;
import com.syntifi.casper.sdk.model.transfer.TransferData;
import com.syntifi.casper.sdk.model.uref.URef;
import com.syntifi.casper.sdk.service.CasperService;

/**
 * Camel CasperProducer component
 * 
 * @author mabahma
 *
 */
@SuppressWarnings("unused")
public class CasperProducer extends HeaderSelectorProducer {

	/**
	 * Camel Casper endpoint
	 */
	private final CasperEndPoint endpoint;
	/**
	 * Casper Java Node API
	 */
	private final CasperService casperService;

	/**
	 * Casper Camel configuration
	 */
	private final CasperConfiguration configuration;

	/**
	 * CasperProducer constructor
	 * 
	 * @param endpoint      : Casper endpoint
	 * @param configuration : CasperConfiguration
	 */
	public CasperProducer(CasperEndPoint endpoint, final CasperConfiguration configuration) {
		super(endpoint, CasperConstants.OPERATION, () -> configuration.getOperationOrDefault(), false);
		this.endpoint = endpoint;
		this.configuration = configuration;
		this.casperService = endpoint.getCasperService();
	}

	@Override
	public CasperEndPoint getEndpoint() {
		return (CasperEndPoint) super.getEndpoint();
	}

	/**
	 * Call to getPeers
	 *
	 * @param message
	 * @throws IOException
	 */
	@InvokeOnHeader(CasperConstants.NETWORK_PEERS)
	void listPeers(Message message) {
		PeerData peerData = null;
		try {
			peerData = casperService.getPeerData();
		} catch (Exception e) {
			handleError(e.getCause(), message);
		}
		if (peerData != null)
			message.setBody(peerData.getPeers());
	}

	/**
	 * Call to getStatus
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.NODE_STATUS)
	void nodeStatus(Message message) {
		StatusData statusData = null;
		try {
			statusData = casperService.getStatus();
		} catch (Exception e) {
			handleError(e.getCause(), message);
		}
		if (statusData != null)
			message.setBody(statusData);
	}

	/**
	 * Call to getDeploy
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.DEPLOY)
	void deploy(Message message) {
		DeployData deploy = null;
		String deployHash = message.getHeader(CasperConstants.DEPLOY_HASH, configuration::getDeployHash, String.class);
		if (StringUtils.isEmpty(deployHash)) {
			handleError(new MissingArgumentException("deployHash parameter is required with endpoint operation " + CasperConstants.DEPLOY), message);
			return;
		}
		try {
			deploy = casperService.getDeploy(deployHash);
		} catch (Exception e) {
			handleError(e.getCause(), message);
		}
		if (deploy != null)
			message.setBody(deploy.getDeploy());
	}

	/**
	 * Call to getBlock without parameters --> Last Block
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.LAST_BLOCK)
	void lastBlock(Message message) {
		JsonBlock block = null;
		try {
			block = casperService.getBlock().getBlock();
		} catch (Exception e) {
			handleError(e.getCause(), message);
		}
		if (block != null)
			message.setBody(block);
	}

	/**
	 * Call to getBlock without with a block hash or a block height
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.BLOCK)
	void block(Message message) {
		JsonBlock block = null;
		String blockHash = message.getHeader(CasperConstants.BLOCK_HASH, configuration::getBlockHash, String.class);
		Long blockHeight = message.getHeader(CasperConstants.BLOCK_HEIGHT, configuration::getBlockHeight, Long.class);
		try {
			if (!StringUtils.isEmpty(blockHash))
				block = casperService.getBlock(new HashBlockIdentifier(blockHash)).getBlock();
			else if (blockHeight != null && blockHeight >= 0)
				block = casperService.getBlock(new HeightBlockIdentifier(blockHeight)).getBlock();
			else
				// get last block
				block = casperService.getBlock().getBlock();
		} catch (Exception e) {
			handleError(e.getCause(), message);
		}
		if (block != null)
			message.setBody(block);
	}

	/**
	 * Call to getStateRootHash
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.STATE_ROOT_HASH)
	void stateRootHash(Message message) {
		StateRootHashData stateRootHashData = null;
		String blockHash = message.getHeader(CasperConstants.BLOCK_HASH, configuration::getBlockHash, String.class);
		Long blockHeight = message.getHeader(CasperConstants.BLOCK_HEIGHT, configuration::getBlockHeight, Long.class);
		try {
			if (!StringUtils.isEmpty(blockHash))
				stateRootHashData = casperService.getStateRootHash(new HashBlockIdentifier(blockHash));
			else if (blockHeight != null && blockHeight >= 0)
				stateRootHashData = casperService.getStateRootHash(new HeightBlockIdentifier(blockHeight));
			else
				stateRootHashData = casperService.getStateRootHash();
		} catch (Exception e) {
			handleError(e.getCause(), message);
		}
		if (stateRootHashData != null)
			message.setBody(stateRootHashData);
	}

	/**
	 * Call to getStateAccountInfo
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.ACCOUNT_INFO)
	void accountInfo(Message message) {
		AccountData accountData = null;
		String blockHash = message.getHeader(CasperConstants.BLOCK_HASH, configuration::getBlockHash, String.class);
		Long blockHeight = message.getHeader(CasperConstants.BLOCK_HEIGHT, configuration::getBlockHeight, Long.class);
		String publicKey = message.getHeader(CasperConstants.PUBLIC_KEY, configuration::getPublicKey, String.class);
		if (StringUtils.isEmpty(publicKey)) {
			handleError(new MissingArgumentException("publicKey parameter is required  with endpoint operation " + CasperConstants.ACCOUNT_INFO), message);
			return;
		}
		try {
			if (!StringUtils.isEmpty(blockHash))
				accountData = casperService.getStateAccountInfo(publicKey, new HashBlockIdentifier(blockHash));
			else if (blockHeight != null)
				accountData = casperService.getStateAccountInfo(publicKey, new HeightBlockIdentifier(blockHeight));
			else
				handleError(new MissingArgumentException("Either blockHeight or BlockHash parameter is required  with endpoint operation " + CasperConstants.ACCOUNT_INFO), message);
		} catch (Exception e) {
			handleError(e.getCause(), message);
		}
		if (accountData != null)
			message.setBody(accountData);
	}

	/**
	 * Call to getBlockTransfers without parameters---> gives latest block transfers
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.LAST_BLOCK_TRANSFERS)
	void lastBlockTransfers(Message message) {
		TransferData transferData = null;
		try {
			transferData = casperService.getBlockTransfers();
		} catch (Exception e) {
			handleError(e.getCause(), message);
		}
		if (transferData != null)
			message.setBody(transferData.getTransfers());
	}

	/**
	 * Call to getBlockTransfers with a block hash or a block height
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.BLOCK_TRANSFERS)
	void blockTransfers(Message message) {
		TransferData transferData = null;
		String blockHash = message.getHeader(CasperConstants.BLOCK_HASH, configuration::getBlockHash, String.class);
		Long blockHeight = message.getHeader(CasperConstants.BLOCK_HEIGHT, configuration::getBlockHeight, Long.class);
		try {
			if (!StringUtils.isEmpty(blockHash))
				transferData = casperService.getBlockTransfers(new HashBlockIdentifier(blockHash));
			else if (blockHeight != null && blockHeight >= 0)
				transferData = casperService.getBlockTransfers(new HeightBlockIdentifier(blockHeight));
			else
				// get latest ones
				transferData = casperService.getBlockTransfers();
		} catch (Exception e) {
			handleError(e.getCause(), message);
		}
		if (transferData != null)
			message.setBody(transferData.getTransfers());
	}

	/**
	 * Call to getStateAuctionInfo
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.AUCTION_INFO)
	void auctionInfo(Message message) {
		AuctionData auction = null;
		String blockHash = message.getHeader(CasperConstants.BLOCK_HASH, configuration::getBlockHash, String.class);
		Long blockHeight = message.getHeader(CasperConstants.BLOCK_HEIGHT, configuration::getBlockHeight, Long.class);
		try {
			if (!StringUtils.isEmpty(blockHash))
				auction = casperService.getStateAuctionInfo(new HashBlockIdentifier(blockHash));
			else if (blockHeight != null && blockHeight >= 0)
				auction = casperService.getStateAuctionInfo(new HeightBlockIdentifier(blockHeight));
			else
				// get latest one
				auction = casperService.getStateAuctionInfo(null);
		} catch (Exception e) {
			handleError(e.getCause(), message);
		}
		if (auction != null)
			message.setBody(auction.getAuctionState());

	}

	/**
	 * Call to getEraInfoBySwitchBlock
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.ERA_INFO)
	void eraInfo(Message message) {
		EraInfoData eraInfoData = null;
		String blockHash = message.getHeader(CasperConstants.BLOCK_HASH, configuration::getBlockHash, String.class);
		Long blockHeight = message.getHeader(CasperConstants.BLOCK_HEIGHT, configuration::getBlockHeight, Long.class);
		try {
			if (!StringUtils.isEmpty(blockHash))
				eraInfoData = casperService.getEraInfoBySwitchBlock(new HashBlockIdentifier(blockHash));
			else if (blockHeight != null && blockHeight >= 0)
				eraInfoData = casperService.getEraInfoBySwitchBlock(new HeightBlockIdentifier(blockHeight));
			else
				handleError(new MissingArgumentException("Either blockHeight or BlockHash parameter is required  with endpoint operation " + CasperConstants.ACCOUNT_INFO), message);
		} catch (Exception e) {
			handleError(e.getCause(), message);
		}
		if (eraInfoData != null)
			message.setBody(eraInfoData.getEraSummary());
	}

	/**
	 * Call to getStateItem
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.STATE_ITEM)
	void storedValue(Message message) {
		StoredValueData value = null;
		String stateRootHash = message.getHeader(CasperConstants.STATE_ROOT_HASH, configuration::getStateRootHash, String.class);
		String path = message.getHeader(CasperConstants.PATH, configuration::getPath, String.class);
		String key = message.getHeader(CasperConstants.ITEM_KEY, configuration::getKey, String.class);
		if (StringUtils.isEmpty(stateRootHash)) {
			handleError(new MissingArgumentException("stateRootHash parameter is required  with endpoint operation " + CasperConstants.STATE_ITEM), message);
			return;
		}
		if (StringUtils.isEmpty(key)) {
			handleError(new MissingArgumentException("key parameter is required   with endpoint operation " + CasperConstants.STATE_ITEM), message);
			return;
		}
		try {
			value = casperService.getStateItem(stateRootHash, key, StringUtils.isEmpty(path) ? Arrays.asList() : Arrays.asList(path.split(",")));
		} catch (Exception e) {
			handleError(e.getCause(), message);
		}
		if (value != null)
			message.setBody(value.getStoredValue());
	}

	/**
	 * Call to dictionnay_item
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.DICTIONARY_ITEM)
	void getDictionayItem(Message message) {
		DictionaryData data = null;
		String stateRootHash = message.getHeader(CasperConstants.STATE_ROOT_HASH, configuration::getStateRootHash, String.class);
		String dictionnaryItemKey = message.getHeader(CasperConstants.DICTIONARY_ITEM_KEY, configuration::getDictionaryItemKey, String.class);
		String seedUref = message.getHeader(CasperConstants.SEED_UREF, configuration::getSeedUref, String.class);
		if (StringUtils.isEmpty(stateRootHash)) {
			handleError(new MissingArgumentException("stateRootHash parameter is required  with endpoint operation " + CasperConstants.DICTIONARY_ITEM), message);
			return;
		}
		if (StringUtils.isEmpty(dictionnaryItemKey)) {
			handleError(new MissingArgumentException("dictionnary key parameter is required   with endpoint operation " + CasperConstants.DICTIONARY_ITEM), message);
			return;
		}

		if (StringUtils.isEmpty(seedUref)) {
			handleError(new MissingArgumentException("seedUref parameter is required   with endpoint operation " + CasperConstants.DICTIONARY_ITEM), message);
			return;
		}

		try {
			URefSeed urefSeed = new URefSeed();
			urefSeed.setDictionaryItemKey(dictionnaryItemKey);
			urefSeed.setUref(URef.fromString(seedUref));
			data = casperService.getStateDictionaryItem(stateRootHash, new URefDictionaryIdentifier(urefSeed));

		} catch (Exception e) {
			handleError(e.getCause(), message);
		}
		if (data != null)
			message.setBody(data);
	}

	/**
	 * Call to getBalance
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.ACCOUNT_BALANCE)
	void accountBalance(Message message) {
		BalanceData balance = null;
		String stateRootHash = message.getHeader(CasperConstants.STATE_ROOT_HASH, configuration::getStateRootHash, String.class);
		String purseUref = message.getHeader(CasperConstants.PURSE_UREF, configuration::getPurseUref, String.class);
		if (StringUtils.isEmpty(stateRootHash)) {
			handleError(new MissingArgumentException("stateRootHash parameter is required   with endpoint operation " + CasperConstants.ACCOUNT_BALANCE), message);
			return;
		}
		if (StringUtils.isEmpty(purseUref)) {
			handleError(new MissingArgumentException("purseUref parameter is required   with endpoint operation " + CasperConstants.ACCOUNT_BALANCE), message);
			return;
		}
		try {
			balance = casperService.getBalance(stateRootHash, purseUref);
		} catch (Exception e) {
			handleError(e.getCause(), message);
		}
		if (balance != null)
			message.setBody(balance);

	}

	/**
	 * Call to put_Deploy
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.PUT_DEPLOY)
	void putDeploy(Message message) {
		DeployResult result = null;
		// Signed Deploy Object must be in the header
		Deploy deloy = message.getHeader(CasperConstants.DEPLOY, Deploy.class);

		if (deloy != null) {
			handleError(new MissingArgumentException("deloy parameter is required   with endpoint operation " + CasperConstants.PUT_DEPLOY), message);
			return;
		}

		try {
			result = casperService.putDeploy(deloy);
		} catch (Exception e) {
			handleError(e.getCause(), message);
		}
		if (result != null)
			message.setBody(result);

	}

	/**
	 * Call query_global_state
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.GLOBAL_STATE)
	void getGlobalState(Message message) {
		// TODO this call will be implemented in 1.4
		handleError(new NotImplementedException(), message);
	}

	/**
	 * Call query_global_state
	 *
	 * @param message
	 * @throws Exception
	 */
	@InvokeOnHeader(CasperConstants.VALIDATOR_CHANGES)
	void getValidatorChanges(Message message) throws Exception {
		// TODO this call will be implemented in 1.4
		handleError(new NotImplementedException(), message);
	}

	/**
	 * handle errors
	 *
	 * @param e
	 * @param message
	 */
	private void handleError(Throwable e, Message message) {
		message.setHeader(CasperConstants.ERROR_CAUSE, e);
		message.getExchange().setException(new CamelExchangeException(e.getMessage(), message.getExchange()));
	}

	public CasperService getCasperService() {
		return casperService;
	}

	public CasperConfiguration getConfiguration() {
		return configuration;
	}

}
