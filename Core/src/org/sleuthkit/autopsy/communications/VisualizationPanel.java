/*
 * Autopsy Forensic Browser
 *
 * Copyright 2017 Basis Technology Corp.
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
package org.sleuthkit.autopsy.communications;

import com.google.common.collect.ImmutableSet;
import com.google.common.eventbus.Subscribe;
import com.mxgraph.layout.mxFastOrganicLayout;
import com.mxgraph.model.mxCell;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.view.mxGraph;
import java.awt.Color;
import java.util.Collection;
import static java.util.Collections.singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.logging.Level;
import javax.swing.JPanel;
import org.apache.commons.lang3.StringUtils;
import org.openide.explorer.ExplorerManager;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.communications.AccountsRootChildren.AccountDeviceInstanceNode;
import static org.sleuthkit.autopsy.communications.RelationshipNode.getAttributeDisplayString;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.datamodel.AccountDeviceInstance;
import org.sleuthkit.datamodel.BlackboardArtifact;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_FROM;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_EMAIL_TO;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM;
import static org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO;
import org.sleuthkit.datamodel.CommunicationsManager;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;

/**
 *
 */
public class VisualizationPanel extends JPanel {

    private ExplorerManager explorerManager;
    private final mxGraph graph;

    private Map<String, mxCell> nodeMap = new HashMap<>();
    private final mxGraphComponent graphComponent;

    /**
     * Creates new form VizPanel
     */
    public VisualizationPanel() {
        initComponents();
        graph = new mxGraph();
        graph.setCellsEditable(false);
        graph.setCellsResizable(false);
        graph.setCellsMovable(true);
        graph.setCellsDisconnectable(false);
        graph.setConnectableEdges(false);
        graph.setDisconnectOnMove(false);
        graph.setEdgeLabelsMovable(false);
        graph.setVertexLabelsMovable(false);
        graphComponent = new mxGraphComponent(graph);
        graphComponent.setAutoScroll(true);
        graphComponent.setOpaque(true);
        graphComponent.setBackground(Color.WHITE);
        this.add(graphComponent);

        CVTEvents.getCVTEventBus().register(this);
    }

    public void initVisualization(ExplorerManager em) {
        explorerManager = em;

        graph.getSelectionModel().addListener(null, (sender, evt) -> {
            Object[] selectionCells = graph.getSelectionCells();
            if (selectionCells.length == 1) {
                mxCell selectionCell = (mxCell) selectionCells[0];

                if (selectionCell.isVertex()) {
                    try {

                        CommunicationsManager commsManager = Case.getCurrentCase().getSleuthkitCase().getCommunicationsManager();

                        AccountDeviceInstanceKey adiKey = (AccountDeviceInstanceKey) selectionCell.getValue();
                        explorerManager.setRootContext(new AccountDetailsNode(
                                singleton(adiKey.getAccountDeviceInstance()),
                                adiKey.getCommunicationsFilter(),
                                commsManager));
                    } catch (TskCoreException tskCoreException) {
                        Logger.getLogger(VisualizationPanel.class.getName()).log(Level.SEVERE,
                                "Could not get communications manager for current case", tskCoreException);
                    }
                }
            }
        });
    }

    private void addEdge(BlackboardArtifact artifact) throws TskCoreException {
        BlackboardArtifact.ARTIFACT_TYPE artfType = BlackboardArtifact.ARTIFACT_TYPE.fromID(artifact.getArtifactTypeID());
        if (null != artfType) {

            String from = null;
            String[] tos = new String[0];

            //Consider refactoring this to reduce boilerplate
            switch (artfType) {
                case TSK_EMAIL_MSG:
                    from = StringUtils.strip(getAttributeDisplayString(artifact, TSK_EMAIL_FROM), " \t\n;");
                    tos = StringUtils.strip(getAttributeDisplayString(artifact, TSK_EMAIL_TO), " \t\n;").split(";");
                    break;
                case TSK_MESSAGE:
                    from = getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_FROM);
                    tos = getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_TO).split(";");
                    break;
                case TSK_CALLLOG:
                    from = getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_FROM);
                    tos = getAttributeDisplayString(artifact, TSK_PHONE_NUMBER_TO).split(";");
                    break;
                default:
                    break;
            }
            for (String to : tos) {
                if (StringUtils.isNotBlank(from) && StringUtils.isNotBlank(to)) {

                    mxCell fromV = getOrCreateNodeDraft(from, 10);
                    mxCell toV = getOrCreateNodeDraft(to, 10);

                    Object[] edgesBetween = graph.getEdgesBetween(fromV, toV);

                    if (edgesBetween.length == 0) {
                        final String edgeName = from + "->" + to;
                        mxCell edge = (mxCell) graph.insertEdge(graph.getDefaultParent(), edgeName, 1d, fromV, toV);
                    } else if (edgesBetween.length == 1) {
                        final mxCell edge = (mxCell) edgesBetween[0];
                        edge.setValue(1d + (double) edge.getValue());
                        edge.setStyle("strokeWidth=" + Math.log((double) edge.getValue()));
                    }
                }
            }
        }
    }

    @Subscribe
    public void pinAccount(PinAccountEvent pinEvent) {

        final AccountDeviceInstanceNode adiNode = pinEvent.getAccountDeviceInstanceNode();
        final AccountDeviceInstanceKey adiKey = adiNode.getAccountDeviceInstanceKey();

        graph.getModel().beginUpdate();
        try {
//            final String nodeId = /*
//                     * adiKey.getDataSourceName() + ":" +
//                     */ adiKey.getAccountDeviceInstance().getAccount().getTypeSpecificID();

            mxCell v = getOrCreateNodeDraft(adiKey);
            CommunicationsManager commsManager = adiNode.getCommsManager();

            Collection<Content> relationshipSources =
                    commsManager.getRelationshipSources(ImmutableSet.of(adiKey.getAccountDeviceInstance()), adiNode.getFilter());

            for (Content source : relationshipSources) {
                if (source instanceof BlackboardArtifact) {
                    addEdge((BlackboardArtifact) source);
                }
            }
        } catch (TskCoreException ex) {
            Exceptions.printStackTrace(ex);
        } finally {
            // Updates the display
            graph.getModel().endUpdate();
            revalidate();
        }
        
        new mxFastOrganicLayout(graph).execute(graph.getDefaultParent());
    }

    private mxCell getOrCreateNodeDraft(AccountDeviceInstanceKey accountDeviceInstanceKey) {
        final AccountDeviceInstance accountDeviceInstance = accountDeviceInstanceKey.getAccountDeviceInstance();
        final String name =// accountDeviceInstance.getDeviceId() + ":"                +
                accountDeviceInstance.getAccount().getTypeSpecificID();
        mxCell nodeDraft = nodeMap.get(name);
        if (nodeDraft == null) {
            double size = accountDeviceInstanceKey.getMessageCount() / 10;
            nodeDraft = (mxCell) graph.insertVertex(
                    graph.getDefaultParent(),
                    name, accountDeviceInstanceKey,
                    new Random().nextInt(200),
                    new Random().nextInt(200),
                    size,
                    size);
            nodeMap.put(name, nodeDraft);
        }
        return nodeDraft;
    }

    private mxCell getOrCreateNodeDraft(String name, long size) {
        mxCell nodeDraft = nodeMap.get(name);
        if (nodeDraft == null) {
            nodeDraft = (mxCell) graph.insertVertex(
                    graph.getDefaultParent(),
                    name,
                    null,
                    new Random().nextInt(200),
                    new Random().nextInt(200),
                    size,
                    size);
//            nodeDraft.setLabel(name);
            nodeMap.put(name, nodeDraft);
        }
        return nodeDraft;
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jToolBar1 = new javax.swing.JToolBar();
        jButton1 = new javax.swing.JButton();

        setLayout(new java.awt.BorderLayout());

        jToolBar1.setRollover(true);

        jButton1.setText(org.openide.util.NbBundle.getMessage(VisualizationPanel.class, "VisualizationPanel.jButton1.text")); // NOI18N
        jButton1.setFocusable(false);
        jButton1.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        jButton1.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        jToolBar1.add(jButton1);

        add(jToolBar1, java.awt.BorderLayout.PAGE_START);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JToolBar jToolBar1;
    // End of variables declaration//GEN-END:variables
}
