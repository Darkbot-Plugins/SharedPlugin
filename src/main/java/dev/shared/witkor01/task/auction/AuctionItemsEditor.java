package dev.shared.witkor01.task.auction;

import com.github.manolo8.darkbot.config.tree.ConfigField;
import com.github.manolo8.darkbot.gui.tree.OptionEditor;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class AuctionItemsEditor extends JPanel implements OptionEditor {

    private static final String[] COLUMNS = {
            "#", "Name", "Type", "Current bid", "Bid?", "Max bid", "Increment"
    };
    private static final int COL_BID = 4;
    private static final int COL_MAX = 5;
    private static final int COL_INC = 6;

    private final transient Auction.AuctionConfig config;
    private final ItemsTableModel tableModel;

    public AuctionItemsEditor(Auction.AuctionConfig config) {
        super(new BorderLayout(0, 2));
        this.config = config;
        this.tableModel = new ItemsTableModel();

        JScrollPane scrollPane = new JScrollPane(buildTable(tableModel));
        scrollPane.setBorder(BorderFactory.createTitledBorder("Auction items"));

        add(scrollPane, BorderLayout.CENTER);
    }

    private JTable buildTable(ItemsTableModel model) {
        JTable table = new JTable(model);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.setRowHeight(18);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {28, 170, 70, 100, 42, 80, 80};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(COL_BID).setCellRenderer(table.getDefaultRenderer(Boolean.class));
        table.getColumnModel().getColumn(COL_BID).setCellEditor(table.getDefaultEditor(Boolean.class));

        TableRowSorter<ItemsTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);

        return table;
    }

    @Override public JComponent getComponent() { return this; }

    @Override
    public void edit(ConfigField field) {
        Auction.AuctionConfig fresh = (Auction.AuctionConfig) field.getParent();
        if (fresh != null) tableModel.refresh(fresh);
        else tableModel.refresh(config);
    }

    @Override public Dimension getReservedSize() { return new Dimension(590, 380); }
    @Override public Dimension getPreferredSize() { return new Dimension(590, 380); }

    private class ItemsTableModel extends AbstractTableModel {
        private final List<Object[]> rows = new ArrayList<>();
        private final List<String> lootIds = new ArrayList<>();
        private transient Auction.AuctionConfig currentConfig;

        void refresh(Auction.AuctionConfig cfg) {
            currentConfig = cfg;
            rows.clear();
            lootIds.clear();
            if (cfg == null || cfg.module == null) { fireTableDataChanged(); return; }

            List<Auction.AuctionItem> items = cfg.module.getItems();
            for (int i = 0; i < items.size(); i++) {
                Auction.AuctionItem it = items.get(i);
                String lootId = it.lootId != null ? it.lootId : it.itemKey;
                lootIds.add(lootId);
                Auction.ItemBidConfig bc = cfg.itemConfigs
                        .computeIfAbsent(lootId, k -> new Auction.ItemBidConfig());
                rows.add(new Object[]{
                        i + 1, it.name, it.type, it.currentBid,
                        bc.enabled, bc.maxBid, bc.increment
                });
            }
            fireTableDataChanged();
        }

        @Override public int getRowCount() { return rows.size(); }
        @Override public int getColumnCount() { return COLUMNS.length; }
        @Override public String getColumnName(int col) { return COLUMNS[col]; }

        @Override
        public Class<?> getColumnClass(int col) {
            switch (col) {
                case 3: return Long.class;
                case 4: return Boolean.class;
                case 5: case 6: return Integer.class;
                default: return String.class;
            }
        }

        @Override public boolean isCellEditable(int row, int col) { return col == COL_BID || col == COL_MAX || col == COL_INC; }
        @Override public Object getValueAt(int row, int col) { return rows.get(row)[col]; }

        @Override
        public void setValueAt(Object value, int row, int col) {
            if (row >= rows.size() || (col != COL_BID && col != COL_MAX && col != COL_INC)) return;
            if (currentConfig == null) return;
            String lootId = lootIds.get(row);
            Auction.ItemBidConfig bc = currentConfig.itemConfigs
                    .computeIfAbsent(lootId, k -> new Auction.ItemBidConfig());
            rows.get(row)[col] = value;
            switch (col) {
                case COL_BID: bc.enabled   = Boolean.TRUE.equals(value); break;
                case COL_MAX: bc.maxBid    = toInt(value); break;
                case COL_INC: bc.increment = toInt(value); break;
            }
            fireTableCellUpdated(row, col);
        }

        private int toInt(Object v) {
            if (v instanceof Integer) return (Integer) v;
            try { return Integer.parseInt(String.valueOf(v)); } catch (NumberFormatException e) { return 0; }
        }
    }
}

