import java.text.DecimalFormat;

record Vector(double x, double y) implements GameStateObject {

	private double norm;
	private double normSquare;

	private static final int offsetDegree = 0;

	private Vector(double x, double y) {
		super();
		this.x = x;
		this.y = y;
	}

	public static Vector getVectorFromCoordinates(double x, double y) {
		return new Vector(x, y);
	}

	public static Vector getVectorFromTwoPoints(Point initialPoint, Point terminalPoint) {
		return new Vector(terminalPoint.x() - initialPoint.x(), terminalPoint.y() - initialPoint.y());
	}

	public static Vector getVectorFromAngleNorm(double angleInDegree, double norm) {
		return new Vector(norm * Math.cos(Math.toRadians(angleInDegree + offsetDegree)), norm * Math.sin(Math.toRadians(angleInDegree + offsetDegree)));
	}

	public double getNorm() {
		if (norm == 0) {
			norm = Math.sqrt(x * x + y * y);
		}
		return norm;
	}

	public double getNormSquare() {
		if (normSquare == 0) {
			normSquare = x * x + y * y;
		}
		return normSquare;
	}

	public Vector normalize() {
		if (getNorm() != 0) {
			Vector v =  new Vector(x()/getNorm(), y()/getNorm());
			v.norm = 1;
			v.normSquare = 1;
			return v;
		}
		throw new IllegalArgumentException("Cannot normalize a null vector");
	}

	public Vector addVector(Vector v) {
		return new Vector(x() + v.x(), y() + v.y());
	}

	public Vector multiply(double factor) {
		return new Vector(x() * factor, y() * factor);
	}

	public Vector round() {
		return new Vector(Point.CGRound(x()), Point.CGRound(y()));
	}

	public Vector copy() {
		return new Vector(x(), y());
	}

	private static final DecimalFormat formatter = new DecimalFormat("+#0000.00;-#");

	@Override
	public String toString() {
		return "Vector " + formatter.format(x()) + " " + formatter.format(y());
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
		Vector other = (Vector) obj;
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
		Vector other = (Vector) obj;
		if (Point.CGRound(x) != Point.CGRound(other.x))
			return false;
		if (Point.CGRound(y) != Point.CGRound(other.y))
			return false;
		return true;
	}

}