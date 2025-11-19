import java.text.DecimalFormat;

record Point(double x, double y) implements GameStateObject {

	public Point addVector(Vector v) {
		return new Point(x + v.x, y + v.y);
	}

	public Point round() {
		return new Point(CGRound(x), CGRound(y));
	}

	public Point truncate() {
		return new Point((int) x, (int) y);
	}

	public static int CGRound(double d) {
		return (int) Math.signum(d) * (int) Math.round(Math.abs(d));
	}

	public double distanceTo(Point p) {
		return Math.sqrt((this.x - p.x) * (this.x - p.x) + (this.y - p.y) * (this.y - p.y));
	}

	public double distanceToSquare(Point p) {
		return (this.x - p.x) * (this.x - p.x) + (this.y - p.y) * (this.y - p.y);
	}

	public static double getDistance(Point p1, Point p2) {
		return Math.sqrt((p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y));
	}

	public static double getDistanceSquare(Point p1, Point p2) {
		return (p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y);
	}

	private static final DecimalFormat formatter = new DecimalFormat("+#00000.00;-#");

	@Override
	public String toString() {
		return "Point " + formatter.format(x) + " " + formatter.format(y);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		long temp;
		temp = Double.doubleToLongBits(x);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(y);
		result = prime * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Point other = (Point) obj;
		if (Double.doubleToLongBits(x) != Double.doubleToLongBits(other.x))
			return false;
		if (Double.doubleToLongBits(y) != Double.doubleToLongBits(other.y))
			return false;
		return true;
	}

	public boolean equalsRounded(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Point other = (Point) obj;
		if (CGRound(x) != CGRound(other.x))
			return false;
		if (CGRound(y) != CGRound(other.y))
			return false;
		return true;
	}

}