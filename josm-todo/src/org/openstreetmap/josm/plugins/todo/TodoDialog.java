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
    private TodoListModel model;
    private JList lstPrimitives;
    private final TodoPopup popupMenu;
    private AddAction actAdd;
    private MarkSelectedAction actMarkSelected;

    /**
     * Builds the content panel for this dialog
     */
    protected void buildContentPanel() {
        DefaultListSelectionModel selectionModel  = new DefaultListSelectionModel();
        model = new TodoListModel(selectionModel);
        lstPrimitives = new JList(model);
        lstPrimitives.setSelectionModel(selectionModel);
        lstPrimitives.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstPrimitives.setCellRenderer(new OsmPrimitivRenderer());
        lstPrimitives.setTransferHandler(null);

        // the select action
        SelectAction actSelect;
        final SideButton selectButton = new SideButton(actSelect = new SelectAction(model));
        lstPrimitives.getSelectionModel().addListSelectionListener(actSelect);

        // the add button
        final SideButton addButton = new SideButton(actAdd = new AddAction(model));

        // the pass button
        PassAction actPass;
        final SideButton passButton = new SideButton(actPass = new PassAction(model));
        lstPrimitives.getSelectionModel().addListSelectionListener(actPass);
        Main.registerActionShortcut(actPass, Shortcut.registerShortcut("subwindow:todo:pass",
                tr("Pass over element without marking it"), KeyEvent.VK_OPEN_BRACKET, Shortcut.DIRECT));

        // the mark button
        MarkAction actMark;
        final SideButton markButton = new SideButton(actMark = new MarkAction(model));
        lstPrimitives.getSelectionModel().addListSelectionListener(actMark);
        Main.registerActionShortcut(actMark, Shortcut.registerShortcut("subwindow:todo:mark",
                tr("Mark element done"), KeyEvent.VK_CLOSE_BRACKET, Shortcut.DIRECT));

        createLayout(lstPrimitives, true, Arrays.asList(new SideButton[] {
                selectButton, addButton, passButton, markButton
        }));
    }


    public TodoDialog() {
        super(tr("Todo list"), "todo", tr("Open the todo list."),
                Shortcut.registerShortcut("subwindow:todo", tr("Toggle: {0}", tr("Todo list")),
                        KeyEvent.VK_T, Shortcut.CTRL_SHIFT), 150);
        buildContentPanel();

        lstPrimitives.addMouseListener(new DblClickHandler());
        lstPrimitives.addMouseListener(new TodoPopupLauncher());
        this.toggleAction.addPropertyChangeListener(this);

        popupMenu = new TodoPopup(lstPrimitives);
    }

    protected static void selectAndZoom(OsmPrimitive object) {
        if (object == null) return;
        if (Main.map == null || Main.map.mapView == null || Main.map.mapView.getEditLayer() == null) return;
        Main.map.mapView.getEditLayer().data.setSelected(object);
        AutoScaleAction.autoScale("selection");
    }

    protected static void selectAndZoom(Collection<OsmPrimitive> object) {
        if (object == null) return;
        if (Main.map == null || Main.map.mapView == null || Main.map.mapView.getEditLayer() == null) return;
        Main.map.mapView.getEditLayer().data.setSelected(object);
        AutoScaleAction.autoScale("selection");
    }

    protected void updateTitle() {
        setTitle(model.getSummary());
    }

    @Override
    public void showNotify() {
        SelectionEventManager.getInstance().addSelectionListener(actAdd, FireMode.IN_EDT_CONSOLIDATED);
        SelectionEventManager.getInstance().addSelectionListener(actMarkSelected, FireMode.IN_EDT_CONSOLIDATED);
    }

    private class SelectAction extends AbstractAction implements ListSelectionListener {
        private final TodoListModel model;

        public SelectAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Zoom"));
            putValue(SHORT_DESCRIPTION,  tr("Zoom to the selected item in the todo list."));
            putValue(SMALL_ICON, ImageProvider.get("dialogs","zoom-best-fit"));
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

    private class SelectUnmarkedAction extends AbstractAction implements ListSelectionListener {
        private final TodoListModel model;

        public SelectUnmarkedAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Select all Unmarked and Zoom"));
            putValue(SHORT_DESCRIPTION,  tr("Select and zoom to all of the unmarked items in the todo list."));
            putValue(SMALL_ICON, ImageProvider.get("dialogs","zoom-best-fit"));
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

    private class PassAction extends AbstractAction implements ListSelectionListener {
        private final TodoListModel model;

        public PassAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Pass"));
            putValue(SHORT_DESCRIPTION,  tr("Moves on to the next item but leaves this item in the todo list. ([)."));
            putValue(SMALL_ICON, ImageProvider.get("dialogs","zoom-best-fit"));
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

        public AddAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Add"));
            putValue(SHORT_DESCRIPTION,  tr("Add the selected items to the todo list."));
            putValue(SMALL_ICON, ImageProvider.get("dialogs","add"));
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (Main.map == null || Main.map.mapView == null || Main.map.mapView.getEditLayer() == null) return;
            Collection<OsmPrimitive> sel = Main.map.mapView.getEditLayer().data.getSelected();
            model.addItems(sel);
            updateTitle();
        }

        public void updateEnabledState() {
            if (Main.map == null || Main.map.mapView == null || Main.map.mapView.getEditLayer() == null) {
                System.out.println("No edit layer");
                setEnabled(false);
            } else {
                System.out.println("Edit layer found with selection " + !Main.map.mapView.getEditLayer().data.selectionEmpty());
                setEnabled(!Main.map.mapView.getEditLayer().data.selectionEmpty());
            }
        }

        @Override
        public void selectionChanged(Collection<? extends OsmPrimitive> arg) {
            updateEnabledState();
        }
    }

    private class MarkSelectedAction extends AbstractAction implements SelectionChangedListener {
        private final TodoListModel model;

        public MarkSelectedAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Mark selected"));
            putValue(SHORT_DESCRIPTION,  tr("Mark the selected items (on the map) as done in the todo list."));
            putValue(SMALL_ICON, ImageProvider.get("dialogs","select"));
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (Main.map == null || Main.map.mapView == null || Main.map.mapView.getEditLayer() == null) return;
            Collection<OsmPrimitive> sel = Main.map.mapView.getEditLayer().data.getSelected();
            model.markItems(sel);
            updateTitle();
        }

        public void updateEnabledState() {
            if (Main.map == null || Main.map.mapView == null || Main.map.mapView.getEditLayer() == null) {
                setEnabled(false);
            } else {
                setEnabled(!Main.map.mapView.getEditLayer().data.selectionEmpty());
            }
        }

        @Override
        public void selectionChanged(Collection<? extends OsmPrimitive> arg) {
            updateEnabledState();
        }
    }

    private class MarkAction extends AbstractAction implements ListSelectionListener {

        TodoListModel model;

        public MarkAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Mark"));
            putValue(SHORT_DESCRIPTION,  tr("Mark the selected item in the todo list as done. (])."));
            putValue(SMALL_ICON, ImageProvider.get("dialogs","check"));
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

        public MarkAllAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Mark all"));
            putValue(SHORT_DESCRIPTION,  tr("Mark all items in the todo list as done."));
            putValue(SMALL_ICON, ImageProvider.get("dialogs","todo"));
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

        public UnmarkAllAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Unmark all"));
            putValue(SHORT_DESCRIPTION,  tr("Unmark all items in the todo list that have been marked as done."));
            putValue(SMALL_ICON, ImageProvider.get("dialogs","refresh"));
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            model.unmarkAll();
            updateTitle();
        }

    }

    private class ClearAction extends AbstractAction {

        TodoListModel model;

        public ClearAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Clear the todo list"));
            putValue(SHORT_DESCRIPTION,  tr("Remove all items (marked and unmarked) from the todo list."));
            putValue(SMALL_ICON, ImageProvider.get("dialogs","remove"));
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
            if (e.getClickCount() < 2 || ! SwingUtilities.isLeftMouseButton(e)) return;
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
            if(idx >= 0)  model.setSelected((OsmPrimitive)model.getElementAt(idx));

            popupMenu.show(lstPrimitives, evt.getX(), evt.getY());
        }
    }

    /**
     * A right-click popup context menu for setting options and performing other functions on the todo list items.
     */
    class TodoPopup extends ListPopupMenu {
        public TodoPopup(JList list) {
            super(list);
            add(new SelectAction(model));
            add(new MarkAction(model));
            addSeparator();
            add(new MarkAllAction(model));
            add(new UnmarkAllAction(model));
            add(new ClearAction(model));
            addSeparator();
            add(actMarkSelected = new MarkSelectedAction(model));
            add(new SelectUnmarkedAction(model));

        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent arg0) {
        actAdd.updateEnabledState();
    }
}
