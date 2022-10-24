package org.apache.camel.component.casper.consumer.sse;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.camel.component.casper.consumer.sse.model.block.BlockData;
import org.apache.camel.component.casper.consumer.sse.model.deploy.accepted.DeployAcceptedData;
import org.apache.camel.component.casper.consumer.sse.model.deploy.expired.DeployExpiredData;
import org.apache.camel.component.casper.consumer.sse.model.deploy.processed.DeployProcessedData;
import org.apache.camel.component.casper.consumer.sse.model.fault.FaultData;
import org.apache.camel.component.casper.consumer.sse.model.sig.FinalitySignatureData;
import org.apache.camel.component.casper.consumer.sse.model.step.StepData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * MVC Controler with casper sse channels
 * 
 * @author p35862
 *
 */
@RestController
public class DataSetController {

	private final DataSetService dataSetService;
	ObjectMapper objectMapper = new ObjectMapper();

	public DataSetController(DataSetService dataSetService) {
		this.dataSetService = dataSetService;
	}

	@GetMapping("/events/main")
	public SseEmitter fetchmain() {
		SseEmitter emitter = new SseEmitter(6000l);

		ExecutorService executor = Executors.newSingleThreadExecutor();
		executor.execute(() -> {
			List<BlockData> dataSets = dataSetService.getBlocks();
			try {

				// emit added blocks events
				for (BlockData dataSet : dataSets) {
					randomDelay();
					emitter.send(objectMapper.writeValueAsString(dataSet));
				}

				// emit processed deploys events
				List<DeployProcessedData> datas = dataSetService.getProcessedDeploys();
				for (DeployProcessedData dat : datas) {
					randomDelay();
					emitter.send(objectMapper.writeValueAsString(dat));

				}

				// emit steps events
				List<StepData> steps = dataSetService.getSteps();
				for (StepData step : steps) {
					randomDelay();
					emitter.send(objectMapper.writeValueAsString(step));
				}

				// emit fault events
				List<FaultData> faults = dataSetService.getFaults();
				for (FaultData fault : faults) {
					randomDelay();
					emitter.send(objectMapper.writeValueAsString(fault));
				}

				List<DeployExpiredData> expireds = dataSetService.getDeploysExpired();
				// emit expired deploys events
				for (DeployExpiredData exp : expireds) {
					randomDelay();
					emitter.send(objectMapper.writeValueAsString(exp));
				}

				emitter.complete();
			} catch (IOException e) {
				emitter.completeWithError(e);
			}
		});
		executor.shutdown();
		return emitter;
	}

	@GetMapping("/events/deploys")
	public SseEmitter fetchdeploys() {
		SseEmitter emitter = new SseEmitter(5000l);

		ExecutorService executor = Executors.newSingleThreadExecutor();
		executor.execute(() -> {
			List<DeployAcceptedData> dataSets = dataSetService.getAcceptedDeploys();
			try {

				// emit accepetd deploys events
				for (DeployAcceptedData dataSet : dataSets) {
					randomDelay();
					emitter.send(objectMapper.writeValueAsString(dataSet));
				}

				emitter.complete();
			} catch (IOException e) {
				emitter.completeWithError(e);
			}
		});
		executor.shutdown();

		return emitter;
	}

	@GetMapping("/events/sigs")
	public SseEmitter fetchsigs() {
		SseEmitter emitter = new SseEmitter(5000l);
		// emit finality signatures events
		ExecutorService executor = Executors.newSingleThreadExecutor();
		executor.execute(() -> {
			List<FinalitySignatureData> dataSets = dataSetService.getFinalitySignatures();
			try {
				for (FinalitySignatureData dataSet : dataSets) {
					randomDelay();
					emitter.send(dataSet);
				}

				emitter.complete();
			} catch (IOException e) {
				emitter.completeWithError(e);
			}
		});
		executor.shutdown();
		return emitter;
	}

	private void randomDelay() {
		try {
			Thread.sleep(0);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
