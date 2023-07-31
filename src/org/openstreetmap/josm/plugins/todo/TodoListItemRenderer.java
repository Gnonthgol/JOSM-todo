// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.todo;

import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

import org.openstreetmap.josm.data.osm.DefaultNameFormatter;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

/**
 * Render a {@link TodoListItem}
 */
public class TodoListItemRenderer implements ListCellRenderer<TodoListItem> {
    private final DefaultNameFormatter formatter = DefaultNameFormatter.getInstance();
    private final DefaultListCellRenderer defaultListCellRenderer = new DefaultListCellRenderer();

    @Override
    public Component getListCellRendererComponent(JList<? extends TodoListItem> list, TodoListItem value, int index,
            boolean isSelected, boolean cellHasFocus) {
        final var def = defaultListCellRenderer.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus);
        boolean fast = list.getModel().getSize() > 1000;

        if (value != null && def instanceof JLabel jLabel) {
            String displayName = value.primitive().getDisplayName(DefaultNameFormatter.getInstance());
            String layerName = value.layer().getName();
            jLabel.setText(displayName + " [" + layerName + "]");
            final var icon = !fast && (value.primitive() instanceof OsmPrimitive osmPrimitive)
                    ? ImageProvider.getPadded(osmPrimitive,
                        // Height of component no yet known, assume the default 16px.
                        ImageProvider.ImageSizes.SMALLICON.getImageDimension())
                    : ImageProvider.get(value.primitive().getType());
            if (icon != null) {
                jLabel.setIcon(icon);
            } else {
                Logging.warn("Null icon for "+value.primitive().getDisplayType());
            }
            jLabel.setToolTipText(formatter.buildDefaultToolTip(value.primitive()));
        }
        return def;
    }
}
