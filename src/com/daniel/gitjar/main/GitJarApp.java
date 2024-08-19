package com.daniel.gitjar.main;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GitJarApp extends JFrame {
    private JTextArea logArea;
    private List<String> folderPaths;
    private static final String PATHS_FILE = "paths.txt";

    public GitJarApp() {
        setTitle("GitJar");
        setSize(600, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Inicializar paths
        initializeFolderPaths();

        JLabel titleLabel = new JLabel("GitJar", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        add(titleLabel, BorderLayout.NORTH);

        JPanel centralPanel = new JPanel(new BorderLayout());

        JPanel buttonPanel = new JPanel();
        JButton pullAllButton = new JButton("Pull All");
        JButton pushAllButton = new JButton("Push All");
        JButton selectButton = new JButton("Seleccionar manualmente");
        JButton editPathsButton = new JButton("Editar paths");
        buttonPanel.add(pullAllButton);
        buttonPanel.add(pushAllButton);
        buttonPanel.add(selectButton);
        buttonPanel.add(editPathsButton);
        centralPanel.add(buttonPanel, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(logArea);
        centralPanel.add(scrollPane, BorderLayout.CENTER);

        add(centralPanel, BorderLayout.CENTER);

        pullAllButton.addActionListener(e -> executeGitCommandForAll("pull"));
        pushAllButton.addActionListener(e -> executeGitCommandForAll("push"));
        selectButton.addActionListener(e -> openManualSelectionWindow());
        editPathsButton.addActionListener(e -> openEditPathsWindow());
    }

    private void initializeFolderPaths() {
        folderPaths = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(PATHS_FILE))) {
            String line;
            while ((line = br.readLine()) != null) {
                folderPaths.add(line);
            }
        } catch (IOException e) {
            // Si no se puede leer el archivo, inicializar paths vacíos
            for (int i = 0; i < 5; i++) {
                folderPaths.add("");
            }
        }
    }

    private void saveFolderPaths() {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(PATHS_FILE))) {
            for (String path : folderPaths) {
                bw.write(path);
                bw.newLine();
            }
        } catch (IOException e) {
            logArea.append("Error al guardar los paths: " + e.getMessage() + "\n");
        }
    }

    private void openEditPathsWindow() {
        JFrame frame = new JFrame("Editar paths");
        frame.setSize(500, 300);
        frame.setLayout(new GridLayout(6, 2)); // Cambiado a 6 filas para incluir botones de guardar y cancelar

        List<JTextField> pathFields = new ArrayList<>();
        for (int i = 0; i < folderPaths.size(); i++) {
            JLabel pathLabel = new JLabel("Path " + (i + 1) + ":");
            JTextField pathField = new JTextField(folderPaths.get(i));
            pathFields.add(pathField);
            frame.add(pathLabel);
            frame.add(pathField);
        }

        JButton saveButton = new JButton("Guardar");
        JButton cancelButton = new JButton("Cancelar");

        saveButton.addActionListener(e -> {
            for (int i = 0; i < pathFields.size(); i++) {
                folderPaths.set(i, pathFields.get(i).getText());
            }
            saveFolderPaths();
            frame.dispose();
        });

        cancelButton.addActionListener(e -> frame.dispose());

        frame.add(saveButton);
        frame.add(cancelButton);

        frame.setVisible(true);
    }

    private void executeGitCommandForAll(String command) {
        ExecutorService executor = Executors.newFixedThreadPool(folderPaths.size());
        for (int i = 0; i < folderPaths.size(); i++) {
            final int index = i;
            executor.submit(() -> executeGitCommand(command, index));
        }
        executor.shutdown();
    }

    private void openManualSelectionWindow() {
        JFrame frame = new JFrame("Selección Manual");
        frame.setSize(500, 300);
        frame.setLayout(new GridLayout(5, 4));

        for (int i = 0; i < folderPaths.size(); i++) {
            final int index = i;
            String folderPath = folderPaths.get(i);
            JLabel folderLabel = new JLabel(new File(folderPath).getName());
            JButton pullButton = new JButton("Pull");
            JButton pushButton = new JButton("Push");
            JButton openButton = new JButton("Abrir");

            pullButton.addActionListener(e -> executeGitCommand("pull", index));
            pushButton.addActionListener(e -> executeGitCommand("push", index));
            openButton.addActionListener(e -> openFolder(index));

            frame.add(folderLabel);
            frame.add(pullButton);
            frame.add(pushButton);
            frame.add(openButton);
        }

        frame.setVisible(true);
    }

    private void executeGitCommand(String command, int folderIndex) {
        String folderPath = folderPaths.get(folderIndex);
        try {
            runGitCommand(folderPath, "add", ".");
            runGitCommand(folderPath, "commit", "-m", "[AutoAdd]");
            runGitCommand(folderPath, command);

            SwingUtilities.invokeLater(() -> {
                logArea.append("Operaciones git completadas en " + folderPath + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        } catch (IOException | InterruptedException ex) {
            SwingUtilities.invokeLater(() -> {
                logArea.append("Error ejecutando operaciones git en " + folderPath + ": " + ex.getMessage() + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }
    }

    private void runGitCommand(String folderPath, String... command) throws IOException, InterruptedException {
        List<String> fullCommand = new ArrayList<>();
        fullCommand.add("git");
        fullCommand.addAll(List.of(command));

        ProcessBuilder pb = new ProcessBuilder(fullCommand);
        pb.directory(new File(folderPath));
        pb.redirectErrorStream(true);
        Process process = pb.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            final String logLine = line;
            SwingUtilities.invokeLater(() -> {
                logArea.append(logLine + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }
        int exitCode = process.waitFor();
        SwingUtilities.invokeLater(() -> {
            logArea.append("Git " + String.join(" ", command) + " completado. Código de salida: " + exitCode + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    private void openFolder(int folderIndex) {
        String folderPath = folderPaths.get(folderIndex);
        try {
            Desktop.getDesktop().open(new File(folderPath));
        } catch (IOException ex) {
            logArea.append("Error al abrir la carpeta " + folderPath + ": " + ex.getMessage() + "\n");
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GitJarApp().setVisible(true));
    }
}

