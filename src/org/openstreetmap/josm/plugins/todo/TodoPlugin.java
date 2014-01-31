package org.openstreetmap.josm.plugins.todo;

import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

public class TodoPlugin extends Plugin {

    private TodoDialog todoDialog = null;

    public TodoPlugin(PluginInformation info) {
        super(info);
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if (newFrame != null && newFrame.mapView != null) {
            todoDialog = new TodoDialog();
            newFrame.addToggleDialog(todoDialog);
        } else if (newFrame == null) {
            todoDialog = null;
        }
    }
}
