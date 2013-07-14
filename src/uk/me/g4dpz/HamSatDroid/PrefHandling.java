package uk.me.g4dpz.HamSatDroid;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.text.InputType;

public class PrefHandling extends PreferenceActivity {

	private static final String HOME_LON = "homeLon";
	private static final String MAIN_PREFERENCES = "main_preferences";
	private static final String HOME_LAT = "homeLat";
	private static final String HOME_LOCATOR = "homeLocator";
	private static final String DEFAULT_LOCATOR = "JJ00aa";
	private static final String ZERO_STRING = "0";
	private Context context;
	private EditTextPreference latPref;
	private EditTextPreference lonPref;
	private EditTextPreference locatorPref;

	@Override
	protected void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		// Load the XML preferences file
		addPreferencesFromResource(R.xml.preference_root);

		latPref = new EditTextPreference(this);
		latPref.setKey(HOME_LAT);
		latPref.setTitle("Latitude");
		latPref.setDefaultValue(ZERO_STRING);
		latPref.setDialogMessage("Please enter latitude in degrees, must be between -90 and 90");
		((PreferenceScreen)findPreference(MAIN_PREFERENCES)).addPreference(latPref);

		lonPref = new EditTextPreference(this);
		lonPref.setKey(HOME_LON);
		lonPref.setTitle("Longitude");
		lonPref.setDefaultValue(ZERO_STRING);
		lonPref.setDialogMessage("Please enter longitude in degrees, must be between -180 and 180");
		((PreferenceScreen)findPreference(MAIN_PREFERENCES)).addPreference(lonPref);

		locatorPref = new EditTextPreference(this);
		locatorPref.setKey(HOME_LOCATOR);
		locatorPref.setTitle("IARU Locator");
		locatorPref.setDefaultValue(DEFAULT_LOCATOR);
		locatorPref.setDialogMessage("Please enter IARU (Maidenhead) Locator");
		((PreferenceScreen)findPreference(MAIN_PREFERENCES)).addPreference(locatorPref);

		// Save context for later
		context = this;

		latPref.getEditText().setInputType(
				InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);

		lonPref.getEditText().setInputType(
				InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);

		locatorPref.getEditText().setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);

		latPref.setOnPreferenceChangeListener(new LatLonLocationPrefChangeListener());
		lonPref.setOnPreferenceChangeListener(new LatLonLocationPrefChangeListener());
		locatorPref.setOnPreferenceChangeListener(new LatLonLocationPrefChangeListener());

	}

	public class LatLonLocationPrefChangeListener implements OnPreferenceChangeListener {

		/**
		 * 
		 */
		private static final String FORMAT_9_4F = "%9.4f";

		@Override
		public boolean onPreferenceChange(final Preference changedPref, final Object newValue) {
			boolean acceptInput = false;

			final String newString = (String)newValue;
			String dialogString = "";

			if (changedPref.getKey().equals(HOME_LAT)) {
				acceptInput = PrefHandling.validateStringValueWithinBounds(newString, 90);
				dialogString = "Invalid Latitude, set to 0";
			}
			if (changedPref.getKey().equals(HOME_LON)) {
				acceptInput = PrefHandling.validateStringValueWithinBounds(newString, 180);
				dialogString = "Invalid Longitude, set to 0";
			}
			if (changedPref.getKey().equals(HOME_LOCATOR)) {
				acceptInput = PrefHandling.validateLocator(newString);
				dialogString = "Invalid Locator, set to JJ00aa";
			}

			if (!acceptInput) {
				new AlertDialog.Builder(context).setMessage(dialogString).setPositiveButton("OK", null).show();
				if (changedPref.getKey().equals(HOME_LAT)) {
					latPref.setText(ZERO_STRING);
				}
				else if (changedPref.getKey().equals(HOME_LON)) {
					lonPref.setText(ZERO_STRING);
				}
				else if (changedPref.getKey().equals(HOME_LOCATOR)) {
					locatorPref.setText(DEFAULT_LOCATOR);
				}
			}
			else {
				if (changedPref.getKey().equals(HOME_LAT)) {
					final String longitude = changedPref.getSharedPreferences().getString(HOME_LON, ZERO_STRING);
					final String grid = HamSatDroid.decLatLonToGrid(Double.parseDouble(newString), Double.parseDouble(longitude));
					changedPref.getEditor().putString(HOME_LOCATOR, grid).commit();
					locatorPref.setText(grid);
				}
				else if (changedPref.getKey().equals(HOME_LON)) {
					final String latitude = changedPref.getSharedPreferences().getString(HOME_LAT, ZERO_STRING);
					final String grid = HamSatDroid.decLatLonToGrid(Double.parseDouble(latitude), Double.parseDouble(newString));
					changedPref.getEditor().putString(HOME_LOCATOR, grid).commit();
					locatorPref.setText(grid);
				}
				else if (changedPref.getKey().equals(HOME_LOCATOR)) {
					final String[] latLong = getLatLongFromLocator(newString);
					changedPref.getEditor().putString(HOME_LAT, latLong[0]).commit();
					latPref.setText(latLong[0]);
					changedPref.getEditor().putString(HOME_LON, latLong[1]).commit();
					lonPref.setText(latLong[1]);
				}
			}

			return acceptInput;
		}

		private String[] getLatLongFromLocator(final String grid) {

			final String[] latLong = new String[2];

			if (grid != null && !grid.isEmpty() && (grid.length() == 4 || grid.length() == 6)) {

				final String lcGrid = grid.toLowerCase(Locale.ENGLISH);

				final int rightField = lcGrid.charAt(0) - 'a';
				final int upField = lcGrid.charAt(1) - 'a';

				final int rightSquare = lcGrid.charAt(2) - '0';
				final int upSquare = lcGrid.charAt(3) - '0';

				int rightSub;
				int upSub;
				if (lcGrid.length() > 4) {
					rightSub = lcGrid.charAt(4) - 'a';
					upSub = lcGrid.charAt(5) - 'a';
				}
				else {
					rightSub = 11;
					upSub = 11;
				}

				latLong[0] = String.format(FORMAT_9_4F, 10.0 * upField + 1.0 * upSquare + 2.5 * upSub / 60.0 - 90.0);
				latLong[1] = String.format(FORMAT_9_4F, 20.0 * rightField + 2.0 * rightSquare + 5.0 * rightSub / 60.0 - 180.0);

			}
			else {
				throw new IllegalArgumentException("Locator was null, empty or incorrect length");
			}

			return latLong;
		}

	}

	private static boolean validateStringValueWithinBounds(final String value, final int bound) {
		boolean isValid = false;

		try {

			final double doubleValue = Double.valueOf(value);

			if ((doubleValue <= bound) && (doubleValue >= -bound)) {
				isValid = true;
			}
		}
		catch (final NumberFormatException e) {
			// NO-OP the string is invalid
		}

		return isValid;
	}

	private static boolean validateLocator(final String locatorString) {

		Pattern p = Pattern.compile("[a-rA-R]{2}[0-9]{2}[a-xA-X]{2}");
		Matcher m = p.matcher(locatorString);

		return m.find();
	}

}
