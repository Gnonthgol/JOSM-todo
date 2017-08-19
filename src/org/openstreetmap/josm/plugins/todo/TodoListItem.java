// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.todo;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;

public class TodoListItem {
    OsmDataLayer layer;
    OsmPrimitive primitive;

    public TodoListItem(OsmDataLayer layer, OsmPrimitive primitive) {
        this.layer = layer;
        this.primitive = primitive;
    }

    @Override
    public String toString() {
        return layer.toString() + "/" + primitive.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof TodoListItem))
            return false;
        TodoListItem item = (TodoListItem)obj;
        return layer.equals(item.layer) && primitive.equals(item.primitive);
    }

    @Override
    public int hashCode() {
        return 31 * layer.hashCode() + primitive.hashCode();
    }
}
