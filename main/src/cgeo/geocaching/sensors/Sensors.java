package cgeo.geocaching.sensors;

import cgeo.geocaching.CgeoApplication;
import cgeo.geocaching.playservices.LocationProvider;
import cgeo.geocaching.sensors.GpsStatusProvider.Status;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.AngleUtils;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.RxUtils;

import org.eclipse.jdt.annotation.NonNull;

import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

import android.content.Context;

import java.util.concurrent.atomic.AtomicBoolean;

public class Sensors {

    private Observable<GeoData> geoDataObservable;
    private Observable<GeoData> geoDataObservableLowPower;
    private Observable<Float> directionObservable;
    private final Observable<Status> gpsStatusObservable;
    @NonNull private volatile GeoData currentGeo = GeoData.DUMMY_LOCATION;
    private volatile float currentDirection = 0.0f;
    private final boolean hasCompassCapabilities;
    private final CgeoApplication app = CgeoApplication.getInstance();

    private static class InstanceHolder {
        static final Sensors INSTANCE = new Sensors();
    }

    private final Action1<GeoData> rememberGeodataAction = new Action1<GeoData>() {
        @Override
        public void call(final GeoData geoData) {
            currentGeo = geoData;
        }
    };

    private final Action1<Float> onNextrememberDirectionAction = new Action1<Float>() {
        @Override
        public void call(final Float direction) {
            currentDirection = direction;
        }
    };

    private Sensors() {
        gpsStatusObservable = GpsStatusProvider.create(app).replay(1).refCount().onBackpressureLatest();
        final Context context = CgeoApplication.getInstance().getApplicationContext();
        hasCompassCapabilities = RotationProvider.hasRotationSensor(context) || OrientationProvider.hasOrientationSensor(context);
    }

    public static final Sensors getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private final Func1<Throwable, Observable<GeoData>> fallbackToGeodataProvider = new Func1<Throwable, Observable<GeoData>>() {
        @Override
        public Observable<GeoData> call(final Throwable throwable) {
            Log.e("Cannot use Play Services location provider, falling back to GeoDataProvider", throwable);
            Settings.setUseGooglePlayServices(false);
            return GeoDataProvider.create(app);
        }
    };

    public void setupGeoDataObservables(final boolean useGooglePlayServices, final boolean useLowPowerLocation) {
        if (useGooglePlayServices) {
            geoDataObservable = LocationProvider.getMostPrecise(app).onErrorResumeNext(fallbackToGeodataProvider).doOnNext(rememberGeodataAction);
            if (useLowPowerLocation) {
                geoDataObservableLowPower = LocationProvider.getLowPower(app).doOnNext(rememberGeodataAction).onErrorResumeNext(geoDataObservable);
            } else {
                geoDataObservableLowPower = geoDataObservable;
            }
        } else {
            geoDataObservable = RxUtils.rememberLast(GeoDataProvider.create(app).doOnNext(rememberGeodataAction), null);
            geoDataObservableLowPower = geoDataObservable;
        }
    }

    private static final Func1<GeoData, Float> GPS_TO_DIRECTION = new Func1<GeoData, Float>() {
        @Override
        public Float call(final GeoData geoData) {
            return AngleUtils.reverseDirectionNow(geoData.getBearing());
        }
    };

    public void setupDirectionObservable() {
        // If we have no magnetic sensor, there is no point in trying to setup any, we will always get the direction from the GPS.
        if (!hasCompassCapabilities) {
            Log.i("No compass capabilities, using only the GPS for the orientation");
            directionObservable = RxUtils.rememberLast(geoDataObservableLowPower.map(GPS_TO_DIRECTION).doOnNext(onNextrememberDirectionAction), 0f);
            return;
        }

        // Combine the magnetic direction observable with the GPS when compass is disabled or speed is high enough.
        final AtomicBoolean useDirectionFromGps = new AtomicBoolean(false);

        // On some devices, the orientation sensor (Xperia and S4 running Lollipop) seems to have been deprecated for real.
        // Use the rotation sensor if it is available unless the orientatation sensor is forced by the user.
        final Observable<Float> sensorDirectionObservable = Settings.useOrientationSensor(app) ? OrientationProvider.create(app) : RotationProvider.create(app);
        final Observable<Float> magneticDirectionObservable = sensorDirectionObservable.onErrorResumeNext(new Func1<Throwable, Observable<Float>>() {
            @Override
            public Observable<Float> call(final Throwable throwable) {
                Log.e("Device orientation is not available due to sensors error, disabling compass", throwable);
                Settings.setUseCompass(false);
                return Observable.<Float>never().startWith(0.0f);
            }
        }).filter(new Func1<Float, Boolean>() {
            @Override
            public Boolean call(final Float aFloat) {
                return Settings.isUseCompass() && !useDirectionFromGps.get();
            }
        });

        final Observable<Float> directionFromGpsObservable = geoDataObservableLowPower.filter(new Func1<GeoData, Boolean>() {
            @Override
            public Boolean call(final GeoData geoData) {
                final boolean useGps = geoData.getSpeed() > 5.0f;
                useDirectionFromGps.set(useGps);
                return useGps || !Settings.isUseCompass();
            }
        }).map(GPS_TO_DIRECTION);

        directionObservable = RxUtils.rememberLast(Observable.merge(magneticDirectionObservable, directionFromGpsObservable).doOnNext(onNextrememberDirectionAction), 0f);
    }

    public Observable<GeoData> geoDataObservable(final boolean lowPower) {
        return lowPower ? geoDataObservableLowPower : geoDataObservable;
    }

    public Observable<Float> directionObservable() {
        return directionObservable;
    }

    public Observable<Status> gpsStatusObservable() {
        return gpsStatusObservable;
    }

    @NonNull
    public GeoData currentGeo() {
        return currentGeo;
    }

    public float currentDirection() {
        return currentDirection;
    }

    public boolean hasCompassCapabilities() {
        return hasCompassCapabilities;
    }

}
