package cgeo.geocaching.sensors;

import cgeo.geocaching.utils.Log;
import cgeo.geocaching.utils.RxUtils;

import rx.Observable;
import rx.Observable.OnSubscribe;
import rx.Subscriber;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;

import android.annotation.TargetApi;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class RotationProvider {

    /**
     * On some Samsung devices, {@link SensorManager#getRotationMatrixFromVector} throws an exception if the rotation
     * vector has more than 4 elements.
     * <p/>
     * This will be detected and remembered after the first occurrence of the exception. Concurrent access
     * is not a problem as this variable can only go from {@code false} to {@code true} and being {@code false}
     * instead of {@code true} is innocuous and will be changed immediately when needed.
     *
     * @see <a href="http://stackoverflow.com/a/22138449">this Stack Overflow answer</a>
     */
    private static boolean isTruncationNeeded = false;

    private RotationProvider() {
        // Utility class, not to be instantiated
    }

    @TargetApi(19)
    public static Observable<Float> create(final Context context) {
        final SensorManager sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        final Sensor rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rotationSensor == null) {
            Log.w("RotationProvider: no rotation sensor on this device");
            return Observable.error(new RuntimeException("no rotation sensor"));
        }
        Log.d("RotationProvider: sensor found");
        final Observable<Float> observable = Observable.create(new OnSubscribe<Float>() {

            @Override
            public void call(final Subscriber<? super Float> subscriber) {
                final SensorEventListener listener = new SensorEventListener() {

                    private final float[] rotationMatrix = new float[16];
                    private final float[] orientation = new float[4];
                    private final float[] values = new float[4];

                    @Override
                    public void onSensorChanged(final SensorEvent event) {
                        if (isTruncationNeeded) {
                            // Since only the four first elements are used (and accepted), we truncate the vector.
                            System.arraycopy(event.values, 0, values, 0, 4);
                            SensorManager.getRotationMatrixFromVector(rotationMatrix, values);
                        } else {
                            try {
                                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                            } catch (final IllegalArgumentException ignored) {
                                Log.d("installing workaround for mismatched number of values in rotation vector");
                                // Install workaround and retry
                                isTruncationNeeded = true;
                                onSensorChanged(event);
                                return;
                            }
                        }
                        SensorManager.getOrientation(rotationMatrix, orientation);
                        subscriber.onNext((float) (orientation[0] * 180 / Math.PI));
                    }

                    @Override
                    public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
                    }

                };
                Log.d("RotationProvider: registering listener");
                sensorManager.registerListener(listener, rotationSensor, SensorManager.SENSOR_DELAY_NORMAL);
                subscriber.add(Subscriptions.create(new Action0() {
                    @Override
                    public void call() {
                        RxUtils.looperCallbacksWorker.schedule(new Action0() {
                            @Override
                            public void call() {
                                Log.d("RotationProvider: unregistering listener");
                                sensorManager.unregisterListener(listener, rotationSensor);
                            }
                        });
                    }
                }));
            }
        });
        return observable.subscribeOn(RxUtils.looperCallbacksScheduler).share().onBackpressureLatest();
    }

    public static boolean hasRotationSensor(final Context context) {
        return ((SensorManager) context.getSystemService(Context.SENSOR_SERVICE)).getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null;
    }

}
