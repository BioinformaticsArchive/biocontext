package uk.ac.man.textpipe.server;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipe;

import martin.common.Misc;
import martin.common.compthreads.IteratorBasedMaster;
import martin.common.compthreads.Problem;

public class PubToolsServer implements Runnable {
	private static final long TIMEOUT_DELAY = 600000;
	
	private class connectionProblem implements Problem<Object>{
		Socket sock;
		private Annotator annotator;
		public connectionProblem(Socket sock, Annotator annotator){
			this.sock = sock;
			this.annotator = annotator;
			TextPipe.debug("Connected.");
		}
		public Object compute() {
			TextPipe.debug("Computing...");
			
			//create a timer which will forcibly close the socket after a minute to prevent 
			//a server being locked by occasional connections that hang
			KillTimer timer = new KillTimer(TIMEOUT_DELAY);
			timer.addSocket(this.sock);
			Thread timerThread = new Thread(timer);
			timerThread.start();
			
			BufferedReader inFromClient = null;
			BufferedWriter outToClient = null;
			
			try{
				inFromClient = new BufferedReader(
						new InputStreamReader(sock.getInputStream()), TextPipe.BUFFER_SIZE);
				outToClient = new BufferedWriter(
						new OutputStreamWriter(sock.getOutputStream()));

				TextPipe.debug("* Connected to " + sock.getInetAddress());

				Map<String, String> values = new HashMap<String, String>();

				boolean open = true;

				// parse in a record line by line
				while (open) {
					String clientLine;
					clientLine = inFromClient.readLine();

					if (clientLine != null) {
						TextPipe.debug("<" + clientLine + "<");

						if (clientLine.equals("getOutputs")){
							TextPipe.debug(">" + Misc.implode(annotator.getOutputFields(), "\t") + ">");
							outToClient.write(Misc.implode(annotator.getOutputFields(), "\t") + "\n");
							outToClient.flush();
						} else if (clientLine.equals("quit")) {
							//quit keyword: kill server 
							TextPipe.debug("received quit, killing program");
							annotator.destroy();
							System.exit(0);
						} else if (clientLine.length() == 0) {
							if (values.size() == 0){
								open=false;
								break;
							} else {
								// end marker: process record, write back results, clear hashmap

								if (values.containsKey("doc_text") || values.containsKey("doc_id")){

									List<Map<String,String>> output = annotator.process(TextPipe.decode(values));

									try {
										outToClient.write(TextPipe.toString(output));
										outToClient.write("\n\n");
										outToClient.flush();
										TextPipe.debug("Wrote output to client:\n" + TextPipe.toString(output));
										
										//reset timer
										timer.disable();
										timerThread.interrupt();
										timer = new KillTimer(TIMEOUT_DELAY);
										timer.addSocket(this.sock);
										timerThread = new Thread(timer);
										timerThread.start();
										
									} catch (java.net.SocketException e) {
										TextPipe.debug("While writing: Connection closed");
									}
								} else {
									outToClient.write("Error: The request need to contain a 'doc_text' or 'doc_id' key/value pair.\n");
									outToClient.flush();
								}

								values.clear();
							}
						} else {
							String[] fields = clientLine.split("\\t");

							String key = fields[0];
							String value = fields.length == 2 ? fields[1] : null;

							TextPipe.debug("got data: "+key+"="+value);
							values.put(key,value);
						}
					} else {
						open=false;
					}
				}

				TextPipe.debug("Closing connection.");

				outToClient.close();
				outToClient = null;
				
				inFromClient.close();
				inFromClient = null;
				
				timer.disable();
				
				return null;
			} catch (Exception e){
				System.err.println(e);
				e.printStackTrace();
			}
			
			try{
				outToClient.close();
				inFromClient.close();
			} catch (Exception e){
			}

			timer.disable();
			
			return null;
		}
	}
	private class ConnectionProblemIterator implements Iterator<Problem<Object>>{
		private ServerSocket sock;
		private Annotator annotator;

		public ConnectionProblemIterator(ServerSocket sock, Annotator annotator){
			this.sock = sock;
			this.annotator = annotator;
		}

		public boolean hasNext() {
			return true;
		}

		public Problem<Object> next() {
			while (true){ 
				try{
					return new connectionProblem(sock.accept(), annotator);
				} catch (Exception e){
					System.err.println(e);
					e.printStackTrace();
				}
			}
		}

		public void remove() {
			throw new IllegalStateException();
		}
	}

	private int port;
	private Annotator annotator;
	private int numThreads;

	public PubToolsServer(int port, Annotator annotator, int numThreads){
		this.port = port;
		this.annotator = annotator;
		this.numThreads = numThreads;
	}
	
	/**
	 * @param port
	 * @param annotator
	 * @param numThreads
	 * @throws Exception
	 */
	public void run(){
		numThreads = annotator.isConcurrencySafe() ? numThreads : 1;

		ServerSocket socket=null;

		try{
			socket = new ServerSocket(port);
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

		IteratorBasedMaster<Object> master = new IteratorBasedMaster<Object>(new ConnectionProblemIterator(socket, annotator),numThreads);
		new Thread(master).start();
		
		for (@SuppressWarnings("unused") Object o : master){
			//dummy method, just to consume returned nulls (concurrency library is not perfectly suited for servers) 
		}
	}
}
