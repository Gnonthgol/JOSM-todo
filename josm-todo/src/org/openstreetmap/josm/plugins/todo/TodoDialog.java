package org.openstreetmap.josm.plugins.todo;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collection;

import javax.swing.AbstractAction;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.OsmPrimitivRenderer;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

public class TodoDialog extends ToggleDialog {

	private static final long serialVersionUID = 3590739974800809827L;
	private TodoListModel model;

	/**
     * Builds the content panel for this dialog
     */
    protected void buildContentPanel() {
        DefaultListSelectionModel selectionModel  = new DefaultListSelectionModel();
        model = new TodoListModel(selectionModel);
        JList lstPrimitives = new JList(model);
        lstPrimitives.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstPrimitives.setSelectionModel(selectionModel);
        lstPrimitives.setCellRenderer(new OsmPrimitivRenderer());
        lstPrimitives.setTransferHandler(null);

        // the select action
        SelectAction actSelect;
        final SideButton selectButton = new SideButton(actSelect = new SelectAction(model));
        lstPrimitives.getSelectionModel().addListSelectionListener(actSelect);

		// the add button
        AddAction actAdd;
        final SideButton addButton = new SideButton(actAdd = new AddAction(model));

        createLayout(lstPrimitives, true, Arrays.asList(new SideButton[] {
            selectButton, addButton
        }));
    }

	
	public TodoDialog() {
		super(tr("Todo list"), "todo", tr("Open the todo list."),
		        Shortcut.registerShortcut("subwindow:todo", tr("Toggle: {0}", tr("Todo list")),
		                KeyEvent.VK_T, Shortcut.CTRL_SHIFT), 150);
		buildContentPanel();
		// TODO Auto-generated constructor stub
	}

	private class SelectAction extends AbstractAction implements ListSelectionListener {
	    private TodoListModel model;

		public SelectAction(TodoListModel model) {
			this.model = model;
	        putValue(NAME, tr("Select"));
	        putValue(SHORT_DESCRIPTION,  tr("Set the selected elements on the map to the selected items in the list above."));
	        putValue(SMALL_ICON, ImageProvider.get("dialogs","select"));
	        updateEnabledState();
	    }

	    public void actionPerformed(ActionEvent e) {
	        OsmPrimitive sel = model.getSelected();
	        if (sel == null) return;
	        if (Main.map == null || Main.map.mapView == null || Main.map.mapView.getEditLayer() == null) return;
	        Main.map.mapView.getEditLayer().data.setSelected(sel);
	    }

	    public void updateEnabledState() {
	        setEnabled(model.getSelected() != null);
	    }

	    public void valueChanged(ListSelectionEvent e) {
	        updateEnabledState();
	    }
	}
	
	private class AddAction extends AbstractAction {
	    private TodoListModel model;

		public AddAction(TodoListModel model) {
			this.model = model;
	        putValue(NAME, tr("Add"));
	        putValue(SHORT_DESCRIPTION,  tr("Add the selected items to the todo list."));
	        putValue(SMALL_ICON, ImageProvider.get("dialogs","add"));
	        setEnabled(true);
	    }

	    public void actionPerformed(ActionEvent e) {
	        if (Main.map == null || Main.map.mapView == null || Main.map.mapView.getEditLayer() == null) return;
	        Collection<OsmPrimitive> sel = Main.map.mapView.getEditLayer().data.getSelected();
	        model.addItems(sel);
	        System.out.printf("Added %d elements there are now %d elements todo%n", sel.size(), model.getSize());
	    }
	}
}
