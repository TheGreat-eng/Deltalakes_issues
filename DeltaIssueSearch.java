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
    private final JTable issueTable; // Bảng hiển thị danh sách vấn đề (issues)
    private final DefaultTableModel tableModel; // Mô hình dữ liệu cho bảng
    private final JTextField searchField; // Ô tìm kiếm
    private final JComboBox<String> tagFilterComboBox; // Bộ lọc theo tag
    private final List<IssueItem> allIssues = new ArrayList<>(); // Danh sách chứa tất cả các issue
    private TableRowSorter<DefaultTableModel> sorter; // Công cụ sắp xếp và lọc bảng
    private final JLabel statusLabel; // Nhãn hiển thị trạng thái

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                e.printStackTrace();
            }
            new DeltaIssueSearch().setVisible(true); // Khởi chạy giao diện chính
        });
    }

    public DeltaIssueSearch() {
        setTitle("Delta Issues - Search by Tag");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        // Định nghĩa tiêu đề cột cho bảng
        String[] columnNames = { "ID", "Tag", "Description", "File name" };
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        sorter = new TableRowSorter<>(tableModel);

        issueTable = new JTable(tableModel);
        issueTable.setRowSorter(sorter);
        issueTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        issueTable.getTableHeader().setReorderingAllowed(false);

        // Định nghĩa độ rộng cột trong bảng
        issueTable.getColumnModel().getColumn(0).setPreferredWidth(50); // ID
        issueTable.getColumnModel().getColumn(1).setPreferredWidth(120); // Tag
        issueTable.getColumnModel().getColumn(2).setPreferredWidth(550); // Description
        issueTable.getColumnModel().getColumn(3).setPreferredWidth(180); // File name

        // Xử lý khi người dùng nhấp đúp vào một dòng trong bảng để mở file
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

        JLabel searchLabel = new JLabel("Search:");
        searchPanel.add(searchLabel, BorderLayout.WEST);
        searchPanel.add(searchField, BorderLayout.CENTER);

        // Bộ lọc theo tag
        tagFilterComboBox = new JComboBox<>();
        tagFilterComboBox.addItem("All");
        tagFilterComboBox.addActionListener(e -> applyFilters());

        // Panel chứa bộ lọc
        JPanel filterPanel = new JPanel(new BorderLayout(5, 0));
        filterPanel.setBorder(new EmptyBorder(0, 10, 10, 10));
        JLabel tagLabel = new JLabel("Filter by Tag:");
        filterPanel.add(tagLabel, BorderLayout.WEST);
        filterPanel.add(tagFilterComboBox, BorderLayout.CENTER);

        // Nút chọn thư mục chứa các file issue
        JButton browseButton = new JButton("Select Directory");
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

        // Kết hợp các panel lại
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.add(searchPanel, BorderLayout.NORTH);
        infoPanel.add(filterPanel, BorderLayout.SOUTH);

        statusLabel = new JLabel("Ready");
        statusLabel.setBorder(new EmptyBorder(5, 10, 5, 10));

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(infoPanel, BorderLayout.NORTH);
        mainPanel.add(new JScrollPane(issueTable), BorderLayout.CENTER);
        mainPanel.add(statusLabel, BorderLayout.SOUTH);

        add(mainPanel);

        findAndLoadDeltaIssues(); // Tự động tìm và tải các issue khi khởi động
    }

    private void findAndLoadDeltaIssues() {
        List<String> potentialPaths = new ArrayList<>();

        potentialPaths.add(".");
        potentialPaths.add("./delta_issues");

        String userDir = System.getProperty("user.dir");
        potentialPaths.add(userDir);
        potentialPaths.add(userDir + "/delta_issues");

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

        statusLabel.setText("Delta_issues directory not found. Please use the 'Select Directory' button.");
    }

    private void loadIssuesFromDirectory(String directoryPath) {
        try {
            tableModel.setRowCount(0);
            allIssues.clear();

            tagFilterComboBox.removeAllItems();
            tagFilterComboBox.addItem("All");

            Path path = Paths.get(directoryPath);
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                JOptionPane.showMessageDialog(this,
                        "Directory does not exist: " + directoryPath,
                        "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            statusLabel.setText("Reading directory: " + directoryPath);

            List<Path> files = Files.list(path)
                    .filter(p -> !Files.isDirectory(p))
                    .collect(Collectors.toList());

            if (files.isEmpty()) {
                statusLabel.setText("No files found in directory: " + directoryPath);
                return;
            }

            Pattern pattern = Pattern.compile("(\\d+)_\\[(.*?)\\]\\s*(.*)");
            Set<String> uniqueTags = new HashSet<>();

            for (Path file : files) {
                String filename = file.getFileName().toString();
                Matcher matcher = pattern.matcher(filename);

                if (matcher.find()) {
                    String id = matcher.group(1);
                    String tag = matcher.group(2);
                    String description = matcher.group(3);

                    String[] multipleTags = tag.split("\\]\\[");

                    for (String t : multipleTags) {
                        uniqueTags.add(t);
                    }

                    IssueItem issue = new IssueItem(id, tag, description, filename, file.toString());
                    allIssues.add(issue);
                    tableModel.addRow(new Object[] { id, tag, description, filename });
                }
            }

            List<String> sortedTags = new ArrayList<>(uniqueTags);
            Collections.sort(sortedTags);
            for (String tag : sortedTags) {
                tagFilterComboBox.addItem(tag);
            }

            statusLabel.setText("Loaded " + allIssues.size() + " files from directory: " + directoryPath);

        } catch (IOException e) {
            JOptionPane.showMessageDialog(this,
                    "Error reading directory: " + e.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
            e.printStackTrace();
            statusLabel.setText("Error: " + e.getMessage());
        }
    }

    /**
     * Áp dụng bộ lọc tìm kiếm và lọc theo tag
     */

    private void applyFilters() {
        RowFilter<DefaultTableModel, Object> searchFilter = null;
        String searchText = searchField.getText().toLowerCase();

        if (searchText.length() > 0) {
            searchFilter = RowFilter.regexFilter("(?i)" + Pattern.quote(searchText));
        }

        RowFilter<DefaultTableModel, Object> tagFilter = null;
        String selectedTag = (String) tagFilterComboBox.getSelectedItem();

        if (selectedTag != null && !selectedTag.equals("All")) {
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
        statusLabel.setText("Showing " + filteredRowCount + " / " + allIssues.size() + " files");
    }

    /**
     * Mở file tương ứng khi nhấn đúp chuột vào dòng trong bảng
     */
    private void openFile(String filename) {
        Optional<IssueItem> item = allIssues.stream()
                .filter(i -> i.getFilename().equals(filename))
                .findFirst();

        if (item.isPresent()) {
            try {
                File file = new File(item.get().getFullPath());

                if (Desktop.isDesktopSupported() && file.exists()) {
                    Desktop.getDesktop().open(file);
                } else {
                    JOptionPane.showMessageDialog(this,
                            "Cannot open file: " + filename,
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this,
                        "Error opening file: " + e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        }
    }

    /**
     * Lớp đại diện cho một issue trong danh sách
     */

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
