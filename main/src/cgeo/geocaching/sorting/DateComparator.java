package cgeo.geocaching.sorting;

import cgeo.geocaching.Geocache;
import cgeo.geocaching.sensors.Sensors;

import java.util.ArrayList;
import java.util.Date;

/**
 * compares caches by date
 */
class DateComparator extends AbstractCacheComparator {

    @Override
    protected int compareCaches(final Geocache cache1, final Geocache cache2) {
        final Date date1 = cache1.getHiddenDate();
        final Date date2 = cache2.getHiddenDate();
        if (date1 != null && date2 != null) {
            final int dateDifference = date1.compareTo(date2);
            if (dateDifference == 0) {
                return sortSameDate(cache1, cache2);
            }
            return dateDifference;
        }
        if (date1 != null) {
            return -1;
        }
        if (date2 != null) {
            return 1;
        }
        return 0;
    }

    @SuppressWarnings("static-method")
    protected int sortSameDate(final Geocache cache1, final Geocache cache2) {
        final ArrayList<Geocache> list = new ArrayList<>();
        list.add(cache1);
        list.add(cache2);
        final DistanceComparator distanceComparator = new DistanceComparator(Sensors.getInstance().currentGeo().getCoords(), list);
        return distanceComparator.compare(cache1, cache2);
    }
}
