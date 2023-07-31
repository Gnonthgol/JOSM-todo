// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.todo;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serial;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.Action;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.openstreetmap.josm.actions.AutoScaleAction;
import org.openstreetmap.josm.actions.AutoScaleAction.AutoScaleMode;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager.FireMode;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.layer.AbstractModifiableLayer;
import org.openstreetmap.josm.gui.layer.AbstractOsmDataLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.util.HighlightHelper;
import org.openstreetmap.josm.gui.widgets.ListPopupMenu;
import org.openstreetmap.josm.gui.widgets.PopupMenuLauncher;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.InputMapUtils;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Todo dialog.
 */
public class TodoDialog extends ToggleDialog implements PropertyChangeListener, LayerChangeListener {

    @Serial
    private static final long serialVersionUID = 3590739974800809827L;

    private final DefaultListSelectionModel selectionModel = new DefaultListSelectionModel();
    private final TodoListModel model = new TodoListModel(selectionModel);
    private final JList<TodoListItem> lstPrimitives = new JList<>(model);
    private final AddAction actAdd = new AddAction(model);
    private final SelectAction actSelect = new SelectAction(model);
    private final PassAction actPass = new PassAction(model);
    private final MarkAction actMark = new MarkAction(model);
    private final MarkSelectedAction actMarkSelected = new MarkSelectedAction(model);
    private final ClearAndAddAction actClearAndAdd = new ClearAndAddAction(model);
    /* The popup must be created AFTER actions */
    private final TodoPopup popupMenu = new TodoPopup(lstPrimitives);

    /**
     * Constructs a new {@code TodoDialog}.
     */
    public TodoDialog() {
        super(tr("Todo list"), "todo", tr("Open the todo list."),
                Shortcut.registerShortcut("subwindow:todo", tr("Toggle: {0}", tr("Todo list")),
                        KeyEvent.VK_T, Shortcut.CTRL_SHIFT), 150);
        buildContentPanel();

        model.addListDataListener(new TitleUpdater());

        MainApplication.getLayerManager().addLayerChangeListener(this);
        DatasetEventManager.getInstance().addDatasetListener(model, FireMode.IN_EDT_CONSOLIDATED);
        lstPrimitives.addMouseListener(new DblClickHandler());
        lstPrimitives.addMouseListener(new TodoPopupLauncher());
        toggleAction.addPropertyChangeListener(this);

        InputMapUtils.addEnterAction(lstPrimitives, actSelect);
    }

    /**
     * Builds the content panel for this dialog
     */
    protected void buildContentPanel() {
        lstPrimitives.setSelectionModel(selectionModel);
        lstPrimitives.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        lstPrimitives.setCellRenderer(new TodoListItemRenderer());
        lstPrimitives.setTransferHandler(null);

        // the select action
        final var selectButton = new SideButton(actSelect);
        lstPrimitives.getSelectionModel().addListSelectionListener(actSelect);
        actSelect.updateEnabledState();

        // the add button
        final var addButton = new SideButton(actAdd);
        actAdd.updateEnabledState();

        // the clear and add button
        addButton.createArrow(l -> showPopupMenu(addButton, actClearAndAdd), true);

        // the pass button
        final var passButton = new SideButton(actPass);
        lstPrimitives.getSelectionModel().addListSelectionListener(actPass);

        // the mark button
        final var markButton = new SideButton(actMark);
        lstPrimitives.getSelectionModel().addListSelectionListener(actMark);

        // the mark from map button
        final var markSelectedButton = new SideButton(actMarkSelected);

        createLayout(lstPrimitives, true, Arrays.asList(selectButton, addButton, passButton, markButton, markSelectedButton));
    }

    private static void showPopupMenu(Component parent, Object... menuItems) {
        final var menu = new JPopupMenu();
        final var box = parent.getBounds();
        for (var item : menuItems) {
            if (item instanceof Action) {
                menu.add((Action) item);
            } else {
                throw new IllegalArgumentException("We don't currently support " + item.getClass());
            }
        }
        menu.show(parent, 0, box.y + box.height);
    }

    private static void zoom(Layer layer) {
        Layer prev = MainApplication.getLayerManager().getEditLayer();

        try {
            MainApplication.getLayerManager().setActiveLayer(layer);
            AutoScaleAction.autoScale(AutoScaleMode.SELECTION);
        } finally {
            if (prev != null) {
                MainApplication.getLayerManager().setActiveLayer(prev);
            }
        }
    }

    static void selectAndZoom(TodoListItem object) {
        if (object == null) return;
        object.layer().getDataSet().setSelected(object.primitive());
        zoom(object.layer());
    }

    static void selectAndZoom(Collection<TodoListItem> object) {
        if (object == null || object.isEmpty()) return;
        Map<AbstractOsmDataLayer, Set<IPrimitive>> sorted = object.stream()
                .collect(Collectors.groupingBy(TodoListItem::layer, Collectors.mapping(TodoListItem::primitive, Collectors.toSet())));
        sorted.forEach((layer, selected) -> layer.getDataSet().setSelected(selected));
        // Find the "most" important layer and zoom to the selection there
        MainLayerManager layerManager = MainApplication.getLayerManager();
        List<AbstractOsmDataLayer> layers =
                // Do edit layer, then active layer, then any other layer that is in the list.
                Stream.concat(Stream.of(layerManager.getEditLayer(), layerManager.getActiveLayer()), sorted.keySet().stream())
                .filter(AbstractOsmDataLayer.class::isInstance)
                .map(AbstractOsmDataLayer.class::cast)
                .toList();
        for (AbstractOsmDataLayer layer : layers) {
            if (sorted.containsKey(layer)) {
                layer.getDataSet().setSelected(sorted.get(layer));
                if (!layer.getDataSet().selectionEmpty()) {
                    zoom(layer);
                    break;
                }
            }
        }
    }

    protected void updateTitle() {
        setTitle(model.getSummary());
    }

    @Override
    public void showNotify() {
        SelectionEventManager.getInstance().addSelectionListenerForEdt(actAdd);
        SelectionEventManager.getInstance().addSelectionListenerForEdt(actClearAndAdd);
        SelectionEventManager.getInstance().addSelectionListenerForEdt(actMarkSelected);
        actAdd.updateEnabledState();
    }

    @Override
    public void hideNotify() {
        SelectionEventManager.getInstance().removeSelectionListener(actAdd);
        SelectionEventManager.getInstance().removeSelectionListener(actClearAndAdd);
        SelectionEventManager.getInstance().removeSelectionListener(actMarkSelected);
    }

    Collection<TodoListItem> getItems() {
        OsmDataLayer layer = MainApplication.getLayerManager().getActiveDataLayer();
        if (layer == null)
            return Collections.emptyList();

        return layer.getDataSet().getSelected().stream().map(primitive -> new TodoListItem(layer, primitive)).collect(Collectors.toList());
    }

    private void runWithPrototype(Runnable runnable) {
        // Set a prototype value to speed up the list painting when `setSelection` methods are called (they call `getListCellRendererComponent`
        // on every list item)
        this.lstPrimitives.setPrototypeCellValue(new TodoListItem(new OsmDataLayer(new DataSet(), "XXXXXXXXXXXXXXXXXXXXXXXX", null),
                new Relation(Long.MAX_VALUE, Integer.MAX_VALUE)));
        runnable.run();
        this.lstPrimitives.setPrototypeCellValue(null);
    }

    private static class SelectAction extends JosmAction implements ListSelectionListener {
        @Serial
        private static final long serialVersionUID = -1857091860257862231L;
        private final TodoListModel model;

        SelectAction(TodoListModel model) {
            super(
                    tr("Zoom"),
                    "dialogs/zoom-best-fit",
                    tr("Zoom to the selected item in the todo list."),
                    Shortcut.registerShortcut("subwindow:todo:zoom_to_selected_item",
                            tr("Zoom to the selected item in the todo list."), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                    false
            );
            this.model = model;
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            selectAndZoom(model.getSelected());
        }

        /**
         * Update the enabled state of the action
         */
        @Override
        public void updateEnabledState() {
            setEnabled(this.model != null && !model.isSelectionEmpty());
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }
    }

    private static class SelectUnmarkedAction extends JosmAction implements ListSelectionListener {
        @Serial
        private static final long serialVersionUID = -6464592606894190151L;
        private final TodoListModel model;

        SelectUnmarkedAction(TodoListModel model) {
            super(
                    tr("Select all Unmarked and Zoom"),
                    "dialogs/zoom-best-fit",
                    tr("Select and zoom to all of the unmarked items in the todo list."),
                    Shortcut.registerShortcut("subwindow:todo:select_unmarked",
                            tr("Select and zoom to all of the unmarked items in the todo list."), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                    false
            );
            this.model = model;
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            selectAndZoom(model.getTodoList());
        }

        /**
         * Update the enabled state of the action
         */
        @Override
        public void updateEnabledState() {
            setEnabled(this.model != null && model.getSize() > 0);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }
    }

    private class PassAction extends JosmAction implements ListSelectionListener {
        @Serial
        private static final long serialVersionUID = -8398150189560976351L;
        private final TodoListModel model;

        PassAction(TodoListModel model) {
            super(
                    tr("Pass"),
                    "dialogs/zoom-best-fit",
                    tr("Moves on to the next item but leaves this item in the todo list."),
                    Shortcut.registerShortcut("subwindow:todo:pass",
                            tr("Pass over element without marking it"), KeyEvent.VK_OPEN_BRACKET, Shortcut.DIRECT),
                    false
            );
            this.model = model;
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            runWithPrototype(model::incrementSelection);
            selectAndZoom(model.getSelected());
        }

        /**
         * Update the enabled state of the action
         */
        @Override
        public void updateEnabledState() {
            setEnabled(this.model != null && model.getSize() > 0);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }
    }

    private class AddAction extends JosmAction implements DataSelectionListener {
        @Serial
        private static final long serialVersionUID = 1946908728505665451L;
        private final TodoListModel model;

        AddAction(TodoListModel model) {
            super(
                    tr("Add"),
                    "dialogs/add",
                    tr("Add the selected items to the todo list."),
                    Shortcut.registerShortcut("subwindow:todo:add",
                            tr("Add the selected items to the todo list."), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                    false
            );
            this.model = model;
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            runWithPrototype(() -> model.addItems(getItems()));
        }

        /**
         * Update the enabled state of the action
         */
        @Override
        public void updateEnabledState() {
            if (MainApplication.getLayerManager().getActiveData() == null) {
                setEnabled(false);
            } else {
                setEnabled(!MainApplication.getLayerManager().getActiveData().selectionEmpty());
            }
        }

        @Override
        public void selectionChanged(SelectionChangeEvent event) {
            updateEnabledState();
        }
    }

    private class ClearAndAddAction extends JosmAction implements DataSelectionListener {
        @Serial
        private static final long serialVersionUID = 6877280292029877851L;
        private final TodoListModel model;

        ClearAndAddAction(TodoListModel model) {
            super(
                tr("Clear and add"),
                "dialogs/selectionlist",
                tr("Clear list, add the selected items to the todo list and zoom first item."),
                Shortcut.registerShortcut("subwindow:todo:clearandadd", tr("Clear and add elements"), KeyEvent.VK_CLOSE_BRACKET, Shortcut.CTRL),
                false
                );
            this.model = model;
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            runWithPrototype(() -> {
                        model.clear();
                        model.addItems(getItems());
                    });
            selectAndZoom(model.getSelected());
        }

        /**
         * Update the enabled state of the action
         */
        @Override
        public void updateEnabledState() {
            if (MainApplication.getLayerManager().getActiveData() == null) {
                setEnabled(false);
            } else {
                setEnabled(!MainApplication.getLayerManager().getActiveData().selectionEmpty());
            }
        }

        @Override
        public void selectionChanged(SelectionChangeEvent event) {
            updateEnabledState();
        }
    }

    private class MarkSelectedAction extends JosmAction implements DataSelectionListener {
        @Serial
        private static final long serialVersionUID = 4978820863995799461L;
        private final TodoListModel model;

        MarkSelectedAction(TodoListModel model) {
            super(
                    tr("Mark selected"),
                    "dialogs/select",
                    tr("Mark the selected items (on the map) as done in the todo list."),
                    Shortcut.registerShortcut("subwindow:todo:mark_selected",
                            tr("Mark selected element done"), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                    false
            );
            this.model = model;
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            runWithPrototype(() -> model.markItems(getItems()));
        }

        /**
         * Update the enabled state of the action
         */
        @Override
        public void updateEnabledState() {
            if (MainApplication.getLayerManager().getActiveData() == null) {
                setEnabled(false);
            } else {
                setEnabled(!MainApplication.getLayerManager().getActiveData().selectionEmpty());
            }
        }

        @Override
        public void selectionChanged(SelectionChangeEvent event) {
            updateEnabledState();
        }
    }

    private class MarkAction extends JosmAction implements ListSelectionListener {

        @Serial
        private static final long serialVersionUID = -2258731277129598881L;
        TodoListModel model;

        MarkAction(TodoListModel model) {
            super(
                    tr("Mark"),
                    "dialogs/check",
                    tr("Mark the selected item in the todo list as done."),
                    Shortcut.registerShortcut("subwindow:todo:mark", tr("Mark element done"), KeyEvent.VK_CLOSE_BRACKET, Shortcut.DIRECT),
                    false
            );
            this.model = model;
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            runWithPrototype(() -> model.markItems(model.getSelected()));
            selectAndZoom(model.getSelected());
        }

        /**
         * Update the enabled state of the action
         */
        @Override
        public void updateEnabledState() {
            setEnabled(this.model != null && !model.isSelectionEmpty());
        }

        @Override
        public void valueChanged(ListSelectionEvent arg0) {
            updateEnabledState();
        }
    }

    private static class MarkAllAction extends JosmAction {

        @Serial
        private static final long serialVersionUID = 4736926588271511062L;
        TodoListModel model;

        MarkAllAction(TodoListModel model) {
            super(
                    tr("Mark all"),
                    "dialogs/todo",
                    tr("Mark all items in the todo list as done."),
                    Shortcut.registerShortcut("subwindow:todo:mark_all",
                            tr("Mark all items in the todo list as done."), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                    false
            );
            this.model = model;
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            model.markAll();
            selectAndZoom(model.getSelected());
        }
    }

    private static class UnmarkAllAction extends JosmAction {

        @Serial
        private static final long serialVersionUID = -2138098883422659123L;
        TodoListModel model;

        UnmarkAllAction(TodoListModel model) {
            super(
                    tr("Unmark all"),
                    "dialogs/refresh",
                    tr("Unmark all items in the todo list that have been marked as done."),
                    Shortcut.registerShortcut("subwindow:todo:unmark_all",
                            tr("Unmark all items in the todo list that have been marked as done."), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                    false
            );
            this.model = model;
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            model.unmarkAll();
        }
    }

    private static class ClearAction extends JosmAction {

        @Serial
        private static final long serialVersionUID = -1380109768343039669L;
        TodoListModel model;

        ClearAction(TodoListModel model) {
            super(
                    tr("Clear the todo list"),
                    "dialogs/delete",
                    tr("Remove all items (marked and unmarked) from the todo list."),
                    Shortcut.registerShortcut("subwindow:todo:clear",
                            tr("Remove all items (marked and unmarked) from the todo list."), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE),
                    false
            );
            this.model = model;
        }

        @Override
        public void actionPerformed(ActionEvent arg0) {
            model.clear();
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
        private final HighlightHelper helper = new HighlightHelper();
        private final boolean highlightEnabled = Config.getPref().getBoolean("draw.target-highlight", true);

        TodoPopupLauncher() {
            super(popupMenu);
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            int idx = lstPrimitives.locationToIndex(e.getPoint());
            if (idx < 0) return;
            // We only need to check for OsmPrimitive since highlightOnly only takes OsmPrimitive
            if (highlightEnabled && MainApplication.isDisplayingMapView() &&
                    model.getElementAt(idx).primitive() instanceof OsmPrimitive osm &&
                       helper.highlightOnly(osm)) {
                MainApplication.getMap().mapView.repaint();
            }
        }

        @Override
        public void mouseExited(MouseEvent me) {
            if (highlightEnabled) helper.clear();
            super.mouseExited(me);
        }
    }

    /**
     * A right-click popup context menu for setting options and performing other functions on the todo list items.
     */
    class TodoPopup extends ListPopupMenu {
        @Serial
        private static final long serialVersionUID = 7359564238177893009L;

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

    /**
     * Updates the dialog title with a summary of the current todo list status
     */
    class TitleUpdater implements ListDataListener {
        @Override
        public void contentsChanged(ListDataEvent e) {
            updateTitle();
        }

        @Override
        public void intervalAdded(ListDataEvent e) {
            updateTitle();
        }

        @Override
        public void intervalRemoved(ListDataEvent e) {
            updateTitle();
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent arg0) {
        actAdd.updateEnabledState();
        actClearAndAdd.updateEnabledState();
    }

    @Override
    public void destroy() {
        super.destroy();
        MainApplication.getLayerManager().removeLayerChangeListener(this);
        DatasetEventManager.getInstance().removeDatasetListener(model);
    }

    @Override
    public void layerAdded(LayerAddEvent e) {
        // Do nothing
    }

    @Override
    public void layerRemoving(LayerRemoveEvent e) {
        if (e.getRemovedLayer() instanceof AbstractModifiableLayer modifiableLayer) {
            model.purgeLayerItems(modifiableLayer);
        }
    }

    @Override
    public void layerOrderChanged(LayerOrderChangeEvent e) {
        // Do nothing
    }
}
