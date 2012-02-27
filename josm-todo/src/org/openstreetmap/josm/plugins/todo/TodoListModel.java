package org.openstreetmap.josm.plugins.todo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.openstreetmap.josm.data.osm.OsmPrimitive;

public class TodoListModel extends AbstractListModel {

	private final List<OsmPrimitive> selection = new ArrayList<OsmPrimitive>();
	private DefaultListSelectionModel selectionModel;
	
	public TodoListModel(DefaultListSelectionModel selectionModel) {
		this.selectionModel = selectionModel;
	}

	@Override
	public Object getElementAt(int index) {
		return selection.get(index);
	}

	@Override
	public int getSize() {
		return selection.size();
	}

	public OsmPrimitive getSelected() {
		if (getSize() == 0 || selectionModel.isSelectionEmpty()) return null;
		return selection.get(selectionModel.getMaxSelectionIndex());
	}

	public void addItems(Collection<OsmPrimitive> items) {
		selection.addAll(items); //TODO remove duplicates
		super.fireIntervalAdded(this, getSize()-items.size(), getSize());
	}

	public void removeSelected() {
		int sel = selectionModel.getMaxSelectionIndex();
		selection.remove(sel);
		super.fireIntervalRemoved(this, sel, sel);
		if (sel == getSize()) sel = 0;
		selectionModel.setSelectionInterval(sel, sel);
	}

}
