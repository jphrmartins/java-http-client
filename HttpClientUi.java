
import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HttpClientUi extends JFrame {

    private JComboBox<String> methodBox;
    private JTextField urlField;
    private JTextArea headersArea;
    private JComboBox<String> bodyTypeBox;
    private JTextArea rawBodyArea;
    private JTable formTable;
    private JTextArea responseArea;
    private DefaultListModel<String> historyModel;

    private HttpClientService service = new HttpClientService();

    public HttpClientUi() {
        setTitle("HTTP WorkArround Client");
        setSize(1200, 700);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // Top bar
        JPanel topPanel = new JPanel(new BorderLayout(10, 10));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        methodBox = new JComboBox<>(new String[] { "GET", "POST", "PUT", "DELETE" });
        urlField = new JTextField();
        JButton sendButton = new JButton("Send");
        JButton curlButton = new JButton("Show Curl");
        sendButton.setBackground(new Color(0, 123, 255));
        sendButton.setForeground(Color.WHITE);

        JPanel leftTop = new JPanel(new BorderLayout(5, 5));
        JPanel rightTop = new JPanel(new BorderLayout(5, 5));
        leftTop.add(methodBox, BorderLayout.WEST);
        leftTop.add(urlField, BorderLayout.CENTER);
        topPanel.add(leftTop, BorderLayout.CENTER);
        rightTop.add(sendButton, BorderLayout.EAST);
        rightTop.add(curlButton, BorderLayout.WEST);
        topPanel.add(rightTop, BorderLayout.EAST);
        JButton importCurlButton = new JButton("Import CURL");
        topPanel.add(importCurlButton, BorderLayout.WEST);

        add(topPanel, BorderLayout.NORTH);

        // Tabs for request
        JTabbedPane requestTabs = new JTabbedPane();
        headersArea = new JTextArea(10, 30);
        headersArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        requestTabs.addTab("Headers", new JScrollPane(headersArea));

        JPanel bodyPanel = new JPanel(new BorderLayout(10, 10));
        bodyTypeBox = new JComboBox<>(new String[] { "Raw", "Form-Data" });
        rawBodyArea = new JTextArea(10, 30);
        rawBodyArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        bodyPanel.add(bodyTypeBox, BorderLayout.NORTH);
        bodyPanel.add(new JScrollPane(rawBodyArea), BorderLayout.CENTER);

        DefaultTableModel tableModel = new DefaultTableModel(new Object[] { "Name", "Value", "Is File" }, 0);
        formTable = new JTable(tableModel);
        JPanel formPanel = new JPanel(new BorderLayout());
        formPanel.add(new JScrollPane(formTable), BorderLayout.CENTER);
        JPanel formButtons = new JPanel();
        JButton addFieldBtn = new JButton("Add");
        JButton removeFieldBtn = new JButton("Remove");
        JButton chooseFileBtn = new JButton("Choose File");
        formButtons.add(addFieldBtn);
        formButtons.add(removeFieldBtn);
        formButtons.add(chooseFileBtn);
        formPanel.add(formButtons, BorderLayout.SOUTH);

        requestTabs.addTab("Body", bodyPanel);
        requestTabs.addTab("Form-Data", formPanel);

        responseArea = new JTextArea();
        responseArea.setEditable(false);
        responseArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, requestTabs,
                new JScrollPane(responseArea));
        centerSplit.setDividerLocation(500);
        add(centerSplit, BorderLayout.CENTER);

        historyModel = new DefaultListModel<>();
        JList<String> historyList = new JList<>(historyModel);
        JScrollPane historyScroll = new JScrollPane(historyList);
        historyScroll.setBorder(new TitledBorder("History"));
        add(historyScroll, BorderLayout.SOUTH);
        loadHistory();

        // Actions
        addFieldBtn.addActionListener(e -> tableModel.addRow(new Object[] { "", "", false }));
        removeFieldBtn.addActionListener(e -> {
            int row = formTable.getSelectedRow();
            if (row >= 0)
                tableModel.removeRow(row);
        });
        chooseFileBtn.addActionListener(e -> {
            int row = formTable.getSelectedRow();
            if (row >= 0) {
                JFileChooser chooser = new JFileChooser();
                if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                    tableModel.setValueAt(chooser.getSelectedFile().getAbsolutePath(), row, 1);
                    tableModel.setValueAt(true, row, 2);
                }
            }
        });

        sendButton.addActionListener(e -> {
            String method = (String) methodBox.getSelectedItem();
            String url = urlField.getText().trim();
            String headers = headersArea.getText();
            String bodyType = (String) bodyTypeBox.getSelectedItem();
            String rawBody = rawBodyArea.getText();
            List<HttpClientService.FormField> fields = new ArrayList<>();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                fields.add(new HttpClientService.FormField(
                        tableModel.getValueAt(i, 0).toString(),
                        tableModel.getValueAt(i, 1).toString(),
                        Boolean.parseBoolean(tableModel.getValueAt(i, 2).toString())));
            }
            try {
                String response = service.sendHttpRequest(method, url, headers, bodyType, rawBody, fields);
                responseArea.setText(response);
                service.saveFullHistory(method, url, headers, bodyType, rawBody, fields);
                loadHistory();
            } catch (Exception ex) {
                responseArea.setText("Error: " + ex.getMessage());
            }
        });

        curlButton.addActionListener(e -> {
            String method = (String) methodBox.getSelectedItem();
            String url = urlField.getText().trim();
            String headers = headersArea.getText();
            String bodyType = (String) bodyTypeBox.getSelectedItem();
            String rawBody = rawBodyArea.getText();
            List<HttpClientService.FormField> fields = new ArrayList<>();
            for (int i = 0; i < tableModel.getRowCount(); i++) {
                fields.add(new HttpClientService.FormField(
                        tableModel.getValueAt(i, 0).toString(),
                        tableModel.getValueAt(i, 1).toString(),
                        Boolean.parseBoolean(tableModel.getValueAt(i, 2).toString())));
            }
            String curlCommand = service.generateCurl(method, url, headers, bodyType, rawBody, fields);

            JDialog dialog = new JDialog(this, "Generated CURL", true);
            JTextArea curlArea = new JTextArea(curlCommand);
            curlArea.setEditable(false);
            curlArea.setLineWrap(true);
            curlArea.setWrapStyleWord(true);
            curlArea.setFont(new Font("Monospaced", Font.PLAIN, 12));

            JScrollPane scrollPane = new JScrollPane(curlArea);
            scrollPane.setPreferredSize(new Dimension(600, 200));

            dialog.add(scrollPane);
            dialog.pack();
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
        });

        importCurlButton.addActionListener(e -> {
            String curlCommand = JOptionPane.showInputDialog(this, "Paste CURL command:");
            if (curlCommand != null && !curlCommand.isBlank()) {
                HttpClientService.ParsedCurl parsed = service.parseCurl(curlCommand);
                methodBox.setSelectedItem(parsed.method);
                urlField.setText(parsed.url);
                headersArea.setText(String.join("\n", parsed.headers));
                rawBodyArea.setText(parsed.body != null ? parsed.body : "");
                bodyTypeBox.setSelectedItem(parsed.body != null ? "Raw" : "Form-Data");
            }
        });
        Consumer<String> importMethod = selected -> {
            String[] parts = selected.split("@@");
            if (parts.length >= 5) {
                methodBox.setSelectedItem(parts[0]);
                urlField.setText(parts[1]);
                headersArea.setText(parts[2].replace("\\n", "\n").trim());
                bodyTypeBox.setSelectedItem(parts[3]);
                rawBodyArea.setText(parts[4].replace("\\n", "\n").trim());

                // Limpar tabela e preencher form-data
                DefaultTableModel model = (DefaultTableModel) formTable.getModel();
                model.setRowCount(0);
                if (parts.length == 6) {
                    for (String f : parts[5].split(";")) {
                        if (!f.isBlank()) {
                            String[] fp = f.split(",", 3);
                            model.addRow(new Object[] { fp[0], fp[1].replace("\\n", "\n"),
                                    Boolean.parseBoolean(fp[2]) });
                        }
                    }
                }
            }
        };

        historyList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    String selected = historyList.getSelectedValue();
                    if (selected != null) {
                        importMethod.accept(selected);
                    }
                }
            }
        });
        historyList.addKeyListener(new KeyListener() {

            @Override
            public void keyTyped(KeyEvent e) {

            }

            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyChar() == '\n') {
                    String selected = historyList.getSelectedValue();
                    if (selected != null) {
                        importMethod.accept(selected);
                    }
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
            }

        });

        // Dentro do construtor, após criar sendButton:
        InputMap inputMap = urlField.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = urlField.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke("control ENTER"), "sendRequest");
        actionMap.put("sendRequest", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                sendButton.doClick(); // Simula clique no botão Send
            }
        });

        setVisible(true);

        try {
            ImageIcon icon = new ImageIcon(getClass().getResource("/logo.png"));
            setIconImage(icon.getImage());
        } catch (Exception e) {
            System.err.println("Erro ao carregar ícone: " + e.getMessage());
        }

    }

    private void loadHistory() {
        historyModel.clear();
        historyModel.addAll(service.loadHistory());
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(HttpClientUi::new);
    }
}
