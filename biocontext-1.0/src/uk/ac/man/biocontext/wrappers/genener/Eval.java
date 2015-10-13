package uk.ac.man.biocontext.wrappers.genener;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import uk.ac.man.biocontext.evaluate.Evaluate;
import uk.ac.man.biocontext.evaluate.EvaluateResult.EvalType;
import uk.ac.man.documentparser.DocumentParser;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.input.DocumentIterator;
import uk.ac.man.entitytagger.EntityTagger;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.matching.Matcher;
import uk.ac.man.textpipe.Annotator;
import uk.ac.man.textpipe.annotators.PrecomputedAnnotator;
import martin.common.ArgParser;
import martin.common.Loggers;
import martin.common.Misc;
import martin.common.StreamIterator;

public class Eval {

	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		ArgParser ap = ArgParser.getParser(args);
		if (ap.containsKey("1")){
			eval1(ap);
		}
		if (ap.containsKey("2")){
			eval2(ap);
		}
	}

	private static void eval2(ArgParser ap) {

		Map<String,String> gene_species_map = Misc.loadMap(ap.getFile("map"));

		Map<String,List<String>> gold = new HashMap<String,List<String>>();

		StreamIterator data = new StreamIterator(ap.getFile("gold"));
		for (String l : data){
			String[] fs = l.split("\t");
			if (!gold.containsKey(fs[0]))
				gold.put(fs[0],new LinkedList<String>());
			String s = gene_species_map.get(fs[1]);
			if (s != null)
				gold.get(fs[0]).add(s);
		}

		Logger l = Loggers.getDefaultLogger(ap);
		Matcher matcher = EntityTagger.getMatcher(ap, l);

		DocumentIterator documents = DocumentParser.getDocuments(ap);
		int total=0;
		int TP=0;
		for (Document d : documents){
			List<Mention> speciesMentions = matcher.match(d);
			Set<String> foundSpecies = new HashSet<String>();
			for (Mention m : speciesMentions)
				for (String t : m.getIds()){
					t = t.replace("species:ncbi:","");
					if (t.equals("562"))
						t = "511145";
					if (t.equals("4932"))
						t = "559292";
					if (t.equals("4896"))
						t = "284812";
					foundSpecies.add(t);
				}

			if (foundSpecies.isEmpty())
				foundSpecies.add("9606");

			total += gold.get(d.getID()).size();
			for (String id : gold.get(d.getID()))
				if (foundSpecies.contains(id))
					TP++;
		}

		System.out.println("Total: " + total + ", TP: " + TP + " (" + ((double)TP/(double)total) + ")");
	}

	private static void eval1(ArgParser ap) throws IOException {
		Map<String,String> gene_species_map = Misc.loadMap(ap.getFile("map"));
		Set<String> speciesFilter = new HashSet<String>();
		if (ap.containsKey("filter"))
			speciesFilter.addAll(Arrays.asList(ap.gets("filter")));

		Annotator predicted = new PrecomputedAnnotator(Evaluate.goldGeneColumns, "farzin", ap.get("table"), ArgParser.getParser());
		
		Map<String,Set<String>> gold = new HashMap<String,Set<String>>();
		StreamIterator data = new StreamIterator(ap.getFile("gold"));
		for (String l : data){
			String[] fs = l.split("\t");
			if (!gold.containsKey(fs[0]))
				gold.put(fs[0],new HashSet<String>());
			gold.get(fs[0]).add(fs[1]);
		}
		
		Map<String,String> freqs = Misc.loadMap(new File("/fs/san/home/mqbpgmg2/temp/genetukit-hist.tsv"), "\t", 0, 1); 

		Map<EvalType,Integer> counter = new HashMap<EvalType,Integer>();
		counter.put(EvalType.TP, 0);
		counter.put(EvalType.FP, 0);
		counter.put(EvalType.FN, 0);

		DocumentIterator documents = DocumentParser.getDocuments(ap);

		int total=0;
		int norm=0;
		
		for (Document d : documents){
			Set<String> predSet = new HashSet<String>();
			Set<String> goldSet = gold.get(d.getID());

			List<Map<String,String>> pred = predicted.processDoc(d);

			for (Map<String,String> m : pred)
				if (!m.get("entity_id").equals("0")){
					predSet.add(m.get("entity_id"));
					total++;
					if (!m.get("entity_id").contains("|"))
						norm++;
				}

			
			if (predSet.size() > 0 && goldSet != null)
				eval(counter, predSet, goldSet, gene_species_map, speciesFilter, freqs);	
		}

		Evaluate.print(counter, null, null);
		System.out.println("Normalized: " + ((double)norm/(double)total));
	}

	private static void eval(Map<EvalType, Integer> counter,
			Set<String> predSet, Set<String> goldSet, Map<String, String> gene_species_map, Set<String> speciesFilter, Map<String, String> freqs) {


		Set<Set<String>> predSet2 = new HashSet<Set<String>>();
		for (String p : predSet){
			Set<String> s = new HashSet<String>();
			for (String p2 : p.split("\\|")){
				if (p2.contains("?"))
					p2 = p2.substring(0,p2.indexOf('?'));
				if (p2.startsWith("gene:ncbi:"))
					p2 = p2.substring("gene:ncbi:".length());
				
				String sp = gene_species_map.get(p2);
				if (sp != null && sp.equals("511145"))
					sp = "562";
				if (sp != null && sp.equals("559292"))
					sp = "4932";
				if (sp != null && sp.equals("284812"))
					sp = "4896";
				
				if (speciesFilter.isEmpty() || (sp != null && speciesFilter.contains(sp)))
					s.add(p2);
			}
			predSet2.add(s);
		}
		
//		Set<Set<String>> predSet3 = new HashSet<Set<String>>();
//		for (Set<String> s : predSet2){
//			int max = 0;
//			String maxv = null;
//			for (String ss : s){
//				Integer f = freqs.containsKey(ss) ? Integer.parseInt(freqs.get(ss)) : null;
//				if (maxv == null || (f != null && f > max)){
//					max = f != null ? f : 0;
//					maxv = ss;
//				}
//			}
//			predSet3.add(new HashSet<String>(Arrays.asList(maxv)));
//		}
//		predSet2 = predSet3;


		if (predSet != null)
			for (Set<String> s : predSet2){
				boolean found = false;
				for (String id : s)
					if (goldSet.contains(id))
						found = true;

				if (found){
					counter.put(EvalType.TP, counter.get(EvalType.TP) + 1);
				}else{
					counter.put(EvalType.FP, counter.get(EvalType.FP) + 1);
					//					System.out.println("FP\t" + s);
				}
			}


		if (goldSet != null)
			for (String p : goldSet)
				if (p.length() > 1){
					boolean found = false;
					for (Set<String> s : predSet2)
						if (s.contains(p))
							found = true;

					if (found)
						//				counter.put(EvalType.TP, counter.get(EvalType.TP) + 1);
						;
					else{
						counter.put(EvalType.FN, counter.get(EvalType.FN) + 1);
						//						System.out.println("FN\t" + p);

					}
				}
	}
}
