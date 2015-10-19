package cgeo.geocaching.playservices;

import cgeo.geocaching.sensors.GeoData;
import cgeo.geocaching.settings.Settings;
import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.RxUtils;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Action0;
import rx.functions.Func1;
import rx.observers.Subscribers;
import rx.subjects.ReplaySubject;
import rx.subscriptions.Subscriptions;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LocationProvider extends LocationCallback implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final LocationRequest LOCATION_REQUEST =
            LocationRequest.create().setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY).setInterval(2000).setFastestInterval(250);
    private static final LocationRequest LOCATION_REQUEST_LOW_POWER =
            LocationRequest.create().setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY).setInterval(10000).setFastestInterval(5000);
    private static final AtomicInteger mostPreciseCount = new AtomicInteger(0);
    private static final AtomicInteger lowPowerCount = new AtomicInteger(0);
    private static LocationProvider instance = null;
    private static final ReplaySubject<GeoData> subject = ReplaySubject.createWithSize(1);
    private final GoogleApiClient locationClient;

    private static synchronized LocationProvider getInstance(final Context context) {
        if (instance == null) {
            instance = new LocationProvider(context);
        }
        return instance;
    }

    private synchronized void updateRequest() {
        if (locationClient.isConnected()) {
            if (mostPreciseCount.get() > 0) {
                Log.d("LocationProvider: requesting most precise locations");
                LocationServices.FusedLocationApi.requestLocationUpdates(locationClient, LOCATION_REQUEST, this, RxUtils.looperCallbacksLooper);
            } else if (lowPowerCount.get() > 0) {
                Log.d("LocationProvider: requesting low-power locations");
                LocationServices.FusedLocationApi.requestLocationUpdates(locationClient, LOCATION_REQUEST_LOW_POWER, this, RxUtils.looperCallbacksLooper);
            } else {
                Log.d("LocationProvider: stopping location requests");
                LocationServices.FusedLocationApi.removeLocationUpdates(locationClient, this);
            }
        }
    }

    private static Observable<GeoData> get(final Context context, final AtomicInteger reference) {
        final LocationProvider instance = getInstance(context);
        return Observable.create(new OnSubscribe<GeoData>() {
            @Override
            public void call(final Subscriber<? super GeoData> subscriber) {
                if (reference.incrementAndGet() == 1) {
                    instance.updateRequest();
                }
                subscriber.add(Subscriptions.create(new Action0() {
                    @Override
                    public void call() {
                        RxUtils.looperCallbacksWorker.schedule(new Action0() {
                            @Override
                            public void call() {
                                if (reference.decrementAndGet() == 0) {
                                    instance.updateRequest();
                                }
                            }
                        }, 2500, TimeUnit.MILLISECONDS);
                    }
                }));
                subscriber.add(subject.subscribe(Subscribers.from(subscriber)));
            }
        });
    }

    public static Observable<GeoData> getMostPrecise(final Context context) {
        return get(context, mostPreciseCount).onBackpressureDrop();
    }

    public static Observable<GeoData> getLowPower(final Context context) {
        // Low-power location without the last stored location
        final Observable<GeoData> lowPowerObservable = get(context, lowPowerCount).skip(1);

        // High-power location without the last stored location
        final Observable<GeoData> highPowerObservable = get(context, mostPreciseCount).skip(1);

        // Use either low-power (with a 6 seconds head start) or high-power observables to obtain a location
        // no less precise than 20 meters.
        final Observable<GeoData> untilPreciseEnoughObservable =
                lowPowerObservable.mergeWith(highPowerObservable.delaySubscription(6, TimeUnit.SECONDS))
                        .takeUntil(new Func1<GeoData, Boolean>() {
                            @Override
                            public Boolean call(final GeoData geoData) {
                                return geoData.getAccuracy() <= 20;
                            }
                        });

        // After sending the last known location, try to get a precise location then use the low-power mode. If no
        // location information is given for 25 seconds (if the network location is turned off for example), get
        // back to the precise location and try again.
        return subject.first().concatWith(untilPreciseEnoughObservable.concatWith(lowPowerObservable).timeout(25, TimeUnit.SECONDS).retry()).onBackpressureDrop();
    }

    /**
     * Build a new geo data provider object.
     * <p/>
     * There is no need to instantiate more than one such object in an application, as observers can be added
     * at will.
     *
     * @param context the context used to retrieve the system services
     */
    private LocationProvider(final Context context) {
        final GeoData initialLocation = GeoData.getInitialLocation(context);
        subject.onNext(initialLocation != null ? initialLocation : GeoData.DUMMY_LOCATION);
        locationClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        locationClient.connect();
    }

    @Override
    public void onConnected(final Bundle bundle) {
        updateRequest();
    }

    @Override
    public void onConnectionFailed(final ConnectionResult connectionResult) {
        Log.e("cannot connect to Google Play location service: " + connectionResult);
        subject.onError(new RuntimeException("Connection failed: " + connectionResult));
    }

    @Override
    public void onLocationResult(final LocationResult result) {
        final Location location = result.getLastLocation();
        if (Settings.useLowPowerMode()) {
            location.setProvider(GeoData.LOW_POWER_PROVIDER);
        }
        subject.onNext(new GeoData(location));
    }

    @Override
    public void onConnectionSuspended(final int arg0) {
        // empty
    }
}
