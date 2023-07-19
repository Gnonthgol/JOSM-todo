// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.todo;

import java.util.Objects;

import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.gui.layer.AbstractOsmDataLayer;

/**
 * An item in the todo list
 * @param layer The originating layer
 * @param primitive The primitive to do.
 */
record TodoListItem(AbstractOsmDataLayer layer, IPrimitive primitive) {
    TodoListItem {
        Objects.requireNonNull(layer, "layer");
        Objects.requireNonNull(primitive, "primitive");
    }

    @Override
    public String toString() {
        return layer + "/" + primitive;
    }
}
