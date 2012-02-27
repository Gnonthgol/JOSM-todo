package org.openstreetmap.josm.plugins.todo;

import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

public class TodoPlugin extends Plugin {

	private TodoDialog todoDialog;

	public TodoPlugin(PluginInformation info) {
		super(info);
		todoDialog = new TodoDialog();
    }

    @Override
    public void mapFrameInitialized(MapFrame oldFrame, MapFrame newFrame) {
        if(newFrame != null)
            newFrame.addToggleDialog(todoDialog);
    }
}
