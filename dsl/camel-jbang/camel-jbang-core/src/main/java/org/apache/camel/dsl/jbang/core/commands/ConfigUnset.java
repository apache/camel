package org.apache.camel.dsl.jbang.core.commands;

import org.apache.camel.dsl.jbang.core.common.CommandLineHelper;

import picocli.CommandLine;

@CommandLine.Command(name = "unset",
		description = "Remove user config value")
public class ConfigUnset extends CamelCommand {

	@CommandLine.Parameters(description = "Configuration key")
	private String key;

	public ConfigUnset(CamelJBangMain main) {
		super(main);
	}

	@Override
	public Integer call() throws Exception {
		CommandLineHelper.loadProperties(properties -> {
			properties.remove(key);
			CommandLineHelper.storeProperties(properties);
		});

		return 0;
	}
}
