package dev.shared.alievs.tasks.auto_dispatch;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;

@Configuration("alievs.auto_dispatch.config")
public class AutoDispatchConfig {

    @Option("alievs.auto_dispatch.collect")
    public boolean collect = true;

    @Option("alievs.auto_dispatch.hire")
    public boolean hire = true;

    @Option("alievs.auto_dispatch.priority")
    public String priority = "";

    @Option("alievs.auto_dispatch.max_uridium")
    @Number(min = 0, max = 1_000_000, step = 100)
    public int maxUridium = 0;

    @Option("alievs.auto_dispatch.max_credits")
    @Number(min = 0, max = 100_000_000, step = 10_000)
    public int maxCredits = 10_000_000;

    @Option("alievs.auto_dispatch.check_interval")
    @Number(min = 30, max = 3600, step = 30)
    public int checkInterval = 300;
}
