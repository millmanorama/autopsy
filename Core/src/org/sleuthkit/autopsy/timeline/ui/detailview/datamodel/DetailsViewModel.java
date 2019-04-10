/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018-2019 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.detailview.datamodel;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.TreeMultimap;
import com.google.common.eventbus.Subscribe;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import static java.util.Comparator.comparing;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Level;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import javafx.concurrent.Task;
import org.apache.commons.lang3.tuple.Pair;
import org.joda.time.DateTimeZone;
import org.joda.time.Interval;
import org.joda.time.Period;
import org.openide.util.Exceptions;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.timeline.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.TimeLineController;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.UIFilter;
import org.sleuthkit.autopsy.timeline.utils.CacheLoaderImpl;
import org.sleuthkit.autopsy.timeline.utils.RangeDivision;
import org.sleuthkit.autopsy.timeline.zooming.TimeUnits;
import org.sleuthkit.autopsy.timeline.zooming.ZoomState;
import org.sleuthkit.datamodel.DescriptionLoD;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.timeline.EventType;
import org.sleuthkit.datamodel.timeline.EventTypeZoomLevel;
import org.sleuthkit.datamodel.timeline.TimelineEvent;
import org.sleuthkit.datamodel.timeline.TimelineFilter;

/**
 * Model for the Details View. Uses FilteredEventsModel as underlying datamodel
 * and supplies abstractions / data objects specific to the DetailsView
 */
final public class DetailsViewModel {

    private final static Logger logger = Logger.getLogger(DetailsViewModel.class.getName());

    private final FilteredEventsModel eventsModel;
    private final LoadingCache<ZoomState, List<TimelineEvent>> eventCache;
    private final TimelineManager eventManager;
    private final SleuthkitCase sleuthkitCase;

    public DetailsViewModel(FilteredEventsModel eventsModel) {
        this.eventsModel = eventsModel;
        this.eventManager = eventsModel.getEventManager();
        this.sleuthkitCase = eventsModel.getSleuthkitCase();
        eventCache = CacheBuilder.newBuilder()
                .maximumSize(1000L)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(new CacheLoaderImpl<>(this::getEventsHelper));
        eventsModel.registerForEvents(this);
    }

    @Subscribe
    void handleCacheInvalidation(FilteredEventsModel.CacheInvalidatedEvent event) {
        eventCache.invalidateAll();
    }

    /**
     * @param zoom
     *
     * @return a list of aggregated events that are within the requested time
     *         range and pass the requested filter, using the given aggregation
     *         to control the grouping of events
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public List<EventStripe> getEventStripes(ZoomState zoom) throws TskCoreException {
        return getEventStripes(UIFilter.getAllPassFilter(), zoom, new ArrayList<>(), null);
    }

    public List<EventStripe> getEventStripes(UIFilter uiFilter, ZoomState zoom) throws TskCoreException {
        return getEventStripes(uiFilter, zoom, new ArrayList<>(), null);
    }

    /**
     * @param uiFilter      Filtere specific to the details view.
     * @param zoom          The zoom state to use to get and cluster the events.
     * @param resultStripes Add event stripes to this list.
     * @param cancelation   A task that can be used to cancel this process.
     *
     * @return a list of EventStripes that are within the requested time range
     *         and pass the requested filter. This will be the same list and
     *         resultStripes.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public List<EventStripe> getEventStripes(UIFilter uiFilter, ZoomState zoom, List<EventStripe> resultStripes, Task<?> cancelation) throws TskCoreException {
        //unpack params
        Interval timeRange = zoom.getTimeRange();
        DescriptionLoD descriptionLOD = zoom.getDescriptionLOD();
        EventTypeZoomLevel typeZoomLevel = zoom.getTypeZoomLevel();

        //get the allowed cluster gap based on the time range in view.
        Long unitPeriod = timeRange.toDurationMillis() / 25;

        //creat a map from eventType and String to events, keeping the events in chronological order.
        TreeMultimap<Pair<EventType, String>, TimelineEvent> stripeMap = TreeMultimap.create(
                Comparator.naturalOrder(),
                comparing(TimelineEvent::getStartMillis));

        //filter (cached) events by uiFilter and add them to stripeMap
        for (TimelineEvent event : getCachedEvents(zoom)) {
            if (cancelation != null && cancelation.isCancelled()) {
                return resultStripes;
            }

            if (uiFilter.test(event)) {
                Pair<EventType, String> stripeKey = Pair.of(event.getEventType(typeZoomLevel), event.getDescription(descriptionLOD));
                stripeMap.put(stripeKey, event);
            }
        }

        //sort map keys by start time.
        List<Pair<EventType, String>> eventKeys = stripeMap.keySet().stream()
                .sorted(comparing(key -> stripeMap.get(key).first().getStartMillis()))
                .collect(toList());

        //for each key, make a stripe, including internal clusters.
        for (Pair<EventType, String> key : eventKeys) {
            if (cancelation != null && cancelation.isCancelled()) {
                return resultStripes;
            }

            //unpack keys 
            EventType zoomedType = key.getLeft();
            String description = key.getRight();

            SortedSet<EventCluster> clusters = new TreeSet<>(comparing(EventCluster::getStartMillis));

            Iterator<TimelineEvent> sortedEventIterator = stripeMap.get(key).iterator();
            TimelineEvent currentEvent = sortedEventIterator.next();
            EventCluster currentCluster = new EventCluster(currentEvent, zoomedType, descriptionLOD);
            while (sortedEventIterator.hasNext()) {
                if (cancelation != null && cancelation.isCancelled()) {
                    return resultStripes;
                }
                TimelineEvent next = sortedEventIterator.next();
                Interval nextEventSpan = new Interval(next.getStartMillis(), next.getEndMillis());

                //if they overlap or gap is small, merge them
                if (isOverlap(currentCluster.getSpan(), nextEventSpan, unitPeriod)) { //merge them
                    currentCluster = currentCluster.add(next, nextEventSpan, zoomedType);
                } else { //done merging into current, set next as new current
                    clusters.add(currentCluster);
                    currentCluster = new EventCluster(next, zoomedType, descriptionLOD);
                }
            }
            clusters.add(currentCluster); //add last cluster
            resultStripes.add(new EventStripe(zoomedType, description, descriptionLOD, clusters));
        }

        return resultStripes;
    }

    /**
     * Do the given spans overalap or have a gap less one quarter
     * timeUnitLength?
     *
     * //TODO: 1/4 factor is arbitrary review! -jm
     *
     * @param clusterSpan
     * @param eventSpan
     * @param unitPeriod
     *
     * @return
     */
    private static boolean isOverlap(Interval clusterSpan, Interval eventSpan, Long unitPeriod) {
        Interval gap = clusterSpan.gap(eventSpan);
        return gap == null || gap.toDurationMillis() <= unitPeriod;
    }

    private List<TimelineEvent> getCachedEvents(ZoomState zoom) throws TskCoreException {
        //get (cached) events based on zoom
        try {
            return eventCache.get(zoom);
        } catch (ExecutionException ex) {
            throw new TskCoreException("Failed to load events from cache for " + zoom.toString(), ex); //NON-NLS
        }
    }

    /**
     * Get a list of TimelineEvents, using the requested zoom paramaters.
     *
     * @param zoom     The ZoomState that determine the zooming, filtering and
     *                 clustering.
     * @param timeZone The time zone to use.
     *
     * @return a list of events within the given timerange, that pass the
     *         supplied filter.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException If there is an error
     *                                                  querying the db.
     */
    List<TimelineEvent> getEventsHelper(ZoomState zoom) throws TskCoreException {
        //unpack params
        Interval timeRange = zoom.getTimeRange();
        TimelineFilter.RootFilter activeFilter = zoom.getFilterState().getActiveFilter();

        long start = timeRange.getStartMillis() / 1000;
        long end = timeRange.getEndMillis() / 1000;

        //ensure length of querried interval is not 0
        end = Math.max(end, start + 1);

        //build dynamic parts of query
        String querySql = "SELECT time, file_obj_id, data_source_obj_id, artifact_id, " // NON-NLS
                          + "  event_id, " //NON-NLS
                          + " hash_hit, " //NON-NLS
                          + " tagged, " //NON-NLS
                          + " event_type_id, super_type_id, "
                          + " full_description, med_description, short_description " // NON-NLS
                          + " FROM " + TimelineManager.getAugmentedEventsTablesSQL(activeFilter) // NON-NLS
                          + " WHERE time >= " + start + " AND time < " + end + " AND " + eventManager.getSQLWhere(activeFilter) // NON-NLS
                          + " ORDER BY time"; // NON-NLS

        List<TimelineEvent> events = new ArrayList<>();

        try (SleuthkitCase.CaseDbQuery dbQuery = sleuthkitCase.executeQuery(querySql);
                ResultSet resultSet = dbQuery.getResultSet();) {
            while (resultSet.next()) {
                events.add(eventHelper(resultSet));
            }
        } catch (TskCoreException ex) {
            logger.log(Level.SEVERE, "Failed to get events with query: " + querySql, ex); // NON-NLS
            throw ex;
        } catch (SQLException ex) {
            logger.log(Level.SEVERE, "Failed to get events with query: " + querySql, ex); // NON-NLS
            throw new TskCoreException("Failed to get events with query: " + querySql, ex);
        }
        return events;
    }

    /**
     * Map a single row in a ResultSet to a TimelineEvent
     *
     * @param resultSet      the result set whose current row should be mapped
     * @param typeColumn     The type column (sub_type or base_type) to use as
     *                       the type of the event
     * @param descriptionLOD the description level of detail for this event
     *
     * @return an TimelineEvent corresponding to the current row in the given
     *         result set
     *
     * @throws SQLException
     */
    private TimelineEvent eventHelper(ResultSet resultSet) throws SQLException, TskCoreException {

        //the event tyepe to use to get the description.
        int eventTypeID = resultSet.getInt("event_type_id");
        EventType eventType = eventManager.getEventType(eventTypeID).orElseThrow(()
                -> new TskCoreException("Error mapping event type id " + eventTypeID + "to EventType."));//NON-NLS

        return new TimelineEvent(
                resultSet.getLong("event_id"), // NON-NLS
                resultSet.getLong("data_source_obj_id"), // NON-NLS
                resultSet.getLong("file_obj_id"), // NON-NLS
                resultSet.getLong("artifact_id"), // NON-NLS
                resultSet.getLong("time"), // NON-NLS
                eventType,
                resultSet.getString("full_description"), // NON-NLS
                resultSet.getString("med_description"), // NON-NLS
                resultSet.getString("short_description"), // NON-NLS
                resultSet.getInt("hash_hit") != 0, //NON-NLS
                resultSet.getInt("tagged") != 0);

    }

    private static Pair<EventType, String> getTypeDescriptionPair(EventCluster eventCluster) {
        return Pair.of(eventCluster.getEventType(), eventCluster.getDescription());
    }

    /** Make a sorted copy of the given set using the given comparator to sort
     * it.
     *
     * @param <X>        The type of elements in the set.
     * @param setA       The set of elements to copy into the new sorted set.
     * @param comparator The comparator to sort the new set by.
     *
     * @return A sorted copy of the given set.
     */
    static <X> SortedSet<X> copyAsSortedSet(Collection<X> setA, Comparator<X> comparator) {
        TreeSet<X> treeSet = new TreeSet<>(comparator);
        treeSet.addAll(setA);
        return treeSet;
    }

    static <X> Set<X> union(Set<X> setA, Set<X> setB) {
        Set<X> s = new HashSet<>(setA);
        s.addAll(setB);
        return s;
    }

}
