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

import com.google.common.base.Preconditions;
import static java.util.Collections.singleton;
import static java.util.Comparator.comparing;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sleuthkit.datamodel.DescriptionLoD;
import org.sleuthkit.datamodel.timeline.EventType;

/**
 * A 'collection' of EventClusters, all having the same type, description, and
 * zoom levels, but not necessarily close together in time.
 */
public final class EventStripe implements MultiEvent<EventCluster> {

    private final EventCluster parent;

    private final SortedSet<EventCluster> clusters;

    /**
     * the type of all the events
     */
    private final EventType type;

    /**
     * the common description of all the events
     */
    private final String description;

    /**
     * the description level of detail that the events were clustered at.
     */
    private final DescriptionLoD lod;

    /**
     * the set of ids of the events
     */
    private final Set<Long> eventIDs;

    /**
     * the ids of the subset of events that have at least one tag applied to
     * them
     */
    private final Set<Long> tagged;

    /**
     * the ids of the subset of events that have at least one hash set hit
     */
    private final Set<Long> hashHits;

//    EventStripe merge(EventStripe stripeB) {
//
//        Preconditions.checkNotNull(stripeB);
//        Preconditions.checkArgument(Objects.equals(description, stripeB.description));
//        Preconditions.checkArgument(Objects.equals(lod, stripeB.lod));
//        Preconditions.checkArgument(Objects.equals(type, stripeB.type));
//        Preconditions.checkArgument(Objects.equals(parent, stripeB.parent));
//
//        getClusters().addAll(stripeB.getClusters());
//        getEventIDs().addAll(stripeB.getEventIDs());
//        getEventIDsWithTags().addAll(stripeB.getEventIDsWithTags());
//        getEventIDsWithHashHits().addAll(stripeB.getEventIDsWithHashHits());
//        EventCluster mergedparent = getParent().orElse(stripeB.getParent().orElse(null));
//
//        return new EventStripe(mergedparent, getEventType(), getDescription(), getDescriptionLoD(), getClusters(), getEventIDs(),
//                getEventIDsWithTags(), getEventIDsWithHashHits());
//    }

    public EventStripe withParent(EventCluster parent) {
        if (Objects.nonNull(this.parent)) {
            throw new IllegalStateException("Event Stripe already has a parent!");
        }
        return new EventStripe(parent, this.type, this.description, this.lod, clusters, eventIDs, tagged, hashHits);
    }

    EventStripe(EventCluster parent, EventType type, String description,
                DescriptionLoD lod, SortedSet<EventCluster> clusters,
                Set<Long> eventIDs, Set<Long> tagged, Set<Long> hashHits) {
        this.parent = parent;
        this.type = type;
        this.description = description;
        this.lod = lod;
        this.clusters = clusters;

        this.eventIDs = eventIDs;
        this.tagged = tagged;
        this.hashHits = hashHits;
    }

    EventStripe(EventType type, String description, DescriptionLoD descriptionLOD, SortedSet<EventCluster> clusters) {
        this.parent = null;
        this.type = type;
        this.description = description;
        this.lod = descriptionLOD;
        this.clusters = clusters;

        this.eventIDs = clusters.stream()
                .flatMap(cluster -> cluster.getEventIDs().stream())
                .collect(Collectors.toSet());
        this.tagged = clusters.stream()
                .flatMap(cluster -> cluster.getEventIDsWithTags().stream())
                .collect(Collectors.toSet());
        this.hashHits = clusters.stream()
                .flatMap(cluster -> cluster.getEventIDsWithHashHits().stream())
                .collect(Collectors.toSet());
    }

//    public EventStripe(EventCluster cluster) {
//        this.clusters = DetailsViewModel.copyAsSortedSet(singleton(cluster.withParent(this)),
//                comparing(EventCluster::getStartMillis));
//
//        type = cluster.getEventType();
//        description = cluster.getDescription();
//        lod = cluster.getDescriptionLoD();
//        eventIDs = cluster.getEventIDs();
//        tagged = cluster.getEventIDsWithTags();
//        hashHits = cluster.getEventIDsWithHashHits();
//        this.parent = null;
//    }

    @Override
    public Optional<EventCluster> getParent() {
        return Optional.ofNullable(parent);
    }

    @Override
    public Optional<EventStripe> getParentStripe() {
        if (getParent().isPresent()) {
            return getParent().get().getParent();
        } else {
            return Optional.empty();
        }
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public EventType getEventType() {
        return type;
    }

    @Override
    public DescriptionLoD getDescriptionLoD() {
        return lod;
    }

    @Override
    public Set<Long> getEventIDs() {
        return eventIDs;
    }

    @Override
    public Set<Long> getEventIDsWithHashHits() {
        return hashHits;
    }

    @Override
    public Set<Long> getEventIDsWithTags() {
        return tagged;
    }

    @Override
    public long getStartMillis() {
        return clusters.first().getStartMillis();
    }

    @Override
    public long getEndMillis() {
        return clusters.last().getEndMillis();
    }

    @Override
    public SortedSet< EventCluster> getClusters() {
        return clusters;
    }

    @Override
    public String toString() {
        return "EventStripe{" + "description=" + description + ", eventIDs=" + (Objects.isNull(eventIDs) ? 0 : eventIDs.size()) + '}'; //NON-NLS
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 79 * hash + Objects.hashCode(this.clusters);
        hash = 79 * hash + Objects.hashCode(this.type);
        hash = 79 * hash + Objects.hashCode(this.description);
        hash = 79 * hash + Objects.hashCode(this.lod);
        hash = 79 * hash + Objects.hashCode(this.eventIDs);
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final EventStripe other = (EventStripe) obj;
        if (!Objects.equals(this.description, other.description)) {
            return false;
        }
        if (!Objects.equals(this.clusters, other.clusters)) {
            return false;
        }
        if (!Objects.equals(this.type, other.type)) {
            return false;
        }
        if (this.lod != other.lod) {
            return false;
        }
        return Objects.equals(this.eventIDs, other.eventIDs);
    }

}
