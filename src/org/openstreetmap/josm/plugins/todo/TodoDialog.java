// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.todo;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.openstreetmap.josm.actions.AutoScaleAction;
import org.openstreetmap.josm.actions.AutoScaleAction.AutoScaleMode;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.widgets.ListPopupMenu;
import org.openstreetmap.josm.gui.widgets.PopupMenuLauncher;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Todo dialog.
 */
public class TodoDialog extends ToggleDialog implements PropertyChangeListener, LayerChangeListener {

    private static final long serialVersionUID = 3590739974800809827L;

    private final DefaultListSelectionModel selectionModel = new DefaultListSelectionModel();
    private final TodoListModel model = new TodoListModel(selectionModel);
    private final JList<TodoListItem> lstPrimitives = new JList<>(model);
    private final AddAction actAdd = new AddAction(model);
    private final PassAction actPass = new PassAction(model);
    private final MarkAction actMark = new MarkAction(model);
    private final MarkSelectedAction actMarkSelected = new MarkSelectedAction(model);
    /* The popup must be created AFTER actions */
    private final TodoPopup popupMenu = new TodoPopup(lstPrimitives);

    // CHECKSTYLE.OFF: LineLength
    private final Shortcut sctPass = Shortcut.registerShortcut("subwindow:todo:pass", tr("Pass over element without marking it"), KeyEvent.VK_OPEN_BRACKET, Shortcut.DIRECT);
    private final Shortcut sctMark = Shortcut.registerShortcut("subwindow:todo:mark", tr("Mark element done"), KeyEvent.VK_CLOSE_BRACKET, Shortcut.DIRECT);
    // CHECKSTYLE.ON: LineLength

    /**
     * Constructs a new {@code TodoDialog}.
     */
    public TodoDialog() {
        super(tr("Todo list"), "todo", tr("Open the todo list."),
                Shortcut.registerShortcut("subwindow:todo", tr("Toggle: {0}", tr("Todo list")),
                        KeyEvent.VK_T, Shortcut.CTRL_SHIFT), 150);
        buildContentPanel();

        MainApplication.getLayerManager().addLayerChangeListener(this);
        lstPrimitives.addMouseListener(new DblClickHandler());
        lstPrimitives.addMouseListener(new TodoPopupLauncher());
        toggleAction.addPropertyChangeListener(this);
    }

    /**
     * Builds the content panel for this dialog
     */
    protected void buildContentPanel() {
        lstPrimitives.setSelectionModel(selectionModel);
        lstPrimitives.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstPrimitives.setCellRenderer(new TodoListItemRenderer());
        lstPrimitives.setTransferHandler(null);

        // the select action
        SelectAction actSelect = new SelectAction(model);
        SideButton selectButton = new SideButton(actSelect);
        lstPrimitives.getSelectionModel().addListSelectionListener(actSelect);

        // the add button
        SideButton addButton = new SideButton(actAdd);
        actAdd.updateEnabledState();

        // the pass button
        SideButton passButton = new SideButton(actPass);
        lstPrimitives.getSelectionModel().addListSelectionListener(actPass);
        MainApplication.registerActionShortcut(actPass, sctPass);

        // the mark button
        SideButton markButton = new SideButton(actMark);
        lstPrimitives.getSelectionModel().addListSelectionListener(actMark);
        MainApplication.registerActionShortcut(actMark, sctMark);

        // the mark from map button
        SideButton markSelectedButton = new SideButton(actMarkSelected);

        createLayout(lstPrimitives, true, Arrays.asList(new SideButton[] {
                selectButton, addButton, passButton, markButton, markSelectedButton
        }));
    }

    private static void zoom(OsmDataLayer layer) {
        OsmDataLayer prev = MainApplication.getLayerManager().getEditLayer();

        try {
            MainApplication.getLayerManager().setActiveLayer(layer);
            AutoScaleAction.autoScale(AutoScaleMode.SELECTION);
        } finally {
            if (prev != null) {
                MainApplication.getLayerManager().setActiveLayer(prev);
            }
        }
    }

    protected static void selectAndZoom(TodoListItem object) {
        if (object == null) return;
        object.layer.data.setSelected(object.primitive);
        zoom(object.layer);
    }

    protected static void selectAndZoom(Collection<TodoListItem> object) {
        if (object == null || object.isEmpty()) return;
        OsmDataLayer layer = null;
        while (!object.isEmpty()) {
            layer = ((TodoListItem) object.toArray()[0]).layer;
            Collection<OsmPrimitive> items = new ArrayList<>();
            for (Iterator<TodoListItem> it = object.iterator(); it.hasNext();) {
                TodoListItem item = it.next();
                if (item.layer != layer) continue;
                items.add(item.primitive);
                it.remove();
            }
            layer.data.setSelected(items);
        }
        zoom(layer);
    }

    protected void updateTitle() {
        setTitle(model.getSummary());
    }

    @Override
    public void showNotify() {
        SelectionEventManager.getInstance().addSelectionListenerForEdt(actAdd);
        SelectionEventManager.getInstance().addSelectionListenerForEdt(actMarkSelected);
        actAdd.updateEnabledState();
    }

    protected Collection<TodoListItem> getItems() {
        OsmDataLayer layer = MainApplication.getLayerManager().getEditLayer();
        if (layer == null)
            return null;

        return layer.data.getSelected().stream().map(primitive -> new TodoListItem(layer, primitive)).collect(Collectors.toList());
    }

    private static class SelectAction extends AbstractAction implements ListSelectionListener {
        private final TodoListModel model;

        SelectAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Zoom"));
            putValue(SHORT_DESCRIPTION, tr("Zoom to the selected item in the todo list."));
            new ImageProvider("dialogs", "zoom-best-fit").getResource().attachImageIcon(this, true);
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            selectAndZoom(model.getSelected());
        }

        public void updateEnabledState() {
            setEnabled(model.getSelected() != null);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }
    }

    private static class SelectUnmarkedAction extends AbstractAction implements ListSelectionListener {
        private final TodoListModel model;

        SelectUnmarkedAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Select all Unmarked and Zoom"));
            putValue(SHORT_DESCRIPTION, tr("Select and zoom to all of the unmarked items in the todo list."));
            new ImageProvider("dialogs", "zoom-best-fit").getResource().attachImageIcon(this, true);
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            selectAndZoom(model.getTodoList());
        }

        public void updateEnabledState() {
            setEnabled(model.getSize() > 0);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }
    }

    private static class PassAction extends AbstractAction implements ListSelectionListener {
        private final TodoListModel model;

        PassAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Pass"));
            putValue(SHORT_DESCRIPTION, tr("Moves on to the next item but leaves this item in the todo list. ([)."));
            new ImageProvider("dialogs", "zoom-best-fit").getResource().attachImageIcon(this, true);
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            model.incrementSelection();
            selectAndZoom(model.getSelected());
        }

        public void updateEnabledState() {
            setEnabled(model.getSize() > 0);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }
    }

    private class AddAction extends AbstractAction implements DataSelectionListener {
        private final TodoListModel model;

        AddAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Add"));
            putValue(SHORT_DESCRIPTION, tr("Add the selected items to the todo list."));
            new ImageProvider("dialogs", "add").getResource().attachImageIcon(this, true);
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            model.addItems(getItems());
            updateTitle();
        }

        public void updateEnabledState() {
            if (MainApplication.getLayerManager().getEditLayer() == null) {
                setEnabled(false);
            } else {
                setEnabled(!MainApplication.getLayerManager().getEditLayer().data.selectionEmpty());
            }
        }

        @Override
        public void selectionChanged(SelectionChangeEvent event) {
            updateEnabledState();
        }
    }

    private class MarkSelectedAction extends AbstractAction implements DataSelectionListener {
        private final TodoListModel model;

        MarkSelectedAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Mark selected"));
            putValue(SHORT_DESCRIPTION, tr("Mark the selected items (on the map) as done in the todo list."));
            new ImageProvider("dialogs", "select").getResource().attachImageIcon(this, true);
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            model.markItems(getItems());
            updateTitle();
        }

        public void updateEnabledState() {
            if (MainApplication.getLayerManager().getEditLayer() == null) {
                setEnabled(false);
            } else {
                setEnabled(!MainApplication.getLayerManager().getEditLayer().data.selectionEmpty());
            }
        }

        @Override
        public void selectionChanged(SelectionChangeEvent event) {
            updateEnabledState();
        }
    }

    private class MarkAction extends AbstractAction implements ListSelectionListener {

        TodoListModel model;

        MarkAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Mark"));
            putValue(SHORT_DESCRIPTION, tr("Mark the selected item in the todo list as done. (])."));
            new ImageProvider("dialogs", "check").getResource().attachImageIcon(this, true);
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            model.markSelected();
            selectAndZoom(model.getSelected());
            updateTitle();
        }

        public void updateEnabledState() {
            setEnabled(model.getSelected() != null);
        }

        @Override
        public void valueChanged(ListSelectionEvent arg0) {
            updateEnabledState();
        }
    }

    private class MarkAllAction extends AbstractAction {

        TodoListModel model;

        MarkAllAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Mark all"));
            putValue(SHORT_DESCRIPTION, tr("Mark all items in the todo list as done."));
            new ImageProvider("dialogs", "todo").getResource().attachImageIcon(this, true);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            model.markAll();
            selectAndZoom(model.getSelected());
            updateTitle();
        }

    }

    private class UnmarkAllAction extends AbstractAction {

        TodoListModel model;

        UnmarkAllAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Unmark all"));
            putValue(SHORT_DESCRIPTION, tr("Unmark all items in the todo list that have been marked as done."));
            new ImageProvider("dialogs", "refresh").getResource().attachImageIcon(this, true);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            model.unmarkAll();
            updateTitle();
        }
    }

    private class ClearAction extends AbstractAction {

        TodoListModel model;

        ClearAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Clear the todo list"));
            putValue(SHORT_DESCRIPTION, tr("Remove all items (marked and unmarked) from the todo list."));
            new ImageProvider("dialogs", "delete").getResource().attachImageIcon(this, true);
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            model.clear();
            updateTitle();
        }
    }

    /**
     * Responds to double clicks on the list of selected objects
     */
    class DblClickHandler extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() < 2 || !SwingUtilities.isLeftMouseButton(e))
                return;
            selectAndZoom(model.getSelected());
        }
    }

    /**
     * The popup menu launcher.
     */
    class TodoPopupLauncher extends PopupMenuLauncher {
        @Override
        public void launch(MouseEvent evt) {
            int idx = lstPrimitives.locationToIndex(evt.getPoint());
            if (idx >= 0)
                model.setSelected(model.getElementAt(idx));

            popupMenu.show(lstPrimitives, evt.getX(), evt.getY());
        }
    }

    /**
     * A right-click popup context menu for setting options and performing other functions on the todo list items.
     */
    class TodoPopup extends ListPopupMenu {
        TodoPopup(JList<TodoListItem> list) {
            super(list);
            add(new SelectAction(model));
            add(new MarkAction(model));
            addSeparator();
            add(new MarkAllAction(model));
            add(new UnmarkAllAction(model));
            add(new ClearAction(model));
            addSeparator();
            add(actMarkSelected);
            add(new SelectUnmarkedAction(model));
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent arg0) {
        actAdd.updateEnabledState();
    }

    @Override
    public void destroy() {
        super.destroy();
        MainApplication.getLayerManager().removeLayerChangeListener(this);
        MainApplication.unregisterActionShortcut(actPass, sctPass);
        MainApplication.unregisterActionShortcut(actMark, sctMark);
    }

    @Override
    public void layerAdded(LayerAddEvent e) {
        // Do nothing
    }

    @Override
    public void layerRemoving(LayerRemoveEvent e) {
        if (e.getRemovedLayer() instanceof OsmDataLayer) {
            if (model.purgeLayerItems((OsmDataLayer) e.getRemovedLayer())) {
                updateTitle();
            }
        }
    }

    @Override
    public void layerOrderChanged(LayerOrderChangeEvent e) {
        // Do nothing
    }
}
