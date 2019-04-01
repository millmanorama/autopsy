/*
 * Autopsy Forensic Browser
 *
 * Copyright 2018 Basis Technology Corp.
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
package org.sleuthkit.autopsy.timeline.ui.listvew.datamodel;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.joda.time.Interval;
import org.sleuthkit.autopsy.timeline.FilteredEventsModel;
import org.sleuthkit.autopsy.timeline.ui.filtering.datamodel.RootFilterState;
import org.sleuthkit.autopsy.timeline.utils.TimelineDBUtils;
import static org.sleuthkit.autopsy.timeline.utils.TimelineDBUtils.unGroupConcat;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TimelineManager;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.timeline.EventType;

/**
 * Model for the ListView. Uses FilteredEventsModel as underlying datamodel and
 * supplies abstractions / data objects specific to the ListView.
 *
 */
public class ListViewModel {

    private final FilteredEventsModel eventsModel;
    private final TimelineManager eventManager;
    private final SleuthkitCase sleuthkitCase;

    public ListViewModel(FilteredEventsModel eventsModel) {
        this.eventsModel = eventsModel;
        this.eventManager = eventsModel.getEventManager();
        this.sleuthkitCase = eventsModel.getSleuthkitCase();
    }

    /**
     * Get a representation of all the events, within the given time range, that
     * pass the given filter, grouped by time and description such that file
     * system events for the same file, with the same timestamp, are combined
     * together.
     *
     * @return A List of combined events, sorted by timestamp.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public List<CombinedEvent> getCombinedEvents() throws TskCoreException {
        return getCombinedEvents(eventsModel.getTimeRange(), eventsModel.getFilterState());
    }

    /**
     * Get a representation of all the events, within the given time range, that
     * pass the given filter, grouped by time and description such that file
     * system events for the same file, with the same timestamp, are combined
     * together.
     *
     * @param timeRange   The Interval that all returned events must be within.
     * @param filterState The Filter that all returned events must pass.
     *
     * @return A List of combined events, sorted by timestamp.
     *
     * @throws org.sleuthkit.datamodel.TskCoreException
     */
    public List<CombinedEvent> getCombinedEvents(Interval timeRange, RootFilterState filterState) throws TskCoreException {
        Long startTime = timeRange.getStartMillis() / 1000;
        Long endTime = timeRange.getEndMillis() / 1000;

        if (Objects.equals(startTime, endTime)) {
            endTime++; //make sure end is at least 1 millisecond after start
        }

        ArrayList<CombinedEvent> combinedEvents = new ArrayList<>();

        TimelineDBUtils dbUtils = new TimelineDBUtils(sleuthkitCase);
        final String querySql = "SELECT full_description, time, file_obj_id, "
                                + dbUtils.csvAggFunction("CAST(tsk_events.event_id AS VARCHAR)") + " AS eventIDs, "
                                + dbUtils.csvAggFunction("CAST(event_type_id AS VARCHAR)") + " AS eventTypes"
                                + " FROM " + TimelineManager.getAugmentedEventsTablesSQL(filterState.getActiveFilter())
                                + " WHERE time >= " + startTime + " AND time <" + endTime + " AND " + eventManager.getSQLWhere(filterState.getActiveFilter())
                                + " GROUP BY time, full_description, file_obj_id ORDER BY time ASC, full_description";

        try (SleuthkitCase.CaseDbQuery dbQuery = sleuthkitCase.executeQuery(querySql);
                ResultSet resultSet = dbQuery.getResultSet();) {

            while (resultSet.next()) {

                //make a map from event type to event ID
                List<Long> eventIDs = unGroupConcat(resultSet.getString("eventIDs"), Long::valueOf);
                List<EventType> eventTypes = unGroupConcat(resultSet.getString("eventTypes"),
                        typesString -> eventManager.getEventType(Integer.valueOf(typesString)).orElseThrow(() -> new TskCoreException("Error mapping event type id " + typesString + ".S")));
                Map<EventType, Long> eventMap = new HashMap<>();
                for (int i = 0; i < eventIDs.size(); i++) {
                    eventMap.put(eventTypes.get(i), eventIDs.get(i));
                }
                combinedEvents.add(new CombinedEvent(resultSet.getLong("time") * 1000, resultSet.getString("full_description"), resultSet.getLong("file_obj_id"), eventMap));
            }

        } catch (SQLException sqlEx) {
            throw new TskCoreException("Failed to execute query for combined events: \n" + querySql, sqlEx); // NON-NLS
        }

        return combinedEvents;
    }
}
