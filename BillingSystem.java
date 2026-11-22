import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple Billing System (Admin + Customer) - Java Swing
 * Admin    : add item, change price, remove item (password protected)
 * Customer : pick items, set quantity, see total and generate bill
 * Items are saved automatically in  <user home>/BillingSystemData/items.csv
 */
public class BillingSystem extends JFrame {

    // ---------- Settings ----------
    static final String ADMIN_PASSWORD = "admin123";   // change if you want
    static final double GST_PERCENT = 5.0;             // tax shown on the bill
    static final Path DATA_FILE =
            Paths.get(System.getProperty("user.home"), "BillingSystemData", "items.csv");

    // ---------- Data ----------
    static class Item {
        String name;
        double price;
        Item(String name, double price) { this.name = name; this.price = price; }
    }

    static class CartLine {
        Item item;
        int qty;
        CartLine(Item item, int qty) { this.item = item; this.qty = qty; }
        double subtotal() { return item.price * qty; }
    }

    private final List<Item> items = new ArrayList<>();
    private final List<CartLine> cart = new ArrayList<>();

    // ---------- UI ----------
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    // admin widgets
    private DefaultTableModel adminModel;
    private JTable adminTable;
    private JTextField nameField, priceField;

    // customer widgets
    private DefaultTableModel shopModel, cartModel;
    private JTable shopTable, cartTable;
    private JSpinner qtySpinner;
    private JLabel subtotalLabel, gstLabel, totalLabel;

    public BillingSystem() {
        super("Billing System");
        loadItems();
        root.add(buildLoginPanel(), "login");
        root.add(buildAdminPanel(), "admin");
        root.add(buildCustomerPanel(), "customer");
        add(root);
        setSize(900, 560);
        setMinimumSize(new Dimension(800, 500));
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        cards.show(root, "login");
    }

    // =====================================================
    //  LOGIN SCREEN
    // =====================================================
    private JPanel buildLoginPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));

        JLabel title = new JLabel("Billing System");
        title.setFont(new Font("SansSerif", Font.BOLD, 30));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel sub = new JLabel("Select how you want to continue");
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton adminBtn = bigButton("Admin Login");
        JButton custBtn = bigButton("Customer");

        adminBtn.addActionListener(e -> {
            JPasswordField pf = new JPasswordField();
            int ok = JOptionPane.showConfirmDialog(this, pf, "Enter admin password",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (ok == JOptionPane.OK_OPTION) {
                if (new String(pf.getPassword()).equals(ADMIN_PASSWORD)) {
                    refreshAdminTable();
                    cards.show(root, "admin");
                } else {
                    JOptionPane.showMessageDialog(this, "Wrong password!",
                            "Access denied", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        custBtn.addActionListener(e -> {
            cart.clear();
            refreshShopTable();
            refreshCartTable();
            cards.show(root, "customer");
        });

        box.add(title);
        box.add(Box.createVerticalStrut(8));
        box.add(sub);
        box.add(Box.createVerticalStrut(30));
        box.add(adminBtn);
        box.add(Box.createVerticalStrut(15));
        box.add(custBtn);
        p.add(box);
        return p;
    }

    private JButton bigButton(String text) {
        JButton b = new JButton(text);
        b.setFont(new Font("SansSerif", Font.BOLD, 18));
        b.setAlignmentX(Component.CENTER_ALIGNMENT);
        b.setMaximumSize(new Dimension(260, 50));
        b.setPreferredSize(new Dimension(260, 50));
        return b;
    }

    // =====================================================
    //  ADMIN SCREEN
    // =====================================================
    private JPanel buildAdminPanel() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // top bar
        JPanel top = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Admin Panel - Manage Items");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        JButton logout = new JButton("Logout");
        logout.addActionListener(e -> cards.show(root, "login"));
        top.add(title, BorderLayout.WEST);
        top.add(logout, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);

        // table
        adminModel = new DefaultTableModel(new String[]{"#", "Item Name", "Price (Rs.)"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        adminTable = new JTable(adminModel);
        adminTable.setRowHeight(26);
        adminTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        adminTable.getColumnModel().getColumn(0).setMaxWidth(50);
        adminTable.getSelectionModel().addListSelectionListener(e -> {
            int r = adminTable.getSelectedRow();
            if (!e.getValueIsAdjusting() && r >= 0) {
                nameField.setText(items.get(r).name);
                priceField.setText(String.valueOf(items.get(r).price));
            }
        });
        p.add(new JScrollPane(adminTable), BorderLayout.CENTER);

        // bottom form
        JPanel form = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        form.setBorder(BorderFactory.createTitledBorder("Item details"));
        nameField = new JTextField(14);
        priceField = new JTextField(8);
        JButton add = new JButton("Add Item");
        JButton update = new JButton("Update Price");
        JButton remove = new JButton("Remove Item");
        JButton clear = new JButton("Clear");

        form.add(new JLabel("Name:"));
        form.add(nameField);
        form.add(new JLabel("Price:"));
        form.add(priceField);
        form.add(add);
        form.add(update);
        form.add(remove);
        form.add(clear);
        p.add(form, BorderLayout.SOUTH);

        add.addActionListener(e -> addItem());
        update.addActionListener(e -> updatePrice());
        remove.addActionListener(e -> removeItem());
        clear.addActionListener(e -> {
            nameField.setText("");
            priceField.setText("");
            adminTable.clearSelection();
        });
        return p;
    }

    private void addItem() {
        String name = nameField.getText().trim();
        Double price = parsePrice(priceField.getText());
        if (name.isEmpty() || price == null) {
            error("Enter a valid item name and a positive price.");
            return;
        }
        if (name.contains(",")) {
            error("Item name must not contain a comma.");
            return;
        }
        for (Item it : items) {
            if (it.name.equalsIgnoreCase(name)) {
                error("This item already exists. Select it and use 'Update Price'.");
                return;
            }
        }
        items.add(new Item(name, price));
        saveItems();
        refreshAdminTable();
        nameField.setText("");
        priceField.setText("");
    }

    private void updatePrice() {
        int r = adminTable.getSelectedRow();
        if (r < 0) { error("Select an item from the table first."); return; }
        Double price = parsePrice(priceField.getText());
        if (price == null) { error("Enter a valid positive price."); return; }
        items.get(r).price = price;
        saveItems();
        refreshAdminTable();
        JOptionPane.showMessageDialog(this, "Price updated.");
    }

    private void removeItem() {
        int r = adminTable.getSelectedRow();
        if (r < 0) { error("Select an item from the table first."); return; }
        int ok = JOptionPane.showConfirmDialog(this,
                "Remove '" + items.get(r).name + "'?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (ok == JOptionPane.YES_OPTION) {
            items.remove(r);
            saveItems();
            refreshAdminTable();
            nameField.setText("");
            priceField.setText("");
        }
    }

    private void refreshAdminTable() {
        adminModel.setRowCount(0);
        for (int i = 0; i < items.size(); i++) {
            Item it = items.get(i);
            adminModel.addRow(new Object[]{i + 1, it.name, String.format("%.2f", it.price)});
        }
    }

    // =====================================================
    //  CUSTOMER SCREEN
    // =====================================================
    private JPanel buildCustomerPanel() {
        JPanel p = new JPanel(new BorderLayout(10, 10));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JPanel top = new JPanel(new BorderLayout());
        JLabel title = new JLabel("Customer - Shopping");
        title.setFont(new Font("SansSerif", Font.BOLD, 20));
        JButton back = new JButton("Back");
        back.addActionListener(e -> cards.show(root, "login"));
        top.add(title, BorderLayout.WEST);
        top.add(back, BorderLayout.EAST);
        p.add(top, BorderLayout.NORTH);

        // left: available items
        shopModel = new DefaultTableModel(new String[]{"Item", "Price (Rs.)"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        shopTable = new JTable(shopModel);
        shopTable.setRowHeight(26);
        shopTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        qtySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 999, 1));
        JButton addCart = new JButton("Add to Cart");
        addCart.addActionListener(e -> addToCart());
        JPanel leftBottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        leftBottom.add(new JLabel("Qty:"));
        leftBottom.add(qtySpinner);
        leftBottom.add(addCart);

        JPanel left = new JPanel(new BorderLayout(5, 5));
        left.setBorder(BorderFactory.createTitledBorder("Available Items"));
        left.add(new JScrollPane(shopTable), BorderLayout.CENTER);
        left.add(leftBottom, BorderLayout.SOUTH);

        // right: cart
        cartModel = new DefaultTableModel(new String[]{"Item", "Price", "Qty", "Subtotal"}, 0) {
            public boolean isCellEditable(int r, int c) { return false; }
        };
        cartTable = new JTable(cartModel);
        cartTable.setRowHeight(26);
        cartTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JButton removeCart = new JButton("Remove Selected");
        JButton clearCart = new JButton("Clear Cart");
        removeCart.addActionListener(e -> {
            int r = cartTable.getSelectedRow();
            if (r < 0) { error("Select an item in the cart first."); return; }
            cart.remove(r);
            refreshCartTable();
        });
        clearCart.addActionListener(e -> { cart.clear(); refreshCartTable(); });
        JPanel rightBottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rightBottom.add(removeCart);
        rightBottom.add(clearCart);

        JPanel right = new JPanel(new BorderLayout(5, 5));
        right.setBorder(BorderFactory.createTitledBorder("Your Cart"));
        right.add(new JScrollPane(cartTable), BorderLayout.CENTER);
        right.add(rightBottom, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right);
        split.setResizeWeight(0.42);
        p.add(split, BorderLayout.CENTER);

        // bottom: totals
        subtotalLabel = new JLabel("Subtotal: Rs. 0.00");
        gstLabel = new JLabel("GST (" + GST_PERCENT + "%): Rs. 0.00");
        totalLabel = new JLabel("TOTAL: Rs. 0.00");
        totalLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        JButton bill = new JButton("Generate Bill");
        bill.setFont(new Font("SansSerif", Font.BOLD, 16));
        bill.addActionListener(e -> showBill());

        JPanel totals = new JPanel(new FlowLayout(FlowLayout.RIGHT, 25, 5));
        totals.add(subtotalLabel);
        totals.add(gstLabel);
        totals.add(totalLabel);
        totals.add(bill);
        p.add(totals, BorderLayout.SOUTH);
        return p;
    }

    private void addToCart() {
        int r = shopTable.getSelectedRow();
        if (r < 0) { error("Select an item from the list first."); return; }
        int qty = (Integer) qtySpinner.getValue();
        Item item = items.get(r);
        for (CartLine line : cart) {               // same item again -> increase qty
            if (line.item == item) {
                line.qty += qty;
                refreshCartTable();
                return;
            }
        }
        cart.add(new CartLine(item, qty));
        refreshCartTable();
    }

    private void refreshShopTable() {
        shopModel.setRowCount(0);
        for (Item it : items) {
            shopModel.addRow(new Object[]{it.name, String.format("%.2f", it.price)});
        }
    }

    private void refreshCartTable() {
        cartModel.setRowCount(0);
        double sub = 0;
        for (CartLine l : cart) {
            cartModel.addRow(new Object[]{l.item.name, String.format("%.2f", l.item.price),
                    l.qty, String.format("%.2f", l.subtotal())});
            sub += l.subtotal();
        }
        double gst = sub * GST_PERCENT / 100.0;
        subtotalLabel.setText(String.format("Subtotal: Rs. %.2f", sub));
        gstLabel.setText(String.format("GST (%.1f%%): Rs. %.2f", GST_PERCENT, gst));
        totalLabel.setText(String.format("TOTAL: Rs. %.2f", sub + gst));
    }

    private void showBill() {
        if (cart.isEmpty()) { error("Your cart is empty."); return; }
        StringBuilder sb = new StringBuilder();
        sb.append("============== BILL ==============\n");
        sb.append(new java.util.Date()).append("\n");
        sb.append("----------------------------------\n");
        sb.append(String.format("%-14s %5s %4s %9s%n", "Item", "Price", "Qty", "Amount"));
        double sub = 0;
        for (CartLine l : cart) {
            sb.append(String.format("%-14s %5.2f %4d %9.2f%n",
                    l.item.name.length() > 14 ? l.item.name.substring(0, 14) : l.item.name,
                    l.item.price, l.qty, l.subtotal()));
            sub += l.subtotal();
        }
        double gst = sub * GST_PERCENT / 100.0;
        sb.append("----------------------------------\n");
        sb.append(String.format("%-25s %8.2f%n", "Subtotal", sub));
        sb.append(String.format("%-25s %8.2f%n", "GST (" + GST_PERCENT + "%)", gst));
        sb.append(String.format("%-25s %8.2f%n", "GRAND TOTAL", sub + gst));
        sb.append("==================================\n");
        sb.append("     Thank you! Visit again.\n");

        JTextArea area = new JTextArea(sb.toString());
        area.setFont(new Font("Monospaced", Font.PLAIN, 14));
        area.setEditable(false);
        JOptionPane.showMessageDialog(this, new JScrollPane(area),
                "Bill", JOptionPane.PLAIN_MESSAGE);
    }

    // =====================================================
    //  FILE STORAGE
    // =====================================================
    private void loadItems() {
        items.clear();
        try {
            if (!Files.exists(DATA_FILE)) {          // first run: sample items
                items.add(new Item("Pen", 10));
                items.add(new Item("Notebook", 50));
                items.add(new Item("Pencil", 5));
                items.add(new Item("Eraser", 3));
                saveItems();
                return;
            }
            for (String line : Files.readAllLines(DATA_FILE)) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    items.add(new Item(parts[0], Double.parseDouble(parts[1])));
                }
            }
        } catch (Exception ex) {
            error("Could not read saved items: " + ex.getMessage());
        }
    }

    private void saveItems() {
        try {
            Files.createDirectories(DATA_FILE.getParent());
            List<String> lines = new ArrayList<>();
            for (Item it : items) lines.add(it.name + "," + it.price);
            Files.write(DATA_FILE, lines);
        } catch (IOException ex) {
            error("Could not save items: " + ex.getMessage());
        }
    }

    // =====================================================
    //  HELPERS
    // =====================================================
    private Double parsePrice(String s) {
        try {
            double v = Double.parseDouble(s.trim());
            return v > 0 ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { }
            new BillingSystem().setVisible(true);
        });
    }
}
