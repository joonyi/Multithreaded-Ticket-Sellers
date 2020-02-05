import java.util.Collections;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class WorkerT implements Runnable{

	private JobProcess jobProcess;
	private FreePageList freePageL;
	private DiskPageList diskPageL;
	private Object mutex, lkalgor;
	private AtomicInteger time;
	private int duration;
	private int jobSize;
	private int haventPOP = 1;
		
	private int cIndex;
	
	private static Random r = new Random();
	private static int[] deltaI = {-1,0,1}; //for locality of reference
	private boolean pageInRef = false;
	

	public WorkerT(JobProcess jb, FreePageList flist, Object lock, Object lk,AtomicInteger num){
		this.jobProcess = jb;
		this.duration = jb.getSer() * 1000;
		this.mutex = lock;
		this.lkalgor = lk;
		this.freePageL = flist;
		this.time = num;
		this.jobSize = jb.getSize();
	}
	
	@Override
	public void run() {
		while(time.get() < 60  && App.flagTA == 0){
			
			//when it is finished
			if(duration <= 0){
				//System.out.println("BREAK");
				if(this.jobProcess.getFrames().size() != jobSize + 1){
					App.okToPop = 1;
					//System.out.println("ENTERRRRRRRRRRRRRR");
					haventPOP = 0;
				}
				
				removeTheContentFromM();
				appPicker(App.prompt);	//after removing it update the page to evict
				writeRecord("Exit");

				break;
				
			}
			
			//implement when to write to memory
			//put all disk memory to physical memory
			while(jobSize + 1 > this.jobProcess.getFrames().size()){
				writeToMemory();
				appPicker(App.prompt);
				if(App.flagTA == 1){
					break;
				}
			}
			
			//after it has put all the disk data to physical memory
			// it will flag the main thread to pop the job
			if(jobSize + 1 == this.jobProcess.getFrames().size() && haventPOP == 1){
				App.okToPop = 1;
				//System.out.println("ENTERRRRRRRRRRRRRR");
				haventPOP = 0;
			}
			
			readFromMemory();
			appPicker(App.prompt);	//after removing it update the page to evict
		}
	}
	
	public void writeToMemory(){
		// first check if jobProcess has zero index
		int nextIndex = -1;
		if(this.cIndex == 0){
			//this is for the start of the execution
			synchronized (this.mutex) {
				if(this.freePageL.hasenoughSpace()){
					
					pageInRef = false;
					
					appPicker(App.prompt);
					
					writeRecord("Enter");
					
					this.freePageL.doAt(this.jobProcess, 0);
					this.jobProcess.addFrame(new MemoryFrame(jobProcess.getName(), 0));
					cIndex--;
					
					this.freePageL.clean(0);

					doSomething(0);
					// make sure that it wont get the zeroth index again in fpl or -1
					while(!withinBound(nextIndex)){	
						nextIndex = fixBound(GenerateLoR(0));
					}
					// loop until nextindex is not zero or the nextindex in the memory is empty
					//while(this.freePageL.get(nextIndex).getIndex() == 0 || !this.freePageL.get(nextIndex).isEmp()){ 
					while(nextIndex == 0 || !this.freePageL.get(nextIndex).isEmp()){
						nextIndex = nextIndex + GenerateLoR(nextIndex);
						
						while(!withinBound(nextIndex)){	
							nextIndex = fixBound(nextIndex);
						}
						
					}
					this.freePageL.doAt(this.jobProcess, nextIndex);
					this.jobProcess.addFrame(this.freePageL.get(nextIndex)); 
					// prompt == 1 or 2
					if(App.prompt <= 2){
						synchronized (this.lkalgor) {
							
							App.algorithmList.set(nextIndex - 1, this.time.get());
						}
				
					}else if(App.prompt!= 5){
						//prompt == 3 or 4
						
						synchronized (this.lkalgor) {
							int k = 1;	//set to one since just adding it
							App.algorithmList.set(nextIndex - 1, k);
						}
					}
					
					App.miss++;
					
					doSomething(nextIndex);
					
				}
				
			}
		}else{		
			
			//get the job list current memoryframe index

			appPicker(App.prompt);
			synchronized(this.mutex){
				
				if(this.freePageL.getFreeSpace() > 1){
					nextIndex = fixBound(GenerateLoR(this.jobProcess.getFrames().get(cIndex).getIndex())); //should still be within locality of ref
					pageInRef = false;
					while(!this.freePageL.get(nextIndex).isEmp() || nextIndex == 0){ 
						nextIndex = nextIndex + GenerateLoR(this.jobProcess.getFrames().get(cIndex).getIndex());

						while(!withinBound(nextIndex)){	
							nextIndex = fixBound(nextIndex);
						}

						
					}
					// if memory frame is not occupied, do the following
					
					this.freePageL.doAt(this.jobProcess, nextIndex);
					if(this.jobProcess.getFrames().size() < jobSize+1){
						this.jobProcess.addFrame(this.freePageL.get(nextIndex)); 
						
						//System.out.println("ADD");
					}else{
						this.jobProcess.getFrames().set(cIndex, this.freePageL.get(nextIndex));
						//System.out.println("SET");
					}
					
					if(App.prompt <= 2){
						synchronized (this.lkalgor) {
							
							App.algorithmList.set(nextIndex - 1, this.time.get());
						}
				
						
					}else if(App.prompt != 5){
						//prompt == 3 or 4
						
						synchronized (this.lkalgor) {
							int k = 1; 	//set to 1 since you are just adding it to the free page list
									
							App.algorithmList.set(nextIndex - 1, k);
						}
					}
					
					App.miss++;
					doSomething(nextIndex);	
					
					 
				}else{
					// swapping algorithm
					//doesnt need nextIndex but cindex for sure
					
					
					
					appPicker(App.prompt);

					App.evict.incrementAndGet();
					pageInRef = false;
					this.freePageL.clean(App.pick);
					this.freePageL.doAt(this.jobProcess, App.pick);
					if(this.jobProcess.getFrames().size() < jobSize+1){
						this.jobProcess.addFrame(this.freePageL.get(App.pick)); 
						
						//System.out.println("ADD");
					}else{
						this.jobProcess.getFrames().set(cIndex, this.freePageL.get(App.pick));
						//System.out.println("SET");
					}
					if(App.prompt <= 2){
						synchronized (this.lkalgor) {
							
							App.algorithmList.set(App.pick - 1, this.time.get());
						}
				
							
					}else if(App.prompt != 5){
							//prompt == 3 or 4
							
						synchronized (this.lkalgor) {
							//reset the swapped frame to 1
							int k = 1;
							App.algorithmList.set(App.pick - 1, k);
						}
					}
						
					App.miss++;
					doSomething(App.pick);
					
				}
				
			}
		}
			
		
	}
	// compare the each jobs frame to physical frame
	// to see if it matches or not
	// if not, call writeToMemory
	public void readFromMemory(){
		
		//if frame is not in free page list then writetomemory else read it.
		if(!this.jobProcess.getFrames().get(cIndex).getPName().equals(this.jobProcess.getName())){
		
			writeToMemory();
			appPicker(App.prompt);	
		}else{
			
			// FIFO does not matter here. FIFO only matters when it is first pushed into the freepagelist
			if(App.prompt == 2){
				synchronized (this.lkalgor) {
					App.algorithmList.set(this.jobProcess.getFrames().get(cIndex).getIndex() - 1, this.time.get());
				}
				
			}else if(App.prompt != 5){
				//prompt == 3 or 4
				
				synchronized (this.lkalgor) {
					int k = App.algorithmList.get(this.jobProcess.getFrames().get(cIndex).getIndex() - 1);
					k++;
					App.algorithmList.set(this.jobProcess.getFrames().get(cIndex).getIndex() - 1, k);
				}
			}
			
			
			pageInRef = true;
			App.hit.incrementAndGet();
				
			
			doSomething(this.jobProcess.getFrames().get(cIndex).getIndex());
		}	
			
			

		
	}
	
	// might reimplement this
	public void removeTheContentFromM(){
		synchronized(this.mutex){
		
			int indexForFrame;
			for( int i = 1 ;i < this.jobProcess.getFrames().size(); i++){
					// if this jobProcess queue element name is equal to this process name
					// remove it from memory
				if(this.jobProcess.getFrames().get(i).getPName().equals(this.jobProcess.getName())){
					indexForFrame = this.jobProcess.getFrames().get(i).getIndex();
					this.freePageL.clean(indexForFrame);
				
					//setting the values back to initial states
					if(App.prompt <= 2){
						App.algorithmList.set(this.jobProcess.getFrames().get(cIndex).getIndex() - 1, 60);
						
					}else if(App.prompt == 3){
						//prompt == 3 or 4
						
						synchronized (this.lkalgor) {
							int k = 9999; //set to 9999 initial state when removed
							App.algorithmList.set(this.jobProcess.getFrames().get(cIndex).getIndex() - 1, k);
						}
					}else if(App.prompt == 4){
						//prompt == 3 or 4
						
						synchronized (this.lkalgor) {
							int k = 0; //set to zero when removed
							App.algorithmList.set(this.jobProcess.getFrames().get(cIndex).getIndex() - 1, k);
						}
					}
				}
			}
		}
		
	}
	
	private void writeRecord(int pageReferenced){
		System.out.println("---------Time: " + time.get() + "--------------------------");
		//System.out.println("Process: " + this.jobProcess.getName() + " index: " + cIndex + " jB size: " + this.jobProcess.getFrames().size()+ " " + this.jobSize + " page refer: " + pageReferenced);
		System.out.println("Process: " + this.jobProcess.getName()  +  " page refer: " + pageReferenced + " If page in Ref:" + pageInRef);
		//System.out.println("Is page in memory: ");
		System.out.println("Process that will be evicted if needed: " + App.pick);
	//	System.out.println(freePageL.getFreeSpace());
	//	System.out.println(this.freePageL);
		
	}
	
	private void writeRecord(String ee){
		System.out.println("\n---------Time: " + time.get() + "--------------------------");
		System.out.println("Process: " + this.jobProcess.getName() + " Enter/exit: " + ee);
		System.out.println("Size: " + this.jobSize + " Duration: " + this.jobProcess.getSer());
		System.out.println(freePageL);
		System.out.println();
		
	}
	
	//do something for 100 msec
	public void doSomething(int pageReferenced){
		try {
			
			if(cIndex == jobSize)
				this.cIndex = 1;	//circle back to 1
			else
				this.cIndex++;
			
			writeRecord(pageReferenced);
			
			duration = duration - 100;
			Thread.sleep(100);
			
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void appPicker(int i){
		if(i == 1){
			//FIFO use time to determine 
			synchronized (this.lkalgor) {
				App.pick = App.algorithmList.indexOf(Collections.min(App.algorithmList))+1;
			}
		}else if(i == 2){
			//LRU use time to determine
			synchronized (this.lkalgor) {
				App.pick = App.algorithmList.indexOf(Collections.min(App.algorithmList))+1;
			}
		}else if(i == 3){
			//LFU //use counter to determine
			synchronized (this.lkalgor) {
				App.pick = App.algorithmList.indexOf(Collections.min(App.algorithmList))+1;
			}
		}else if(i == 4){
			//MFU	/use counter to determine
			synchronized (this.lkalgor) {
				App.pick = App.algorithmList.indexOf(Collections.max(App.algorithmList))+1;
			}
		}else if(i == 5){
			//random pick
			App.pick = r.nextInt(App.MEMORY-1)+1;
		}
	}
	
	private boolean withinBound(int index){
		if(index <= 99 && index >= 1 ){
			return true;
		}
		
		return false;
	}
	
	private int fixBound(int index){
		
		while(index < 1){
			index = index + 100;
		}
		
		while(index > 99){
			index = index - 99;
		}

		return index;
	}
	
	// generate the locality of reference
	private int GenerateLoR(int index){
		int pageReference;
		int k = r.nextInt(2);
		int j;
		int first = r.nextInt(10);
		if(first >= 0 && first <= 6) //70 percent
			pageReference = index + deltaI[r.nextInt(3)]; //0,1,2 index
		else{
			j = r.nextInt(8) + 2;
			if(k == 0)
				pageReference = index + r.nextInt(j+1);
			else
				pageReference = index - r.nextInt(j+1);

		}
		return pageReference;
		
	}

}










