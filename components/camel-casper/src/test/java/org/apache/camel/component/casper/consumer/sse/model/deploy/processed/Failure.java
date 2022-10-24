package org.apache.camel.component.casper.consumer.sse.model.deploy.processed;

import java.util.ArrayList;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE DeployProcessed Event Failure POJO
 * 
 * @author p35862
 *
 */
public class Failure {
	private Effect effect;
	private String cost;
	private String errorMessage;
	private ArrayList<String> transfers;

	public Failure(Effect effect, String cost, String errorMessage, ArrayList<String> transfers) {

		this.effect = effect;
		this.cost = cost;
		this.errorMessage = errorMessage;
		this.transfers = transfers;
	}

	public Effect getEffect() {
		return effect;
	}

	public void setEffect(Effect effect) {
		this.effect = effect;
	}

	public String getCost() {
		return cost;
	}

	public void setCost(String cost) {
		this.cost = cost;
	}

	@JsonProperty("error_message")
	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public ArrayList<String> getTransfers() {
		return transfers;
	}

	public void setTransfers(ArrayList<String> transfers) {
		this.transfers = transfers;
	}

}