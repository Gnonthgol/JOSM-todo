// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.todo;

import java.util.Objects;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

/**
 * An item in the todo list
 */
public class TodoListItem {
    final OsmDataLayer layer;
    final OsmPrimitive primitive;

    /**
     * Create a new item
     * @param layer The originating layer
     * @param primitive The primitive to do.
     */
    public TodoListItem(OsmDataLayer layer, OsmPrimitive primitive) {
        this.layer = Objects.requireNonNull(layer, "layer");
        this.primitive = Objects.requireNonNull(primitive, "primitive");
    }

    @Override
    public String toString() {
        return layer.toString() + "/" + primitive.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TodoListItem))
            return false;
        TodoListItem item = (TodoListItem) obj;
        return layer.equals(item.layer) && primitive.equals(item.primitive);
    }

    @Override
    public int hashCode() {
        return 31 * layer.hashCode() + primitive.hashCode();
    }
}
