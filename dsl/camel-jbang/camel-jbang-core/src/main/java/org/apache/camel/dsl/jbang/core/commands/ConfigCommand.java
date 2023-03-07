package org.apache.camel.dsl.jbang.core.commands;

import picocli.CommandLine;

@CommandLine.Command(name = "config", description = "Interacts with camel-jbang config file")
public class ConfigCommand extends CamelCommand {

	public ConfigCommand(CamelJBangMain main) {
		super(main);
	}

	@Override
	public Integer call() throws Exception {
		// defaults to list
		new CommandLine(new ConfigList(getMain())).execute();
		return 0;
	}
}
