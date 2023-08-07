// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.todo;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import javax.swing.AbstractButton;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.commons.support.ReflectionSupport;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.widgets.ListPopupMenu;
import org.openstreetmap.josm.plugins.PluginClassLoader;
import org.openstreetmap.josm.testutils.annotations.Main;
import org.openstreetmap.josm.testutils.annotations.Projection;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ResourceProvider;

/**
 * Test class for {@link TodoDialog}
 */
@Main
@Projection
class TodoDialogTest {
    private DataSet ds;
    private TodoDialog dialog;
    private TodoListModel model;
    private JosmAction actAdd;
    private ListPopupMenu popupMenu;

    @SuppressWarnings("unchecked")
    private <T> T tryToReadFieldValue(String field) throws Exception {
        return (T) ReflectionSupport.tryToReadFieldValue(TodoDialog.class.getDeclaredField(field), this.dialog).get();
    }

    @BeforeEach
    void setup() throws Exception {
        // This fixes an issue where tests might not be able to locate images in an IDE
        // There is probably a better way to do this
        if (ImageProvider.getIfAvailable("todo") == null) {
            ResourceProvider.addAdditionalClassLoader(new PluginClassLoader(new URL[] {new File(".").toURI().toURL()},
                    this.getClass().getClassLoader(), null));
        }
        this.dialog = new TodoDialog();
        this.model = tryToReadFieldValue("model");
        this.actAdd = tryToReadFieldValue("actAdd");
        this.popupMenu = tryToReadFieldValue("popupMenu");
        this.ds = new DataSet();
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(this.ds, "TodoDialogTest", null));
        this.ds.addPrimitive(TestUtils.newNode("access=no"));
        this.ds.addPrimitive(TestUtils.newNode("access=yes"));
        this.ds.addPrimitive(TestUtils.newNode("access=maybe"));
    }

    @Test
    void testAdd() {
        // Sort the list for stability in tests
        final var list = new ArrayList<>(this.ds.allPrimitives());
        list.sort(Comparator.comparing(prim -> prim.get("access")));

        this.ds.setSelected(list);
        assertEquals(0, this.model.getSize());
        this.actAdd.actionPerformed(null);
        assertEquals(3, this.model.getSize());
        assertEquals(1, this.model.getTodoList().stream().filter(item -> item.primitive().hasTag("access", "no")).count());
        assertEquals(1, this.model.getTodoList().stream().filter(item -> item.primitive().hasTag("access", "yes")).count());
        assertEquals(1, this.model.getTodoList().stream().filter(item -> item.primitive().hasTag("access", "maybe")).count());
        assertEquals(this.model.getTodoList().size(), this.model.getTodoList().stream().distinct().count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"yes", "no", "maybe"})
    void testRemove(String value) {
        this.testAdd(); // Add the primitives to the model
        final var action = ((AbstractButton) this.popupMenu.getComponent(1)).getAction();
        assertEquals("org.openstreetmap.josm.plugins.todo.TodoDialog$MarkAction", action.getClass().getName());
        final var todoListItem = this.model.getTodoList().stream()
                .filter(item -> item.primitive().hasTag("access", value)).findFirst().orElseThrow(AssertionError::new);
        this.model.setSelected(Collections.singleton(todoListItem));
        assertEquals(1, this.model.getSelected().size());
        assertSame(todoListItem, this.model.getSelected().iterator().next());
        action.actionPerformed(null);
        assertFalse(this.model.getTodoList().contains(todoListItem));
    }

    @Test
    void testClear() {
        this.testAdd(); // Add the primitives to the model
        final var action = ((AbstractButton) this.popupMenu.getComponent(5)).getAction();
        assertEquals("org.openstreetmap.josm.plugins.todo.TodoDialog$ClearAction", action.getClass().getName());
        action.actionPerformed(null);
        assertTrue(this.model.getTodoList().isEmpty());
    }

    /**
     * Non-regression test for #23104: `Mark Selected` should account for items not in the list
     */
    @Test
    void testNonRegression23104() throws Exception {
        // Sort the list for stability in tests
        final var list = new ArrayList<>(this.ds.allPrimitives());
        list.sort(Comparator.comparing(prim -> prim.get("access")));
        this.ds.setSelected(list.get(0));
        this.actAdd.actionPerformed(null);
        final var actMarkSelected = (JosmAction) tryToReadFieldValue("actMarkSelected");
        assertEquals(1, this.model.getSize());
        assertSame(list.get(0), this.model.getElementAt(0).primitive());
        this.ds.setSelected(list.get(1));
        assertDoesNotThrow(() -> actMarkSelected.actionPerformed(null));
        this.ds.setSelected(list.get(1), list.get(2));
        assertDoesNotThrow(() -> actMarkSelected.actionPerformed(null));
        this.ds.setSelected(list);
        assertDoesNotThrow(() -> actMarkSelected.actionPerformed(null));
    }
}
