package org.apache.camel.dsl.jbang.core.commands;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;

import picocli.CommandLine;

@CommandLine.Command(name = "list",
		description = "Displays user config overrides")
public class ConfigList extends CamelCommand {

	public ConfigList(CamelJBangMain main) {
		super(main);
	}

	@Override
	public Integer call() throws Exception {
		CommandLineHelper.loadProperties(properties ->
				properties.entrySet().forEach(entry -> System.out.println(entry.getKey())));

		return 0;
	}
}
