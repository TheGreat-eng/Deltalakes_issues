import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class DeltaIssueSearch extends JFrame {
    private final JTable issueTable;
    private final DefaultTableModel tableModel;
    private final JTextField searchField;
    private final JComboBox<String> tagFilterComboBox;
    private final List<IssueItem> allIssues = new ArrayList<>();
    private TableRowSorter<DefaultTableModel> sorter;
    private final JLabel statusLabel;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new DeltaIssueSearch().setVisible(true);
        });
    }

    public DeltaIssueSearch() {
        setTitle("Delta Issues - Tìm kiếm theo Tag");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        // Tạo model cho table
        String[] columnNames = { "ID", "Tag", "Mô tả", "Tên file" };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        // Tạo sorter
        sorter = new TableRowSorter<>(tableModel);

        // Tạo table
        issueTable = new JTable(tableModel);
        issueTable.setRowSorter(sorter);
        issueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        issueTable.getTableHeader().setReorderingAllowed(false);

        // Thiết lập độ rộng cột
        issueTable.getColumnModel().getColumn(0).setPreferredWidth(50); // ID
        issueTable.getColumnModel().getColumn(1).setPreferredWidth(120); // Tag
        issueTable.getColumnModel().getColumn(2).setPreferredWidth(550); // Mô tả
        issueTable.getColumnModel().getColumn(3).setPreferredWidth(180); // Tên file

        // Double-click để mở file
        issueTable.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    int row = issueTable.getSelectedRow();
                    if (row >= 0) {
                        row = issueTable.convertRowIndexToModel(row);
                        String filename = (String) tableModel.getValueAt(row, 3);
                        openFile(filename);
                    }
                }
            }
        });

        // Panel tìm kiếm
        JPanel searchPanel = new JPanel(new BorderLayout(5, 0));
        searchPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        searchField = new JTextField();
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilters();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilters();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilters();
            }
        });

        JLabel searchLabel = new JLabel("Tìm kiếm:");
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        // Tạo combobox cho lọc tag
        tagFilterComboBox = new JComboBox<>();
        tagFilterComboBox.addItem("Tất cả");
        tagFilterComboBox.addActionListener(e -> applyFilters());

        JPanel filterPanel = new JPanel(new BorderLayout(5, 0));
        filterPanel.setBorder(new EmptyBorder(0, 10, 10, 10));
        JLabel tagLabel = new JLabel("Lọc theo Tag:");
        filterPanel.add(tagLabel, BorderLayout.WEST);
        filterPanel.add(tagFilterComboBox, BorderLayout.CENTER);

        // Thêm nút Browse để chọn thư mục
        JButton browseButton = new JButton("Chọn thư mục");
        browseButton.addActionListener(e -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            int option = fileChooser.showOpenDialog(this);
            if (option == JFileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile();
                loadIssuesFromDirectory(selectedFile.getAbsolutePath());
            }
        });

        JPanel browsePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        browsePanel.add(browseButton);
        filterPanel.add(browsePanel, BorderLayout.EAST);

        // Panel thông tin
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.add(searchPanel, BorderLayout.NORTH);
        infoPanel.add(filterPanel, BorderLayout.SOUTH);

        // Status bar
        statusLabel = new JLabel("Sẵn sàng");
        statusLabel.setBorder(new EmptyBorder(5, 10, 5, 10));

        // Panel chính
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(infoPanel, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(issueTable), BorderLayout.CENTER);
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        // Thêm vào frame
        add(mainPanel);

        // Tự động tìm và đọc thư mục delta_issues
        findAndLoadDeltaIssues();
    }

    private void findAndLoadDeltaIssues() {
        // Tìm thư mục delta_issues trong các vị trí phổ biến
        List<String> potentialPaths = new ArrayList<>();

        // Thêm thư mục hiện tại và các thư mục con của nó
        potentialPaths.add(".");
        potentialPaths.add("./delta_issues");

        // Thêm một số vị trí phổ biến khác
        String userDir = System.getProperty("user.dir");
        potentialPaths.add(userDir);
        potentialPaths.add(userDir + "/delta_issues");

        // Kiểm tra các đường dẫn
        for (String path : potentialPaths) {
            File dir = new File(path);
            File deltaDir = new File(dir, "delta_issues");

            if (deltaDir.exists() && deltaDir.isDirectory()) {
                loadIssuesFromDirectory(deltaDir.getAbsolutePath());
                return;
            } else if (path.endsWith("delta_issues") && dir.exists() && dir.isDirectory()) {
                loadIssuesFromDirectory(dir.getAbsolutePath());
                return;
            }
        }

        // Nếu không tìm thấy, hiển thị thông báo
        statusLabel.setText("Không tìm thấy thư mục delta_issues. Vui lòng sử dụng nút 'Chọn thư mục'.");
    }

    private void loadIssuesFromDirectory(String directoryPath) {
        try {
            // Xóa dữ liệu cũ
            tableModel.setRowCount(0);
            allIssues.clear();

            // Xóa các tag cũ
            tagFilterComboBox.removeAllItems();
            tagFilterComboBox.addItem("Tất cả");

            // Đường dẫn đến thư mục
            Path path = Paths.get(directoryPath);
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                JOptionPane.showMessageDialog(this,
                        "Thư mục không tồn tại: " + directoryPath,
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                return;
            }

            statusLabel.setText("Đang đọc thư mục: " + directoryPath);

            // Lấy danh sách các file trong thư mục
            List<Path> files = Files.list(path)
                    .filter(p -> !Files.isDirectory(p))
                    .collect(Collectors.toList());

            if (files.isEmpty()) {
                statusLabel.setText("Không tìm thấy file nào trong thư mục: " + directoryPath);
                return;
            }

            // Pattern để tìm ID, tag và mô tả
            Pattern pattern = Pattern.compile("(\\d+)_\\[(.*?)\\]\\s*(.*)");
            Set<String> uniqueTags = new HashSet<>();

            for (Path file : files) {
                String filename = file.getFileName().toString();
                Matcher matcher = pattern.matcher(filename);

                if (matcher.find()) {
                    String id = matcher.group(1);
                    String tag = matcher.group(2);
                    String description = matcher.group(3);

                    // Xử lý trường hợp có nhiều tag
                    String[] multipleTags = tag.split("\\]\\[");

                    // Lưu ý các tag bên trong
                    for (String t : multipleTags) {
                        uniqueTags.add(t);
                    }

                    IssueItem issue = new IssueItem(id, tag, description, filename, file.toString());
                    allIssues.add(issue);
                    tableModel.addRow(new Object[] { id, tag, description, filename });
                }
            }

            // Thêm các tag vào combobox
            List<String> sortedTags = new ArrayList<>(uniqueTags);
            Collections.sort(sortedTags);
            for (String tag : sortedTags) {
                tagFilterComboBox.addItem(tag);
            }

            statusLabel.setText("Đã tải " + allIssues.size() + " file từ thư mục: " + directoryPath);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Lỗi khi đọc thư mục: " + e.getMessage(),
                    "Lỗi", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            statusLabel.setText("Lỗi: " + e.getMessage());
        }
    }

    private void applyFilters() {
        RowFilter<DefaultTableModel, Object> searchFilter = null;
        String searchText = searchField.getText().toLowerCase();

        if (searchText.length() > 0) {
            searchFilter = RowFilter.regexFilter("(?i)" + Pattern.quote(searchText));
        }

        RowFilter<DefaultTableModel, Object> tagFilter = null;
        String selectedTag = (String) tagFilterComboBox.getSelectedItem();

        if (selectedTag != null && !selectedTag.equals("Tất cả")) {
            tagFilter = RowFilter.regexFilter("(?i)" + Pattern.quote(selectedTag), 1);
        }

        if (searchFilter != null && tagFilter != null) {
            List<RowFilter<DefaultTableModel, Object>> filters = new ArrayList<>();
            filters.add(searchFilter);
            filters.add(tagFilter);
            sorter.setRowFilter(RowFilter.andFilter(filters));
        } else if (searchFilter != null) {
            sorter.setRowFilter(searchFilter);
        } else if (tagFilter != null) {
            sorter.setRowFilter(tagFilter);
        } else {
            sorter.setRowFilter(null);
        }

        int filteredRowCount = issueTable.getRowCount();
        statusLabel.setText("Hiển thị " + filteredRowCount + " / " + allIssues.size() + " file");
    }

    private void openFile(String filename) {
        // Tìm file trong danh sách
        Optional<IssueItem> item = allIssues.stream()
                .filter(i -> i.getFilename().equals(filename))
                .findFirst();

        if (item.isPresent()) {
            try {
                File file = new File(item.get().getFullPath());

                // Mở file với ứng dụng mặc định của hệ thống
                if (Desktop.isDesktopSupported() && file.exists()) {
                    Desktop.getDesktop().open(file);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Không thể mở file: " + filename,
                            "Lỗi", JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        "Lỗi khi mở file: " + e.getMessage(),
                        "Lỗi", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }

    // Lớp để lưu trữ thông tin issue
    private static class IssueItem {
        private final String id;
        private final String tag;
        private final String description;
        private final String filename;
        private final String fullPath;

        public IssueItem(String id, String tag, String description, String filename, String fullPath) {
            this.id = id;
            this.tag = tag;
            this.description = description;
            this.filename = filename;
            this.fullPath = fullPath;
        }

        public String getId() {
            return id;
        }

        public String getTag() {
            return tag;
        }

        public String getDescription() {
            return description;
        }

        public String getFilename() {
            return filename;
        }

        public String getFullPath() {
            return fullPath;
        }
    }
}