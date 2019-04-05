package com.firebase.geofire.android.testing;

import android.content.Context;
import android.util.Log;

import com.firebase.geofire.android.GeoFire;
import com.firebase.geofire.android.GeoLocation;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.util.Date;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import androidx.test.core.app.ApplicationProvider;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * This is a JUnit rule that can be used for hooking up Geofire with a real database instance.
 */
public final class GeoFireTestingRule {

    static final long DEFAULT_TIMEOUT_SECONDS = 5;

    private static final String ALPHA_NUM_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";

    private static final String API_KEY= "**YOUR-API-key**";
    private static final String APP_ID= "com.firebase.sfvehiclesexample";
    private static final String PROJECT_ID= "**PROJECT-ID**";

    private DatabaseReference databaseReference;

    public final String databaseUrl;

    /** Timeout in seconds. */
    public final long timeout;

    public GeoFireTestingRule(final Context context,final String databaseUrl) {
        this(context,databaseUrl, DEFAULT_TIMEOUT_SECONDS);
    }

    public GeoFireTestingRule(final Context context, final String databaseUrl, final long timeout) {
        this.databaseUrl = databaseUrl;
        this.timeout = timeout;
        FirebaseApp app= FirebaseApp.initializeApp(context,
                new FirebaseOptions.Builder()
                        .setDatabaseUrl(this.databaseUrl)
                        .setApiKey(API_KEY)
                        .setApplicationId(APP_ID)
                        .setProjectId(PROJECT_ID)
                        .build(),"GeoFireTest"
        );
        this.databaseReference = FirebaseDatabase.getInstance(app).getReference();

        this.databaseReference.child("geofire").child("test").setValue(new Date().getTime());
        System.out.println(this.databaseReference.getPath().toString());
    }

    /** This will return you a new Geofire instance that can be used for testing. */
    public GeoFire newTestGeoFire() {
        return new GeoFire(databaseReference.child("geofire"));
    }

    /**
     * Sets a given location key from the latitude and longitude on the provided Geofire instance.
     * This operation will run asynchronously.
     */
    public void setLocation(GeoFire geoFire, String key, double latitude, double longitude) {
        setLocation(geoFire, key, latitude, longitude, false);
    }

    /**
     * Removes a location on the provided Geofire instance.
     * This operation will run asynchronously.
     */
    public void removeLocation(GeoFire geoFire, String key) {
        removeLocation(geoFire, key, false);
    }

    /** Sets the value on the given databaseReference and waits until the operation has successfully finished. */
    public void setValueAndWait(DatabaseReference databaseReference, Object value) {
        final SimpleFuture<DatabaseError> futureError = new SimpleFuture<DatabaseError>();
        databaseReference.setValue(value, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
                System.out.println(databaseError);
                futureError.put(databaseError);
            }
        });
        try {
            assertNull(futureError.get(timeout, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            fail("Timeout occurred!");
        }
    }

    /**
     * Sets a given location key from the latitude and longitude on the provided Geofire instance.
     * This operation will run asychronously or synchronously depending on the wait boolean.
     */
    public void setLocation(GeoFire geoFire, String key, double latitude, double longitude, boolean wait) {
        final SimpleFuture<DatabaseError> futureError = new SimpleFuture<DatabaseError>();
        geoFire.setLocation(key, new GeoLocation(latitude, longitude), new GeoFire.CompletionListener() {
            @Override
            public void onComplete(String key, DatabaseError error) {
                System.out.println(error);
                futureError.put(error);
            }
        });
        if (wait) {
            try {
                assertNull(futureError.get(timeout, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (TimeoutException e) {
                fail("Timeout occured!");
            }
        }
    }

    /**
     * Removes a location on the provided Geofire instance.
     * This operation will run asychronously or synchronously depending on the wait boolean.
     */
    public void removeLocation(GeoFire geoFire, String key, boolean wait) {
        final SimpleFuture<DatabaseError> futureError = new SimpleFuture<DatabaseError>();
        geoFire.removeLocation(key, new GeoFire.CompletionListener() {
            @Override
            public void onComplete(String key, DatabaseError error) {
                futureError.put(error);
            }
        });
        if (wait) {
            try {
                assertNull(futureError.get(timeout, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (TimeoutException e) {
                fail("Timeout occured!");
            }
        }
    }

    /** This lets you blockingly wait until the onGeoFireReady was fired on the provided Geofire instance. */
    public void waitForGeoFireReady(GeoFire geoFire) throws InterruptedException {
        final Semaphore semaphore = new Semaphore(0);
        geoFire.getDatabaseReference().addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                semaphore.release();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                fail("Firebase error: " + databaseError);
            }
        });

        assertTrue("Timeout occurred!", semaphore.tryAcquire(timeout, TimeUnit.SECONDS));
    }


    private static String randomAlphaNumericString(int length) {
        StringBuilder sb = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < length; i++ ) {
            sb.append(ALPHA_NUM_CHARS.charAt(random.nextInt(ALPHA_NUM_CHARS.length())));
        }
        return sb.toString();
    }
}
