// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.todo;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Collection;

import javax.swing.AbstractAction;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JList;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.AutoScaleAction;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager.FireMode;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.gui.OsmPrimitivRenderer;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.widgets.ListPopupMenu;
import org.openstreetmap.josm.gui.widgets.PopupMenuLauncher;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Shortcut;

public class TodoDialog extends ToggleDialog implements PropertyChangeListener {

    private static final long serialVersionUID = 3590739974800809827L;

    private final DefaultListSelectionModel selectionModel = new DefaultListSelectionModel();
    private final TodoListModel model = new TodoListModel(selectionModel);
    private final JList<OsmPrimitive> lstPrimitives = new JList<>(model);
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
        lstPrimitives.setCellRenderer(new OsmPrimitivRenderer());
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
        Main.registerActionShortcut(actPass, sctPass);

        // the mark button
        SideButton markButton = new SideButton(actMark);
        lstPrimitives.getSelectionModel().addListSelectionListener(actMark);
        Main.registerActionShortcut(actMark, sctMark);

        createLayout(lstPrimitives, true, Arrays.asList(new SideButton[] {
                selectButton, addButton, passButton, markButton
        }));
    }

    protected static void selectAndZoom(OsmPrimitive object) {
        if (object == null || Main.getLayerManager().getEditLayer() == null)
            return;
        Main.getLayerManager().getEditLayer().data.setSelected(object);
        AutoScaleAction.autoScale("selection");
    }

    protected static void selectAndZoom(Collection<OsmPrimitive> object) {
        if (object == null || Main.getLayerManager().getEditLayer() == null)
            return;
        Main.getLayerManager().getEditLayer().data.setSelected(object);
        AutoScaleAction.autoScale("selection");
    }

    protected void updateTitle() {
        setTitle(model.getSummary());
    }

    @Override
    public void showNotify() {
        SelectionEventManager.getInstance().addSelectionListener(actAdd, FireMode.IN_EDT_CONSOLIDATED);
        SelectionEventManager.getInstance().addSelectionListener(actMarkSelected, FireMode.IN_EDT_CONSOLIDATED);
        actAdd.updateEnabledState();
    }

    private static class SelectAction extends AbstractAction implements ListSelectionListener {
        private final TodoListModel model;

        SelectAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Zoom"));
            putValue(SHORT_DESCRIPTION, tr("Zoom to the selected item in the todo list."));
            new ImageProvider("dialogs", "zoom-best-fit").getResource().attachImageIcon(this);
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
            new ImageProvider("dialogs", "zoom-best-fit").getResource().attachImageIcon(this);
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
            new ImageProvider("dialogs", "zoom-best-fit").getResource().attachImageIcon(this);
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

    private class AddAction extends AbstractAction implements SelectionChangedListener {
        private final TodoListModel model;

        AddAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Add"));
            putValue(SHORT_DESCRIPTION, tr("Add the selected items to the todo list."));
            new ImageProvider("dialogs", "add").getResource().attachImageIcon(this);
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (Main.getLayerManager().getEditLayer() == null)
                return;
            Collection<OsmPrimitive> sel = Main.getLayerManager().getEditLayer().data.getSelected();
            model.addItems(sel);
            updateTitle();
        }

        public void updateEnabledState() {
            if (Main.getLayerManager().getEditLayer() == null) {
                setEnabled(false);
            } else {
                setEnabled(!Main.getLayerManager().getEditLayer().data.selectionEmpty());
            }
        }

        @Override
        public void selectionChanged(Collection<? extends OsmPrimitive> arg) {
            updateEnabledState();
        }
    }

    private class MarkSelectedAction extends AbstractAction implements SelectionChangedListener {
        private final TodoListModel model;

        MarkSelectedAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Mark selected"));
            putValue(SHORT_DESCRIPTION, tr("Mark the selected items (on the map) as done in the todo list."));
            new ImageProvider("dialogs", "select").getResource().attachImageIcon(this);
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (Main.getLayerManager().getEditLayer() == null)
                return;
            Collection<OsmPrimitive> sel = Main.getLayerManager().getEditLayer().data.getSelected();
            model.markItems(sel);
            updateTitle();
        }

        public void updateEnabledState() {
            if (Main.getLayerManager().getEditLayer() == null) {
                setEnabled(false);
            } else {
                setEnabled(!Main.getLayerManager().getEditLayer().data.selectionEmpty());
            }
        }

        @Override
        public void selectionChanged(Collection<? extends OsmPrimitive> arg) {
            updateEnabledState();
        }
    }

    private class MarkAction extends AbstractAction implements ListSelectionListener {

        TodoListModel model;

        MarkAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Mark"));
            putValue(SHORT_DESCRIPTION, tr("Mark the selected item in the todo list as done. (])."));
            new ImageProvider("dialogs", "check").getResource().attachImageIcon(this);
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
            new ImageProvider("dialogs", "todo").getResource().attachImageIcon(this);
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
            new ImageProvider("dialogs", "refresh").getResource().attachImageIcon(this);
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
            new ImageProvider("dialogs", "delete").getResource().attachImageIcon(this);
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
        TodoPopup(JList<OsmPrimitive> list) {
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
        Main.unregisterActionShortcut(actPass, sctPass);
        Main.unregisterActionShortcut(actMark, sctMark);
    }
}
