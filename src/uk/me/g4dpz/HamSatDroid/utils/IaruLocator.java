package uk.me.g4dpz.HamSatDroid.utils;

public class IaruLocator {

	/**
	 * Average earth radius in kilometers, IUGG definition.
	 */
	private static final double AVG_EARTH_RADIUS_KM = 6371.009;
	/**
	 * Average earth radius in statute miles, IUGG definition.
	 */
	private static final double AVG_EARTH_RADIUS_SM = 3958.761;
	/**
	 * Average earth radius in nautical miles, IUGG definition.
	 */
	private static final double AVG_EARTH_RADIUS_NM = 3440.069;

	/**
	 * latitude of location.
	 */
	private Latitude latitude;
	/**
	 * longitude of location.
	 */
	private Longitude longitude;

	/**
	 * No argument constructor.
	 *
	 */
	public IaruLocator() {
		this.latitude = new Latitude();
		this.longitude = new Longitude();
	}

	/**
	 * @param latitudeIn
	 *            initial latitude
	 * @param longitudeIn
	 *            initial longitude
	 */
	public IaruLocator(final Latitude latitudeIn, final Longitude longitudeIn) {
		this.latitude = latitudeIn;
		this.longitude = longitudeIn;
	}

	/**
	 * @param latitudeIn
	 *            initial latitude
	 * @param longitudeIn
	 *            initial longitude
	 */
	public IaruLocator(final double latitudeIn, final double longitudeIn) {
		this.latitude = Latitude.fromDegrees(latitudeIn);
		this.longitude = Longitude.fromDegrees(longitudeIn);
	}

	/**
	 * @param maidenhead
	 *            used construct location from maidenhead locator string
	 */
	public IaruLocator(final String maidenhead) {
		this.latitude = extractLat(maidenhead);
		this.longitude = extractLon(maidenhead);
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj instanceof IaruLocator) {
			return ((IaruLocator)obj).hashCode() == this.hashCode();
		}
		return false;
	}

	@Override
	public int hashCode() {
		int hash = 1;
		hash = hash * 17 + this.latitude.hashCode();
		hash = hash * 31 + this.longitude.hashCode();
		return hash;
	}

	/**
	 * @return maidenhead locator string
	 */
	public String toMaidenhead() {
		return toMaidenhead(this.latitude.toDegrees(), this.longitude.toDegrees());
	}

	/**
	 * @param latitudeIn
	 *            latitude component of locator string
	 * @param longitudeIn
	 *            longitude component of locator string
	 * @return maidenhead locator string
	 */
	public static String toMaidenhead(final double latitudeIn, final double longitudeIn) {

		double longitude = longitudeIn + 180;
		longitude /= 2;
		char lonFirst = (char)('A' + (longitude / 10));
		char lonSecond = (char)('0' + longitude % 10);
		char lonThird = (char)('A' + (longitude % 1) * 24);

		double latitude = latitudeIn + 90;
		char latFirst = (char)('A' + (latitude / 10));
		char latSecond = (char)('0' + latitude % 10);
		char latThird = (char)('A' + (latitude % 1) * 24);

		StringBuilder sb = new StringBuilder();
		sb.append(lonFirst);
		sb.append(latFirst);
		sb.append(lonSecond);
		sb.append(latSecond);
		sb.append(("" + lonThird).toLowerCase());
		sb.append(("" + latThird).toLowerCase());

		return sb.toString();
	}

	/**
	 * @param maidenheadIn
	 *            locator string to be converted
	 * @return latitude
	 */
	public static Latitude extractLat(final String maidenheadIn) {
		String maidenhead = maidenheadIn.toUpperCase();
		double latitude = -90 + 10 * (maidenhead.charAt(1) - 'A') + (maidenhead.charAt(3) - '0') + 2.5 / 60
				* (maidenhead.charAt(5) - 'A') + 2.5 / 60 / 2;
		return Latitude.fromDegrees(latitude);
	}

	/**
	 * @param maidenheadIn
	 *            locator string to be converted
	 * @return longitude
	 */
	public static Longitude extractLon(final String maidenheadIn) {
		String maidenhead = maidenheadIn.toUpperCase();
		double longitude = -180 + 20 * (maidenhead.charAt(0) - 'A') + 2 * (maidenhead.charAt(2) - '0') + 5.0 / 60
				* (maidenhead.charAt(4) - 'A') + 5.0 / 60 / 2;
		return Longitude.fromDegrees(longitude);
	}

	/**
	 * @return latitude
	 */
	public Latitude getLatitude() {
		return latitude;
	}

	/**
	 * @return longitude
	 */
	public Longitude getLongitude() {
		return longitude;
	}

	/**
	 * @param latitudeIn
	 *            north/south component of location
	 */
	public void setLatitude(final Latitude latitudeIn) {
		this.latitude = latitudeIn;
	}

	/**
	 * @param longitudeIn
	 *            east/west component of location
	 */
	public void setLongitude(final Longitude longitudeIn) {
		this.longitude = longitudeIn;
	}

	/**
	 * @param loc2
	 *            second location
	 * @return great circle distance in miles
	 */
	public double getDistanceMi(final IaruLocator loc2) {
		return getDistanceMi(this, loc2);
	}

	/**
	 * @param loc2
	 *            second location
	 * @return great circle distance in kilometers
	 */
	public double getDistanceKm(final IaruLocator loc2) {
		return getDistanceKm(this, loc2);
	}

	/**
	 * @param loc2
	 *            second location
	 * @return great circle distance in nautical miles
	 */
	public double getDistanceNm(final IaruLocator loc2) {
		return getDistanceNm(this, loc2);
	}

	/**
	 * @param loc1
	 *            first location
	 * @param loc2
	 *            second location
	 * @return great circle distance in miles
	 */
	public static double getDistanceMi(final IaruLocator loc1, final IaruLocator loc2) {
		return getDistance(loc1, loc2, AVG_EARTH_RADIUS_SM);
	}

	/**
	 * @param loc1
	 *            first location
	 * @param loc2
	 *            second location
	 * @return great circle distance in kilometers
	 */
	private static double getDistanceKm(final IaruLocator loc1, final IaruLocator loc2) {
		return getDistance(loc1, loc2, AVG_EARTH_RADIUS_KM);
	}

	/**
	 * @param loc1
	 *            first location
	 * @param loc2
	 *            second location
	 * @return great circle distance in nautical miles
	 */
	private static double getDistanceNm(final IaruLocator loc1, final IaruLocator loc2) {
		return getDistance(loc1, loc2, AVG_EARTH_RADIUS_NM);
	}

	/**
	 * @param loc1
	 *            first location
	 * @param loc2
	 *            second location
	 * @param radius
	 *            radius of the earth in the units desired for result
	 * @return great circle distance between the two locations, result units
	 *         same of the radius units
	 */
	private static double getDistance(final IaruLocator loc1, final IaruLocator loc2, final double radius) {
		if (loc1.equals(loc2)) {
			return 0;
		}
		return Math.acos(Math.sin(loc1.getLatitude().getRadians()) * Math.sin(loc2.getLatitude().getRadians())
				+ Math.cos(loc1.getLatitude().getRadians()) * Math.cos(loc2.getLatitude().getRadians())
				* Math.cos(loc2.getLongitude().getRadians() - loc1.getLongitude().getRadians()))
				* radius;
	}

	/**
	 * @param loc2
	 *            destination location
	 * @return bearing in degrees
	 */
	public double getBearing(final IaruLocator loc2) {
		return getBearing(this, loc2);
	}

	/**
	 * @param loc1
	 *            source location
	 * @param loc2
	 *            destination location
	 * @return bearing in degrees
	 */
	public static double getBearing(final IaruLocator loc1, final IaruLocator loc2) {
		if (loc1.equals(loc2)) {
			return Double.NaN;
		}

		double dLon = loc2.getLongitude().getRadians() - loc1.getLongitude().getRadians();

		double y = Math.sin(dLon) * Math.cos(loc2.getLatitude().getRadians());
		double x = Math.cos(loc1.getLatitude().getRadians()) * Math.sin(loc2.getLatitude().getRadians())
				- Math.sin(loc1.getLatitude().getRadians()) * Math.cos(loc2.getLatitude().getRadians()) * Math.cos(dLon);
		return (Angle.radiansToDegrees(Math.atan2(y, x)) + 360) % 360;
	}
}
