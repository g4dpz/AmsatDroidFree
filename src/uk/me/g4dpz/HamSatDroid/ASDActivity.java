package uk.me.g4dpz.HamSatDroid;

import uk.me.g4dpz.satellite.GroundStationPosition;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.google.analytics.tracking.android.EasyTracker;

/**
 * @author g4dpz
 * 
 */
abstract class ASDActivity extends Activity {

	private static final String ZERO_STRING = "0";
	private static final String HOME_LON = "homeLon";
	private static final String HOME_LAT = "homeLat";

	private double homeLat = 43;
	private double homeLon = -79;

	protected final double getHomeLat() {
		return homeLat;
	}

	protected final void setHomeLat(final double homeLat) {
		this.homeLat = homeLat;
	}

	protected final double getHomeLon() {
		return homeLon;
	}

	protected final void setHomeLon(final double homeLon) {
		this.homeLon = homeLon;
	}

	/**
	 * @throws NumberFormatException
	 */
	protected void setObserver() throws NumberFormatException {
		final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		final String shomeLat = sharedPref.getString(HOME_LAT, ZERO_STRING);
		final String shomeLon = sharedPref.getString(HOME_LON, ZERO_STRING);
		HamSatDroid.setGroundStation(new GroundStationPosition(Double.valueOf(shomeLat), Double.valueOf(shomeLon), 0));
	}

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	@Override
	protected void onStart() {
		super.onStart();
		EasyTracker.getInstance().activityStart(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		EasyTracker.getInstance().activityStop(this);
	}

}