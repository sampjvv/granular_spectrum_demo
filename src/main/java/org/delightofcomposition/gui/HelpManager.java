package org.delightofcomposition.gui;

import java.awt.Component;
import java.util.IdentityHashMap;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;

/**
 * Singleton managing help tooltip state for educational tooltips.
 * When help mode is active, all registered components show their help text as tooltips.
 */
public class HelpManager {

    private static final HelpManager INSTANCE = new HelpManager();

    private static final int HELP_DISMISS_DELAY = 10_000; // 10 seconds

    private boolean helpModeActive = false;
    private int defaultDismissDelay = ToolTipManager.sharedInstance().getDismissDelay();
    private final Map<JComponent, String> helpTexts = new IdentityHashMap<>();

    private HelpManager() {}

    public static HelpManager getInstance() {
        return INSTANCE;
    }

    public void register(JComponent component, String helpText) {
        helpTexts.put(component, helpText);
        if (helpModeActive) {
            setTooltipRecursive(component, helpText);
        }
    }

    public void setHelpMode(boolean active) {
        this.helpModeActive = active;
        ToolTipManager ttm = ToolTipManager.sharedInstance();
        if (active) {
            ttm.setDismissDelay(HELP_DISMISS_DELAY);
        } else {
            ttm.setDismissDelay(defaultDismissDelay);
        }
        for (Map.Entry<JComponent, String> entry : helpTexts.entrySet()) {
            setTooltipRecursive(entry.getKey(), active ? entry.getValue() : null);
        }
    }

    public boolean isHelpMode() {
        return helpModeActive;
    }

    private static void setTooltipRecursive(JComponent comp, String text) {
        comp.setToolTipText(text);
        for (Component child : comp.getComponents()) {
            if (child instanceof JComponent) {
                setTooltipRecursive((JComponent) child, text);
            }
        }
    }
}
