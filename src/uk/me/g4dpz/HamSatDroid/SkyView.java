package uk.me.g4dpz.HamSatDroid;

import java.text.NumberFormat;
import java.util.Date;
import java.util.GregorianCalendar;

import uk.me.g4dpz.satellite.SatPos;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Bundle;
import android.os.Handler;
import android.view.GestureDetector;
import android.view.GestureDetector.OnGestureListener;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.FrameLayout;
import android.widget.TextView;

public class SkyView extends ASDActivity implements SensorEventListener, OnGestureListener {

	private static final float ORIENTATION = 0;
	private TrackView sView;
	private static Handler handler = new Handler();
	private long startTime;
	private int skyWidth = 480;
	private int skyHeight = 480;
	private GestureDetector gestureScanner;
	private static final int SWIPE_MINDISTANCE = 120;
	private static final int SWIPE_THRESHOLD_VELOCITY = 200;
	private static final double PI_DIV_BY_TWO = Math.PI / 2.0;
	private static final String DEG_UTF8 = "\u00B0";

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		gestureScanner = new GestureDetector(this);

		sView = new TrackView(this);
		sView.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT));
		setContentView(R.layout.sky_screen);
		((FrameLayout)findViewById(R.id.SKY_VIEW_FRAME)).addView(sView);

		// check if we have a groundstation, if not create one
		if (HamSatDroid.getGroundStation() == null) {
			setObserver();
		}

		// Get home lat/lon
		setHomeLat(HamSatDroid.getGroundStation().getLatitude());
		setHomeLon(HamSatDroid.getGroundStation().getLongitude());

		// Set UI refresh timer
		if (startTime == 0) {
			startTime = System.currentTimeMillis();
			final TimerRunnable timerRunnable = new TimerRunnable();
			handler.removeCallbacks(timerRunnable);
			handler.postDelayed(timerRunnable, 100);
		}

		// Set header and details
		((TextView)findViewById(R.id.SKY_VIEW_SATELLITE_NAME)).setText(HamSatDroid.getSelectedSatellite().getTLE().getName());
		final NumberFormat numberFormatter = NumberFormat.getNumberInstance();
		numberFormatter.setMaximumFractionDigits(4);
		((TextView)findViewById(R.id.SKY_VIEW_HOME_LOCATION)).setText("Home Lat/Lon: " + numberFormatter.format(getHomeLat())
				+ DEG_UTF8 + "/" + numberFormatter.format(getHomeLon()) + DEG_UTF8 + "\nHome Gridsquare: "
				+ HamSatDroid.decLatLonToGrid(getHomeLat(), getHomeLon()));
		((TextView)findViewById(R.id.SKY_VIEW_PASS_DETAILS)).setText("Pass Information \n"
				+ HamSatDroid.getSelectedPass().toString());

	}

	private class TimerRunnable implements Runnable {
		@Override
		public void run() {
			sView.invalidate();
			// Recalc current position & update UI
			((TextView)findViewById(R.id.SKY_VIEW_SAT_DETAILS)).setText("Satellite Location \n"
					+ HamSatDroid.getSelectedSatellite()
							.getPosition(HamSatDroid.getGroundStation(), new GregorianCalendar().getTime()).toShortString());
			handler.postDelayed(this, 5000);
		}
	}

	private class TrackView extends View {

		public TrackView(final Context context) {
			super(context);
		}

		@Override
		protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {

			if (View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.AT_MOST) {
				skyWidth = View.MeasureSpec.getSize(widthMeasureSpec);
			} else if (View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.EXACTLY) {
				skyWidth = View.MeasureSpec.getSize(widthMeasureSpec);
			} else if (View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.UNSPECIFIED) {
				skyWidth = 320;
			}

			if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.AT_MOST) {
				skyHeight = View.MeasureSpec.getSize(heightMeasureSpec);
			} else if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.EXACTLY) {
				skyHeight = View.MeasureSpec.getSize(heightMeasureSpec);
			} else if (View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) {
				skyHeight = 320;
			}

			setMeasuredDimension(skyWidth, skyHeight);
		}

		@Override
		protected void onDraw(final Canvas argCanvas) {
			final Canvas canvas = new Canvas();
			final Bitmap bitmap = Bitmap.createBitmap(skyWidth, skyHeight, Bitmap.Config.ARGB_8888);
			canvas.setBitmap(bitmap);
			final float centerX = 0;
			final float centerY = 0;

			float radius = 0;
			if (skyWidth < skyHeight) {
				radius = skyWidth * 0.35f;
			} else {
				radius = skyHeight * 0.35f;
			}

			final Matrix matrix = new Matrix();
			matrix.postRotate(-1 * ORIENTATION);
			matrix.postTranslate(skyWidth / 2.0f, skyHeight / 2.0f);
			canvas.setMatrix(matrix);

			final float scale = getContext().getResources().getDisplayMetrics().density;
			final float lineWidth = (int)(2 * scale + 0.5f);
			final float fontSize = (int)(18 * scale + 0.5f);

			// Draw frame circle
			// canvas.drawColor(Color.BLACK);
			final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			linePaint.setColor(Color.LTGRAY);
			linePaint.setStyle(Paint.Style.STROKE);
			linePaint.setStrokeWidth(lineWidth);
			canvas.drawCircle(centerX, centerY, radius, linePaint);
			canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, linePaint);
			canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, linePaint);
			canvas.drawCircle(centerX, centerY, radius / 2, linePaint);
			final Paint writingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			final Typeface typeFace = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL);
			writingPaint.setTypeface(typeFace);
			writingPaint.setTextAlign(Paint.Align.CENTER);

			writingPaint.setColor(Color.CYAN);
			writingPaint.setTextSize(fontSize);
			canvas.drawText("E", centerX + radius + fontSize / 2, centerY + fontSize / 2, writingPaint);
			canvas.drawText("W", centerX - radius - fontSize / 2, centerY + fontSize / 2, writingPaint);
			writingPaint.setColor(Color.LTGRAY);
			canvas.drawText("45" + DEG_UTF8, centerX + fontSize, centerY - radius / 2 - fontSize / 2, writingPaint);
			writingPaint.setColor(Color.BLUE);
			canvas.drawText("S", centerX, centerY + radius + fontSize, writingPaint);
			writingPaint.setColor(Color.RED);
			canvas.drawText("N", centerX, centerY - radius - fontSize / 2, writingPaint);

			// Get current GMT date and time
			// Draw satellite path
			linePaint.setColor(Color.YELLOW);
			linePaint.setStrokeWidth(lineWidth);
			writingPaint.setColor(Color.YELLOW);
			writingPaint.setTextSize(fontSize);
			final Date t = HamSatDroid.getSelectedPass().getStartTime();
			final Path satPath = new Path();
			float pathX = 0;
			float pathY = 0;
			SatPos pos = HamSatDroid.getSelectedSatellite().getPosition(HamSatDroid.getGroundStation(), t);
			while (t.before(HamSatDroid.getSelectedPass().getEndTime())) {
				pos = HamSatDroid.getSelectedSatellite().getPosition(HamSatDroid.getGroundStation(), t);
				pathX = centerX + azelToX(pos.getAzimuth(), pos.getElevation(), radius);
				pathY = centerY - azelToY(pos.getAzimuth(), pos.getElevation(), radius);
				if (t.compareTo(HamSatDroid.getSelectedPass().getStartTime()) == 0) {
					canvas.drawText("AOS", pathX, pathY, writingPaint);
					satPath.moveTo(pathX, pathY);
				} else {
					satPath.lineTo(pathX, pathY);
				}
				t.setTime(t.getTime() + 5000);
			}
			canvas.drawText("LOS", pathX, pathY, writingPaint);
			canvas.drawPath(satPath, linePaint);

			// Draw satellite
			final Paint solidPaint = new Paint();
			solidPaint.setColor(Color.RED);
			pos = HamSatDroid.getSelectedSatellite().getPosition(HamSatDroid.getGroundStation(),
					new GregorianCalendar().getTime());
			if (pos.getElevation() > 0) {
				// Draw satellite current position
				writingPaint.setColor(Color.RED);
				writingPaint.setTextSize(fontSize / 2);
				canvas.drawCircle(centerX + azelToX(pos.getAzimuth(), pos.getElevation(), radius),
						centerY - azelToY(pos.getAzimuth(), pos.getElevation(), radius), radius / 25f, solidPaint);
			}

			argCanvas.drawBitmap(bitmap, getLeft(), getTop(), null);
		}

		private float azelToX(final double azimuth, final double elevation, final double circleRadius) {
			final double r = circleRadius * (PI_DIV_BY_TWO - elevation) / PI_DIV_BY_TWO;
			return (float)(r * Math.cos(PI_DIV_BY_TWO - azimuth));
		}

		private float azelToY(final double azimuth, final double elevation, final double circleRadius) {
			final double r = circleRadius * (PI_DIV_BY_TWO - elevation) / PI_DIV_BY_TWO;
			return (float)(r * Math.sin(PI_DIV_BY_TWO - azimuth));
		}

	}

	@Override
	public void onSensorChanged(final SensorEvent event) {
		sView.invalidate();
	}

	@Override
	public void onAccuracyChanged(final Sensor sensor, final int accuracy) {
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
}
