package uk.ac.man.textpipe;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.man.textpipe.annotators.CacheAnnotator;

import martin.common.ArgParser;
import martin.common.Misc;

public class TextPipeClient extends Annotator{
	private String host;
	private int port;
	private String[] outputFields;

	public TextPipeClient(String host, int port){
		this.host = host;
		this.port = port;		
	}

	public TextPipeClient(){
		//If created as an annotator; init must be called
	}

	public TextPipeClient(String string) {
		String[] fs = string.split(":");
		this.host = fs[0];
		this.port = Integer.parseInt(fs[1]);
	}

	@Override
	public void init(java.util.Map<String,String> data) {
		this.host = data.get("host");
		this.port = Integer.parseInt(data.get("port"));
	}

	private String[] retrieveOutputFields(){
		try{
			Socket socket = new Socket(host, port);

			BufferedWriter outStream = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
			outStream.write("getOutputs\n");
			outStream.flush();
			String s = new BufferedReader(new InputStreamReader(socket.getInputStream())).readLine();
			socket.close();
			if (s.length() == 0)
				return new String[]{};
			else {
				List<String> l = Arrays.asList(s.split("\t"));
				List<String> result = new ArrayList<String>();
				for (int i = 0; i < l.size(); i++)
					if (!l.get(i).equals("doc_id"))
						result.add(l.get(i));
				return result.toArray(new String[]{});
			}
		} catch (Exception e){
			throw new RuntimeException(e);
		}
	}

	@Override
	public String[] getOutputFields() {
		if (this.outputFields == null)
			this.outputFields = retrieveOutputFields();
		return this.outputFields;
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {
		while (true) {
			try{
				Socket socket = new Socket(host, port);

				BufferedWriter outStream = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
				BufferedReader inStream = new BufferedReader(new InputStreamReader(socket.getInputStream()));

				data = TextPipe.encode(data);

				for (String k : data.keySet())
					outStream.write(k + "\t" + data.get(k) + "\n");

				outStream.write("\n");
				outStream.flush();

				String line = inStream.readLine();

				List<Map<String,String>> res = new ArrayList<Map<String,String>>();

				while (line.length() > 0){
					Map<String,String> m = new HashMap<String,String>();
					while (line.length() > 0){

						String[] fs = line.split("\t");

						if (fs.length != 2){
							inStream.close();
							outStream.close();
							throw new IllegalStateException("Incorrect number of columns read for line '" + line + "', document: " + data.get("doc_id"));
						}

						if (!fs[1].equals("null"))
							m.put(fs[0],fs[1]);

						line = inStream.readLine();
					}
					res.add(TextPipe.decode(m));
					line = inStream.readLine();
				}

				inStream.close();
				outStream.close();

				return res;
			} catch (SocketException e){
				System.err.println("Exception (h: " + host + ", p: " + port +"): " + e);
				e.printStackTrace();
				System.err.println("Waiting 10 seconds, and then retrying...");
			} catch (IOException e){
				System.err.println("Exception (h: " + host + ", p: " + port +"): " + e);
				e.printStackTrace();
				System.err.println("Waiting 10 seconds, and then retrying...");
			}
			try{
				Thread.sleep(10000);
			} catch (Exception e){
			}
		}
	}

	public static void main(String[] args){
		Map<String,String> input = new HashMap<String,String>();

		input.put("doc_id", "testdoc");
		input.put("doc_text", "here are a few species: human mouse Drosophila melanogaster E. coli");

		Annotator annotator = new TextPipeClient("localhost",6879);

		String[] outFields = annotator.getOutputFields();

		System.out.println(Misc.implode(outFields, "\t"));

		List<Map<String,String>> output = annotator.process(input);

		for (Map<String,String> m : output){
			for (String k : outFields)
				System.out.print(m.get(k)+"\t");
			System.out.println();
		}
	}

	public static TextPipeClient get(Map<String, String> data, String name,
			String defaultHost, int defaultPort) {

		TextPipeClient ptc;
		
		if (data != null && name != null && data.containsKey(name + "Host") && data.containsKey(name + "Port"))
			ptc = new TextPipeClient(data.get(name+"Host"), Integer.parseInt(data.get(name+"Port")));
		else
			ptc = new TextPipeClient(defaultHost,defaultPort);
		
		return ptc;
	}
	
	public static Annotator get(Map<String, String> data, String name,
			String defaultHost, int defaultPort, String db, String cacheTable) {

		ArgParser ap = ArgParser.getParser();

		TextPipeClient ptc = get(data, name, defaultHost, defaultPort);
		
		return new CacheAnnotator(ptc, db, cacheTable, ap);
	}

	@Override
	public String helpMessage() {
		return "default TextPipeClient help message. Fix me!";
	}
}
