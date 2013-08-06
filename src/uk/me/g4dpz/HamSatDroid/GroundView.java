package uk.me.g4dpz.HamSatDroid;

import java.text.NumberFormat;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;

import uk.me.g4dpz.satellite.InvalidTleException;
import uk.me.g4dpz.satellite.SatNotFoundException;
import uk.me.g4dpz.satellite.SatPos;
import uk.me.g4dpz.satellite.Satellite;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.Display;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

public class GroundView extends ASDActivity implements OnGestureListener {

	private static final String W_STRING = "W";
	private static final String E_STRING = "E";
	private static final String S_STRING = "S";
	private static final String N_STRING = "N";
	private static final int SWIPE_MINDISTANCE = 120;
	private static final int SWIPE_THRESHOLD_VELOCITY = 200;
	private static final String DEG_UTF8 = "\u00B0";

	private MapView mapView;
	private static Handler handler = new Handler();
	private long mStartTime;
	private GestureDetector gestureScanner;
	private Bitmap mapBitmap;
	private Bitmap satBitmap;
	private Bitmap obsBitmap;
	private Bitmap scaledMap;
	private Bitmap homePic;
	private Bitmap satPic;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mapBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.world);
		satBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.saticon);
		obsBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.home);

		gestureScanner = new GestureDetector(this);

		mapView = new MapView(this);
		setContentView(R.layout.map_screen_layout);
		((FrameLayout)findViewById(R.id.MAP_VIEW_FRAME)).addView(mapView);

		// check if we have a groundstation, if not create one
		if (HamSatDroid.getGroundStation() == null) {
			setObserver();
		}

		// Get home lat/lon
		setHomeLat(HamSatDroid.getGroundStation().getLatitude());
		setHomeLon(HamSatDroid.getGroundStation().getLongitude());

		// Set UI refresh timer
		if (mStartTime == 0) {
			mStartTime = System.currentTimeMillis();
			final TimerRunnable mTimerRunnable = new TimerRunnable();
			handler.removeCallbacks(mTimerRunnable);
			handler.postDelayed(mTimerRunnable, 10);
		}

		// Set header
		((TextView)findViewById(R.id.MAP_VIEW_SATELLITE_NAME)).setText(HamSatDroid.getSelectedSatellite().getTLE().getName());

		final NumberFormat mFormatter = NumberFormat.getNumberInstance();
		mFormatter.setMaximumFractionDigits(4);

		final TextView observerText = (TextView)findViewById(R.id.MAP_VIEW_OBS_LAT_LON);

		if (observerText != null) {
			observerText.setText(formatGeoText("Home Latitude", getHomeLat(), N_STRING, S_STRING)
					+ formatGeoText("Home Longitude", getHomeLon(), E_STRING, W_STRING) + "Home Gridsquare: "
					+ HamSatDroid.decLatLonToGrid(getHomeLat(), getHomeLon()));
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mapBitmap = null;
		satBitmap = null;
		obsBitmap = null;
		scaledMap = null;
		homePic = null;
		satPic = null;
	}

	private class TimerRunnable implements Runnable {
		@Override
		public void run() {
			mapView.invalidate();
			// Recalc current position & update UI
			final SatPos pos = HamSatDroid.getSelectedSatellite().getPosition(HamSatDroid.getGroundStation(),
					new GregorianCalendar().getTime());
			final NumberFormat mFormatter = NumberFormat.getNumberInstance();
			mFormatter.setMaximumFractionDigits(2);

			double longitude = GroundView.radToDeg(pos.getLongitude());

			if (longitude > 180.0) {
				longitude = -1.0 * (360.0 - longitude);
			}

			final TextView latLongText = (TextView)findViewById(R.id.MAP_VIEW_SAT_LAT_LON);

			if (latLongText != null) {
				latLongText.setText(formatGeoText("Satellite Latitude", GroundView.radToDeg(pos.getLatitude()), N_STRING,
						S_STRING)
						+ formatGeoText("Satellite Longitude", longitude, E_STRING, W_STRING)
						+ String.format("Range: %5.0f KM", pos.getRange()));
			}

			handler.postDelayed(this, 1000);
		}

	}

	private String formatGeoText(final String elementName, final double value, final String posStr, final String negStr) {
		return String.format(Locale.ENGLISH, "%s: %5.1f%s %s\n", elementName, Math.abs(value), DEG_UTF8, (value >= 0) ? posStr
				: negStr);
	}

	private class MapView extends View {

		private final Paint passLinePaint;
		private final Paint trackLinePaint;
		private final Paint writingPaint;
		private final Paint footprintLinePaint;
		private final int displayHeight;
		private final int displayWidth;
		private final Display display;
		private int mapWidth;
		private int mapHeight;

		public MapView(final Context context) {
			super(context);

			passLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			passLinePaint.setColor(Color.YELLOW);
			passLinePaint.setStyle(Paint.Style.STROKE);
			passLinePaint.setPathEffect(new DashPathEffect(new float[] { 5, 5 }, 0));

			trackLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			trackLinePaint.setColor(Color.YELLOW);
			trackLinePaint.setStyle(Paint.Style.STROKE);

			writingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

			footprintLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			footprintLinePaint.setColor(Color.RED);
			footprintLinePaint.setStyle(Paint.Style.STROKE);

			display = ((WindowManager)getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();

			displayHeight = display.getHeight();
			displayWidth = display.getWidth();

			mapWidth = mapBitmap.getWidth();
			mapHeight = mapBitmap.getHeight();
			double scale = 1.0;

			// we process differently if it's portrait or landscape
			if (displayHeight > displayWidth) {
				if (mapWidth > displayWidth) {
					scale = (double)displayWidth / (double)mapWidth;
					mapWidth *= scale;
					mapHeight *= scale;
				}
				if (mapHeight > displayHeight) {
					scale = scale * ((double)displayHeight / (double)mapHeight);
					mapWidth *= scale;
					mapHeight *= scale;
				}
			}
			else {
				if (mapHeight > displayHeight) {
					scale = scale * ((double)displayHeight / (double)mapHeight);
					mapWidth *= scale;
					mapHeight *= scale;
				}
				if (mapWidth > displayWidth) {
					scale = (double)displayWidth / (double)mapWidth;
					mapWidth *= scale;
					mapHeight *= scale;
				}
			}

			mapWidth = (int)Math.round(Math.floor(mapWidth));
			mapHeight = (int)Math.round(Math.floor(mapHeight));

			scaledMap = Bitmap.createScaledBitmap(mapBitmap, mapWidth, mapHeight, false);

			homePic = Bitmap.createScaledBitmap(obsBitmap, (int)Math.round(Math.floor(obsBitmap.getWidth() * scale)),
					(int)Math.round(Math.floor(obsBitmap.getHeight() * scale)), false);

			passLinePaint.setStrokeWidth((float)(2 * scale));
			trackLinePaint.setStrokeWidth((float)(2 * scale));
			footprintLinePaint.setStrokeWidth((float)(2 * scale));

			satPic = Bitmap.createScaledBitmap(satBitmap, (int)Math.round(Math.floor(satBitmap.getWidth() * scale)),
					(int)Math.round(Math.floor(satBitmap.getHeight() * scale)), false);

			writingPaint.setTextSize((float)(18 * scale));
			final Typeface mType = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL);
			writingPaint.setTypeface(mType);
			writingPaint.setTextAlign(Paint.Align.CENTER);
			writingPaint.setColor(Color.BLACK);

		}

		@Override
		protected void onDraw(final Canvas canvas) {
			canvas.drawBitmap(scaledMap, getLeft(), getTop(), null);

			final int homeTop = (int)Math.round((90.0 - getHomeLat()) * (mapHeight / 180.0) - homePic.getHeight() / 2.0);
			final int homeLeft = (int)Math.round((getHomeLon() + 180.0) * (mapWidth / 360.0) - homePic.getWidth() / 2.0);
			canvas.drawBitmap(homePic, homeLeft + getLeft(), homeTop + getTop(), null);

			try {

				final int orbitMinutes = (int)(24 * 60 / HamSatDroid.getSelectedSatellite().getTLE().getMeanmo());

				final Date timeNow = new GregorianCalendar().getTime();

				// calculate the positions for the current orbit
				List<SatPos> positions = HamSatDroid.getPassPredictor().getPositions(timeNow, 30, 0, orbitMinutes);

				drawTrack(canvas, positions);

				final Date endOfOrbit = new Date(timeNow.getTime() + orbitMinutes * 60 * 1000);

				positions = HamSatDroid.getPassPredictor().getPositions(endOfOrbit, 30, 0, orbitMinutes * 2);

				drawTrack(canvas, positions);

				// Get current GMT date and time and calc satellite position
				final SatPos pos = HamSatDroid.getSelectedSatellite().getPosition(HamSatDroid.getGroundStation(),
						new GregorianCalendar().getTime());

				// Draw footprint
				drawFootprint(canvas, mapWidth, mapHeight, footprintLinePaint, pos);

				// Draw satellite

				double longitude = GroundView.radToDeg(pos.getLongitude());

				if (longitude > 180.0) {
					longitude -= 180.0;
				}
				else {
					longitude += 180.0;
				}

				final int satTop = (int)Math.round((90.0 - GroundView.radToDeg(pos.getLatitude())) * (mapHeight / 180.0)
						- satPic.getHeight() / 2.0);
				final int satLeft = (int)Math.round(longitude * (mapWidth / 360.0) - satPic.getWidth() / 2.0);
				canvas.drawBitmap(satPic, satLeft + getLeft(), satTop + getTop(), null);

			}
			catch (final InvalidTleException e) {
				e.printStackTrace();
			}
			catch (final SatNotFoundException e) {
				e.printStackTrace();
			}
		}

		private void drawFootprint(Canvas canvas, int mapWidth, int mapHeight, final Paint footprintLinePaint, SatPos pos)
				throws InvalidTleException, SatNotFoundException {

			final double[][] points = pos.getRangeCircle();

			final Path trackPath = new Path();

			float pathX = 0;
			float pathY = 0;
			float oldPathX = 181;

			for (int i = 0; i < 360; i++) {
				pathX = (float)points[i][1];

				if (pathX <= 180.0) {
					pathX += 180;
				}
				else {
					pathX -= 180;
				}
				pathX *= mapWidth / 360.0;

				pathY = (int)Math.round((90.0 - points[i][0]) * (mapHeight / 180.0));
				if (i == 0 || Math.abs(pathX - oldPathX) > 180) {
					trackPath.moveTo(pathX, pathY);
				}
				else {
					trackPath.lineTo(pathX, pathY);
				}

				oldPathX = pathX;
			}
			canvas.drawPath(trackPath, footprintLinePaint);
		}

		private void drawTrack(final Canvas canvas, final List<SatPos> positions) throws InvalidTleException,
				SatNotFoundException {

			final Path trackPath = new Path();

			float pathX = 0;
			float pathY = 0;
			float oldPathX = 181;

			for (int i = 0; i < positions.size(); i++) {
				pathX = (float)GroundView.radToDeg(positions.get(i).getLongitude());

				if (pathX <= 180.0) {
					pathX += 180;
				}
				else {
					pathX -= 180;
				}
				pathX *= mapWidth / 360.0;

				pathY = (int)Math.round((90.0 - GroundView.radToDeg(positions.get(i).getLatitude())) * (mapHeight / 180.0));
				if (i == 0 || Math.abs(pathX - oldPathX) > 180) {
					trackPath.moveTo(pathX, pathY);
				}
				else {
					trackPath.lineTo(pathX, pathY);
				}

				oldPathX = pathX;
			}
			canvas.drawPath(trackPath, trackLinePaint);
		}

	}

	@Override
	public boolean onTouchEvent(final MotionEvent me) {
		return gestureScanner.onTouchEvent(me);
	}

	@Override
	public boolean onFling(final MotionEvent e1, final MotionEvent e2, final float velocityX, final float velocityY) {
		if (e2.getX() - e1.getX() > SWIPE_MINDISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
			finish();
		}
		return true;
	}

	@Override
	public void onLongPress(final MotionEvent e) {
	}

	@Override
	public boolean onScroll(final MotionEvent e1, final MotionEvent e2, final float distanceX, final float distanceY) {
		return false;
	}

	@Override
	public void onShowPress(final MotionEvent e) {
	}

	@Override
	public boolean onSingleTapUp(final MotionEvent e) {
		return false;
	}

	@Override
	public boolean onDown(final MotionEvent e) {
		return false;
	}

	/**
	 * @param pos
	 * @return
	 */
	private static double radToDeg(final double value) {
		return value / Satellite.TWO_PI * 360.0;
	}

}
