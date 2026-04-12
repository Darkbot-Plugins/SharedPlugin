package dev.shared.do_gamer.module.simple_galaxy_gate.config;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JEditorPane;

import eu.darkbot.api.config.ConfigSetting;
import eu.darkbot.api.config.util.OptionEditor;

public abstract class HtmlInstructions implements OptionEditor<String> {
    @Override
    public JComponent getEditorComponent(ConfigSetting<String> setting) {
        String style = "padding: 5px; background-color: #3b3b3b; border: 1px solid #999999;";
        String html = "<div style='" + style + "'>" + this.getEditorValue() + "</div>";
        JEditorPane editor = new JEditorPane("text/html", html);
        editor.setEditable(false);
        editor.setOpaque(false);
        editor.setBorder(BorderFactory.createEmptyBorder(5, 0, 10, 0));
        editor.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        return editor;
    }

    protected String buildList(String title, String... items) {
        StringBuilder html = new StringBuilder();
        int size = items.length;
        if (title != null) {
            html.append("<b>").append(title).append("</b>");
            if (size > 0) {
                html.append("<br>");
            }
        }
        for (int i = 0; i < size; i++) {
            html.append(" - ").append(items[i]);
            if (i < size - 1) {
                html.append("<br>");
            }
        }
        return html.toString();
    }
}
