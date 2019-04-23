/*
 * Autopsy Forensic Browser
 *
 * Copyright 2016-2019 Basis Technology Corp.
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

import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Rectangle;
import com.github.davidmoten.rtree.geometry.internal.RectangleDouble;
import com.google.common.collect.Iterables;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javafx.beans.InvalidationListener;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.chart.Axis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import static javafx.scene.layout.Region.USE_PREF_SIZE;
import org.joda.time.DateTime;
import org.sleuthkit.autopsy.coreutils.ThreadConfined;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ui.AbstractTimelineChart;
import org.sleuthkit.autopsy.timeline.ui.ContextMenuProvider;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.DetailViewEvent;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.EventCluster;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.EventStripe;
import org.sleuthkit.autopsy.timeline.ui.detailview.datamodel.SingleDetailsViewEvent;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.DescriptionFilter;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.FilterState;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.timeline.EventTypeZoomLevel;
import rx.Observable;

/**
 * One "lane" of a the details view, contains all the core logic and layout
 * code.
 *
 * NOTE: It was too hard to control the threading of this chart via the
 * complicated default listeners. Instead clients should use addEvent and
 * removeEvent to add and remove data.
 */
abstract class DetailsChartLane<Y extends DetailViewEvent> extends XYChart<DateTime, Y> implements ContextMenuProvider {

    private static final String STYLE_SHEET = GuideLine.class.getResource("EventsDetailsChart.css").toExternalForm(); //NON-NLS

    static final int MINIMUM_EVENT_NODE_GAP = 4;
    static final int MINIMUM_ROW_HEIGHT = 24;

    private final DetailsChart parentChart;
    private final TimeLineController controller;
    private final DetailsChartLayoutSettings layoutSettings;
    private final ObservableList<EventNodeBase<?>> selectedNodes;

    private final Map<Y, EventNodeBase<?>> eventMap = new HashMap<>();

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)
    final ObservableList< EventNodeBase<?>> nodes = FXCollections.observableArrayList();
    final ObservableList< EventNodeBase<?>> sortedNodes = nodes.sorted(Comparator.comparing(EventNodeBase::getStartMillis));

    /**
     * the group that all event nodes are added to. This facilitates scrolling
     * by allowing a single translation of this group.
     */
    final Group nodeGroup = new Group();

    private final boolean useQuickHideFilters;

    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)//at start of layout pass
    private double descriptionWidth;
    @ThreadConfined(type = ThreadConfined.ThreadType.JFX)//at start of layout pass
    private Set<String> activeQuickHidefilters = new HashSet<>();

    /** listener that triggers chart layout pass */
    final InvalidationListener layoutInvalidationListener = observable -> layoutPlotChildren();

    boolean quickHideFiltersEnabled() {
        return useQuickHideFilters;
    }

    @Override
    public void clearContextMenu() {
        parentChart.clearContextMenu();
    }

    @Override
    public ContextMenu getContextMenu(MouseEvent clickEvent) {
        return parentChart.getContextMenu(clickEvent);
    }

    EventNodeBase<?> createNode(DetailsChartLane<?> chart, DetailViewEvent event) throws TskCoreException {
        if (event.getEventIDs().size() == 1) {
            return new SingleEventNode(this, new SingleDetailsViewEvent(controller.getEventsModel().getEventById(Iterables.getOnlyElement(event.getEventIDs()))), null);
        } else if (event instanceof SingleDetailsViewEvent) {
            return new SingleEventNode(chart, (SingleDetailsViewEvent) event, null);
        } else if (event instanceof EventCluster) {
            return new EventClusterNode(chart, (EventCluster) event, null);
        } else {
            return new EventStripeNode(chart, (EventStripe) event, null);
        }
    }

    @Override
    synchronized protected void layoutPlotChildren() {
        setCursor(Cursor.WAIT);
        if (useQuickHideFilters) {
            //These don't change during a layout pass and are expensive to compute per node.  So we do it once at the start
            activeQuickHidefilters = getController().getQuickHideFilters().stream()
                    .filter(FilterState<DescriptionFilter>::isActive)
                    .map(FilterState<DescriptionFilter>::getFilter)
                    .map(DescriptionFilter::getDescription)
                    .collect(Collectors.toSet());
        }
        descriptionWidth = layoutSettings.getTruncateAll() ? layoutSettings.getTruncateWidth() : USE_PREF_SIZE;

        if (layoutSettings.getBandByType()) {
            EventTypeZoomLevel eventTypeZoom = controller.getEventsModel().getEventTypeZoom();
            maxY.set(0);
            sortedNodes.stream()
                    .collect(groupingBy(eventNodeBase -> eventNodeBase.getEventType(eventTypeZoom),
                            TreeMap::new, toList()))
                    .values()
                    .forEach(inputNodes -> maxY.set(layoutEventNodes(inputNodes, maxY.get())));
        } else {
            maxY.set(layoutEventNodes(sortedNodes, 0));
        }
        doAdditionalLayout();
        setCursor(null);
    }

    @Override
    public TimeLineController getController() {
        return controller;
    }

    public ObservableList<EventNodeBase<?>> getSelectedNodes() {
        return selectedNodes;
    }

    public ReadOnlyDoubleProperty maxVScrollProperty() {
        return maxY.getReadOnlyProperty();
    }
    /**
     * the maximum y value used so far during the most recent layout pass
     */
    private final ReadOnlyDoubleWrapper maxY = new ReadOnlyDoubleWrapper(0.0);

    DetailsChartLane(DetailsChart parentChart, Axis<DateTime> dateAxis, Axis<Y> verticalAxis, boolean useQuickHideFilters) {
        super(dateAxis, verticalAxis);
        this.parentChart = parentChart;
        this.layoutSettings = parentChart.getLayoutSettings();
        this.controller = parentChart.getController();
        this.selectedNodes = parentChart.getSelectedNodes();
        this.useQuickHideFilters = useQuickHideFilters;

        //add a dummy series or the chart is never rendered
        setData(FXCollections.observableList(Arrays.asList(new Series<>())));

        Tooltip.install(this, AbstractTimelineChart.getDefaultTooltip());

        dateAxis.setAutoRanging(false);
        setLegendVisible(false);
        setPadding(Insets.EMPTY);
        setAlternativeColumnFillVisible(true);

        sceneProperty().addListener(observable -> {
            Scene scene = getScene();
            if (scene != null && scene.getStylesheets().contains(STYLE_SHEET) == false) {
                scene.getStylesheets().add(STYLE_SHEET);
            }
        });

        //add listener for events that should trigger layout
        layoutSettings.bandByTypeProperty().addListener(layoutInvalidationListener);
        layoutSettings.oneEventPerRowProperty().addListener(layoutInvalidationListener);
        layoutSettings.truncateAllProperty().addListener(layoutInvalidationListener);
        layoutSettings.truncateWidthProperty().addListener(layoutInvalidationListener);
        layoutSettings.descrVisibilityProperty().addListener(layoutInvalidationListener);
        controller.getQuickHideFilters().addListener(layoutInvalidationListener);

        //all nodes are added to nodeGroup to facilitate scrolling rather than to getPlotChildren() directly
        getPlotChildren().add(nodeGroup);
    }

    /**
     * Layout the nodes in the given list, starting form the given minimum y
     * coordinate via the following algorithm:
     *
     * We start with a list of nodes (each representing an event) sorted by span
     * start time of the underlying event
     *
     * - initialize empty map (maxXatY) from y-ranges to max used x-value
     *
     * - for each node:
     *
     * -- size the node based on its children (use this algorithm recursively)
     *
     * -- get the event's start position from the dateaxis
     *
     * -- to position node: check if maxXatY is to the left of the left x coord:
     * if maxXatY is less than the left x coord, good, put the current node
     * here, mark right x coord as maxXatY, go to next node ; if maxXatY is
     * greater than the left x coord, increment y position, do check again until
     * maxXatY less than left x coord.
     *
     * @param nodes collection of nodes to layout, sorted by event start time
     * @param minY  the minimum y coordinate to position the nodes at.
     *
     * @return the maximum y coordinate used by any of the layed out nodes.
     */
    public double layoutEventNodes(final Collection<? extends EventNodeBase<?>> nodes, final double minY) {
        double localMax = minY;
        RTree<String, Rectangle> tree = RTree.create();

        //for each node do a recursive layout to size it and then position it in first available slot
        for (EventNodeBase<?> bundleNode : nodes) {
            if (useQuickHideFilters && activeQuickHidefilters.contains(bundleNode.getDescription())) {
                //if the node hiden is hidden by  quick hide filter, hide it and skip layout
                bundleNode.setVisible(false);
                bundleNode.setManaged(false);
            } else {
                layoutBundleHelper(bundleNode);
                //get computed height and width
                double h = bundleNode.getBoundsInLocal().getHeight();
                double w = bundleNode.getBoundsInLocal().getWidth();
                //get left and right x coords from axis plus computed width
                double xLeft = getXForEpochMillis(bundleNode.getStartMillis()) - bundleNode.getLayoutXCompensation();
                double xRight = xLeft + w + MINIMUM_EVENT_NODE_GAP;

                //initial test position
                double yTop = (layoutSettings.getOneEventPerRow())
                        ? (localMax + MINIMUM_EVENT_NODE_GAP)// if onePerRow, just put it at end
                        : computeYTop(minY, h, tree, xLeft, xRight);
                double yBottom = yTop + h;

                tree = tree.add(bundleNode.getDescription(), RectangleDouble.create(xLeft, yTop, xRight, yBottom));
                localMax = Math.max(yBottom, localMax);

                //animate node to new position
                bundleNode.animateTo(xLeft, yTop);
            }
        }
        return localMax; //return new max
    }

    @Override
    final public void requestChartLayout() {
        super.requestChartLayout();
    }

    double getXForEpochMillis(Long millis) {
        DateTime dateTime = new DateTime(millis);
        return getXAxis().getDisplayPosition(dateTime);
    }

    @Deprecated
    @Override
    protected void dataItemAdded(Series<DateTime, Y> series, int itemIndex, Data<DateTime, Y> item) {
    }

    @Deprecated
    @Override
    protected void dataItemRemoved(Data<DateTime, Y> item, Series<DateTime, Y> series) {
    }

    @Deprecated
    @Override
    protected void dataItemChanged(Data<DateTime, Y> item) {
    }

    @Deprecated
    @Override
    protected void seriesAdded(Series<DateTime, Y> series, int seriesIndex) {
    }

    @Deprecated
    @Override
    protected void seriesRemoved(Series<DateTime, Y> series) {
    }

    /**
     * add an event to this chart
     *
     * @see note in main section of class JavaDoc
     *
     * @param event
     */
    void addEvent(Y event) throws TskCoreException {
        EventNodeBase<?> eventNode = createNode(this, event);
        eventMap.put(event, eventNode);

        nodes.add(eventNode);
        nodeGroup.getChildren().add(eventNode);
    }

    /**
     * remove an event from this chart
     *
     * @see note in main section of class JavaDoc
     *
     * @param event
     */
    void removeEvent(Y event) {
        EventNodeBase<?> removedNode = eventMap.remove(event);
//        Platform.runLater(() -> {
        nodes.remove(removedNode);
        nodeGroup.getChildren().removeAll(removedNode);
//        });
    }

    public synchronized void setVScroll(double vScrollValue) {
        nodeGroup.setTranslateY(-vScrollValue);
    }

    /**
     * @return all the nodes that pass the given predicate
     */
    synchronized Iterable<EventNodeBase<?>> getAllNodes() {
        return getNodes(dummy -> true);
    }

    /**
     * @return all the nodes that pass the given predicate
     */
    private synchronized Iterable<EventNodeBase<?>> getNodes(Predicate<EventNodeBase<?>> predicate) {
        //use this recursive function to flatten the tree of nodes into an single stream.
        Function<EventNodeBase<?>, Stream<EventNodeBase<?>>> stripeFlattener
                = new Function<EventNodeBase<?>, Stream<EventNodeBase<?>>>() {
            @Override
            public Stream<EventNodeBase<?>> apply(EventNodeBase<?> node) {
                return Stream.concat(
                        Stream.of(node),
                        node.getSubNodes().stream().flatMap(this::apply));
            }
        };

        return sortedNodes.stream()
                .flatMap(stripeFlattener)
                .filter(predicate).collect(Collectors.toList());
    }

    /**
     * Given information about the current layout pass so far and about a
     * particular node, compute the y position of that node.
     *
     *
     * @param yMin    the smallest (towards the top of the screen) y position to
     *                consider
     * @param h       the height of the node we are trying to position
     * @param maxXatY a map from y ranges to the max x within that range. NOTE:
     *                This map will be updated to include the node in question.
     * @param xLeft   the left x-cord of the node to position
     * @param xRight  the right x-cord of the node to position
     *
     * @return the y position for the node in question.
     */
    double computeYTop(double yMin, double h, RTree<String, Rectangle> tree, double xLeft, double xRight) {
        double yTop = yMin;

        //until the node is not overlapping any others try moving it down.
        boolean overlapping = true;
        Rectangle verticalOccupancy = null;
        while (overlapping) { //while overlapping
            double yBottom = yTop + h; //compute bottom as top plus height

            RectangleDouble nodeRect = RectangleDouble.create(xLeft, yTop, xRight, yBottom); // probe rectangle
            Observable<Entry<String, Rectangle>> search = tree.search(nodeRect, MINIMUM_EVENT_NODE_GAP); // search for intersections

            double maxIntersectY = -1; //no intersection
            Iterable<Entry<String, Rectangle>> iterator = search.toBlocking().toIterable();

            for (Entry<String, Rectangle> r : iterator) {  //intersections...
                Rectangle intersected = r.geometry();
                if (verticalOccupancy == null) {
                    verticalOccupancy = intersected;
                } else {
                    try {
                        verticalOccupancy = RectangleDouble.create(
                                Math.max(intersected.x1(), verticalOccupancy.x1()),
                                Math.min(intersected.y1(), verticalOccupancy.y1()),
                                Math.min(intersected.x2(), verticalOccupancy.x2()),
                                Math.max(intersected.y2(), verticalOccupancy.y2())
                        );
                    } catch (java.lang.IllegalArgumentException e) {
                    }
                }

                maxIntersectY = Math.max(maxIntersectY, r.geometry().y2()); //compute max (bottom ) of all intersections
                break;
            }

            if (maxIntersectY >= 0) { // there was an intersection
                yTop = maxIntersectY + MINIMUM_EVENT_NODE_GAP; // move probe rectanlge down
            } else {
                overlapping = false; // no intersection, break loop
            }
        };
        if (verticalOccupancy != null) {
            tree.add("dummy", verticalOccupancy);
        }
        return yTop;
    }

    /**
     * Set layout parameters on the given node and layout its children
     *
     * @param eventNode the Node to layout
     */
    void layoutBundleHelper(final EventNodeBase< ?> eventNode) {
        //make sure it is shown
        eventNode.setVisible(true);
        eventNode.setManaged(true);
        //apply advanced layout description visibility options
        eventNode.setDescriptionVisibility(layoutSettings.getDescrVisibility());
        eventNode.setMaxDescriptionWidth(descriptionWidth);

        //do recursive layout
        eventNode.layoutChildren();
    }

    abstract void doAdditionalLayout();

    DetailsChart getParentChart() {
        return parentChart;
    }
}
