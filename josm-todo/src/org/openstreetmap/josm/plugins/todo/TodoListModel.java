package org.openstreetmap.josm.plugins.todo;

import java.util.ArrayList;
import java.util.Collection;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListSelectionModel;

import org.openstreetmap.josm.data.osm.OsmPrimitive;

public class TodoListModel extends AbstractListModel {

    private final ArrayList<OsmPrimitive> todoList = new ArrayList<OsmPrimitive>();
    private final ArrayList<OsmPrimitive> doneList = new ArrayList<OsmPrimitive>();
    private final DefaultListSelectionModel selectionModel;

    public TodoListModel(DefaultListSelectionModel selectionModel) {
        this.selectionModel = selectionModel;
    }

    @Override
    public Object getElementAt(int index) {
        return todoList.get(index);
    }

    @Override
    public int getSize() {
        return todoList.size();
    }

    public int getDoneSize() {
        return doneList.size();
    }

    public OsmPrimitive getSelected() {
        if (getSize() == 0 || selectionModel.isSelectionEmpty()) return null;
        return todoList.get(selectionModel.getMinSelectionIndex());
    }

    public void addItems(Collection<OsmPrimitive> items) {
        doneList.removeAll(items);
        int size = getSize();
        if (size == 0) {
            todoList.addAll(items);
            super.fireIntervalAdded(this, 0, getSize());
            selectionModel.setSelectionInterval(0, 0);
        } else {
            todoList.ensureCapacity(size + items.size());
            for (OsmPrimitive item: items) {
                if (!todoList.contains(item))
                    todoList.add(item);
            }
            super.fireIntervalAdded(this, size, getSize());
        }
    }

    public void markSelected() {
        int sel = selectionModel.getMaxSelectionIndex();
        doneList.add(todoList.remove(sel));
        super.fireIntervalRemoved(this, sel, sel);
        if (sel == getSize()) sel = 0;
        selectionModel.setSelectionInterval(sel, sel);
    }

    public void setSelected(OsmPrimitive element) {
        int sel = todoList.indexOf(element);
        if (sel == -1) return;
        selectionModel.setSelectionInterval(sel, sel);
    }

    public void markAll() {
        int size = getSize();
        doneList.addAll(todoList);
        todoList.clear();
        super.fireIntervalRemoved(this, 0, size);
    }

    public void clear() {
        int size = getSize();
        doneList.clear();
        todoList.clear();
        super.fireIntervalRemoved(this, 0, size);
    }

    public void unmarkAll() {
        int size = getSize();
        todoList.addAll(doneList);
        doneList.clear();
        super.fireIntervalAdded(this, size, getSize());
    }

}
