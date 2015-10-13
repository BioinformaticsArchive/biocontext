package uk.ac.man.biocontext.webinterface;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.util.Map;

import martin.common.ArgParser;
import martin.common.Misc;

public class GeneDescriptions {
	public static void main(String[] args){
		ArgParser ap = new ArgParser(args);
	
		System.out.println("Loading gene_info, symbols...");
		Map<String,String> geneSymbols = Misc.loadMap(ap.getFile("gene_info"),"\t",1,2);
		System.out.println("Loading gene_info, tax links...");
		Map<String,String> geneTax = Misc.loadMap(ap.getFile("gene_info"),"\t",1,0);
		System.out.println("Loading taxonomy...");
		Map<String,String> speciesNames = Misc.loadMap(ap.getFile("taxonomy"));

		try{
			BufferedWriter outStream = new BufferedWriter(new FileWriter(ap.getFile("out")));
		for (String k : geneSymbols.keySet()){
			String t = geneTax.get(k);
			if (t != null && speciesNames.get(t) != null)
				t = speciesNames.get(t);
			else
				t = null;

			
			if (t != null){
				if (t.contains(" ")){
					int x = t.indexOf(" ");
					t = t.substring(0,1) + "." + t.substring(x);					
				}
				outStream.write(k + "\t" + geneSymbols.get(k) +  " (" + t + ")\n");
			}else{
				outStream.write(k + "\t" + geneSymbols.get(k) + "\n");
			}
		}
		outStream.close();
		} catch (Exception e){
			System.err.println(e.toString());
			e.printStackTrace();
			System.exit(-1);
		}
	}
}
