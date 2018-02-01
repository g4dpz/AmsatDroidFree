package uk.me.g4dpz.HamSatDroid;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import uk.me.g4dpz.HamSatDroid.utils.IaruLocator;
import uk.me.g4dpz.satellite.GroundStationPosition;
import uk.me.g4dpz.satellite.InvalidTleException;
import uk.me.g4dpz.satellite.PassPredictor;
import uk.me.g4dpz.satellite.SatNotFoundException;
import uk.me.g4dpz.satellite.SatPassTime;
import uk.me.g4dpz.satellite.Satellite;
import uk.me.g4dpz.satellite.SatelliteFactory;
import uk.me.g4dpz.satellite.TLE;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

public class HamSatDroid extends ASDActivity implements OnGestureListener {

	/**
	 * 
	 */
	private static final String PERIOD = ".";
	/**
	 * 
	 */
	private static final String COMMA = ",";
	private static final String WEATHER_CELESTRAK = "WEATHER_CELESTRAK";
	private static final String CUBESAT_CELESTRAK = "CUBESAT_CELESTRAK";
	private static final String AMATEUR_CELESTRAK = "AMATEUR_CELESTRAK";
	private static final String RESOURCES_CELESTRAK = "RESOURCES_CELESTRAK";
	private static final String NEW_CELESTRAK = "NEW_CELESTRAK";
	private static final String AMATEUR_AMSAT = "AMATEUR_AMSAT";
	private static final String COLON_NL = ":\n";
	private static final String FOR_THE_NEXT = ", for the next ";
	private static final String PASS_PREDICTIONS_FOR_SATELLITE = "Pass predictions for satellite ";
	private static final String SLASH = "/";
	private static final String FOR_HOME_COORDINATES_LAT_LON_GRIDSQUARE = ", for home coordinates (lat/lon/gridsquare) ";
	private static final String PASS_HEADER = "passHeader";
	private static final String SELECTED_PASS_TIME = "selectedPassTime";
	private static final String SELECTED_SAT_INDEX = "selectedSatIndex";
	private static final String CANCEL = "Cancel";
	private static final String OK = "OK";
	private static final String FILE = "File ";
	private static final String ZERO_STRING = "0";
	private static final String HOME_LON = "homeLon";
	private static final String HOME_LAT = "homeLat";
	private static final String KEPS_UPDATED = "Keps updated!";
	private Context context;
	// Filenames and URLs
	private final String elemfile = android.os.Environment.getExternalStorageDirectory().getAbsolutePath()
			+ "/Download/nasabare.txt";
	private static final String BIN_PASS_FILENAME = "prefs.bin";
	private static final String BIN_ELEM_FILENAME = "elems.bin";
	private static final String ELEM_URL_AMATEUR_AMSAT = "http://www.amsat.org/amsat/ftp/keps/current/nasabare.txt";
	private static final String ELEM_URL_AMATEUR_CELESTRAK = "http://celestrak.com/NORAD/elements/amateur.txt";
	private static final String ELEM_URL_WEATHER_CELESTRAK = "http://celestrak.com/NORAD/elements/noaa.txt";
	private static final String ELEM_URL_CUBESAT_CELESTRAK = "http://celestrak.com/NORAD/elements/cubesat.txt";
	private static final String ELEM_URL_RESOURCES_CELESTRAK = "http://celestrak.com/NORAD/elements/resource.txt";
	private static final String ELEM_URL_NEW_CELESTRAK = "http://celestrak.com/NORAD/elements/tle-new.txt";
	// Various
	private static List<TLE> allSatElems;
	private int defaultSatIndex;
	private static List<SatPassTime> passes = new ArrayList<SatPassTime>();
	private String passHeader;
	private ArrayAdapter<SatPassTime> passAdapter;
	// Used by location methods
	private Boolean trackingLocation = false;
	private final LocationListener locationListener = new UserLocationListener();
	private AlertDialog locationProgressDialog;
	private AsyncTask<Integer, Integer, Boolean> updateKepsTask;
	private AlertDialog kepsProgressDialog;
	private View startTimeDialogLayout;

	private Calendar startTimeCalendar;

	private TextView timeDisplay;
	private Button setStartTime;

	private GestureDetector gestureScanner;
	private static final int SWIPE_MINDISTANCE = 120;
	private static final int SWIPE_THRESHOLD_VELOCITY = 200;

	private AlertDialog timePickerDialog;

	// For SkyView
	private static SatPassTime selectedPass;

	private static GroundStationPosition groundStation;

	private static PassPredictor passPredictor;

	private static Satellite selectedSatellite;

	private static String kepsSource;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		gestureScanner = new GestureDetector(this);

		// Load layout from XML
		setContentView(R.layout.pass_screen);

		// Save context for later
		context = this;

		// Set default preferences
		PreferenceManager.setDefaultValues(this, R.xml.preference_root, false);

		// Initialise passes header text view
		if (passHeader != null) {
			((TextView)findViewById(R.id.latlon_view)).setText(passHeader);
		}
		else {
			((TextView)findViewById(R.id.latlon_view)).setText("");
		}

		// Get saved element data from binary file
		restoreElemFromFile();
		if (allSatElems == null) {
			// Element data not found, load satellite data from file SD card
			loadElemFromFile();
			if (allSatElems == null) {
				// File could not be loaded from SD card, use default
				loadElemFromInternalFile();
				new AlertDialog.Builder(context)
						.setMessage(FILE + elemfile + " could not be found. Used default data (may be out of date).")
						.setPositiveButton(OK, null).show();

			}
			// Save element data to binary file
			saveElemToFile();
		}

		bindSatList();

		// Create passes adapter and bind it to ListView
		bindPassView();

		// Set passes calc button callback
		((Button)findViewById(R.id.CalculatePassButton)).setOnClickListener(new Button.OnClickListener() {
			@Override
			public void onClick(final View v) {
				final String selectedTime = (String)((Spinner)findViewById(R.id.TimeSelectorSpinner)).getSelectedItem();
				if (selectedTime.equals("Next Pass Only")) {
					new CalcPassTask().execute(0);
				}
				else if (selectedTime.equals("6 hours")) {
					new CalcPassTask().execute(6);
				}
				else if (selectedTime.equals("12 hours")) {
					new CalcPassTask().execute(12);
				}
				else if (selectedTime.equals("24 hours")) {
					new CalcPassTask().execute(24);
				}
			}
		});

		timeDisplay = (TextView)findViewById(R.id.StartTime);
		// startTimeCalendar = Calendar.getInstance();

		// add a click listener to the set start time button
		setStartTime = (Button)findViewById(R.id.SetStartTimeButton);
		setStartTime.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View v) {
				final LayoutInflater inflater = (LayoutInflater)context.getSystemService(LAYOUT_INFLATER_SERVICE);
				startTimeDialogLayout = inflater.inflate(R.layout.start_time_dialog,
						(ViewGroup)findViewById(R.id.START_TIME_DIALOG));
				if (startTimeCalendar != null) {
					final DatePicker datePicker = (DatePicker)startTimeDialogLayout.findViewById(R.id.DatePicker01);
					final TimePicker timePicker = (TimePicker)startTimeDialogLayout.findViewById(R.id.TimePicker01);
					timePicker.setCurrentHour(startTimeCalendar.getTime().getHours());
					timePicker.setCurrentMinute(startTimeCalendar.getTime().getMinutes());
					datePicker.updateDate(startTimeCalendar.get(Calendar.YEAR), startTimeCalendar.get(Calendar.MONTH),
							startTimeCalendar.get(Calendar.DAY_OF_MONTH));
				}
				timePickerDialog = new AlertDialog.Builder(context).create();
				timePickerDialog.setButton(DialogInterface.BUTTON_POSITIVE, "SET", new TimePickerDialogListener());
				timePickerDialog.setButton(DialogInterface.BUTTON_NEUTRAL, "Use Current", new TimePickerDialogListener());
				timePickerDialog.setButton(DialogInterface.BUTTON_NEGATIVE, CANCEL, new TimePickerDialogListener());
				timePickerDialog.setView(startTimeDialogLayout);
				timePickerDialog.show();

			}
		});

		// display the current time
		updateStartTimeDisplay();

	}

	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public void onStop() {
		super.onStop();
	}

	private class TimePickerDialogListener implements DialogInterface.OnClickListener {
		@Override
		public void onClick(final DialogInterface di, final int which) {
			switch (which) {
			case DialogInterface.BUTTON_POSITIVE:
				final DatePicker datePicker = (DatePicker)startTimeDialogLayout.findViewById(R.id.DatePicker01);
				final TimePicker timePicker = (TimePicker)startTimeDialogLayout.findViewById(R.id.TimePicker01);
				if (startTimeCalendar == null) {
					startTimeCalendar = Calendar.getInstance();
				}
				startTimeCalendar.set(datePicker.getYear(), datePicker.getMonth(), datePicker.getDayOfMonth(),
						timePicker.getCurrentHour(), timePicker.getCurrentMinute());
				updateStartTimeDisplay();
				break;
			case DialogInterface.BUTTON_NEUTRAL:
				// startTimeCalendar = c;
				startTimeCalendar = null;
				updateStartTimeDisplay();
				break;
			case DialogInterface.BUTTON_NEGATIVE:
				break;
			default:
				break;
			}
		}
	}

	private void updateStartTimeDisplay() {
		if (startTimeCalendar != null) {
			timeDisplay.setText("Starting: "
					+ DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(startTimeCalendar.getTime()));
		}
		else {
			timeDisplay.setText("Starting: now");
		}

	}

	private void bindSatList() {
		// Initialise Satellite List
		final Spinner s = (Spinner)findViewById(R.id.SatelliteSelectorSpinner);
		final ArrayAdapter<TLE> adapter = new ArrayAdapter<TLE>(context, android.R.layout.simple_spinner_item, allSatElems);
		adapter.setDropDownViewResource(android.R.layout.simple_dropdown_item_1line);
		s.setAdapter(adapter);
	}

	@Override
	protected void onResume() {
		super.onResume();

		final SharedPreferences prefs = getPreferences(0);

		// Restore spinner selections
		final int satIndex = prefs.getInt(SELECTED_SAT_INDEX, defaultSatIndex);
		final int timeIndex = prefs.getInt(SELECTED_PASS_TIME, 0);
		((Spinner)findViewById(R.id.SatelliteSelectorSpinner)).setSelection(satIndex);
		((Spinner)findViewById(R.id.TimeSelectorSpinner)).setSelection(timeIndex);

		// Restore passes header
		final String restoredText = prefs.getString(PASS_HEADER, null);
		if (restoredText != null) {
			((TextView)findViewById(R.id.latlon_view)).setText(restoredText);
		}

		// Retrieve passes
		restorePassesFromFile();
	}

	@Override
	protected void onPause() {
		super.onPause();

		final SharedPreferences.Editor editor = getPreferences(0).edit();

		// Save spinner selections
		editor.putInt(SELECTED_SAT_INDEX, ((Spinner)findViewById(R.id.SatelliteSelectorSpinner)).getSelectedItemPosition());
		editor.putInt(SELECTED_PASS_TIME, ((Spinner)findViewById(R.id.TimeSelectorSpinner)).getSelectedItemPosition());

		try {
			setObserver();
			setSatellite();
		}
		catch (IllegalArgumentException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		catch (InvalidTleException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		catch (SatNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		// Save passes
		if (passes != null) {
			FileOutputStream fos;
			try {
				fos = openFileOutput(BIN_PASS_FILENAME, MODE_PRIVATE);
				final ObjectOutputStream os = new ObjectOutputStream(fos);
				os.writeObject(passes);
				os.close();
			}
			catch (final FileNotFoundException e) {
				e.printStackTrace();
			}
			catch (final IOException e) {
				e.printStackTrace();
			}
		}

		// Save passes header
		if (passHeader != null) {
			editor.putString(PASS_HEADER, passHeader);
		}
		editor.commit();

		if (trackingLocation) {
			cancelLocationRequest();

		}

	}

	@SuppressWarnings("unchecked")
	void restorePassesFromFile() {
		FileInputStream fis;
		try {
			fis = openFileInput(BIN_PASS_FILENAME);
			final ObjectInputStream is = new ObjectInputStream(fis);
			final List<SatPassTime> passTmp = (ArrayList<SatPassTime>)is.readObject();
			if (passTmp != null) {
				HamSatDroid.setPasses(passTmp);
				bindPassView();
			}
			is.close();
		}
		catch (final FileNotFoundException e) {
			e.printStackTrace();
		}
		catch (final IOException e) {
			e.printStackTrace();
		}
		catch (final ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	public void bindPassView() {
		passAdapter = new ArrayAdapter<SatPassTime>(context, R.layout.pass_row, passes);
		((ListView)findViewById(R.id.PASS_LIST_VIEW)).setAdapter(passAdapter);
		((ListView)findViewById(R.id.PASS_LIST_VIEW)).setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(final AdapterView<?> parent, final View view, final int position, final long id) {
				// Get selected passes and start SkyView
				HamSatDroid.setSelectedPass((SatPassTime)parent.getItemAtPosition(position));
				final Intent launchSkyViewIntent = new Intent().setClass(context, SkyView.class);
				startActivity(launchSkyViewIntent);

				// Uri uri = Uri.parse("content://calendar/events");
				// ContentResolver cr = context.getContentResolver();
				// ContentValues values = new ContentValues();
				// values.put("eventTimezone", "EST");
				// values.put("calendarId", 1); // query
				// content://calendar/calendars for
				// more
				// values.put("title", "Sat Pass");
				// values.put("allDay", 0);
				// values.put("dtstart", dtstart); // long (start date
				// in ms)
				// values.put("dtend", dtend); // long (end date in ms)
				// values.put("description",
				// "Bring computers and alcohol");
				// values.put("eventLocation", "ZA WARULDO");
				// values.put("transparency", 0);
				// values.put("visibility", 0);
				// values.put("hasAlarm", 0);
				// cr.insert(uri, values);

			}
		});
	}

	private void recalcPass(final int hoursAhead) {

		// How long to go back/forward in time to find a passes (in hours)
		final int calcRange = 24;

		// Get home lat and lon from saved preferences
		setObserver();

		Calendar myCal;
		if (startTimeCalendar != null) {
			myCal = startTimeCalendar;
		}
		else {
			// Get current GMT date and time
			myCal = Calendar.getInstance();
		}

		Log.d("HamSatDroid time: ", myCal.toString());

		TLE myelem = null;

		// Calculate next satellite passes
		try {

			myelem = setSatellite();

			HamSatDroid.setPassPredictor(new PassPredictor(myelem, HamSatDroid.getGroundStation()));

			HamSatDroid.setPasses(getPassPredictor().getPasses(myCal.getTime(), hoursAhead, true));
		}
		catch (final InvalidTleException e) {
			passHeader = "ERROR: Bad Keplerian Elements";
		}
		catch (final SatNotFoundException e) {
			passHeader = "ERROR: Unknown Satellite";
		}

		// setPass(mysat.calcPass(dayGHAAref, dayGHAAref + (hoursAhead / 24.0),
		// (calcRange / 24.0), homeLat, homeLong));
		final NumberFormat formatter = NumberFormat.getNumberInstance();
		formatter.setMaximumFractionDigits(4);

		final double homeLat = HamSatDroid.getGroundStation().getLatitude();
		final double homeLong = HamSatDroid.getGroundStation().getLongitude();

		final IaruLocator locator = new IaruLocator(homeLat, homeLong);

		if (passes.get(0).getMaxEl() == 0) {
			passHeader = "No passes found for satellite " + myelem.getName() + FOR_HOME_COORDINATES_LAT_LON_GRIDSQUARE
					+ formatter.format(homeLat) + SLASH + formatter.format(homeLong) + SLASH + locator.toMaidenhead()
					+ " for the next " + calcRange + " hours.\n";
		}
		else {
			if (hoursAhead == 0) {
				passHeader = PASS_PREDICTIONS_FOR_SATELLITE + myelem.getName() + FOR_HOME_COORDINATES_LAT_LON_GRIDSQUARE
						+ formatter.format(homeLat) + SLASH + formatter.format(homeLong) + SLASH + locator.toMaidenhead()
						+ FOR_THE_NEXT + "passes only" + ", starting "
						+ DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(myCal.getTime()) + COLON_NL;
			}
			else {
				passHeader = PASS_PREDICTIONS_FOR_SATELLITE + myelem.getName() + FOR_HOME_COORDINATES_LAT_LON_GRIDSQUARE
						+ formatter.format(homeLat) + SLASH + formatter.format(homeLong) + SLASH + locator.toMaidenhead()
						+ FOR_THE_NEXT + hoursAhead + " hours, starting "
						+ DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT).format(myCal.getTime()) + COLON_NL;
			}
		}

	}

	/**
	 * @return
	 * @throws IllegalArgumentException
	 * @throws SatNotFoundException
	 * @throws InvalidTleException
	 */
	private TLE setSatellite() throws IllegalArgumentException, InvalidTleException, SatNotFoundException {
		final TLE myelem = (TLE)((Spinner)findViewById(R.id.SatelliteSelectorSpinner)).getSelectedItem();

		HamSatDroid.setSelectedSatellite(SatelliteFactory.createSatellite(myelem));

		HamSatDroid.setPassPredictor(new PassPredictor(myelem, HamSatDroid.getGroundStation()));

		return myelem;
	}

	/**
	 * @throws NumberFormatException
	 */
	@Override
	public void setObserver() throws NumberFormatException {
		final SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		String shomeLat = sharedPref.getString(HOME_LAT, ZERO_STRING);
		shomeLat = shomeLat.replace(COMMA, PERIOD);
		String shomeLon = sharedPref.getString(HOME_LON, ZERO_STRING);
		shomeLon = shomeLon.replace(COMMA, PERIOD);
		HamSatDroid.setGroundStation(new GroundStationPosition(Double.valueOf(shomeLat), Double.valueOf(shomeLon), 0));
	}

	private void setLocationPreference(final String provider) {
		if (!trackingLocation) {
			final LocationManager locMgr = (LocationManager)getSystemService(LOCATION_SERVICE);
			if (!locMgr.isProviderEnabled(provider)) {
				final Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
				// intent.addCategory(Intent.CATEGORYLAUNCHER);
				// intent.setComponent(new
				// ComponentName("com.android.settings","com.android.settings.SecuritySettings"));
				// intent.setFlags(Intent.FLAGACTIVITY_NEW_TASK);
				Toast.makeText(context, "Please enable " + provider + " location in systems settings screen", Toast.LENGTH_LONG)
						.show();
				startActivity(intent);
			}
			else {
				locMgr.requestLocationUpdates(provider, 0, 0, locationListener);
				trackingLocation = true;
				locationProgressDialog = new ProgressDialog(context);
				locationProgressDialog.setMessage("Retrieving location...");
				locationProgressDialog.setCancelable(false);
				locationProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, CANCEL, new LocationDialogListener());
				locationProgressDialog.show();
			}
		}
	}

	private class UserLocationListener implements LocationListener {

		private static final String LOCATION_SET_TO_LAT_LON = "IaruLocator set to lat/lon ";

		@Override
		public void onLocationChanged(final Location location) {
			final SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
			editor.putString(HOME_LAT, Double.toString(location.getLatitude()));
			editor.putString(HOME_LON, Double.toString(location.getLongitude()));
			editor.commit();
			String dialogString = "";
			if (location.hasAccuracy()) {
				dialogString = LOCATION_SET_TO_LAT_LON + Double.toString(location.getLatitude()) + SLASH
						+ Double.toString(location.getLongitude()) + ", location accuracy " + location.getAccuracy() + " meters";
			}
			else {
				dialogString = LOCATION_SET_TO_LAT_LON + Double.toString(location.getLatitude()) + SLASH
						+ Double.toString(location.getLongitude()) + ", unknown location accuracy";
			}
			new AlertDialog.Builder(context).setMessage(dialogString).setPositiveButton(OK, null).show();
			cancelLocationRequest();
		}

		@Override
		public void onProviderDisabled(final String provider) {
		}

		@Override
		public void onProviderEnabled(final String provider) {
		}

		@Override
		public void onStatusChanged(final String provider, final int status, final Bundle extras) {
		}

	}

	private class LocationDialogListener implements DialogInterface.OnClickListener {
		@Override
		public void onClick(final DialogInterface di, final int which) {
			cancelLocationRequest();
		}
	}

	private void cancelLocationRequest() {
		final LocationManager locMgr = (LocationManager)getSystemService(LOCATION_SERVICE);
		trackingLocation = false;
		locMgr.removeUpdates(locationListener);
		locationProgressDialog.dismiss();
	}

	private class CalcPassTask extends AsyncTask<Integer, Integer, Long> {

		@Override
		protected Long doInBackground(final Integer... timeOffsetArray) {
			// do not do any UI work here
			final Integer timeOffset = timeOffsetArray[0];
			recalcPass(timeOffset);
			return (long)0;
		}

		@Override
		protected void onProgressUpdate(final Integer... progress) {
		}

		@Override
		protected void onPostExecute(final Long result) {
			((ProgressBar)findViewById(R.id.PassProgressBar)).setVisibility(View.INVISIBLE);
			bindPassView();
			((TextView)findViewById(R.id.latlon_view)).setText(passHeader);
		}

		@Override
		protected void onPreExecute() {
			((ProgressBar)findViewById(R.id.PassProgressBar)).setVisibility(View.VISIBLE);
		}
	}

	@SuppressWarnings("unchecked")
	void restoreElemFromFile() {
		FileInputStream fis;
		try {
			fis = openFileInput(BIN_ELEM_FILENAME);
			final ObjectInputStream is = new ObjectInputStream(fis);
			final List<TLE> elemTmp = (ArrayList<TLE>)is.readObject();
			if (elemTmp != null) {
				HamSatDroid.setAllSatElems(elemTmp);
			}
			is.close();
		}
		catch (final IOException e) {
			e.printStackTrace();
		}
		catch (final ClassNotFoundException e) {
			e.printStackTrace();
		}
	}

	boolean loadElemFromFile() {
		boolean success = false;
		try {
			allSatElems = TLE.importSat(new FileInputStream(new File(elemfile)));
			success = true;
			bindSatList();
		}
		catch (final FileNotFoundException e) {
			e.printStackTrace();
		}
		catch (final IOException e) {
			e.printStackTrace();
		}
		return success;
	}

	boolean loadElemFromNetwork() {
		boolean success = false;
		URL url;

		try {
			final String kepSource = HamSatDroid.getKepsSource();
			if (AMATEUR_AMSAT.equals(kepSource)) {

				url = new URL(ELEM_URL_AMATEUR_AMSAT);
			}
			else if (AMATEUR_CELESTRAK.equals(kepSource)) {
				url = new URL(ELEM_URL_AMATEUR_CELESTRAK);
			}
			else if (WEATHER_CELESTRAK.equals(kepSource)) {
				url = new URL(ELEM_URL_WEATHER_CELESTRAK);
			}
			else if (CUBESAT_CELESTRAK.equals(kepSource)) {
				url = new URL(ELEM_URL_CUBESAT_CELESTRAK);
			}
			else if (RESOURCES_CELESTRAK.equals(kepSource)) {
				url = new URL(ELEM_URL_RESOURCES_CELESTRAK);
			}
			else if (NEW_CELESTRAK.equals(kepSource)) {
				url = new URL(ELEM_URL_NEW_CELESTRAK);
			}
			else {
				throw new IllegalArgumentException("Unknown keplerian source[" + kepSource + "]");
			}

			final List<TLE> tmpSatElems = TLE.importSat(url.openStream());
			if (tmpSatElems != null) {
				allSatElems = tmpSatElems;
				success = true;
			}
			allSatElems = tmpSatElems;
			success = true;
		}
		catch (final FileNotFoundException e) {
			e.printStackTrace();
		}
		catch (final IOException e) {
			e.printStackTrace();
		}
		catch (final IllegalArgumentException e) {
			e.printStackTrace();
		}
		return success;
	}

	void loadElemFromInternalFile() {
		try {
			allSatElems = TLE.importSat(getResources().openRawResource(R.raw.nasabare));
		}
		catch (final FileNotFoundException e) {
			e.printStackTrace();
		}
		catch (final IOException e) {
			e.printStackTrace();
		}
	}

	void saveElemToFile() {
		FileOutputStream fos;
		if (allSatElems != null) {
			try {
				fos = openFileOutput(BIN_ELEM_FILENAME, MODE_PRIVATE);
				final ObjectOutputStream os = new ObjectOutputStream(fos);
				os.writeObject(allSatElems);
				os.close();
			}
			catch (final FileNotFoundException e) {
				e.printStackTrace();
			}
			catch (final IOException e) {
				e.printStackTrace();
			}
		}

	}

	private class LoadElemNetTask extends AsyncTask<Integer, Integer, Boolean> {

		@Override
		protected Boolean doInBackground(final Integer... timeOffset) {
			// do not do any UI work here
			return loadElemFromNetwork();
		}

		@Override
		protected void onProgressUpdate(final Integer... progress) {
		}

		@Override
		protected void onCancelled() {
			kepsProgressDialog.dismiss();
		}

		@Override
		protected void onPostExecute(final Boolean result) {
			kepsProgressDialog.dismiss();
			if (result) {
				saveElemToFile();
				bindSatList();
				Toast.makeText(context, KEPS_UPDATED, Toast.LENGTH_LONG).show();
			}
			else {
				new AlertDialog.Builder(context).setMessage("Could not download file from network. Kept existing element data.")
						.setPositiveButton(OK, null).show();
			}
		}

		@Override
		protected void onPreExecute() {
			kepsProgressDialog = new ProgressDialog(context);
			kepsProgressDialog.setMessage("Downloading keps...");
			kepsProgressDialog.setCancelable(false);
			kepsProgressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, CANCEL, new NetKepsDialogListener());
			kepsProgressDialog.show();
		}
	}

	private class NetKepsDialogListener implements DialogInterface.OnClickListener {
		@Override
		public void onClick(final DialogInterface di, final int which) {
			updateKepsTask.cancel(true);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(final Menu menu) {

		// Inflate the menu XML resource.
		final MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main_menu, menu);

		final int androidVersion = Build.VERSION.SDK_INT;

		if (androidVersion > 17 /* Build.VERSION_CODES.JELLY_BEAN_MR1 */) {
			menu.findItem(R.id.MENU_LOAD_ELEM).setVisible(false);
		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(final MenuItem item) {
		switch (item.getItemId()) {
		case R.id.MENU_GET_GPS_LOCATION:
			setLocationPreference(LocationManager.GPS_PROVIDER);
			return true;
		case R.id.MENU_GET_NETWORK_LOCATION:
			setLocationPreference(LocationManager.NETWORK_PROVIDER);
			return true;
		case R.id.MENU_ENTER_LOCATION:
			final Intent launchPreferencesIntent = new Intent().setClass(this, PrefHandling.class);
			startActivity(launchPreferencesIntent);
			return true;
		case R.id.MENU_LOAD_ELEM:
			if (loadElemFromFile()) {
				saveElemToFile();
				Toast.makeText(context, KEPS_UPDATED, Toast.LENGTH_LONG).show();
			}
			else {
				new AlertDialog.Builder(context).setMessage(FILE + elemfile + " could not be found. Kept existing element data.")
						.setPositiveButton(OK, null).show();
			}
			return true;
		case R.id.MENU_DOWNLOAD_AMATEUR_AMSAT:
			setKepsSource(AMATEUR_AMSAT);
			updateKepsTask = new LoadElemNetTask();
			updateKepsTask.execute(0);
			return true;
		case R.id.MENU_DOWNLOAD_AMATEUR_CELESTRAK:
			setKepsSource(AMATEUR_CELESTRAK);
			updateKepsTask = new LoadElemNetTask();
			updateKepsTask.execute(0);
			return true;
		case R.id.MENU_DOWNLOAD_WEATHER_CELESTRAK:
			setKepsSource(WEATHER_CELESTRAK);
			updateKepsTask = new LoadElemNetTask();
			updateKepsTask.execute(0);
			return true;
		case R.id.MENU_DOWNLOAD_NEW_CELESTRAK:
			setKepsSource(NEW_CELESTRAK);
			updateKepsTask = new LoadElemNetTask();
			updateKepsTask.execute(0);
			return true;
		case R.id.MENU_DOWNLOAD_RESOURCES_CELESTRAK:
			setKepsSource(RESOURCES_CELESTRAK);
			updateKepsTask = new LoadElemNetTask();
			updateKepsTask.execute(0);
			return true;
		case R.id.MENU_DOWNLOAD_CUBESAT_CELESTRAK:
			setKepsSource(CUBESAT_CELESTRAK);
			updateKepsTask = new LoadElemNetTask();
			updateKepsTask.execute(0);
			return true;
		case R.id.MENU_HELP:
			// new AlertDialog.Builder(context)
			// .setMessage(getString(R.string.HelpString))
			// .setPositiveButton("Dismiss", null)
			// .show();
			final Intent myIntent = new Intent(Intent.ACTION_VIEW,
					Uri.parse("http://sites.google.com/site/hamsatdroid/installation-and-user-guide-1"));
			startActivity(myIntent);
			return true;
		case R.id.MENU_ABOUT:
			try {
				new AlertDialog.Builder(context)
						.setMessage(
								"Version " + this.getPackageManager().getPackageInfo(this.getPackageName(), 0).versionName
										+ "\n\n" + getString(R.string.AboutString)).setPositiveButton("Dismiss", null).show();
			}
			catch (final NameNotFoundException e) {
				e.printStackTrace();
			}
			return true;
		case R.id.LAUNCH_GROUND_VIEW:
			launchGroundView();
			return true;
		default:
			break;
		}
		return false;
	}

	private void launchGroundView() {
		final Intent launchGroundViewIntent = new Intent().setClass(this, GroundView.class);
		startActivity(launchGroundViewIntent);
	}

	@Override
	public boolean onTouchEvent(final MotionEvent me) {
		return gestureScanner.onTouchEvent(me);
	}

	@Override
	public boolean onDown(final MotionEvent arg0) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY) {
		if (e1.getX() - e2.getX() > SWIPE_MINDISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
			launchGroundView();
		}
		return true;
	}

	@Override
	public void onLongPress(final MotionEvent e) {
		// TODO Auto-generated method stub
	}

	@Override
	public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX, final float distanceY) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void onShowPress(final MotionEvent e) {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean onSingleTapUp(final MotionEvent e) {
		// TODO Auto-generated method stub
		return false;
	}

	/**
	 * @return the allSatElems
	 */
	public static final List<TLE> getAllSatElems() {
		return allSatElems;
	}

	/**
	 * @param allSatElems
	 *            the allSatElems to set
	 */
	public static final void setAllSatElems(final List<TLE> allSatElems) {
		HamSatDroid.allSatElems = allSatElems;
	}

	/**
	 * @param selectedPass
	 *            the selectedPass to set
	 */
	public static final void setSelectedPass(final SatPassTime selectedPass) {
		HamSatDroid.selectedPass = selectedPass;
	}

	/**
	 * @param passes
	 *            the passes to set
	 */
	public static final void setPasses(final List<SatPassTime> passes) {
		HamSatDroid.passes = passes;
	}

	/**
	 * @param groundStation
	 *            the groundStation to set
	 */
	public static final void setGroundStation(final GroundStationPosition groundStation) {
		HamSatDroid.groundStation = groundStation;
	}

	/**
	 * @param passPredictor
	 *            the passPredictor to set
	 */
	public static final void setPassPredictor(final PassPredictor passPredictor) {
		HamSatDroid.passPredictor = passPredictor;
	}

	private static void setSelectedSatellite(final Satellite satellite) {
		HamSatDroid.selectedSatellite = satellite;
	}

	/**
	 * @return the selectedPass
	 */
	public static SatPassTime getSelectedPass() {
		return selectedPass;
	}

	/**
	 * @return the groundStation
	 */
	public static GroundStationPosition getGroundStation() {
		return groundStation;
	}

	/**
	 * @return the selectedSatellite
	 */
	public static Satellite getSelectedSatellite() {
		return selectedSatellite;
	}

	/**
	 * @return the passPredictor
	 */
	public static PassPredictor getPassPredictor() {
		return passPredictor;
	}

	/**
	 * @return
	 * 
	 */
	public static List<SatPassTime> getPasses() {
		return HamSatDroid.passes;
	}

	/**
	 * @param kepsSource
	 *            the kepsSource to set
	 */
	public static void setKepsSource(String kepsSource) {
		HamSatDroid.kepsSource = kepsSource;
	}

	/**
	 * @return the kepsSource
	 */
	public static String getKepsSource() {
		return kepsSource;
	}
}
