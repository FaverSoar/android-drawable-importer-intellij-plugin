package de.mprengemann.intellij.plugin.androidicons.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.vfs.VirtualFile;
import de.mprengemann.intellij.plugin.androidicons.settings.SettingsHelper;
import de.mprengemann.intellij.plugin.androidicons.util.AndroidResourcesHelper;
import de.mprengemann.intellij.plugin.androidicons.util.ImageUtils;
import de.mprengemann.intellij.plugin.androidicons.util.RefactorHelper;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class AndroidIconsImporter extends DialogWrapper {

    private VirtualFile assetRoot;

    private Project project;
    private JLabel imageContainer;
    private TextFieldWithBrowseButton resRoot;
    private JComboBox assetSpinner;
    private JComboBox colorSpinner;
    private JTextField resExportName;
    private JCheckBox LDPICheckBox;
    private JCheckBox MDPICheckBox;
    private JCheckBox HDPICheckBox;
    private JCheckBox XHDPICheckBox;
    private JCheckBox XXHDPICheckBox;
    private JPanel container;
    private String assetColor;
    private String assetName;
    private boolean exportNameChanged = false;

    public AndroidIconsImporter(@Nullable final Project project, Module module) {
        super(project, true);
        this.project = project;

        setTitle("Android Icons Importer");
        setResizable(false);

        AndroidResourcesHelper.initResourceBrowser(project, module, "Select res root", this.resRoot);

        assetRoot = SettingsHelper.getAssetPath();
        colorSpinner.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                assetColor = (String) colorSpinner.getSelectedItem();
                updateImage();
            }
        });

        AssetSpinnerRenderer renderer = new AssetSpinnerRenderer();
        //noinspection GtkPreferredJComboBoxRenderer
        assetSpinner.setRenderer(renderer);
        assetSpinner.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                assetName = (String) assetSpinner.getSelectedItem();
                updateImage();
            }
        });

        fillComboBoxes();

        resExportName.addKeyListener(new KeyAdapter() {
            @Override
            public void keyTyped(KeyEvent keyEvent) {
                super.keyTyped(keyEvent);
                if (!exportNameChanged && keyEvent != null && keyEvent.getKeyCode() > -1) {
                    exportNameChanged = true;
                }
            }

            @Override
            public void keyPressed(KeyEvent keyEvent) {
                super.keyPressed(keyEvent);
            }

            @Override
            public void keyReleased(KeyEvent keyEvent) {
                super.keyReleased(keyEvent);
            }
        });
        imageContainer.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                super.componentResized(e);
                updateImage();
            }
        });

        init();
    }

    private void updateImage() {
        if (imageContainer == null) {
            return;
        }
        String path = "/" + assetColor.replace(" ", "_") + "/xxhdpi/ic_action_" + assetName + ".png";
        File imageFile = new File(assetRoot.getCanonicalPath() + path);
        ImageUtils.updateImage(imageContainer, imageFile);
        if (!exportNameChanged) {
            resExportName.setText("ic_action_" + assetName + ".png");
        }
    }

    private void fillComboBoxes() {
        if (this.assetRoot.getCanonicalPath() == null) {
            return;
        }
        File assetRoot = new File(this.assetRoot.getCanonicalPath());
        final FilenameFilter systemFileNameFiler = new FilenameFilter() {
            @Override
            public boolean accept(File file, String s) {
                return !s.startsWith(".");
            }
        };
        File[] colorDirs = assetRoot.listFiles(systemFileNameFiler);
        Comparator<File> alphabeticalComparator = new Comparator<File>() {
            @Override
            public int compare(File file1, File file2) {
                if (file1 != null && file2 != null) {
                    return file1.getName().compareTo(file2.getName());
                }
                return 0;
            }
        };
        Arrays.sort(colorDirs, alphabeticalComparator);
        for (File file : colorDirs) {
            if (!file.isDirectory()) {
                continue;
            }
            colorSpinner.addItem(file.getName().replace("_", " "));
        }

        if (colorDirs.length < 1) {
            return;
        }
        File exColorDir = colorDirs[0];
        File[] densities = exColorDir.listFiles(systemFileNameFiler);
        if (densities == null || densities.length < 1) {
            return;
        }
        File exDensity = densities[0];
        File[] assets = exDensity.listFiles(systemFileNameFiler);
        Arrays.sort(assets, alphabeticalComparator);
        for (File asset : assets) {
            if (asset.isDirectory()) {
                continue;
            }
            String extension = asset.getName().substring(asset.getName().lastIndexOf(".") + 1);
            if (!extension.equalsIgnoreCase("png")) {
                continue;
            }
            assetSpinner.addItem(asset.getName().replace("ic_action_", "").replace("." + extension, ""));
        }
        assetColor = (String) colorSpinner.getSelectedItem();
        assetName = (String) assetSpinner.getSelectedItem();
    }

    @Override
    protected void doOKAction() {
        importIcons();
        super.doOKAction();
    }

    private void importIcons() {
        List<FromToPath> paths = new ArrayList<FromToPath>();
        if (LDPICheckBox.isSelected()) {
            paths.add(new FromToPath("ldpi"));
        }
        if (MDPICheckBox.isSelected()) {
            paths.add(new FromToPath("mdpi"));
        }
        if (HDPICheckBox.isSelected()) {
            paths.add(new FromToPath("hdpi"));
        }
        if (XHDPICheckBox.isSelected()) {
            paths.add(new FromToPath("xhdpi"));
        }
        if (XXHDPICheckBox.isSelected()) {
            paths.add(new FromToPath("xxhdpi"));
        }

        copyDrawables(paths);
    }

    private boolean copyDrawables(List<FromToPath> paths) {
        try {
            if (paths != null && paths.size() > 0) {
                List<File> sources = new ArrayList<File>();
                List<File> targets = new ArrayList<File>();
                File source;
                for (FromToPath path : paths) {
                    source = new File(path.source);
                    if (source.exists()) {
                        sources.add(source);
                        targets.add(new File(path.target));
                    }
                }
                RefactorHelper.copy(project, sources, targets);
            } else {
                return false;
            }
        } catch (IOException e) {
            return false;
        }

        return true;
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        if (StringUtils.isEmpty(resRoot.getText().trim())) {
            return new ValidationInfo("Please select the resources root.", resRoot);
        }

        if (StringUtils.isEmpty(resExportName.getText().trim())) {
            return new ValidationInfo("Please select a name for the drawable.", resExportName);
        } else if (!resExportName.getText().matches("[a-z0-9_.]*")) {
            return new ValidationInfo(
                "Please select a valid name for the drawable. There are just \"[a-z0-9_.]\" allowed.",
                resExportName);
        }

        return null;
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return container;
    }

    private class FromToPath {
        public final String source;
        public final String target;
        public final String resolution;

        private FromToPath(String resolution) {
            this.resolution = resolution;

            String resRootText = resRoot.getText();

            String fromName = "ic_action_" + assetName + ".png";
            String toName = resExportName.getText();

            this.source = assetRoot.getCanonicalPath() + "/" + assetColor.replace(" ",
                                                                                  "_") + "/" + resolution + "/" + fromName;
            this.target = resRootText + "/drawable-" + resolution + "/" + toName;
        }
    }

    private class AssetSpinnerRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList jList, Object o, int i, boolean b, boolean b2) {
            JLabel label = (JLabel) super.getListCellRendererComponent(jList, o, i, b, b2);
            if (label != null) {
                String item = (String) assetSpinner.getItemAt(i);
                String path = "/black/mdpi/ic_action_" + item + ".png";
                File imageFile = new File(assetRoot.getCanonicalPath() + path);
                if (imageFile.exists()) {
                    label.setIcon(new ImageIcon(imageFile.getAbsolutePath()));
                }
            }
            return label;
        }
    }
}