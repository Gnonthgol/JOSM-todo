package org.openstreetmap.josm.plugins.todo;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

public class TodoDialog extends ToggleDialog {

    private static final long serialVersionUID = 3590739974800809827L;
    private TodoListModel model;
    private JList lstPrimitives;
    private final TodoPopup popupMenu;
    private AddAction actAdd;

    /**
     * Builds the content panel for this dialog
     */
    protected void buildContentPanel() {
        DefaultListSelectionModel selectionModel  = new DefaultListSelectionModel();
        model = new TodoListModel(selectionModel);
        lstPrimitives = new JList(model);
        lstPrimitives.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        lstPrimitives.setSelectionModel(selectionModel);
        lstPrimitives.setCellRenderer(new OsmPrimitivRenderer());
        lstPrimitives.setTransferHandler(null);

        // the select action
        SelectAction actSelect;
        final SideButton selectButton = new SideButton(actSelect = new SelectAction(model));
        lstPrimitives.getSelectionModel().addListSelectionListener(actSelect);

        // the add button
        final SideButton addButton = new SideButton(actAdd = new AddAction(model));

        // the mark button
        MarkAction actMark;
        final SideButton markButton = new SideButton(actMark = new MarkAction(model));
        lstPrimitives.getSelectionModel().addListSelectionListener(actMark);
        Main.registerActionShortcut(actMark, Shortcut.registerShortcut("subwindow:todo:mark",
                tr("Mark element done"), KeyEvent.VK_TAB, Shortcut.DIRECT));

        createLayout(lstPrimitives, true, Arrays.asList(new SideButton[] {
                selectButton, addButton, markButton
        }));
    }


    public TodoDialog() {
        super(tr("Todo list"), "todo", tr("Open the todo list."),
                Shortcut.registerShortcut("subwindow:todo", tr("Toggle: {0}", tr("Todo list")),
                        KeyEvent.VK_T, Shortcut.CTRL_SHIFT), 150);
        buildContentPanel();

        lstPrimitives.addMouseListener(new DblClickHandler());
        lstPrimitives.addMouseListener(new TodoPopupLauncher());

        popupMenu = new TodoPopup(lstPrimitives);
    }

    protected static void selectAndZoom(OsmPrimitive object) {
        if (object == null) return;
        if (Main.map == null || Main.map.mapView == null || Main.map.mapView.getEditLayer() == null) return;
        Main.map.mapView.getEditLayer().data.setSelected(object);
        AutoScaleAction.autoScale("selection");
    }

    @Override
    public void showNotify() {
        SelectionEventManager.getInstance().addSelectionListener(actAdd, FireMode.IN_EDT_CONSOLIDATED);
    }

    private class SelectAction extends AbstractAction implements ListSelectionListener {
        private final TodoListModel model;

        public SelectAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Zoom"));
            putValue(SHORT_DESCRIPTION,  tr("Select and zoom to the selected item"));
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
            putValue(SHORT_DESCRIPTION,  tr("Mark the selected item as done."));
            putValue(SMALL_ICON, ImageProvider.get("dialogs","check"));
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            model.markSelected();
            selectAndZoom(model.getSelected());
        }

        public void updateEnabledState() {
            setEnabled(model.getSelected() != null);
        }

        @Override
        public void valueChanged(ListSelectionEvent arg0) {
            updateEnabledState();
        }

    }

    private class MarkAllAction extends AbstractAction implements ListSelectionListener {

        TodoListModel model;

        public MarkAllAction(TodoListModel model) {
            this.model = model;
            putValue(NAME, tr("Mark all"));
            putValue(SHORT_DESCRIPTION,  tr("Mark all items in the list as done."));
            putValue(SMALL_ICON, ImageProvider.get("dialogs","todo"));
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            model.markAll();
            selectAndZoom(model.getSelected());
        }

        public void updateEnabledState() {
            setEnabled(model.getSelected() != null);
        }

        @Override
        public void valueChanged(ListSelectionEvent arg0) {
            updateEnabledState();
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
            if(idx < 0)  return;
            model.setSelected((OsmPrimitive)model.getElementAt(idx));

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
            add(new MarkAllAction(model));
        }
    }
}
