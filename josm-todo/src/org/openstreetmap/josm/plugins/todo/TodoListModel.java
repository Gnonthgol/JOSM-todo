package org.openstreetmap.josm.plugins.todo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListSelectionModel;

import org.openstreetmap.josm.data.osm.OsmPrimitive;

public class TodoListModel extends AbstractListModel {

	private final List<OsmPrimitive> selection = new ArrayList<OsmPrimitive>();
	
	public TodoListModel(DefaultListSelectionModel selectionModel) {
		// TODO Auto-generated constructor stub
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
		// TODO Auto-generated method stub
		return null;
	}

	public void addItems(Collection<OsmPrimitive> items) {
		selection.addAll(items);
		super.fireContentsChanged(this, getSize()-items.size(), getSize());
	}

}
