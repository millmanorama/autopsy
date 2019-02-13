/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2018 Basis Technology Corp.
 * Contact: carrier <at> sleuthkit <dot> org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sleuthkit.autopsy.corecomponents;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.logging.Level;
import org.sleuthkit.autopsy.coreutils.PlatformUtil;

import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.text.BadLocationException;
import javax.swing.text.Utilities;
import org.netbeans.api.progress.ProgressHandle;
import org.openide.nodes.Node;
import org.openide.util.lookup.ServiceProvider;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.NoCurrentCaseException;
import org.sleuthkit.autopsy.core.UserPreferences;
import org.sleuthkit.autopsy.corecomponentinterfaces.DataContentViewer;
import static org.sleuthkit.autopsy.corecomponents.Bundle.*;
import org.sleuthkit.autopsy.coreutils.FileUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.datamodel.DataConversion;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskException;

/**
 * Hex view of file contents.
 */
@SuppressWarnings("PMD.SingularField") // UI widgets cause lots of false positives
@ServiceProvider(service = DataContentViewer.class, position = 1)
public class DataContentViewerHex extends javax.swing.JPanel implements DataContentViewer {

    private static final long pageLength = 16384;
    private final byte[] data = new byte[(int) pageLength];
    private static int currentPage = 1;
    private int totalPages;
    private Content dataSource;

    private static final Logger logger = Logger.getLogger(DataContentViewerHex.class.getName());

    /**
     * Creates new form DataContentViewerHex
     */
    public DataContentViewerHex() {
        initComponents();
        customizeComponents();
        this.resetComponent();
        logger.log(Level.INFO, "Created HexView instance: " + this); //NON-NLS
    }

    private void customizeComponents() {
        outputTextArea.setComponentPopupMenu(rightClickMenu);
        ActionListener actList = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JMenuItem jmi = (JMenuItem) e.getSource();
                if (jmi.equals(copyMenuItem)) {
                    outputTextArea.copy();
                } else if (jmi.equals(selectAllMenuItem)) {
                    outputTextArea.selectAll();
                }
            }
        };
        copyMenuItem.addActionListener(actList);
        selectAllMenuItem.addActionListener(actList);
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        rightClickMenu = new javax.swing.JPopupMenu();
        copyMenuItem = new javax.swing.JMenuItem();
        selectAllMenuItem = new javax.swing.JMenuItem();
        jScrollPane3 = new javax.swing.JScrollPane();
        outputTextArea = new javax.swing.JTextArea();
        jScrollPane2 = new javax.swing.JScrollPane();
        hexViewerPanel = new javax.swing.JPanel();
        totalPageLabel = new javax.swing.JLabel();
        ofLabel = new javax.swing.JLabel();
        currentPageLabel = new javax.swing.JLabel();
        pageLabel = new javax.swing.JLabel();
        prevPageButton = new javax.swing.JButton();
        nextPageButton = new javax.swing.JButton();
        pageLabel2 = new javax.swing.JLabel();
        goToPageTextField = new javax.swing.JTextField();
        goToPageLabel = new javax.swing.JLabel();
        goToOffsetLabel = new javax.swing.JLabel();
        goToOffsetTextField = new javax.swing.JTextField();
        launchHxDButton = new javax.swing.JButton();

        copyMenuItem.setText(org.openide.util.NbBundle.getMessage(DataContentViewerHex.class, "DataContentViewerHex.copyMenuItem.text")); // NOI18N
        rightClickMenu.add(copyMenuItem);

        selectAllMenuItem.setText(org.openide.util.NbBundle.getMessage(DataContentViewerHex.class, "DataContentViewerHex.selectAllMenuItem.text")); // NOI18N
        rightClickMenu.add(selectAllMenuItem);

        setPreferredSize(new java.awt.Dimension(100, 58));

        jScrollPane3.setPreferredSize(new java.awt.Dimension(300, 33));

        outputTextArea.setEditable(false);
        outputTextArea.setFont(new java.awt.Font("Courier New", 0, 11)); // NOI18N
        outputTextArea.setTabSize(0);
        outputTextArea.setInheritsPopupMenu(true);
        jScrollPane3.setViewportView(outputTextArea);

        jScrollPane2.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        jScrollPane2.setVerticalScrollBarPolicy(javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);

        totalPageLabel.setText(org.openide.util.NbBundle.getMessage(DataContentViewerHex.class, "DataContentViewerHex.totalPageLabel.text_1")); // NOI18N

        ofLabel.setText(org.openide.util.NbBundle.getMessage(DataContentViewerHex.class, "DataContentViewerHex.ofLabel.text_1")); // NOI18N

        currentPageLabel.setText(org.openide.util.NbBundle.getMessage(DataContentViewerHex.class, "DataContentViewerHex.currentPageLabel.text_1")); // NOI18N
        currentPageLabel.setMaximumSize(new java.awt.Dimension(18, 14));
        currentPageLabel.setMinimumSize(new java.awt.Dimension(18, 14));

        pageLabel.setText(org.openide.util.NbBundle.getMessage(DataContentViewerHex.class, "DataContentViewerHex.pageLabel.text_1")); // NOI18N
        pageLabel.setMaximumSize(new java.awt.Dimension(33, 14));
        pageLabel.setMinimumSize(new java.awt.Dimension(33, 14));

        prevPageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back.png"))); // NOI18N
        prevPageButton.setText(org.openide.util.NbBundle.getMessage(DataContentViewerHex.class, "DataContentViewerHex.prevPageButton.text")); // NOI18N
        prevPageButton.setBorderPainted(false);
        prevPageButton.setContentAreaFilled(false);
        prevPageButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back_disabled.png"))); // NOI18N
        prevPageButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        prevPageButton.setPreferredSize(new java.awt.Dimension(23, 23));
        prevPageButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_back_hover.png"))); // NOI18N
        prevPageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                prevPageButtonActionPerformed(evt);
            }
        });

        nextPageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward.png"))); // NOI18N
        nextPageButton.setText(org.openide.util.NbBundle.getMessage(DataContentViewerHex.class, "DataContentViewerHex.nextPageButton.text")); // NOI18N
        nextPageButton.setBorderPainted(false);
        nextPageButton.setContentAreaFilled(false);
        nextPageButton.setDisabledIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward_disabled.png"))); // NOI18N
        nextPageButton.setMargin(new java.awt.Insets(2, 0, 2, 0));
        nextPageButton.setPreferredSize(new java.awt.Dimension(23, 23));
        nextPageButton.setRolloverIcon(new javax.swing.ImageIcon(getClass().getResource("/org/sleuthkit/autopsy/corecomponents/btn_step_forward_hover.png"))); // NOI18N
        nextPageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextPageButtonActionPerformed(evt);
            }
        });

        pageLabel2.setText(org.openide.util.NbBundle.getMessage(DataContentViewerHex.class, "DataContentViewerHex.pageLabel2.text")); // NOI18N
        pageLabel2.setMaximumSize(new java.awt.Dimension(29, 14));
        pageLabel2.setMinimumSize(new java.awt.Dimension(29, 14));

        goToPageTextField.setText(org.openide.util.NbBundle.getMessage(DataContentViewerHex.class, "DataContentViewerHex.goToPageTextField.text")); // NOI18N
        goToPageTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                goToPageTextFieldActionPerformed(evt);
            }
        });

        goToPageLabel.setText(org.openide.util.NbBundle.getMessage(DataContentViewerHex.class, "DataContentViewerHex.goToPageLabel.text")); // NOI18N

        goToOffsetLabel.setText(org.openide.util.NbBundle.getMessage(DataContentViewerHex.class, "DataContentViewerHex.goToOffsetLabel.text")); // NOI18N

        goToOffsetTextField.setText(org.openide.util.NbBundle.getMessage(DataContentViewerHex.class, "DataContentViewerHex.goToOffsetTextField.text")); // NOI18N
        goToOffsetTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                goToOffsetTextFieldActionPerformed(evt);
            }
        });

        launchHxDButton.setText(org.openide.util.NbBundle.getMessage(DataContentViewerHex.class, "DataContentViewerHex.launchHxDButton.text")); // NOI18N
        launchHxDButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                launchHxDButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout hexViewerPanelLayout = new javax.swing.GroupLayout(hexViewerPanel);
        hexViewerPanel.setLayout(hexViewerPanelLayout);
        hexViewerPanelLayout.setHorizontalGroup(
            hexViewerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(hexViewerPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(pageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(currentPageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(ofLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(totalPageLabel)
                .addGap(50, 50, 50)
                .addComponent(pageLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(prevPageButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(0, 0, 0)
                .addComponent(nextPageButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(goToPageLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(goToPageTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(goToOffsetLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(goToOffsetTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(launchHxDButton)
                .addContainerGap(146, Short.MAX_VALUE))
        );
        hexViewerPanelLayout.setVerticalGroup(
            hexViewerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(hexViewerPanelLayout.createSequentialGroup()
                .addGroup(hexViewerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(pageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(hexViewerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(currentPageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(ofLabel)
                        .addComponent(totalPageLabel))
                    .addComponent(pageLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(nextPageButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(prevPageButton, javax.swing.GroupLayout.PREFERRED_SIZE, 23, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(goToPageLabel)
                    .addComponent(goToPageTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(goToOffsetLabel)
                    .addGroup(hexViewerPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(goToOffsetTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(launchHxDButton)))
                .addGap(0, 0, 0))
        );

        launchHxDButton.setEnabled(PlatformUtil.isWindowsOS());

        jScrollPane2.setViewportView(hexViewerPanel);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jScrollPane2, javax.swing.GroupLayout.DEFAULT_SIZE, 827, Short.MAX_VALUE)
            .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.DEFAULT_SIZE, 239, Short.MAX_VALUE))
        );
    }// </editor-fold>//GEN-END:initComponents

    private void prevPageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_prevPageButtonActionPerformed
        setDataViewByPageNumber(currentPage - 1);
        goToPageTextField.setText(Integer.toString(currentPage));
    }//GEN-LAST:event_prevPageButtonActionPerformed

    private void nextPageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextPageButtonActionPerformed
        setDataViewByPageNumber(currentPage + 1);
        goToPageTextField.setText(Integer.toString(currentPage));
    }//GEN-LAST:event_nextPageButtonActionPerformed

    private void goToPageTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_goToPageTextFieldActionPerformed
        String pageNumberStr = goToPageTextField.getText();
        int pageNumber = 0;

        try {
            pageNumber = Integer.parseInt(pageNumberStr);
        } catch (NumberFormatException ex) {
            pageNumber = totalPages + 1;
        }
        if (pageNumber > totalPages || pageNumber < 1) {
            JOptionPane.showMessageDialog(this,
                    NbBundle.getMessage(this.getClass(),
                            "DataContentViewerHex.goToPageTextField.msgDlg",
                            totalPages),
                    NbBundle.getMessage(this.getClass(),
                            "DataContentViewerHex.goToPageTextField.err"),
                    JOptionPane.WARNING_MESSAGE);
            return;
        }
        setDataViewByPageNumber(pageNumber);
    }//GEN-LAST:event_goToPageTextFieldActionPerformed

    /**
     * *
     * Calculates the offset relative to the current caret position.
     *
     * @param userInput the user provided signed offset value.
     *
     * @return returns the resultant offset value relative to the current caret
     *         position. -1L is returned if the resultant offset cannot be
     *         calculated.
     */
    private long getOffsetRelativeToCaretPosition(Long userInput) {
        String userSelectedLine;
        try {
            // get the selected line. Extract the current hex offset location.
            userSelectedLine = outputTextArea.getText().subSequence(
                    Utilities.getRowStart(outputTextArea, outputTextArea.getCaretPosition()),
                    Utilities.getRowEnd(outputTextArea, outputTextArea.getCaretPosition()))
                    .toString();
            // NOTE: This needs to change if the outputFormat of outputTextArea changes.
            String hexForUserSelectedLine = userSelectedLine.substring(0, userSelectedLine.indexOf(":"));

            return Long.decode(hexForUserSelectedLine) + userInput;
        } catch (BadLocationException | StringIndexOutOfBoundsException | NumberFormatException ex) {
            // thrown in case the caret location is out of the range of the outputTextArea.
            return -1L;
        }
    }

    private void goToOffsetTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_goToOffsetTextFieldActionPerformed
        long offset;
        try {
            if (goToOffsetTextField.getText().startsWith("+") || goToOffsetTextField.getText().startsWith("-")) {
                offset = getOffsetRelativeToCaretPosition(Long.decode(goToOffsetTextField.getText()));
            } else {
                offset = Long.decode(goToOffsetTextField.getText());
            }
        } catch (NumberFormatException ex) {
            // notify the user and return
            JOptionPane.showMessageDialog(this, NbBundle.getMessage(this.getClass(), "DataContentViewerHex.goToOffsetTextField.msgDlg", goToOffsetTextField.getText()));
            return;
        }

        if (offset >= 0) {
            setDataViewByOffset(offset);
        } else {
            outputTextArea.setText(NbBundle.getMessage(DataContentViewerHex.class, "DataContentViewerHex.setDataView.invalidOffset.negativeOffsetValue"));
        }
    }//GEN-LAST:event_goToOffsetTextFieldActionPerformed

    @NbBundle.Messages({"DataContentViewerHex.launchError=Unable to launch HxD Editor. "
                        + "Please specify the HxD install location in Tools -> Options -> External Viewer",
                        "DataContentViewerHex.copyingFile=Copying file to open in HxD..."})
    private void launchHxDButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_launchHxDButtonActionPerformed
        new BackgroundFileCopyTask().execute();
    }//GEN-LAST:event_launchHxDButtonActionPerformed

    /**
     * Performs the file copying and process launching in a SwingWorker so that the 
     * UI is not blocked when opening large files.
     */
    private class BackgroundFileCopyTask extends SwingWorker<Void, Void> {
        private boolean wasCancelled = false;
        
        @Override
        public Void doInBackground() throws InterruptedException {
            ProgressHandle progress = ProgressHandle.createHandle(DataContentViewerHex_copyingFile(), () -> {
                //Cancel the swing worker (which will interrupt the ContentUtils call below)
                this.cancel(true);
                wasCancelled = true;
                return true;
            });
            
            try {
                File HxDExecutable = new File(UserPreferences.getExternalHexEditorPath());
                if(!HxDExecutable.exists() || !HxDExecutable.canExecute()) {
                    JOptionPane.showMessageDialog(null, DataContentViewerHex_launchError());
                    return null;
                }
                
                String tempDirectory = Case.getCurrentCaseThrows().getTempDirectory();
                File tempFile = Paths.get(tempDirectory,
                        FileUtil.escapeFileName(dataSource.getId() + dataSource.getName())).toFile();
                
                progress.start(100);
                ContentUtils.writeToFile(dataSource, tempFile, progress, this, true);
                
                if(wasCancelled) {
                    tempFile.delete();
                    progress.finish();
                    return null;
                }
                
                try {
                    ProcessBuilder launchHxDExecutable = new ProcessBuilder();
                    launchHxDExecutable.command(String.format("\"%s\" \"%s\"", 
                            HxDExecutable.getAbsolutePath(), 
                            tempFile.getAbsolutePath()));
                    launchHxDExecutable.start();
                } catch (IOException ex) {
                    logger.log(Level.WARNING, "Unsuccessful attempt to launch HxD", ex);
                    JOptionPane.showMessageDialog(null, DataContentViewerHex_launchError());
                    tempFile.delete();
                }
            } catch (NoCurrentCaseException | IOException ex) {
                logger.log(Level.SEVERE, "Unable to copy file into temp directory", ex);
                JOptionPane.showMessageDialog(null, DataContentViewerHex_launchError());
            }
            
            progress.finish();
            return null;
        }
    }
    
    
    
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem copyMenuItem;
    private javax.swing.JLabel currentPageLabel;
    private javax.swing.JLabel goToOffsetLabel;
    private javax.swing.JTextField goToOffsetTextField;
    private javax.swing.JLabel goToPageLabel;
    private javax.swing.JTextField goToPageTextField;
    private javax.swing.JPanel hexViewerPanel;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JButton launchHxDButton;
    private javax.swing.JButton nextPageButton;
    private javax.swing.JLabel ofLabel;
    private javax.swing.JTextArea outputTextArea;
    private javax.swing.JLabel pageLabel;
    private javax.swing.JLabel pageLabel2;
    private javax.swing.JButton prevPageButton;
    private javax.swing.JPopupMenu rightClickMenu;
    private javax.swing.JMenuItem selectAllMenuItem;
    private javax.swing.JLabel totalPageLabel;
    // End of variables declaration//GEN-END:variables

    /**
     * Sets the DataView (The tabbed panel) by page number
     *
     * @param page Page to display (1-based counting)
     */
    private void setDataViewByPageNumber(int page) {
        if (this.dataSource == null) {
            return;
        }
        if (page == 0) {
            return;
        }
        currentPage = page;
        long offset = (currentPage - 1) * pageLength;
        setDataView(offset);
        goToOffsetTextField.setText(Long.toString(offset));
    }

    /**
     * Sets the DataView (The tabbed panel) by offset
     *
     * @param offset Page to display (1-based counting)
     */
    private void setDataViewByOffset(long offset) {
        if (this.dataSource == null) {
            return;
        }
        currentPage = (int) (offset / pageLength) + 1;
        setDataView(offset);
        goToPageTextField.setText(Integer.toString(currentPage));
    }

    private void setDataView(long offset) {
        // change the cursor to "waiting cursor" for this operation
        this.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        String errorText = null;

        int bytesRead = 0;
        if (dataSource.getSize() > 0) {
            try {
                bytesRead = dataSource.read(data, offset, pageLength); // read the data
            } catch (TskException ex) {
                errorText = NbBundle.getMessage(this.getClass(), "DataContentViewerHex.setDataView.errorText", offset,
                        offset + pageLength);
                logger.log(Level.WARNING, "Error while trying to show the hex content.", ex); //NON-NLS
            }
        }

        // set the data on the bottom and show it
        if (bytesRead <= 0) {
            errorText = NbBundle.getMessage(this.getClass(), "DataContentViewerHex.setDataView.errorText", offset,
                    offset + pageLength);
        }

        // disable or enable the next button
        if ((errorText == null) && (currentPage < totalPages)) {
            nextPageButton.setEnabled(true);
        } else {
            nextPageButton.setEnabled(false);
        }

        if ((errorText == null) && (currentPage > 1)) {
            prevPageButton.setEnabled(true);
        } else {
            prevPageButton.setEnabled(false);
        }

        currentPageLabel.setText(Integer.toString(currentPage));
        setComponentsVisibility(true); // shows the components that not needed

        // set the output view
        if (errorText == null) {
            int showLength = bytesRead < pageLength ? bytesRead : (int) pageLength;
            outputTextArea.setText(DataConversion.byteArrayToHex(data, showLength, offset));
        } else {
            outputTextArea.setText(errorText);
        }

        outputTextArea.setCaretPosition(0);
        this.setCursor(null);
    }

    @Override
    public void setNode(Node selectedNode) {
        if ((selectedNode == null) || (!isSupported(selectedNode))) {
            resetComponent();
            return;
        }

        Content content = DataContentViewerUtility.getDefaultContent(selectedNode);
        if (content == null) {
            resetComponent();
            return;
        }

        dataSource = content;
        totalPages = 0;
        if (dataSource.getSize() > 0) {
            totalPages = Math.round((dataSource.getSize() - 1) / pageLength) + 1;
        }
        totalPageLabel.setText(Integer.toString(totalPages));

        this.setDataViewByPageNumber(1);
    }

    @Override
    public String getTitle() {
        return NbBundle.getMessage(this.getClass(), "DataContentViewerHex.title");
    }

    @Override
    public String getToolTip() {
        return NbBundle.getMessage(this.getClass(), "DataContentViewerHex.toolTip");
    }

    @Override
    public DataContentViewer createInstance() {
        return new DataContentViewerHex();
    }

    @Override
    public void resetComponent() {
        // clear / reset the fields
        currentPage = 1;
        this.dataSource = null;
        currentPageLabel.setText("");
        totalPageLabel.setText("");
        outputTextArea.setText("");
        setComponentsVisibility(false); // hides the components that not needed
    }

    /**
     * To set the visibility of specific components in this class.
     *
     * @param isVisible whether to show or hide the specific components
     */
    private void setComponentsVisibility(boolean isVisible) {
        currentPageLabel.setVisible(isVisible);
        totalPageLabel.setVisible(isVisible);
        ofLabel.setVisible(isVisible);
        prevPageButton.setVisible(isVisible);
        nextPageButton.setVisible(isVisible);
        pageLabel.setVisible(isVisible);
        pageLabel2.setVisible(isVisible);
        goToPageTextField.setVisible(isVisible);
        goToPageLabel.setVisible(isVisible);
        goToOffsetTextField.setVisible(isVisible);
        goToOffsetLabel.setVisible(isVisible);
        launchHxDButton.setVisible(isVisible);
    }

    @Override
    public boolean isSupported(Node node) {
        if (node == null) {
            return false;
        }
        Content content = node.getLookup().lookup(Content.class);
        if (content != null && content.getSize() > 0) {
            return true;
        }

        return false;
    }

    @Override
    public int isPreferred(Node node) {
        return 1;
    }

    @Override
    public Component getComponent() {
        return this;
    }
}
