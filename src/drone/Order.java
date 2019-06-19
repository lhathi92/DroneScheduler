package drone;


class Order{
	String orderId;
	String timeReceived;
	String timeToDeliverString;
	double distanceFromRoot;
	long timeToDeliver;
	String departureTime;
	
	public Order() {

	}

	public Order(String id, String timeReceived, double distanceFromRoot, long roundTripTimeForDistanceinMillis,
			String roundTripTimeString) {
		this.orderId=id;
		this.timeReceived=timeReceived;
		this.distanceFromRoot=distanceFromRoot;
		this.timeToDeliver=roundTripTimeForDistanceinMillis;
		this.timeToDeliverString=roundTripTimeString;
	}

	public String getOrderId() {
		return orderId;
	}
	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}
	public long getTimeToDeliver() {
		return timeToDeliver;
	}
	public void setTimeToDeliver(long timeToDeliver) {
		this.timeToDeliver = timeToDeliver;
	}

	public String getDepartureTime() {
		return departureTime;
	}

	public void setDepartureTime(String departureTime) {
		this.departureTime = departureTime;
	}

	public String getTimeReceived() {
		return timeReceived;
	}

	public void setTimeReceived(String timeReceived) {
		this.timeReceived = timeReceived;
	}

	public double getDistanceFromRoot() {
		return distanceFromRoot;
	}

	public void setDistanceFromRoot(double distanceFromRoot) {
		this.distanceFromRoot = distanceFromRoot;
	}

	public String getTimeToDeliverString() {
		return timeToDeliverString;
	}

	public void setTimeToDeliverString(String timeToDeliverString) {
		this.timeToDeliverString = timeToDeliverString;
	}

	@Override
	public String toString() {
		return orderId + timeReceived + timeToDeliver + distanceFromRoot;
	}
}