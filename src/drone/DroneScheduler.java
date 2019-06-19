package drone;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DroneScheduler {

	// Order Input format: Id NxWy 07:04:32
	/**
	 * Priority queue will keep track of orders recived upto current time,
	 * sorted on timeToDeliver
	 */
	PriorityQueue<Order> q=new PriorityQueue<Order>(new OrderComparator());
	private static String outputFilePath="";
	// entry to mark of end of file/orders to be received
	private static final String LAST_ENTRY = "end_of_orders";
	AtomicBoolean lastEntryFound = new AtomicBoolean(false);
	// Global counters for total orders, promoters, detractors to calculate NPS
	AtomicInteger totalCount = new AtomicInteger(0);
	AtomicInteger promoters = new AtomicInteger(0);
	AtomicInteger detractors = new AtomicInteger(0);
	AtomicInteger nps = new AtomicInteger(0);
	// Buffer for orders so that we can group writes to o/p file
	ArrayList<Order> buffer = new ArrayList<Order>();

	public static void main(String arg[]) throws Exception{

		if(arg==null || arg.length==0){
			throw new Exception("No input!");
		}

		if(arg.length<2){
			throw new Exception("Enter valid inputs for input and output file.");
		}

		String inputFilePath=arg[0];
		outputFilePath=arg[1];

		DroneScheduler droneSched=new DroneScheduler();

		// keeps reading from file but not overflow memory
		final BlockingQueue<Order> orderQueue = new LinkedBlockingQueue<Order>(1000);
		CountDownLatch latch = new CountDownLatch(3);
		droneSched.readFileAndPopulateData(inputFilePath, orderQueue, latch);

		while (orderQueue.size() == 0) {
			Thread.sleep(10);
			System.out.println("Waiting..");
		}

		droneSched.addOrdersToProcessingQueue(orderQueue, latch);

		droneSched.processQueue(latch);

		latch.await();

	}

	private Thread processQueue(CountDownLatch latch) {
		QueueProcessor qProcessor = new QueueProcessor(latch);
		Thread th = new Thread(qProcessor);
		th.start();

		return th;

	}

	/**
	 * Add order to priority queue if time received for order >= current time
	 */
	private void addOrdersToProcessingQueue(final BlockingQueue<Order> orderQueue, CountDownLatch latch) {

		Thread addOrdersThread = new Thread(() -> {
			while (!orderQueue.isEmpty() || !lastEntryFound.get()) {
				Order order = orderQueue.peek();
				System.out
						.println("order null? " + (order == null) + "order= " + order.getOrderId());
				try {
				while (compareCurrTimeInString(order.getTimeReceived())) {
					try {
						TimeUnit.SECONDS.sleep(1);
							System.out.println("Waiting for start time");
					} catch (InterruptedException e) {
						// timeunit interruption, can be ignored
					}
				}
				} catch (Exception e) {
					System.out.println("Error comparing time for orders " + e);
				}
				System.out.println("Adding order " + order.getOrderId() + " to pq");
				q.add(orderQueue.poll());
			}
		});
		addOrdersThread.setName("AddOrdersToPQ");
		addOrdersThread.start();
	}

	/**
	 * If current time > parameter time
	 * 
	 * @throws ParseException
	 */
	private boolean compareCurrTimeInString(String time) throws ParseException {
		SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
		Date date1 = format.parse(time);
		long difference = System.currentTimeMillis() - date1.getTime();

		return difference > 0 ? true : false;
	}

	/**
	 * Reads input file till last entry is reached, adds orders to blockingqueue
	 */
	private void readFileAndPopulateData(String filePath, final BlockingQueue<Order> orderQueue,
			CountDownLatch latch)
			throws FileNotFoundException {

		Thread readTh = new Thread(() -> {

			String line = "";
			int lineCount = 0;
			FileReader fr = null;
			BufferedReader br = null;
			try {
				fr = new FileReader(filePath);
				br = new BufferedReader(fr);

				while ((line = br.readLine()) != null) {
					if (line.contains(LAST_ENTRY)) {
						lastEntryFound.set(true);
						break;
					}
					String lineSplit[] = line.split(" ");
					if (lineSplit.length != 3) {
						System.out.println("Invalid entry at line " + lineCount);
						continue;
					} else {
						// add to queue and process
						if (!processInputAndPopulateOrder(lineSplit, orderQueue))
							System.out.println("Skipping order " + lineSplit[0] + " due to invalid format.");
					}

				}
				System.out.println("queue size= " + orderQueue.size());
			} catch (Exception e) {
				System.out.println("Error parsing orders " + e);
			} finally {
				try {
					if (br != null)
						br.close();
					if (fr != null)
						fr.close();
				} catch (IOException e) {
					System.out.println("Unable to close file readers.");
				} finally {
					latch.countDown();
				}
			}
		});

		readTh.setName("ReadFile");
		readTh.start();

	}

	/* Parse the file input line and create object order with validations */
	private boolean processInputAndPopulateOrder(String[] lineSplit, final BlockingQueue<Order> orderQueue) {
		double distanceFromRoot=parseCoordinatesAndGetDistance(lineSplit[1]);
		if(distanceFromRoot==-1)
			return false;
		if(lineSplit[0].isEmpty() || lineSplit[2].isEmpty())
			return false;
		System.out.println(
				"Order time received= " + lineSplit[2] + " distance " + distanceFromRoot + " id " + lineSplit[0]);
		Order order=new Order(lineSplit[0],lineSplit[2],distanceFromRoot,getRoundTripTimeForDistanceinMillis(distanceFromRoot),getRoundTripTimeString(distanceFromRoot));

		orderQueue.add(order);

		return true;
	}

	public class QueueProcessor implements Runnable {

		private boolean doStop = false;
		private CountDownLatch latch;
		private String prevDepartureTime = "";
		private String prevDeliveryCompletionTime = "";

		public QueueProcessor(CountDownLatch latch) {
			this.latch = latch;
		}

		public synchronized void doStop() {
			this.doStop = true;
		}

		private synchronized boolean keepRunning() {
			return this.doStop == false;
		}

		@Override
		public void run() {
			try {
				while (keepRunning()) {
					if (q.isEmpty() && lastEntryFound.get()) {
						try {
							writeBufferedOrdersToFile(buffer, outputFilePath);
							writeNPSToFile(outputFilePath);
						} catch (IOException e) {
							e.printStackTrace();
						}
						doStop();
					}
					try {
						if (q.isEmpty() || compareCurrTimeInString(q.peek().getTimeReceived())
								|| !compareCurrTimeInString(prevDeliveryCompletionTime))
							TimeUnit.SECONDS.sleep(1);
						else
							processQueueEntry(q.poll());
					} catch (InterruptedException e) {
						e.printStackTrace();
					} catch (ParseException e) {
						e.printStackTrace();
					}

				}
			} finally {
				latch.countDown();
			}
		}

		private void processQueueEntry(Order order) {
			try {
				String currDepartureTime = "";
				if (prevDepartureTime.isEmpty()) {
					currDepartureTime = "6:00:00";
				} else {
					currDepartureTime = addOneSecToTimeString(prevDeliveryCompletionTime);
				}
				prevDepartureTime = currDepartureTime;
				prevDeliveryCompletionTime = addTimesInString(currDepartureTime, order.getTimeToDeliverString());
				order.setDepartureTime(prevDepartureTime);
				buffer.add(order);
				writeBufferedOrdersToFile(buffer, outputFilePath);
				totalCount.incrementAndGet();
				if (order.getTimeToDeliver() >= 3600000)
					promoters.getAndIncrement();
				else if (order.getTimeToDeliver() > (3 * 3600000)) {
					detractors.getAndIncrement();
				}
			} catch (Exception e) {
				System.out.println("Error processing order " + order.getOrderId() + " :" + e);
			}
		}

	}

	String addTimesInString(String time1, String time2) throws ParseException {
		SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
		DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		Date date1 = format.parse(time1);
		Date date2 = format.parse(time2);

		Date resultDate = new Date(date1.getTime() + (date2.getTime()));
		String result = dateFormat.format(resultDate);
		return result;
	}

	public void writeNPSToFile(String outputFilePath2) throws IOException {
		// TODO Auto-generated method stub
		FileWriter fw = null;
		PrintWriter pw = null;
		try {
			fw = new FileWriter(outputFilePath2, true);
			pw = new PrintWriter(fw);
			int nps = (promoters.get() / totalCount.get()) - (detractors.get() / totalCount.get());
			pw.println("NPS " + nps);

		} catch (Exception e) {
			System.out.println("Error in writing to output file: " + e);
		} finally {
			if (pw != null)
				pw.close();
			if (fw != null)
				fw.close();
			buffer.clear();
		}

	}

	public void writeBufferedOrdersToFile(ArrayList<Order> buffer, String outputFilePath2) throws IOException {

		if (buffer.size() == 1000) {
			FileWriter fw = null;
			PrintWriter pw = null;
			try {
				fw = new FileWriter(outputFilePath2, true);
				pw = new PrintWriter(fw);
				for (int i = 0; i < buffer.size(); i++) {
					Order bufferedOrder = buffer.get(i);
					pw.println(bufferedOrder.getOrderId() + " " + bufferedOrder.getDepartureTime());

				}

			} catch (Exception e) {
				System.out.println("Error in writing to output file: " + e);
			} finally {
				if (pw != null)
					pw.close();
				if (fw != null)
					fw.close();
				buffer.clear();
			}
		}
	}

	String addOneSecToTimeString(String time) throws ParseException {
		SimpleDateFormat format = new SimpleDateFormat("HH:mm:ss");
		Date date1 = format.parse(time);
		Date resultDate = new Date(date1.getTime() + (1 * 1000));
		DateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
		String result = dateFormat.format(resultDate);

		return result;

	}

	private double parseCoordinatesAndGetDistance(String coordinates) {

		if(coordinates==null || coordinates.length()==0)
			return -1;
		int x=-1,y=-1;
		String coordinate="";
		for(char c:coordinates.toCharArray()){
			if(c=='n' || c=='N' || c=='s' || c=='S'){
				continue;
			} else if(c=='e' || c=='E' || c=='w' || c=='W'){
				//flush out North or south distance caontained so far
				if(!coordinate.isEmpty())
					x=Integer.parseInt(coordinate);
				coordinate="";
			} else if(c>=0 || c<=9){
				//append integers after N/S or E/W
				coordinate=coordinate+c;
			} else{
				System.out.println("Invalid character "+c);
			}
		}
		if(!coordinate.isEmpty()){
			y=Integer.parseInt(coordinate);
		}

		return getDistanceFromWareHouse(x, y);

	}

	double getDistanceFromWareHouse(int x, int y){

		if(x<0 || y<0){
			return -1;
		}

		int sumOfSquares=(x*x)+(y*y);
		double diagonalLength=Math.sqrt(sumOfSquares);

		return diagonalLength;

	}

	long getRoundTripTimeForDistanceinMillis(double distance){
		int time = (int) ( 100 * 2 * distance);

		int min = time/100;
		int sec = (60 * (time%100))/100;

		int hours=0;
		if(min>60){
			hours=min/60;
		}

		long tripTime=(hours*3600000)+(min*60000)+(sec*1000);

		return tripTime;

	}

	String getRoundTripTimeString(double distance){

		int time = (int) ( 100 * 2 * distance);

		int min = time/100;
		int sec = (60 * (time%100))/100;

		int hours=0;
		if(min>60){
			hours=min/60;
		}

		StringBuilder tripTime=new StringBuilder();
		tripTime.append(hours<10?"0"+hours:hours);
		tripTime.append(":");
		tripTime.append(min<10?"0"+min:min);
		tripTime.append(":");
		tripTime.append(sec);

		return tripTime.toString();
	}


	class OrderComparator implements Comparator<Order>{
		public int compare(Order o1,Order o2){
			if(o1.timeToDeliver<o2.timeToDeliver)
				return 1;
			else if(o1.timeToDeliver>o2.timeToDeliver)
				return -1;
			else return 0;
		}
	}

}
