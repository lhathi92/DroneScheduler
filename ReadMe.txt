DroneScheduler:

The code contains two classes: DroneScheduler (with a main) and Order.
The input requirement for DroneScheduler is run DroneScheduler inputFile outputFile
The mains starts 3 processes in different threads.
1. readFileAndPopulateData which keeps reading the file till last entry is reached and converts input lines to 'Order'
It then puts the order in a Queue to be processed.
2. addOrdersToProcessingQueue. The processingQueue is a PriorityQueue (minHeap) which has orders sorted on time that will be taken to deliver.
When orderRecivedTime becomes>= currentTime, we move the order from Queue to PQ.
3. processQueue This function implements a thread which gets the head of the PQ which will have the least time to deliver,
adds to a buffer of orders, updates departureTime and completionTime for this order.
When buffer size==100, this writes orders buffered so far to the output file.
It also tracks total orders processed, promoters and detractors.
In the end, this function will output the NPS.

Assumptions:
- The file is being read the same day as the orders have come in. 
- The town is a grid, the warehouse and drone are at point (0,0).
- A drone can carry one package at a time.
- It starts from (0,0) goes to customer destination and comes back to (0,0).
- Customer destination point can be thought of as a diagonal to (0,0). 
- The distance travelled by the drone=diagonal length, i.e. sqrt of x coordinate^2 + y coordinate^2.
- One distance unit=One time unit.
- This helps us calculate the rounTrip time for the drone for Order x.
- We can have orders before 6am.
