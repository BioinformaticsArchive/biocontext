package uk.ac.man.textpipe.server;

import java.io.Closeable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class KillTimer implements Runnable {

	private long ms;
	private boolean enabled;

	private List<Closeable> closeables = new ArrayList<Closeable>();
	private List<Thread> threads = new ArrayList<Thread>();
	private List<Socket> sockets = new ArrayList<Socket>();
	private List<Process> processes = new ArrayList<Process>();
	
	public KillTimer(long ms){
		this.ms = ms;
		this.enabled = true;
	}

	@Override
	public void run() {
		try{
			Thread.sleep(ms);
		} catch (Exception e){
		}
		if (enabled){
			try {
				for (Thread t : threads)
					t.interrupt();
				for (Closeable c : closeables)
					c.close();
				for (Socket s : sockets)
					s.close();
				for (Process p : processes)
					p.destroy();
			} catch (Exception e){
			}			
		}
	}

	public void enable(){
		this.enabled = true;
	}
	public void disable(){
		this.enabled = false;
	}
	public boolean isEnabled(){
		return enabled;
	}
	
	public void addCloseable(Closeable c){
		closeables.add(c);
	}
	
	public void addThread(Thread t){
		threads.add(t);
	}
	
	public void addSocket(Socket s){
		sockets.add(s);
	}

	public void addProcess(Process p) {
		processes.add(p);
	}
}
