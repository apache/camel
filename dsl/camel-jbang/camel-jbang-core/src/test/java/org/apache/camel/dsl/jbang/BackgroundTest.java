package org.apache.camel.dsl.jbang;

import org.apache.camel.dsl.jbang.core.commands.Bind;
import org.apache.camel.dsl.jbang.core.commands.CamelJBangMain;
import org.apache.camel.dsl.jbang.core.commands.CodeGenerator;
import org.apache.camel.dsl.jbang.core.commands.CodeRestGenerator;
import org.apache.camel.dsl.jbang.core.commands.Complete;
import org.apache.camel.dsl.jbang.core.commands.DependencyCommand;
import org.apache.camel.dsl.jbang.core.commands.DependencyCopy;
import org.apache.camel.dsl.jbang.core.commands.DependencyList;
import org.apache.camel.dsl.jbang.core.commands.Export;
import org.apache.camel.dsl.jbang.core.commands.Init;
import org.apache.camel.dsl.jbang.core.commands.Pipe;
import org.apache.camel.dsl.jbang.core.commands.Run;
import org.apache.camel.dsl.jbang.core.commands.action.CamelAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelGCAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelLogAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelReloadAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelResetStatsAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelRouteStartAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelRouteStopAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelSourceAction;
import org.apache.camel.dsl.jbang.core.commands.action.CamelSourceTop;
import org.apache.camel.dsl.jbang.core.commands.action.CamelThreadDump;
import org.apache.camel.dsl.jbang.core.commands.action.CamelTraceAction;
import org.apache.camel.dsl.jbang.core.commands.action.LoggerAction;
import org.apache.camel.dsl.jbang.core.commands.action.RouteControllerAction;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogCommand;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogComponent;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogDataFormat;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogDoc;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogKamelet;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogLanguage;
import org.apache.camel.dsl.jbang.core.commands.catalog.CatalogOther;
import org.apache.camel.dsl.jbang.core.commands.process.CamelContextStatus;
import org.apache.camel.dsl.jbang.core.commands.process.CamelContextTop;
import org.apache.camel.dsl.jbang.core.commands.process.CamelCount;
import org.apache.camel.dsl.jbang.core.commands.process.CamelProcessorStatus;
import org.apache.camel.dsl.jbang.core.commands.process.CamelProcessorTop;
import org.apache.camel.dsl.jbang.core.commands.process.CamelRouteStatus;
import org.apache.camel.dsl.jbang.core.commands.process.CamelRouteTop;
import org.apache.camel.dsl.jbang.core.commands.process.CamelStatus;
import org.apache.camel.dsl.jbang.core.commands.process.CamelTop;
import org.apache.camel.dsl.jbang.core.commands.process.Hawtio;
import org.apache.camel.dsl.jbang.core.commands.process.Jolokia;
import org.apache.camel.dsl.jbang.core.commands.process.ListBlocked;
import org.apache.camel.dsl.jbang.core.commands.process.ListCircuitBreaker;
import org.apache.camel.dsl.jbang.core.commands.process.ListEndpoint;
import org.apache.camel.dsl.jbang.core.commands.process.ListEvent;
import org.apache.camel.dsl.jbang.core.commands.process.ListHealth;
import org.apache.camel.dsl.jbang.core.commands.process.ListInflight;
import org.apache.camel.dsl.jbang.core.commands.process.ListMetric;
import org.apache.camel.dsl.jbang.core.commands.process.ListProcess;
import org.apache.camel.dsl.jbang.core.commands.process.ListService;
import org.apache.camel.dsl.jbang.core.commands.process.ListVault;
import org.apache.camel.dsl.jbang.core.commands.process.StopProcess;

import org.junit.jupiter.api.Test;

import org.assertj.core.api.Assertions;

import java.io.PrintWriter;
import java.io.StringWriter;

import picocli.CommandLine;

public class BackgroundTest {

	@Test
	public void test() {
		CamelJBangMain main = new CamelJBangMain();
		CommandLine commandLine = new CommandLine(main)
				.addSubcommand("init", new CommandLine(new Init(main)))
				.addSubcommand("run", new CommandLine(new Run(main)))
				.addSubcommand("log", new CommandLine(new CamelLogAction(main)))
				.addSubcommand("ps", new CommandLine(new ListProcess(main)))
				.addSubcommand("stop", new CommandLine(new StopProcess(main)))
				.addSubcommand("trace", new CommandLine(new CamelTraceAction(main)))
				.addSubcommand("get", new CommandLine(new CamelStatus(main))
						.addSubcommand("context", new CommandLine(new CamelContextStatus(main)))
						.addSubcommand("route", new CommandLine(new CamelRouteStatus(main)))
						.addSubcommand("processor", new CommandLine(new CamelProcessorStatus(main)))
						.addSubcommand("count", new CommandLine(new CamelCount(main)))
						.addSubcommand("health", new CommandLine(new ListHealth(main)))
						.addSubcommand("endpoint", new CommandLine(new ListEndpoint(main)))
						.addSubcommand("event", new CommandLine(new ListEvent(main)))
						.addSubcommand("inflight", new CommandLine(new ListInflight(main)))
						.addSubcommand("blocked", new CommandLine(new ListBlocked(main)))
						.addSubcommand("route-controller", new CommandLine(new RouteControllerAction(main)))
						.addSubcommand("circuit-breaker", new CommandLine(new ListCircuitBreaker(main)))
						.addSubcommand("metric", new CommandLine(new ListMetric(main)))
						.addSubcommand("service", new CommandLine(new ListService(main)))
						.addSubcommand("source", new CommandLine(new CamelSourceAction(main)))
						.addSubcommand("vault", new CommandLine(new ListVault(main))))
				.addSubcommand("top", new CommandLine(new CamelTop(main))
						.addSubcommand("context", new CommandLine(new CamelContextTop(main)))
						.addSubcommand("route", new CommandLine(new CamelRouteTop(main)))
						.addSubcommand("processor", new CommandLine(new CamelProcessorTop(main)))
						.addSubcommand("source", new CommandLine(new CamelSourceTop(main))))
				.addSubcommand("cmd", new CommandLine(new CamelAction(main))
						.addSubcommand("start-route", new CommandLine(new CamelRouteStartAction(main)))
						.addSubcommand("stop-route", new CommandLine(new CamelRouteStopAction(main)))
						.addSubcommand("reset-stats", new CommandLine(new CamelResetStatsAction(main)))
						.addSubcommand("reload", new CommandLine(new CamelReloadAction(main)))
						.addSubcommand("thread-dump", new CommandLine(new CamelThreadDump(main)))
						.addSubcommand("logger", new CommandLine(new LoggerAction(main)))
						.addSubcommand("gc", new CommandLine(new CamelGCAction(main))))
				.addSubcommand("dependency", new CommandLine(new DependencyCommand(main))
						.addSubcommand("list", new CommandLine(new DependencyList(main)))
						.addSubcommand("copy", new CommandLine(new DependencyCopy(main))))
				.addSubcommand("generate", new CommandLine(new CodeGenerator(main))
						.addSubcommand("rest", new CommandLine(new CodeRestGenerator(main))))
				.addSubcommand("catalog", new CommandLine(new CatalogCommand(main))
						.addSubcommand("component", new CommandLine(new CatalogComponent(main)))
						.addSubcommand("dataformat", new CommandLine(new CatalogDataFormat(main)))
						.addSubcommand("language", new CommandLine(new CatalogLanguage(main)))
						.addSubcommand("other", new CommandLine(new CatalogOther(main)))
						.addSubcommand("kamelet", new CommandLine(new CatalogKamelet(main))))
				.addSubcommand("doc", new CommandLine(new CatalogDoc(main)))
				.addSubcommand("jolokia", new CommandLine(new Jolokia(main)))
				.addSubcommand("hawtio", new CommandLine(new Hawtio(main)))
				.addSubcommand("bind", new CommandLine(new Bind(main)))
				.addSubcommand("pipe", new CommandLine(new Pipe(main)))
				.addSubcommand("export", new CommandLine(new Export(main)))
				.addSubcommand("completion", new CommandLine(new Complete(main)));

		StringWriter sw = new StringWriter();
		commandLine.setOut(new PrintWriter(sw));

		int exitCode = commandLine.execute("init", "test.yaml", "--directory=target/data");
		Assertions.assertThat(exitCode).isGreaterThanOrEqualTo(0);

		exitCode = commandLine.execute("run", "target/data/test.yaml", "--background");
		// ProcessHandle.current().info().commandLine().orElse(null);
		// /home/federico/.sdkman/candidates/java/17.0.2-open/bin/java -agentlib:jdwp=transport=dt_socket,address=127.0.0.1:27155,suspend=y,server=n -ea -Djava.security.egd=file:///dev/urandom -Djavax.xml.accessExternalSchema=file,http,https -Djavax.xml.accessExternalDTD=file,http -Dderby.stream.error.file=target/derby.log -Djava.awt.headless=true -Djava.util.logging.config.file=/home/federico/Work/croway/camel/dsl/camel-jbang/camel-jbang-core/target/test-classes/logging.properties -Dorg.apache.activemq.default.directory.prefix=target/ -XX:+ExitOnOutOfMemoryError -XX:+HeapDumpOnOutOfMemoryError -Didea.test.cyclic.buffer.size=1048576 -javaagent:/app/idea-IC/plugins/java/lib/rt/debugger-agent.jar=file:/tmp/capture.props -Dfile.encoding=UTF-8 -classpath /home/federico/.m2/repository/org/junit/platform/junit-platform-launcher/1.9.2/junit-platform-launcher-1.9.2.jar:/app/idea-IC/lib/idea_rt.jar:/app/idea-IC/plugins/junit/lib/junit5-rt.jar:/app/idea-IC/plugins/junit/lib/junit-rt.jar:/home/federico/Work/croway/camel/dsl/camel-jbang/camel-jbang-core/target/test-classes:/home/federico/Work/croway/camel/dsl/camel-jbang/camel-jbang-core/target/classes:/home/federico/.m2/repository/org/apache/camel/camel-main/4.0.0-SNAPSHOT/camel-main-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-api/4.0.0-SNAPSHOT/camel-api-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/jakarta/xml/bind/jakarta.xml.bind-api/4.0.0/jakarta.xml.bind-api-4.0.0.jar:/home/federico/.m2/repository/jakarta/activation/jakarta.activation-api/2.1.0/jakarta.activation-api-2.1.0.jar:/home/federico/.m2/repository/org/apache/camel/camel-base/4.0.0-SNAPSHOT/camel-base-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-core-engine/4.0.0-SNAPSHOT/camel-core-engine-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-base-engine/4.0.0-SNAPSHOT/camel-base-engine-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-core-reifier/4.0.0-SNAPSHOT/camel-core-reifier-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-core-processor/4.0.0-SNAPSHOT/camel-core-processor-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-management-api/4.0.0-SNAPSHOT/camel-management-api-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-support/4.0.0-SNAPSHOT/camel-support-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-util/4.0.0-SNAPSHOT/camel-util-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-core-languages/4.0.0-SNAPSHOT/camel-core-languages-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-core-model/4.0.0-SNAPSHOT/camel-core-model-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-kamelet-main/4.0.0-SNAPSHOT/camel-kamelet-main-4.0.0-20230301.023808-34.jar:/home/federico/.m2/repository/org/apache/camel/camel-management/4.0.0-SNAPSHOT/camel-management-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-log/4.0.0-SNAPSHOT/camel-log-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-health/4.0.0-SNAPSHOT/camel-health-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-console/4.0.0-SNAPSHOT/camel-console-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-dsl-modeline/4.0.0-SNAPSHOT/camel-dsl-modeline-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-kamelet/4.0.0-SNAPSHOT/camel-kamelet-4.0.0-20230301.023808-47.jar:/home/federico/.m2/repository/org/apache/camel/camel-bean/4.0.0-SNAPSHOT/camel-bean-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-jackson/4.0.0-SNAPSHOT/camel-jackson-4.0.0-20230301.023808-47.jar:/home/federico/.m2/repository/org/apache/camel/camel-jfr/4.0.0-SNAPSHOT/camel-jfr-4.0.0-20230301.023808-48.jar:/home/federico/.m2/repository/org/apache/camel/camel-platform-ht
		Assertions.assertThat(sw.toString()).doesNotContain("No Camel integration files to run");
		Assertions.assertThat(exitCode).isGreaterThanOrEqualTo(0);
	}
}
