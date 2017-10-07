// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.todo;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListSelectionModel;

public class TodoListModel extends AbstractListModel<TodoListItem> {

    private final ArrayList<TodoListItem> todoList = new ArrayList<>();
    private final ArrayList<TodoListItem> doneList = new ArrayList<>();
    private final DefaultListSelectionModel selectionModel;

    public TodoListModel(DefaultListSelectionModel selectionModel) {
        this.selectionModel = selectionModel;
    }

    @Override
    public TodoListItem getElementAt(int index) {
        return todoList.get(index);
    }

    @Override
    public int getSize() {
        return todoList.size();
    }

    public int getDoneSize() {
        return doneList.size();
    }

    public TodoListItem getSelected() {
        if (getSize() == 0 || selectionModel.isSelectionEmpty() || selectionModel.getMinSelectionIndex() >= getSize())
            return null;
        return todoList.get(selectionModel.getMinSelectionIndex());
    }

    public List<TodoListItem> getTodoList() {
        return todoList;
    }

    public void incrementSelection() {
        int idx;
        if (getSize() == 0)
            return;
        if (selectionModel.isSelectionEmpty())
            idx = 0;
        else
            idx = selectionModel.getMinSelectionIndex() + 1;

        if (idx > getSize()-1)
            idx = getSize()-1;

        selectionModel.setSelectionInterval(idx, idx);
    }

    public void addItems(Collection<TodoListItem> items) {
        if (items == null || items.isEmpty())
            return;
        doneList.removeAll(items);
        int size = getSize();
        if (size == 0) {
            todoList.addAll(items);
            super.fireIntervalAdded(this, 0, getSize()-1);
            selectionModel.setSelectionInterval(0, 0);
        } else {
            todoList.ensureCapacity(size + items.size());
            for (TodoListItem item: items) {
                if (!todoList.contains(item))
                    todoList.add(item);
            }
            super.fireIntervalAdded(this, size, getSize()-1);
        }
    }

    public void markSelected() {
        if (selectionModel.isSelectionEmpty() || getSize() == 0)
            return;
        int sel = selectionModel.getMinSelectionIndex();
        if (sel >= todoList.size())
            return;
        doneList.add(todoList.remove(sel));
        super.fireIntervalRemoved(this, sel, sel);
        if (sel == getSize())
            sel = 0;
        selectionModel.setSelectionInterval(sel, sel);
    }

    public void setSelected(TodoListItem element) {
        int sel = todoList.indexOf(element);
        if (sel == -1)
            return;
        selectionModel.setSelectionInterval(sel, sel);
    }

    public void markAll() {
        int size = getSize();
        if (size == 0)
            return;
        doneList.addAll(todoList);
        todoList.clear();
        super.fireIntervalRemoved(this, 0, size-1);
    }

    public void markItems(Collection<TodoListItem> items) {
        if (items == null || items.isEmpty())
            return;
        int size = getSize();
        if (size == 0)
            return;

        int sel = selectionModel.getMinSelectionIndex();

        for (TodoListItem item: items) {
            int i;
            if ((i = todoList.indexOf(item)) != -1) {
                todoList.remove(i);
                doneList.add(item);
                if (sel > i)
                    sel--;
            }
        }
        if (sel >= getSize())
            sel = 0;
        super.fireIntervalRemoved(this, 0, size-1);
        selectionModel.setSelectionInterval(sel, sel);
    }

    public void clear() {
        int size = getSize();
        doneList.clear();
        todoList.clear();
        if (size > 0)
            super.fireIntervalRemoved(this, 0, size-1);
    }

    public void unmarkAll() {
        if (getDoneSize() == 0)
            return;
        int size = getSize();
        if (size == 0) {
            todoList.addAll(doneList);
            doneList.clear();
            super.fireIntervalAdded(this, 0, getSize()-1);
            selectionModel.setSelectionInterval(0, 0);
        } else {
            todoList.addAll(doneList);
            doneList.clear();
            super.fireIntervalAdded(this, size, getSize()-1);
        }
    }

    public String getSummary() {
        int totalSize = getSize()+getDoneSize();
        if (getSize() == 0 && getDoneSize() == 0)
            return tr("Todo list");
        else return tr("Todo list {0}/{1} ({2}%)", getDoneSize(), totalSize, 100.0*getDoneSize()/totalSize);
    }

}
