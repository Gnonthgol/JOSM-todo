// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins.todo;

import java.awt.Component;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JLabel;

import org.openstreetmap.josm.gui.DefaultNameFormatter;
import org.openstreetmap.josm.gui.OsmPrimitivRenderer;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.tools.ImageProvider;

public class TodoListItemRenderer implements ListCellRenderer<TodoListItem> {
    private final DefaultNameFormatter formatter = DefaultNameFormatter.getInstance();
    private final DefaultListCellRenderer defaultListCellRenderer = new DefaultListCellRenderer();
    private final OsmPrimitivRenderer renderer = new OsmPrimitivRenderer();
    @Override
    public Component getListCellRendererComponent(JList<? extends TodoListItem> list, TodoListItem value, int index,
            boolean isSelected, boolean cellHasFocus) {
        Component def = defaultListCellRenderer.getListCellRendererComponent(list, null, index, isSelected, cellHasFocus);
        boolean fast = list.getModel().getSize() > 1000;

        if (value != null && def instanceof JLabel) {
            String displayName = value.primitive.getDisplayName(DefaultNameFormatter.getInstance());
            String layerName = value.layer.getName();
            ((JLabel) def).setText(displayName + " [" + layerName + "]");
            final ImageIcon icon = fast
                    ? ImageProvider.get(value.primitive.getType())
                    : ImageProvider.getPadded(value.primitive,
                        // Height of component no yet known, assume the default 16px.
                        ImageProvider.ImageSizes.SMALLICON.getImageDimension());
            if (icon != null) {
                ((JLabel) def).setIcon(icon);
            } else {
                Main.warn("Null icon for "+value.primitive.getDisplayType());
            }
            ((JLabel) def).setToolTipText(formatter.buildDefaultToolTip(value.primitive));
        }
        return def;
    }
}
