package dev.shared.alievs.behaviours.auto_dispatch;

import eu.darkbot.api.config.annotations.Configuration;
import eu.darkbot.api.config.annotations.Dropdown;
import eu.darkbot.api.config.annotations.Number;
import eu.darkbot.api.config.annotations.Option;

import java.util.ArrayList;
import java.util.List;

@Configuration("alievs.auto_dispatch.config")
public class AutoDispatchConfig {

    @Option("alievs.auto_dispatch.collect")
    public boolean collect = true;

    @Option("alievs.auto_dispatch.hire")
    public boolean hire = true;

    @Option("alievs.auto_dispatch.skip_gg")
    public boolean skipInGg = true;

    @Option("alievs.auto_dispatch.priority")
    @Dropdown(options = RetrieverDropdown.class)
    public String priority = "";

    @Option("alievs.auto_dispatch.default_retriever")
    @Dropdown(options = RetrieverDropdown.class)
    public String defaultRetriever = "R-01";

    @Option("alievs.auto_dispatch.max_uridium")
    @Number(min = 0, max = 1_000_000, step = 100)
    public int maxUridium = 0;

    @Option("alievs.auto_dispatch.max_credits")
    @Number(min = 0, max = 100_000_000, step = 10_000)
    public int maxCredits = 10_000_000;

    @Option("alievs.auto_dispatch.check_interval")
    @Number(min = 30, max = 3600, step = 30)
    public int checkInterval = 300;

    /**
     * Dropdown listing every dispatch retriever. The first entry is empty ("none"): for Priority it means
     * "no preferred retriever", for Default retriever it means "leave the slot free".
     */
    public static class RetrieverDropdown implements Dropdown.Options<String> {
        private static final List<String> NAMES = List.of(
                "",
                "R-01", "R-02", "R-03", "R-04", "R-05", "R-06",
                "H-03", "H-04", "H-05",
                "B-01", "B-02", "B-03", "B-04", "B-05", "B-06",
                "A-01", "A-02", "A-03", "A-04", "A-05", "A-06");

        @Override
        public List<String> options() {
            return new ArrayList<>(NAMES);
        }

        @Override
        public String getText(String option) {
            return (option == null || option.isEmpty()) ? "(none)" : option;
        }
    }
}
