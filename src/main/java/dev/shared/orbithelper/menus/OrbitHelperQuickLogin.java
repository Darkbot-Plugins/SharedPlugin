package dev.shared.orbithelper.menus;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;

import javax.swing.JComponent;
import javax.swing.JOptionPane;

import eu.darkbot.api.PluginAPI;
import eu.darkbot.api.extensions.ExtraMenus;
import eu.darkbot.api.extensions.Feature;
import eu.darkbot.api.extensions.PluginInfo;
import eu.darkbot.api.managers.BackpageAPI;
import eu.darkbot.api.managers.ExtensionsAPI;
import eu.darkbot.api.managers.I18nAPI;

@Feature(name = "Orbit Helper Quick Login", description = "Adds a button to navigate to Orbit Helper from the menu.")
public class OrbitHelperQuickLogin implements ExtraMenus {

    private final BackpageAPI backpageAPI;

    public OrbitHelperQuickLogin(BackpageAPI backpageAPI) {
        this.backpageAPI = backpageAPI;
    }

    @Override
    public Collection<JComponent> getExtraMenuItems(PluginAPI api) {
        I18nAPI i18n = api.requireAPI(I18nAPI.class);
        ExtensionsAPI extensionsAPI = api.requireAPI(ExtensionsAPI.class);
        PluginInfo plugin = extensionsAPI.getFeatureInfo(getClass()).getPluginInfo();

        return Arrays.asList(
                createSeparator("Orbit Helper"),
                create(i18n.get(plugin, "orbithelper.quick_login.menu.open"), e -> openOrbitHelper()));
    }

    /**
     * Opens Orbit Helper login page with current game credentials
     */
    private void openOrbitHelper() {
        try {
            String sid = backpageAPI.getSid();
            URI instanceURIObj = backpageAPI.getInstanceURI();
            String instanceURI = instanceURIObj != null ? instanceURIObj.toString() : null;

            if (sid == null || sid.isEmpty()) {
                showError("No Session ID found");
                return;
            }

            if (instanceURI == null || instanceURI.isEmpty()) {
                showError("No Instance Found");
                return;
            }

            // Extract server name from instanceURI
            // Example: https://int1.darkorbit.com/ -> int1
            String server = extractServerName(instanceURI);

            if (server == null || server.isEmpty()) {
                showError("Invalid Instance");
                return;
            }

            // Build Orbit Helper URL
            String orbitHelperUrl = String.format("https://orbithelper.com/darkbot-login?server=%s&sid=%s", server,
                    sid);

            // Open URL in default browser
            Desktop.getDesktop().browse(new URI(orbitHelperUrl));

        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
            showError("Failed to open browser");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Unknown error occurred");
        }
    }

    /**
     * Extracts server name from instance URI
     *
     * @param instanceURI Example: https://int1.darkorbit.com/
     * @return Server name, example: int1
     */
    private String extractServerName(String instanceURI) {
        try {
            URI uri = new URI(instanceURI);
            String host = uri.getHost();

            if (host != null && host.contains(".")) {
                // Extract the first part before the first dot
                return host.substring(0, host.indexOf('.'));
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Shows an error message to the user
     */
    private void showError(String message) {
        JOptionPane.showMessageDialog(null, message, "Orbit Helper Error", JOptionPane.ERROR_MESSAGE);
    }
}