package org.apache.camel.component.casper.examples;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.casper.CasperConstants;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.camel.impl.DefaultCamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.syntifi.casper.sdk.model.auction.AuctionState;
import com.syntifi.casper.sdk.model.balance.BalanceData;
import com.syntifi.casper.sdk.model.block.JsonBlock;
import com.syntifi.casper.sdk.model.era.JsonEraValidators;
import com.syntifi.casper.sdk.model.stateroothash.StateRootHashData;

/**
 * Demo Class loading Casper components example routes  
 * @author p35862
 *
 */

@SuppressWarnings("deprecation")
public class DemoApp {
	public static final Logger LOG = LoggerFactory.getLogger(DemoApp.class);

	/**
	 * loads route 1
	 * @param cntxt : Camel context
	 * @param temp : producerTemplate
	 * @throws Exception : exception
	 */
	private static void loadroute1(CamelContext cntxt, ProducerTemplate temp) throws Exception {
		cntxt.addRoutes(new RouteBuilder() {
			public void configure() throws Exception {
				fromF("direct:%s" , CasperConstants.STATE_ROOT_HASH).routeId("STATE_ROOT_HASH")
						.to("casper:http://65.21.227.180:7777/?operation=" + CasperConstants.STATE_ROOT_HASH)
						.process(new Processor() {

							@Override
							public void process(Exchange exchange) throws Exception {
								StateRootHashData state = (StateRootHashData) exchange.getIn().getBody();
								LOG.info("* Current STATE_ROOT_HASH is : " + state.getStateRootHash());
							}
						});
			}
		});

		cntxt.start();
		temp.sendBody("direct:" + CasperConstants.STATE_ROOT_HASH, "This is a test message");
		cntxt.stop();

	}

	/**
	 * loads route 2
	 * @param cntxt : Camel context
	 * @param temp : producerTemplate
	 * @throws Exception : exception
	 */
	private static void loadroute2(CamelContext cntxt, ProducerTemplate temp) throws Exception {
		cntxt.addRoutes(new RouteBuilder() {
			public void configure() throws Exception {
				from("file:src/main/resources/datas/?fileName=get_block.txt&charset=utf-8&noop=true")
						.convertBodyTo(String.class).routeId(CasperConstants.BLOCK).setHeader("BLOCK_HASH", body())
						.to("casper:http://65.21.227.180:7777/?operation=" + CasperConstants.BLOCK)

						.process(new Processor() {

							@Override
							public void process(Exchange exchange) throws Exception {
								JsonBlock block = (JsonBlock) exchange.getIn().getBody();
								String blockHash = (String) exchange.getIn().getHeader("BLOCK_HASH");
								LOG.info("* getBlock was called with parameter block Hash = " + blockHash);
								LOG.info("* getBlock retrieved a block that was minted at era ="
										+ block.getHeader().getEraId() + " and has as parent hash = "
										+ block.getHeader().getParentHash());

							}
						});

			}

		});

		cntxt.start();
		Thread.sleep(5000);
		cntxt.stop();

	}

	/**
	 * loads route 3
	 * @param cntxt : Camel context
	 * @param temp : producerTemplate
	 * @throws Exception : exception
	 */
	private static void loadroute3(CamelContext cntxt, ProducerTemplate temp) throws Exception {
		cntxt.addRoutes(new RouteBuilder() {
			public void configure() throws Exception {
					// JSON Data Format
				JacksonDataFormat jsonDataFormat = new JacksonDataFormat(JsonEraValidators.class);

				
				from("file:src/main/resources/datas/?fileName=get_auction_info.txt&charset=utf-8&noop=true")
						.convertBodyTo(String.class).routeId(CasperConstants.AUCTION_INFO).setHeader("BLOCK_HEIGHT", body())
						.to("casper:http://65.21.227.180:7777/?operation=" + CasperConstants.AUCTION_INFO)
						.process(new Processor() {
							
							@Override
							public void process(Exchange exchange) throws Exception {
								AuctionState auctionState = (AuctionState) exchange.getIn().getBody();
								String blockHeight = (String) exchange.getIn().getHeader("BLOCK_HEIGHT");
								LOG.info("* getAuctionUnfo was called with parameter block height = " + blockHeight);
								exchange.getOut().setBody(auctionState.getEraValidators());
								LOG.info("* Save validators of this Auction into a json file under path src/main/resources/datas/");
							}
						}).marshal(jsonDataFormat).to("file:src/main/resources/datas/?fileName=era_validators.txt")
						;
			}

		});

		cntxt.start();
		Thread.sleep(5000);
		cntxt.stop();

	}
	
	
	/**
	 * loads route 4
	 * @param cntxt : Camel context
	 * @param temp : producerTemplate
	 * @throws Exception : exception
	 */
	private static void loadroute4(CamelContext cntxt, ProducerTemplate temp) throws Exception {
		cntxt.addRoutes(new RouteBuilder() {
			public void configure() throws Exception {
				fromF("direct:%s" , CasperConstants.STATE_ROOT_HASH+"_01").routeId("STATE_ROOT_HASH")
						.to("casper:http://65.21.227.180:7777/?operation=" + CasperConstants.STATE_ROOT_HASH)
						.process(new Processor() {

						
							@Override
							public void process(Exchange exchange) throws Exception {
								StateRootHashData state = (StateRootHashData) exchange.getIn().getBody();
								LOG.info("* Current STATE_ROOT_HASH is : " + state.getStateRootHash());
								LOG.info("* Using this  STATE_ROOT_HASH  " + state.getStateRootHash() + " to Query "+CasperConstants.ACCOUNT_BALANCE + " for this Account : 017d9aa0b86413d7ff9a9169182c53f0bacaa80d34c211adab007ed4876af17077");
								exchange.getOut().setHeader(CasperConstants.STATE_ROOT_HASH, state.getStateRootHash());
								exchange.getOut().setHeader(CasperConstants.PURSE_UREF, "uref-e18e33382032c835e9ccf367baa20e043229c6d45d135b60aa7301ff1eeb317b-007");
							}
						})
						
						.to("casper:http://65.21.227.180:7777/?operation=" + CasperConstants.ACCOUNT_BALANCE)
						.process(new Processor() {

							@Override
							public void process(Exchange exchange) throws Exception {
								BalanceData balance = (BalanceData) exchange.getIn().getBody();
								LOG.info("* balance of account 017d9aa0b86413d7ff9a9169182c53f0bacaa80d34c211adab007ed4876af17077 : " + balance.getValue());
							}})
						
						;
			}
		});

		cntxt.start();
		temp.sendBody("direct:" + CasperConstants.STATE_ROOT_HASH+"_01", "This is a test message");
		cntxt.stop();

	}

	/**
	 * Main method
	 * @param args : args 
	 * @throws Exception : exception
	 */
	public static void main(String[] args) throws Exception {

		CamelContext context = new DefaultCamelContext();
		ProducerTemplate template = context.createProducerTemplate();
		LOG.info(
				"------------------------------- this route performs a call to getStaterouteHash and print the current STATE_ROOT_HASH to console ---------------------------------");
		loadroute1(context, template);
		LOG.info(
				"------------------------------- This route reads a block hash from a file, performs a call to getBlock with the block hash-------------------------------------------");
		loadroute2(context, template);
		LOG.info(
				"------------------------------- This route reads a block heigh from a file, performs a call to getAuctionInfo with the block height-------------------------------------------");
		LOG.info(
				"------------------------------- Then saves the validator slot of the auction to a json file------------------------------------------------------------------------------------");
		
		loadroute3(context, template);
		LOG.info(
				"------------------------------- this route performs a call to getStaterouteHash and uses the StaterouteHash to query accout balance for-------------------------------------------");
		LOG.info(
				"------------------------------- account : 017d9aa0b86413d7ff9a9169182c53f0bacaa80d34c211adab007ed4876af17077 and print the balance to console ------------------------------------------------------------------------------------");
		loadroute4(context, template);
	}
}
