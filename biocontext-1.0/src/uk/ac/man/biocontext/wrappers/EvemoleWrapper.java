package uk.ac.man.biocontext.wrappers;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import uk.ac.man.biocontext.dataholders.Event;
import uk.ac.man.biocontext.util.Misc;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.TextPipe;
import uk.ac.man.textpipe.TextPipeClient;

public class EvemoleWrapper extends Annotator{
	private TextPipeClient ptcGdep, ptcGnat;
	private File evemoleDir;
	private File tmpDir;

	@Override
	public void init(java.util.Map<String,String> data) {
		ptcGdep = new TextPipeClient("localhost", 57011);
		ptcGnat = new TextPipeClient("localhost", 57002);

		if (data != null && data.containsKey("gdepHost") && data.containsKey("gdepPort"))
			ptcGdep = new TextPipeClient(data.get("gdepHost"), Integer.parseInt(data.get("gdepPort")));

		if (data != null && data.containsKey("gnatHost") && data.containsKey("gnatPort"))
			ptcGnat = new TextPipeClient(data.get("gnatHost"), Integer.parseInt(data.get("gnatPort")));

		tmpDir = data != null && data.containsKey("tmpDir") ? new File(data.get("tmpDir")) : new File("/tmp/");
		evemoleDir = data != null && data.containsKey("evemoleDir") ? new File(data.get("evemoleDir")) : new File("/fs/linserver/data1/mbaxgfs2/farzin/evemole/");
	}

	@Override
	public String[] getOutputFields() {
		return new String[]{"id","trigger_start", "trigger_end", "trigger_text", "type", "participants"};
	}

	@Override
	public List<Map<String, String>> process(Map<String, String> data) {

		if (!data.containsKey("doc_text")){
			System.err.println("Received process requiest without a doc_text parameter");
			return new ArrayList<Map<String,String>>();			
		}			
		
		List<Map<String, String>> gdepdata = ptcGdep.process(data);
		List<Map<String, String>> gnatdata = ptcGnat.process(data);

		String id = "1";

		File inputDir = Misc.getRandomTmpDir(tmpDir);
		inputDir.mkdir();
		writeText2File(id, data.get("doc_text"), inputDir);
		writeGdep2File(id, gdepdata, inputDir);
		writeGnat2File(id, gnatdata, inputDir);

		File outputDir = runEvemole(inputDir);

		List<Map<String, String>> res = readEvemoleResults(id, outputDir, inputDir);		

		Misc.delete(inputDir);
		Misc.delete(outputDir);

		return res ;
	}
	
	public List<Map<String, String>> readEvemoleResults(String id, File outputDir, File inputDir){
		List<Map<String, String>> res = new ArrayList<Map<String,String>>();

		FileReader eveOutput, a1File;
		List<Event> events = new ArrayList<Event>();

		try {
			eveOutput = new FileReader(outputDir.getAbsolutePath() + "/CRF/file"+id+".a2.t1");
			BufferedReader br = new BufferedReader(eveOutput);

			String readTemp;
			String output = "";

			while((readTemp = br.readLine()) != null) {
				output += readTemp;
				output += "\n";
			}
			for (String s : output.split("\n")){

				if (s.startsWith("E")){
					String[] line = s.split("\t");

//					Event e = new Event();
//					e.setId(line[0]);
//					String [] rest = line[1].split(" ");
//
//					e.setEveType(rest[0].split(":")[0]);
//					e.setTrigName(rest[0].split(":")[1]);
//					//e.addParticName(rest[1].split(":")[1]);
//					events.add(e);

				}
			}

			for (String s : output.split("\n")){
				if (s.startsWith("T")){
					String name = s.split("\t")[0];
					for (Event e : events){
//						for (String p : e.getParticNames()){
//							if (p.equals(name)){
//								e.addParticipant(new Token(s));
//							}
//						}
//						if (e.getTrigName().equals(name)){
//							e.setTrigger(new Token(s));
//						}
					}

				}
			}
			eveOutput.close();
			a1File = new FileReader(inputDir + "/file" + id + ".a1");
			br = new BufferedReader(a1File);

			String proteins = "";
			while((readTemp = br.readLine()) != null) {
				proteins += readTemp;
				proteins += "\n";
			}

			for (String s : proteins.split("\n")){
				String name = s.split("\t")[0];
				for (Event e : events){
//					for (String p : e.getParticNames()){
//						if (p.equals(name)){
//							e.addParticipant(new Token(s));
//						}
//					}
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		}

		for (Event e : events){
			res.add(e.toMap());
		}
		return res;
	}

	public File runEvemole(File inputDir){
		String profile = "FARZIN";
		File outputDir = Misc.getRandomTmpDir(tmpDir);

		String evemoleCommand = "python " + evemoleDir.getAbsolutePath() + "/src/evemole.py -p " + profile + 
		" -o " + outputDir.getAbsolutePath() + " -i " + inputDir.getAbsolutePath();
		System.out.println(evemoleCommand);

		try{
			Process p = Runtime.getRuntime().exec(evemoleCommand, null, evemoleDir);
			//			new Thread(new InputStreamDumper(p.getInputStream(), System.out)).start();
			//			new Thread(new InputStreamDumper(p.getErrorStream(), System.err)).start();
			p.waitFor();
		} catch (Exception e){
			System.err.println(e);
			e.printStackTrace();
			System.exit(-1);
		}

		return outputDir;
	}
	
	private void writeText2File(String id, String text, File inputDir) {
		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(inputDir.getAbsolutePath() + "/file" + id + ".txt"));
			outStream.write(text);
			outStream.close();
		}
		catch(IOException e){
			e.printStackTrace();
		}
	}

	private void writeGdep2File(String id, List<Map<String, String>> gdepdata, File inputDir) {
		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(inputDir.getAbsolutePath() + "/file" + id + ".CoNLL"));
			for (Map<String, String> i : gdepdata){
				outStream.write(i.get("gdep_data").replace(TextPipe.NEWLINE_STRING, "\n").replace(TextPipe.TAB_STRING, "\t") + "\n");
			}
			outStream.close();
		}
		catch(IOException e){
			e.printStackTrace();	
		} 	
	}
	private void writeGnat2File(String id, List<Map<String, String>> gnatdata, File inputDir){
		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(inputDir.getAbsolutePath() + "/file" + id + ".a1"));
			for (Map<String, String> i : gnatdata){
				outStream.write("T");
				outStream.write(i.get("id"));
				outStream.write("\tProtein ");
				outStream.write(i.get("entity_start") + " " + i.get("entity_end") + "\t" + i.get("entity_term") + "\n");
			}
			outStream.close();
		}
		catch(IOException e){
			e.printStackTrace();	
		}		
	}
}
