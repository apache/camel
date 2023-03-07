package org.apache.camel.dsl.jbang.core.commands;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;

import picocli.CommandLine;

@CommandLine.Command(name = "get",
		description = "Displays user config value")
public class ConfigGet extends CamelCommand {

	@CommandLine.Parameters(description = "Configuration key")
	private String key;

	public ConfigGet(CamelJBangMain main) {
		super(main);
	}

	@Override
	public Integer call() throws Exception {
		CommandLineHelper.loadProperties(properties -> {
			if (properties.containsKey(key)) {
				System.out.println(properties.get(key));
			} else {
				System.out.println(key + " key not found");
			}
		});

		return 0;
	}
}
