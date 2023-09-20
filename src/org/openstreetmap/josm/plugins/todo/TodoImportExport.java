// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.todo;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.stream.JsonParser;

import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.IRelation;
import org.openstreetmap.josm.data.osm.IWay;
import org.openstreetmap.josm.data.osm.SimplePrimitiveId;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.AbstractOsmDataLayer;
import org.openstreetmap.josm.io.session.PluginSessionExporter;
import org.openstreetmap.josm.io.session.PluginSessionImporter;

/**
 * Import and export the TODO list items
 */
public class TodoImportExport implements PluginSessionImporter, PluginSessionExporter {
    private record ParsedTodoListItem(TodoListItem item, boolean done){}

    @Override
    public void write(OutputStream outputStream) {
        final var dialog = MainApplication.getMap().getToggleDialog(TodoDialog.class);
        try (var generator = Json.createGenerator(outputStream)) {
            generator.writeStartArray();
            for (var item : dialog.model.getTodoList()) {
                generator.writeStartObject();
                generator.write("layer", item.layer().getName());
                // We ought to store the geometry and tags of new items, but getting a new object based off of the centroid
                // should work 99% of the time. If we are storing geometry and tags of items, we will probably have
                // issues if someone imports a dataset and uses the TODO list plugin.
                if (item.primitive().isNew()) {
                    // We need to convert the primitive to something we can persist across sessions.
                    // For now, we will only store the centroid
                    final ILatLon centroid;
                    if (item.primitive() instanceof ILatLon ll) {
                        centroid = ll;
                    } else {
                        centroid = item.primitive().getBBox().getCenter();
                    }
                    generator.writeStartObject("centroid")
                            .write("lat", centroid.lat())
                            .write("lon", centroid.lon())
                            .writeEnd();
                }
                // This needs to be >=0 for the parsing section.
                generator.write("primitive", item.primitive().getOsmPrimitiveId().toString());
                if (dialog.model.isDone(item)) {
                    generator.write("done", true);
                }
                generator.writeEnd();
            }
            generator.writeEnd();
        }
    }

    @Override
    public boolean read(InputStream inputStream) {
        try (var parser = Json.createParser(inputStream)) {
            if (parser.hasNext() && parser.next() == JsonParser.Event.START_ARRAY) {
                final var items = parser.getArrayStream().filter(val -> val.getValueType() == JsonValue.ValueType.OBJECT)
                        .map(JsonValue::asJsonObject).map(TodoImportExport::parseItem).filter(Objects::nonNull)
                        .toList();
                final var model = MainApplication.getMap().getToggleDialog(TodoDialog.class).model;
                model.addItems(items.stream().map(ParsedTodoListItem::item).toList());
                model.markItems(items.stream().filter(ParsedTodoListItem::done).map(ParsedTodoListItem::item).toList());
                return true;
            }
        }
        return false;
    }

    private static ParsedTodoListItem parseItem(JsonObject object) {
        final var layerString = object.getString("layer", null);
        final var primitiveIdString = object.getString("primitive", null);
        final var done = object.getBoolean("done", false);
        var primitiveId = primitiveIdString == null ? null : SimplePrimitiveId.fromString(primitiveIdString);
        TodoListItem item = null;
        if (layerString != null && primitiveId != null) {
            // Hopefully the user doesn't have two layers with the same primitive.
            // If this happens often, we may need to fiddle with the save code.
            final var layers = MainApplication.getLayerManager().getLayersOfType(AbstractOsmDataLayer.class)
                    .stream().filter(layer -> layerString.equals(layer.getName())).toList();
            for (var layer : layers) {
                final var primitive = layer.getDataSet().getPrimitiveById(primitiveId);
                if (primitive != null) {
                    item = new TodoListItem(layer, primitive);
                }
            }
            if (item == null && object.containsKey("centroid")) {
                final var centroid = object.getJsonObject("centroid");
                final ILatLon ll = new LatLon(centroid.getJsonNumber("lat").doubleValue(), centroid.getJsonNumber("lon").doubleValue());
                for (var layer : layers) {
                    final var bbox = new BBox(ll);
                    final var prim = switch (primitiveId.getType()) {
                        case NODE -> {
                            for (INode node : layer.getDataSet().searchNodes(bbox)) {
                                if (node.isNew() && node.equalsEpsilon(ll)) {
                                    yield node;
                                }
                            }
                            yield null;
                        }
                        case CLOSEDWAY, WAY -> {
                            for (IWay<?> way : layer.getDataSet().searchWays(bbox)) {
                                if (way.isNew() && way.getBBox().getCenter().equalsEpsilon(ll)) {
                                    yield way;
                                }
                            }
                            yield null;
                        }
                        case MULTIPOLYGON, RELATION -> {
                            for (IRelation<?> relation : layer.getDataSet().searchRelations(bbox)) {
                                if (relation.isNew() && relation.getBBox().getCenter().equalsEpsilon(ll)) {
                                    yield relation;
                                }
                            }
                            yield null;
                        }
                    };
                    if (prim != null) {
                        item = new TodoListItem(layer, prim);
                    }
                }
            }
        }
        if (item == null) {
            return null;
        }
        return new ParsedTodoListItem(item, done);
    }

    @Override
    public boolean requiresSaving() {
        final var todo = MainApplication.getMap().getToggleDialog(TodoDialog.class);
        return todo != null && !todo.model.getTodoList().isEmpty();
    }

    @Override
    public String getFileName() {
        return "todo.json";
    }
}
