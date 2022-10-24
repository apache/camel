package org.apache.camel.component.casper.consumer.sse.model.step;

import org.apache.camel.component.casper.consumer.sse.model.deploy.processed.Effect;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SSE Step Event POJO
 * 
 * @author mabahma
 *
 */
public class Step {

	private int eraId;
	private Effect effect;

	@JsonProperty("era_id")
	public float getEraId() {
		return eraId;
	}

	public void setEraId(int eraId) {
		this.eraId = eraId;
	}

	@JsonProperty("execution_effect")
	public Effect getEffect() {
		return effect;
	}

	public void setEffect(Effect effect) {
		this.effect = effect;
	}

	public Step(int eraId, Effect effect) {

		this.eraId = eraId;
		this.effect = effect;
	}

}
