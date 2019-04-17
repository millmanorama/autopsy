/*
 * Autopsy Forensic Browser
 *
 * Copyright 2011-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.detailview;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;
import javafx.application.Platform;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.chart.Axis;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import org.apache.commons.lang3.StringUtils;
import org.controlsfx.control.action.Action;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.progress.ProgressIndicator;
import org.sleuthkit.autopsy.timeline.FXMLConstructor;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ViewMode;
import org.sleuthkit.autopsy.timeline.ui.AbstractTimelineChart;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailViewEvent;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailsViewModel;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.UIFilter;
import org.sleuthkit.autopsy.timeline.utils.MappedList;
import org.sleuthkit.autopsy.timeline.zooming.ZoomState;
import org.sleuthkit.datamodel.DescriptionLoD;
import org.sleuthkit.datamodel.TskCoreException;

/**
 * Controller class for a DetailsChart based implementation of a timeline view.
 *
 * This class listens to changes in the assigned FilteredEventsModel and updates
 * the internal DetailsChart to reflect the currently requested view settings.
 *
 * Conceptually this view visualizes trees of events grouped by type and
 * description as a set of nested rectangles with their positions along the
 * x-axis tied to their times, and their vertical positions arbitrary but
 * constrained by the heirarchical relationships of the tree.The root of the
 * trees are EventStripes, which contain EventCluster, which contain more finely
 * grouped EventStripes, etc, etc. The leaves of the trees are EventClusters or
 * SingleEvents.
 */
final public class DetailViewPane extends AbstractTimelineChart<DateTime, EventStripe, EventNodeBase<?>, DetailsChart> {

    private final static Logger logger = Logger.getLogger(DetailViewPane.class.getName());

    @NbBundle.Messages("DetailsUpdateTask.continueButton=Continue")
    private final static ButtonType ContinueButtonType = new ButtonType(Bundle.DetailsUpdateTask_continueButton(), ButtonBar.ButtonData.OK_DONE);
    @NbBundle.Messages("DetailsUpdateTask.backButton=Back (Cancel)")
    private final static ButtonType back = new ButtonType(Bundle.DetailsUpdateTask_backButton(), ButtonBar.ButtonData.CANCEL_CLOSE);

    private final DateAxis detailsChartDateAxis = new DateAxis();
    private final DateAxis pinnedDateAxis = new DateAxis();

    @NbBundle.Messages("DetailViewPane.primaryLaneLabel.text=All Events (Filtered)")
    private final Axis<EventStripe> verticalAxis = new EventAxis<>(Bundle.DetailViewPane_primaryLaneLabel_text());

    /**
     * ObservableList of events selected in this detail view. It is
     * automatically mapped from the list of nodes selected in this view.
     */
    private final MappedList<DetailViewEvent, EventNodeBase<?>> selectedEvents;

    /**
     * Local copy of the zoomState. Used to backout of a zoomState change
     * without needing to requery/redraw the view.
     */
    private ZoomState currentZoom;
    private final DetailsViewModel detailsViewModel;

    /**
     * Constructor for a DetailViewPane
     *
     * @param controller the Controller to use
     */
    public DetailViewPane(TimeLineController controller) {
        super(controller);
        this.detailsViewModel = new DetailsViewModel(getEventsModel());
        this.selectedEvents = new MappedList<>(getSelectedNodes(), EventNodeBase<?>::getEvent);

        //initialize chart;
        setChart(new DetailsChart(detailsViewModel, controller, detailsChartDateAxis, pinnedDateAxis, verticalAxis, getSelectedNodes()));

        //bind layout fo axes and spacers
        detailsChartDateAxis.getTickMarks().addListener((Observable observable) -> layoutDateLabels());
        detailsChartDateAxis.getTickSpacing().addListener(observable -> layoutDateLabels());
        verticalAxis.setAutoRanging(false); //prevent XYChart.updateAxisRange() from accessing dataSeries on JFX thread causing ConcurrentModificationException

        getSelectedNodes().addListener((Observable observable) -> {
            //update selected nodes highlight
            getChart().setHighlightPredicate(getSelectedNodes()::contains);

            try {
                //update controllers list of selected event ids when view's selection changes.
                getController().selectEventIDs(getSelectedNodes().stream()
                        .flatMap(detailNode -> detailNode.getEventIDs().stream())
                        .collect(Collectors.toList()));
            } catch (TskCoreException ex) {
                logger.log(Level.SEVERE, "Error selecting nodes.", ex);
                new Alert(Alert.AlertType.ERROR, "Error selecting nodes").showAndWait();
            }
        });
    }

    /*
     * Get all the trees of events flattened into a single list, but only
     * including EventStripes and any leaf SingleEvents, since, EventClusters
     * contain no interesting non-time related information.
     */
    public ObservableList<DetailViewEvent> getAllNestedEvents() {
        return getChart().getAllNestedEvents();
    }

    /*
     * Get a list of the events that are selected in thes view.
     */
    public ObservableList<DetailViewEvent> getSelectedEvents() {
        return selectedEvents;
    }

    /**
     * Observe the list of events that should be highlighted in this view.
     *
     *
     * @param highlightedEvents the ObservableList of events that should be
     *                          highlighted in this view.
     */
    public void setHighLightedEvents(ObservableList<DetailViewEvent> highlightedEvents) {
        highlightedEvents.addListener((Observable observable) -> {
            /*
             * build a predicate that matches events with the same description
             * as any of the events in highlightedEvents or which are selected
             */
            Predicate<EventNodeBase<?>> highlightPredicate
                    = highlightedEvents.stream() // => events
                            .map(DetailViewEvent::getDescription)// => event descriptions 
                            .map(new Function<String, Predicate<EventNodeBase<?>>>() {
                                @Override
                                public Predicate<EventNodeBase<?>> apply(String description) {
                                    return eventNode -> StringUtils.equalsIgnoreCase(eventNode.getDescription(), description);
                                }
                            })// => predicates that match strings agains the descriptions of the events in highlightedEvents
                            .reduce(getSelectedNodes()::contains, Predicate::or); // => predicate that matches an of the descriptions or selected nodes
            getChart().setHighlightPredicate(highlightPredicate); //use this predicate to highlight nodes
        });
    }

    @Override
    final protected DateAxis getXAxis() {
        return detailsChartDateAxis;
    }

    /**
     * Get a new Action that will unhide events with the given description.
     *
     * @param description    the description to unhide
     * @param descriptionLoD the description level of detail to match
     *
     * @return a new Action that will unhide events with the given description.
     */
    public Action newUnhideDescriptionAction(String description, DescriptionLoD descriptionLoD) {
        return new UnhideDescriptionAction(description, descriptionLoD, getChart());
    }

    /**
     * Get a new Action that will hide events with the given description.
     *
     * @param description    the description to hide
     * @param descriptionLoD the description level of detail to match
     *
     * @return a new Action that will hide events with the given description.
     */
    public Action newHideDescriptionAction(String description, DescriptionLoD descriptionLoD) {
        return new HideDescriptionAction(description, descriptionLoD, getChart());
    }

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    @Override
    protected void clearData() {
        getChart().reset();
    }

    @Override
    protected Boolean isTickBold(DateTime value) {
        return false;
    }

    @Override
    final protected Axis<EventStripe> getYAxis() {
        return verticalAxis;
    }

    @Override
    protected double getTickSpacing() {
        return detailsChartDateAxis.getTickSpacing().get();
    }

    @Override
    protected String getTickMarkLabel(DateTime value) {
        return detailsChartDateAxis.getTickMarkLabel(value);
    }

    @Override
    protected Task<Boolean> getNewUpdateTask() {
        return new DetailsUpdateTask();
    }

    @Override
    protected void applySelectionEffect(EventNodeBase<?> c1, Boolean selected) {
        c1.applySelectionEffect(selected);
    }

    @Override
    protected double getAxisMargin() {
        return 0;
    }

    @Override
    final protected ViewMode getViewMode() {
        return ViewMode.DETAIL;
    }

    @Override
    protected ImmutableList<Node> getSettingsControls() {
        return ImmutableList.copyOf(new DetailViewSettingsPane(getChart().getLayoutSettings()).getChildrenUnmodifiable());
    }

    @Override
    protected boolean hasCustomTimeNavigationControls() {
        return false;
    }

    @Override
    protected ImmutableList<Node> getTimeNavigationControls() {
        return ImmutableList.of();
    }

    /**
     * A Pane that contains widgets to adjust settings specific to a
     * DetailViewPane
     */
    static private class DetailViewSettingsPane extends HBox {

        @FXML
        private RadioButton hiddenRadio;

        @FXML
        private RadioButton showRadio;

        @FXML
        private ToggleGroup descrVisibility;

        @FXML
        private RadioButton countsRadio;

        @FXML
        private CheckBox bandByTypeBox;

        @FXML
        private CheckBox oneEventPerRowBox;

        @FXML
        private CheckBox truncateAllBox;

        @FXML
        private Slider truncateWidthSlider;

        @FXML
        private Label truncateSliderLabel;

        @FXML
        private MenuButton advancedLayoutOptionsButtonLabel;

        @FXML
        private ToggleButton pinnedEventsToggle;

        private final DetailsChartLayoutSettings layoutSettings;

        DetailViewSettingsPane(DetailsChartLayoutSettings layoutSettings) {
            this.layoutSettings = layoutSettings;
            FXMLConstructor.construct(DetailViewSettingsPane.this, "DetailViewSettingsPane.fxml"); //NON-NLS
        }

        @NbBundle.Messages({
            "DetailViewPane.truncateSliderLabel.text=max description width (px):",
            "DetailViewPane.advancedLayoutOptionsButtonLabel.text=Advanced Layout Options",
            "DetailViewPane.bandByTypeBox.text=Band by Type",
            "DetailViewPane.oneEventPerRowBox.text=One Per Row",
            "DetailViewPane.truncateAllBox.text=Truncate Descriptions",
            "DetailViewPane.showRadio.text=Show Full Description",
            "DetailViewPane.countsRadio.text=Show Counts Only",
            "DetailViewPane.hiddenRadio.text=Hide Description"})
        @FXML
        void initialize() {
            assert bandByTypeBox != null : "fx:id=\"bandByTypeBox\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; //NON-NLS
            assert oneEventPerRowBox != null : "fx:id=\"oneEventPerRowBox\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; //NON-NLS
            assert truncateAllBox != null : "fx:id=\"truncateAllBox\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; //NON-NLS
            assert truncateWidthSlider != null : "fx:id=\"truncateAllSlider\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; //NON-NLS
            assert pinnedEventsToggle != null : "fx:id=\"pinnedEventsToggle\" was not injected: check your FXML file 'DetailViewSettings.fxml'."; //NON-NLS

            //bind widgets to settings object properties
            bandByTypeBox.selectedProperty().bindBidirectional(layoutSettings.bandByTypeProperty());

            oneEventPerRowBox.selectedProperty().bindBidirectional(layoutSettings.oneEventPerRowProperty());
            truncateAllBox.selectedProperty().bindBidirectional(layoutSettings.truncateAllProperty());
            truncateSliderLabel.disableProperty().bind(truncateAllBox.selectedProperty().not());
            pinnedEventsToggle.selectedProperty().bindBidirectional(layoutSettings.pinnedLaneShowing());

            final InvalidationListener sliderListener = observable -> {
                if (truncateWidthSlider.isValueChanging() == false) {
                    layoutSettings.truncateWidthProperty().set(truncateWidthSlider.getValue());
                }
            };
            truncateWidthSlider.valueProperty().addListener(sliderListener);
            truncateWidthSlider.valueChangingProperty().addListener(sliderListener);

            descrVisibility.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
                if (newToggle == countsRadio) {
                    layoutSettings.descrVisibilityProperty().set(DescriptionVisibility.COUNT_ONLY);
                } else if (newToggle == showRadio) {
                    layoutSettings.descrVisibilityProperty().set(DescriptionVisibility.SHOWN);
                } else if (newToggle == hiddenRadio) {
                    layoutSettings.descrVisibilityProperty().set(DescriptionVisibility.HIDDEN);
                }
            });

            //Assign localized labels
            truncateSliderLabel.setText(Bundle.DetailViewPane_truncateSliderLabel_text());
            advancedLayoutOptionsButtonLabel.setText(Bundle.DetailViewPane_advancedLayoutOptionsButtonLabel_text());
            bandByTypeBox.setText(Bundle.DetailViewPane_bandByTypeBox_text());
            oneEventPerRowBox.setText(Bundle.DetailViewPane_oneEventPerRowBox_text());
            truncateAllBox.setText(Bundle.DetailViewPane_truncateAllBox_text());
            showRadio.setText(Bundle.DetailViewPane_showRadio_text());
            countsRadio.setText(Bundle.DetailViewPane_countsRadio_text());
            hiddenRadio.setText(Bundle.DetailViewPane_hiddenRadio_text());
        }
    }

    @NbBundle.Messages({
        "DetailsUpdateTask.queryDb=Retrieving event data",
        "DetailsUpdateTask.name=Updating Details View",
        "DetailsUpdateTask.updateUI=Populating view",
        "# {0} - number of events",
        "DetailsUpdateTask.prompt=You are about to show details for {0} event clusters."
        + "  This might be very slow and could exhaust available memory.\n\nDo you want to continue?"})
    private class DetailsUpdateTask extends ViewRefreshTask<Interval> {

        DetailsUpdateTask() {
            super(Bundle.DetailsUpdateTask_name(), true);
        }

        @Override
        protected Boolean call() throws Exception {
            super.call();

            if (isCancelled()) {
                return null;
            }
            ZoomState newZoom = getEventsModel().getZoomState();

            //If the view doesn't need refreshing or if the ZoomState hasn't actually changed, just bail
            if (needsRefresh() == false && Objects.equals(currentZoom, newZoom)) {
                return true;
            }
            updateMessage(Bundle.DetailsUpdateTask_queryDb());

            //get the event stripes to be displayed
            List<EventStripe> resultStripes = detailsViewModel.getEventStripes(UIFilter.getAllPassFilter(), newZoom, new ProgressCancellable());
            if (isCancelled()) {
                return null;
            }
            final int size = resultStripes.size();
            if (size > 2000) {
                promptToCancel(size);

            }
            if (isCancelled()) {
                return null;
            }
            //we made it through the whole process, we are going to accept the new zoom
            currentZoom = newZoom;
            Platform.runLater(() -> {
                getChart().getRootEventStripes().retainAll(resultStripes);
                getChart().getAllNestedEvents().retainAll(resultStripes);
                getChart().addStripes(resultStripes);
                setDateValues(getEventsModel().getTimeRange());
            });
            updateMessage(Bundle.DetailsUpdateTask_updateUI());
            return resultStripes.isEmpty() == false;
        }

        private void promptToCancel(final int size) {
            Task<ButtonType> task = new Task<ButtonType>() {
                @Override
                protected ButtonType call() throws Exception {
                    if (getScene() != null && getScene().getWindow() != null) {
                        Alert alert = new Alert(Alert.AlertType.WARNING, Bundle.DetailsUpdateTask_prompt(size), ContinueButtonType, back);
                        alert.setHeaderText("");
                        alert.initModality(Modality.APPLICATION_MODAL);
                        alert.initOwner(getScene().getWindow());
                        return alert.showAndWait().orElse(back);
                    } else {
                        return back;
                    }
                }
            };
            //show dialog on JFX thread and block this thread until the dialog is dismissed.
            Platform.runLater(task);
            try {
                if (task.get() == back) {
                    DetailsUpdateTask.this.cancel(true);
                    getController().retreat();;
                }
            } catch (InterruptedException | ExecutionException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        @Override
        protected void cancelled() {
            super.cancelled();
        }

        @Override
        protected void setDateValues(Interval timeRange) {
            detailsChartDateAxis.setRange(timeRange, true);
            pinnedDateAxis.setRange(timeRange, true);
        }

        @Override
        protected void succeeded() {
            super.succeeded();
            layoutDateLabels();
        }

        private class ProgressCancellable implements DetailsViewModel.CancellabelProgress {

            long total;

            @Override
            public void start(String message, int totalWorkUnits) {
                updateMessage(message);
                total = totalWorkUnits;
                updateProgress(0, totalWorkUnits);
            }

            @Override
            public void start(String message) {
                updateMessage(message);
            }

            @Override
            public void switchToIndeterminate(String message) {
                updateMessage(message);
                updateProgress(-1, 1);
            }

            @Override
            public void switchToDeterminate(String message, int workUnitsCompleted, int totalWorkUnits) {
                updateMessage(message);
                total = totalWorkUnits;
                updateProgress(workUnitsCompleted, totalWorkUnits);
            }

            @Override
            public void progress(String message) {
                updateMessage(message);
            }

            @Override
            public void progress(int workUnitsCompleted) {
                updateProgress(workUnitsCompleted, total);
            }

            @Override
            public void progress(String message, int workUnitsCompleted) {
                updateMessage(message);
                updateProgress(workUnitsCompleted, total);

            }

            @Override
            public void setCancelling(String cancellingMessage) {
                updateMessage(cancellingMessage);
                cancel(true);
            }

            @Override
            public void finish() {
                updateProgress(1.0, 1.0);
            }

            @Override
            public boolean isCancelled() {
                return DetailsUpdateTask.this.isCancelled();
            }
        }
    }
}
