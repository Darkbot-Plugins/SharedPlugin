package dev.shared.fix3dll.behaviour.auto_dispatch.config;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;

import dev.shared.fix3dll.behaviour.auto_dispatch.DispatchSchedule;

import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.util.OptionEditor;

/**
 * Renders the read-only status row of the settings: the retrievers in progress with their
 * expected finishing times, and when the next check will run.
 *
 * <p>The row is backed by a setting that never holds a value. Its content is produced from
 * {@link DispatchSchedule} each time the row is drawn, so the status is always current and is
 * never written to the configuration file.
 */
public final class DispatchStatusEditor implements OptionEditor<String> {
    private static final String BULLET = "&bull; ";

    private final DispatchSchedule schedule;

    public DispatchStatusEditor(final DispatchSchedule schedule) {
        this.schedule = schedule;
    }

    /**
     * Builds the status as a bulleted list, one entry per group of retrievers followed by the
     * time of the next check.
     *
     * @param setting the placeholder setting this editor is attached to; unused
     * @return a label showing the current status
     */
    @Override
    public JComponent getEditorComponent(final ConfigSetting<String> setting) {
        StringBuilder html = new StringBuilder("<html>");
        for (String line : this.schedule.describeRunning()) {
            html.append(BULLET).append(line).append("<br>");
        }
        html.append(BULLET).append("Next check: ").append(this.schedule.describeNextCheck()).append("</html>");
        JLabel label = new JLabel(html.toString());
        label.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        return label;
    }

    /**
     * @return always {@code null}: the row is display-only and has no value to store
     */
    @Override
    public String getEditorValue() {
        return null;
    }
}
