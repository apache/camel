package org.apache.camel.component.casper.consumer.sse.model.step;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * * SSE Step Event Data POJO
 * 
 * @author mabahma
 *
 */
public class StepData {

	Step step;

	@JsonProperty("Step")
	public Step getStep() {
		return step;
	}

	public void setStepData(Step step) {
		this.step = step;
	}

	public StepData(Step step) {

		this.step = step;
	}

}
