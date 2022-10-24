package org.apache.camel.component.casper.consumer.sse;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.PostConstruct;

import org.apache.camel.component.casper.consumer.sse.model.block.*;
import org.apache.camel.component.casper.consumer.sse.model.deploy.accepted.*;
import org.apache.camel.component.casper.consumer.sse.model.deploy.expired.DeployExpired;
import org.apache.camel.component.casper.consumer.sse.model.deploy.expired.DeployExpiredData;
import org.apache.camel.component.casper.consumer.sse.model.deploy.processed.DeployProcessed;
import org.apache.camel.component.casper.consumer.sse.model.deploy.processed.DeployProcessedData;
import org.apache.camel.component.casper.consumer.sse.model.deploy.processed.Effect;
import org.apache.camel.component.casper.consumer.sse.model.deploy.processed.ExecutionResult;
import org.apache.camel.component.casper.consumer.sse.model.deploy.processed.Failure;
import org.apache.camel.component.casper.consumer.sse.model.fault.Fault;
import org.apache.camel.component.casper.consumer.sse.model.fault.FaultData;
import org.apache.camel.component.casper.consumer.sse.model.sig.FinalitySignature;
import org.apache.camel.component.casper.consumer.sse.model.sig.FinalitySignatureData;
import org.apache.camel.component.casper.consumer.sse.model.step.Step;
import org.apache.camel.component.casper.consumer.sse.model.step.StepData;
import org.springframework.stereotype.Service;

/**
 * Data Service Loader (for tests) 
 * @author p35862
 *
 */
@Service
public class DataSetService {
	private final List<BlockData> blokcs = new ArrayList<>();

	private final List<DeployAcceptedData> acceptedDeploys = new ArrayList<>();

	private final List<FinalitySignatureData> sigs = new ArrayList<>();

	private final List<DeployProcessedData> processedDeploys = new ArrayList<>();

	private final List<StepData> steps = new ArrayList<>();

	private final List<DeployExpiredData> expiredDeploys = new ArrayList<>();

	private final List<FaultData> faults = new ArrayList<>();

	@PostConstruct
	public void setup() {
		createBlockAddedDataSet();
		createDeployAcceptedDataSet();
		createDeployProcessedDataSet();
		createFinalitySignatureDataSet();
		createStepDataSet();
		createDeployExpiredDataSet();
		createFaultDataSet();
	}

	public List<BlockData> getBlocks() {
		return Collections.unmodifiableList(blokcs);
	}

	public List<DeployProcessedData> getProcessedDeploys() {
		return Collections.unmodifiableList(processedDeploys);
	}

	public List<FinalitySignatureData> getFinalitySignatures() {
		return Collections.unmodifiableList(sigs);
	}

	public List<DeployAcceptedData> getAcceptedDeploys() {
		return Collections.unmodifiableList(acceptedDeploys);
	}

	public List<StepData> getSteps() {
		return Collections.unmodifiableList(steps);
	}

	public List<DeployExpiredData> getDeploysExpired() {
		return Collections.unmodifiableList(expiredDeploys);
	}

	public List<FaultData> getFaults() {
		return Collections.unmodifiableList(faults);
	}

	private Iterable<BlockData> createBlockAddedDataSet() {

		Body body = new Body("01717c1899762ffdbd12def897ac905f1debff38e8bafb081620cb6da5a6bb1f25", new ArrayList<>(),
				new ArrayList<>());
		BlockHeader header = new BlockHeader("b9b7465aa84343597def17fe64afe6b5851d20736bbbbd1d998f4fb76156de7a",
				"39d5cc72a0129781ac261dcf306c26a600b51ace307b98cd5e8ce3feb91af51c",
				"7a8d15d2fb0679b5549064431eb1116043b186ea562af6d76ec207f029075278", true,
				"8acc39d236c63fb415823884212d051bf674f3b4ce809d408314e07ddc4339e5", null, "2022-02-22T15:12:15.872Z",
				3827, 558756, "1.4.4");
		Block block = new Block("a636c524063588756cff0f306a13ce2804fa50fe2430e7910196fa93a55ed5f9", header, body,
				new ArrayList<>());
		BlockAdded added = new BlockAdded("a636c524063588756cff0f306a13ce2804fa50fe2430e7910196fa93a55ed5f9", block);
		this.blokcs.add(new BlockData(added));
		return blokcs;
	}

	private Iterable<DeployAcceptedData> createDeployAcceptedDataSet() {
		DeployHeader deployHeader = new DeployHeader(
				"01b92e36567350dd7b339d709bfe341df6fda853e85315418f1bb3ddd414d9f5bee95d5bf8f3a397a3d9",
				"2022-02-22T13:35:55.863Z", "1day", 1,
				"acf8fd7edd5389f43b9250f743691d932ffd2d43bba8477ac4c877c8036dab55", new ArrayList<>(), "casper");
		ModuleBytes mdb = new ModuleBytes("", new ArrayList<>());
		Payment p = new Payment(mdb);
		Transfer tr = new Transfer(new ArrayList<>());
		Session ss = new Session(tr);
		DeployAccepted da = new DeployAccepted("6baaf4b76adbcbba47ce2ad013dc9f29c2ebdc1a9c25abe95d5bf8f3a397a3d9",
				deployHeader, p, ss, new ArrayList<>());

		this.acceptedDeploys.add(new DeployAcceptedData(da));
		return acceptedDeploys;

	}

	private Iterable<DeployProcessedData> createDeployProcessedDataSet() {

		Effect effect = new Effect(new ArrayList<>(), new ArrayList<>());
		Failure failure = new Failure(effect, "100000000", "Insufficient payment", new ArrayList<>());
		ExecutionResult result = new ExecutionResult(failure);

		DeployProcessed deployProcessed = new DeployProcessed(
				"9271857ed0614ab8361ba52c9471102e1fdee06199b151c769b24edb537bcb59",
				"0180001fced7b5fe8a7b746fcca75dd4427e0880adbe5a6171ca291edb4f00e845", "2022-03-02T08:40:21.007Z", "30m",
				new ArrayList<>(), "6a808167becb4f29ef0ce63803041dc4185b4a2910e647bfedb5d10b33decb83", result);

		this.processedDeploys.add(new DeployProcessedData(deployProcessed));
		return processedDeploys;

	}

	private Iterable<FinalitySignatureData> createFinalitySignatureDataSet() {
		FinalitySignature finalitySignature = new FinalitySignature(
				"7d7275ba304cef0f55dd48603fd28450e10d1836c7e1d7182fe95c8e37508c92", 4019,
				"011a7800d407a2d98c7524e8e3f76ae94b0dcb8ea938679d2495a4d74dc80b4671fe21a4eb705f8d4c16cba2bb65b3388123fed2393650a71f8001bd682bb19c09",
				"01652d9fbd8dbb443af0122cd4347f4107e697306e5b90f93dbf959f7612e5e7d2");
		this.sigs.add(new FinalitySignatureData(finalitySignature));
		return sigs;

	}

	private Iterable<StepData> createStepDataSet() {
		Effect effet = new Effect(new ArrayList<>(), new ArrayList<>());
		Step step = new Step(1254, effet);
		this.steps.add(new StepData(step));
		return steps;
	}

	private Iterable<DeployExpiredData> createDeployExpiredDataSet() {
		DeployExpired expired = new DeployExpired("9271857ed0614ab8361ba52c9471102e1fdee06199b151c769b24edb537bcb59");
		this.expiredDeploys.add(new DeployExpiredData(expired));
		return expiredDeploys;
	}

	private Iterable<FaultData> createFaultDataSet() {
		Fault fault = new Fault(1452, "2022-02-22T15:12:15.872Z",
				"9271857ed0614ab8361ba52c9471102e1fdee06199b151c769b24edb537bcb59");
		this.faults.add(new FaultData(fault));
		return faults;
	}

}
