package cgeo.geocaching;

import cgeo.geocaching.compatibility.Compatibility;
import cgeo.geocaching.files.LocalStorage;
import cgeo.geocaching.location.Geopoint;
import cgeo.geocaching.location.GeopointFormatter.Format;
import cgeo.geocaching.network.Network;
import cgeo.geocaching.network.Parameters;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.FileUtils;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.RxUtils;

import ch.boye.httpclientandroidlib.HttpResponse;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jdt.annotation.NonNull;

import rx.Observable;
import rx.functions.Func0;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public final class StaticMapsProvider {
    static final int MAPS_LEVEL_MAX = 5;
    private static final String PREFIX_PREVIEW = "preview";
    private static final String GOOGLE_STATICMAP_URL = "http://maps.google.com/maps/api/staticmap";
    private static final int GOOGLE_MAX_ZOOM = 20;
    private static final String SATELLITE = "satellite";
    private static final String ROADMAP = "roadmap";
    private static final String WAYPOINT_PREFIX = "wp";
    private static final String MAP_FILENAME_PREFIX = "map_";
    private static final String MARKERS_URL = "http://status.cgeo.org/assets/markers/";

    private static volatile long last403 = 0;

    /** We assume there is no real usable image with less than 1k. */
    private static final int MIN_MAP_IMAGE_BYTES = 1000;

    /**
     * max size in free API version: https://developers.google.com/maps/documentation/staticmaps/#Imagesizes
     */
    private static final int GOOGLE_MAPS_MAX_SIZE = 640;

    private StaticMapsProvider() {
        // utility class
    }

    private static File getMapFile(final String geocode, final String prefix, final boolean createDirs) {
        return LocalStorage.getStorageFile(geocode, MAP_FILENAME_PREFIX + prefix, false, createDirs);
    }

    private static <T> Observable<T> checkDownloadPermission(final Observable<T> ifPermitted) {
        return Observable.defer(new Func0<Observable<T>>() {
            @Override
            public Observable<T> call() {
                if (System.currentTimeMillis() - last403 >= 30000) {
                    return ifPermitted;
                }
                Log.d("StaticMaps.downloadMap: request ignored because of recent \"permission denied\" answer");
                return Observable.empty();
            }
        });
    }

    private static Observable<String> downloadDifferentZooms(final String geocode, final String markerUrl, final String prefix, final String latlonMap, final int width, final int height, final Parameters waypoints) {
        return checkDownloadPermission(Observable.merge(downloadMap(geocode, 20, SATELLITE, markerUrl, prefix + '1', "", latlonMap, width, height, waypoints),
                downloadMap(geocode, 18, SATELLITE, markerUrl, prefix + '2', "", latlonMap, width, height, waypoints),
                downloadMap(geocode, 16, ROADMAP, markerUrl, prefix + '3', "", latlonMap, width, height, waypoints),
                downloadMap(geocode, 14, ROADMAP, markerUrl, prefix + '4', "", latlonMap, width, height, waypoints),
                downloadMap(geocode, 11, ROADMAP, markerUrl, prefix + '5', "", latlonMap, width, height, waypoints)));
    }

    private static Observable<String> downloadMap(final String geocode, final int zoom, final String mapType, final String markerUrl, final String prefix, final String shadow, final String latlonMap, final int width, final int height, final Parameters waypoints) {
        // If it has been less than 30 seconds since we got a 403 (permission denied) from Google servers,
        // do not try again.
        final int scale = width <= GOOGLE_MAPS_MAX_SIZE ? 1 : 2;
        final float aspectRatio = width / (float) height;
        final int requestWidth = Math.min(width / scale, GOOGLE_MAPS_MAX_SIZE);
        final int requestHeight = (aspectRatio > 1) ? Math.round(requestWidth / aspectRatio) : requestWidth;
        final int requestZoom = Math.min((scale == 2) ? zoom + 1 : zoom, GOOGLE_MAX_ZOOM);
        return checkDownloadPermission(Observable.defer(new Func0<Observable<String>>() {
            @Override
            public Observable<String> call() {
                final Parameters params = new Parameters(
                        "center", latlonMap,
                        "zoom", String.valueOf(requestZoom),
                        "size", String.valueOf(requestWidth) + 'x' + String.valueOf(requestHeight),
                        "scale", String.valueOf(scale),
                        "maptype", mapType,
                        "markers", "icon:" + markerUrl + '|' + shadow + latlonMap,
                        "sensor", "false");
                if (waypoints != null) {
                    params.addAll(waypoints);
                }
                final HttpResponse httpResponse = Network.getRequest(GOOGLE_STATICMAP_URL, params);

                if (httpResponse == null) {
                    Log.e("StaticMapsProvider.downloadMap: httpResponse is null");
                    return Observable.just(prefix);
                }
                final int statusCode = httpResponse.getStatusLine().getStatusCode();
                if (statusCode != 200) {
                    Log.d("StaticMapsProvider.downloadMap: httpResponseCode = " + statusCode);
                    if (statusCode == 403) {
                        last403 = System.currentTimeMillis();
                    }
                    return Observable.just(prefix);
                }
                final File file = getMapFile(geocode, prefix, true);
                if (LocalStorage.saveEntityToFile(httpResponse, file)) {
                    // Delete image if it has no contents
                    final long fileSize = file.length();
                    if (fileSize < MIN_MAP_IMAGE_BYTES) {
                        FileUtils.deleteIgnoringFailure(file);
                    }
                }
                return Observable.just(prefix);
            }
        }).subscribeOn(RxUtils.networkScheduler));
    }

    public static Observable<String> downloadMaps(final Geocache cache) {
        if ((!Settings.isStoreOfflineMaps() && !Settings.isStoreOfflineWpMaps()) || StringUtils.isBlank(cache.getGeocode())) {
            return Observable.empty();
        }
        // TODO Check if this is also OK, was width -25
        final Point displaySize = Compatibility.getDisplaySize();

        final List<Observable<String>> downloaders = new LinkedList<>();

        if (Settings.isStoreOfflineMaps() && cache.getCoords() != null) {
            downloaders.add(storeCachePreviewMap(cache));
            downloaders.add(storeCacheStaticMap(cache, displaySize.x, displaySize.y));
        }

        // clean old and download static maps for waypoints if one is missing
        if (Settings.isStoreOfflineWpMaps()) {
            for (final Waypoint waypoint : cache.getWaypoints()) {
                if (!hasAllStaticMapsForWaypoint(cache.getGeocode(), waypoint)) {
                    downloaders.add(refreshAllWpStaticMaps(cache, displaySize.x, displaySize.y));
                }
            }

        }

        return checkDownloadPermission(Observable.merge(downloaders));
    }

    /**
     * Deletes and download all Waypoints static maps.
     */
    private static Observable<String> refreshAllWpStaticMaps(final Geocache cache, final int width, final int height) {
        LocalStorage.deleteFilesWithPrefix(cache.getGeocode(), MAP_FILENAME_PREFIX + WAYPOINT_PREFIX);
        final List<Observable<String>> downloaders = new LinkedList<>();
        for (final Waypoint waypoint : cache.getWaypoints()) {
            downloaders.add(storeWaypointStaticMap(cache.getGeocode(), width, height, waypoint));
        }
        return checkDownloadPermission(Observable.merge(downloaders));
    }

    public static Observable<String> storeWaypointStaticMap(final Geocache cache, final Waypoint waypoint) {
        // TODO Check if this is also OK, was width -25
        final Point displaySize = Compatibility.getDisplaySize();
        return storeWaypointStaticMap(cache.getGeocode(), displaySize.x, displaySize.y, waypoint);
    }

    private static Observable<String> storeWaypointStaticMap(final String geocode, final int width, final int height, final Waypoint waypoint) {
        if (geocode == null) {
            Log.e("storeWaypointStaticMap - missing input parameter geocode");
            return Observable.empty();
        }
        if (waypoint == null) {
            Log.e("storeWaypointStaticMap - missing input parameter waypoint");
            return Observable.empty();
        }
        Geopoint coordinates = waypoint.getCoords();
        if (coordinates == null) {
            return Observable.empty();
        }
        final String wpLatlonMap = coordinates.format(Format.LAT_LON_DECDEGREE_COMMA);
        final String wpMarkerUrl = getWpMarkerUrl(waypoint);
        if (!hasAllStaticMapsForWaypoint(geocode, waypoint)) {
            // download map images in separate background thread for higher performance
            return downloadMaps(geocode, wpMarkerUrl, WAYPOINT_PREFIX + waypoint.getId() + '_' + waypoint.getStaticMapsHashcode() + "_", wpLatlonMap, width, height, null);
        }
        return Observable.empty();
    }

    public static Observable<String> storeCacheStaticMap(final Geocache cache) {
        // TODO Check if this is also OK, was width -25
        final Point displaySize = Compatibility.getDisplaySize();
        return storeCacheStaticMap(cache, displaySize.x, displaySize.y);
    }

    private static Observable<String> storeCacheStaticMap(final Geocache cache, final int width, final int height) {
        final String latlonMap = cache.getCoords().format(Format.LAT_LON_DECDEGREE_COMMA);
        final Parameters waypoints = new Parameters();
        for (final Waypoint waypoint : cache.getWaypoints()) {
            Geopoint coordinates = waypoint.getCoords();
            if (coordinates == null) {
                continue;
            }
            final String wpMarkerUrl = getWpMarkerUrl(waypoint);
            waypoints.put("markers", "icon:" + wpMarkerUrl + '|' + coordinates.format(Format.LAT_LON_DECDEGREE_COMMA));
        }
        // download map images in separate background thread for higher performance
        final String cacheMarkerUrl = getCacheMarkerUrl(cache);
        return downloadMaps(cache.getGeocode(), cacheMarkerUrl, "", latlonMap, width, height, waypoints);
    }

    public static Observable<String> storeCachePreviewMap(final Geocache cache) {
        final String latlonMap = cache.getCoords().format(Format.LAT_LON_DECDEGREE_COMMA);
        final Point displaySize = Compatibility.getDisplaySize();
        final String markerUrl = MARKERS_URL + "my_location_mdpi.png";
        return downloadMap(cache.getGeocode(), 15, ROADMAP, markerUrl, PREFIX_PREVIEW, "shadow:false|", latlonMap, displaySize.x, displaySize.y, null);
    }

    private static Observable<String> downloadMaps(final String geocode, final String markerUrl, final String prefix,
            final String latlonMap, final int width, final int height,
                                                   final Parameters waypoints) {
        return downloadDifferentZooms(geocode, markerUrl, prefix, latlonMap, width, height, waypoints);
    }

    private static String getCacheMarkerUrl(final Geocache cache) {
        final StringBuilder url = new StringBuilder(MARKERS_URL);
        url.append("marker_cache_").append(cache.getType().id);
        if (cache.isFound()) {
            url.append("_found");
        } else if (cache.isDisabled() || cache.isArchived()) {
            url.append("_disabled");
        }
        url.append(".png");
        return url.toString();
    }

    private static String getWpMarkerUrl(final Waypoint waypoint) {
        final String type = waypoint.getWaypointType() != null ? waypoint.getWaypointType().id : null;
        return MARKERS_URL + "marker_waypoint_" + type + ".png";
    }

    public static void removeWpStaticMaps(final Waypoint waypoint, final String geocode) {
        if (waypoint == null) {
            return;
        }
        final int waypointId = waypoint.getId();
        final int waypointMapHash = waypoint.getStaticMapsHashcode();
        for (int level = 1; level <= MAPS_LEVEL_MAX; level++) {
            final File mapFile = getMapFile(geocode, WAYPOINT_PREFIX + waypointId + "_" + waypointMapHash + '_' + level, false);
            if (!FileUtils.delete(mapFile)) {
                Log.e("StaticMapsProvider.removeWpStaticMaps failed for " + mapFile.getAbsolutePath());
            }
        }
    }

    /**
     * Check if at least one map file exists for the given cache.
     *
     * @return <code>true</code> if at least one map file exists; <code>false</code> otherwise
     */
    public static boolean hasStaticMap(@NonNull final Geocache cache) {
       final String geocode = cache.getGeocode();
        if (StringUtils.isBlank(geocode)) {
            return false;
        }
        for (int level = 1; level <= MAPS_LEVEL_MAX; level++) {
            final File mapFile = getMapFile(geocode, String.valueOf(level), false);
            if (mapFile.exists()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if at least one map file exists for the given geocode and waypoint ID.
     *
     * @return <code>true</code> if at least one map file exists; <code>false</code> otherwise
     */
    public static boolean hasStaticMapForWaypoint(final String geocode, final Waypoint waypoint) {
        final int waypointId = waypoint.getId();
        final int waypointMapHash = waypoint.getStaticMapsHashcode();
        for (int level = 1; level <= MAPS_LEVEL_MAX; level++) {
            final File mapFile = getMapFile(geocode, WAYPOINT_PREFIX + waypointId + "_" + waypointMapHash + "_" + level, false);
            if (mapFile.exists()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if all map files exist for the given geocode and waypoint ID.
     *
     * @return <code>true</code> if all map files exist; <code>false</code> otherwise
     */
    public static boolean hasAllStaticMapsForWaypoint(final String geocode, final Waypoint waypoint) {
        final int waypointId = waypoint.getId();
        final int waypointMapHash = waypoint.getStaticMapsHashcode();
        for (int level = 1; level <= MAPS_LEVEL_MAX; level++) {
            final File mapFile = getMapFile(geocode, WAYPOINT_PREFIX + waypointId + "_" + waypointMapHash + "_" + level, false);
            final boolean mapExists = mapFile.exists();
            if (!mapExists) {
                return false;
            }
        }
        return true;
    }

    public static Bitmap getPreviewMap(final Geocache cache) {
        return decodeFile(getMapFile(cache.getGeocode(), PREFIX_PREVIEW, false));
    }

    public static Bitmap getWaypointMap(final String geocode, final Waypoint waypoint, final int level) {
        final int waypointId = waypoint.getId();
        final int waypointMapHash = waypoint.getStaticMapsHashcode();
        return decodeFile(getMapFile(geocode, WAYPOINT_PREFIX + waypointId + "_" + waypointMapHash + "_" + level, false));
    }

    public static Bitmap getCacheMap(final String geocode, final int level) {
        return decodeFile(getMapFile(geocode, String.valueOf(level), false));
    }

    private static Bitmap decodeFile(final File mapFile) {
        // avoid exception in system log, if we got nothing back from Google.
        if (mapFile.exists()) {
            return BitmapFactory.decodeFile(mapFile.getPath());
        }
        return null;
    }
}
