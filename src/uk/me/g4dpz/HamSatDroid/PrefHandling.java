package uk.me.g4dpz.HamSatDroid;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import uk.me.g4dpz.HamSatDroid.utils.IaruLocator;
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
		private static final String FORMAT_10_5F = "%10.5f";

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
					final IaruLocator locator = new IaruLocator(Double.parseDouble(newString), Double.parseDouble(longitude));
					String grid = locator.toMaidenhead();
					changedPref.getEditor().putString(HOME_LOCATOR, grid).commit();
					locatorPref.setText(grid);
				}
				else if (changedPref.getKey().equals(HOME_LON)) {
					final String latitude = changedPref.getSharedPreferences().getString(HOME_LAT, ZERO_STRING);
					final IaruLocator locator = new IaruLocator(Double.parseDouble(latitude), Double.parseDouble(newString));
					String grid = locator.toMaidenhead();
					changedPref.getEditor().putString(HOME_LOCATOR, grid).commit();
					locatorPref.setText(grid);
				}
				else if (changedPref.getKey().equals(HOME_LOCATOR)) {

					final IaruLocator iaruLocator = new IaruLocator(newString);
					final String latitude = String.format(FORMAT_10_5F, iaruLocator.getLatitude().toDegrees());
					final String longitude = String.format(FORMAT_10_5F, iaruLocator.getLongitude().toDegrees());

					changedPref.getEditor().putString(HOME_LAT, latitude).commit();
					latPref.setText(latitude);
					changedPref.getEditor().putString(HOME_LON, longitude).commit();
					lonPref.setText(longitude);
				}
			}

			return acceptInput;
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

		final Pattern p = Pattern.compile("[a-rA-R]{2}[0-9]{2}[a-xA-X]{2}");
		final Matcher m = p.matcher(locatorString);

		return m.find();
	}

}
