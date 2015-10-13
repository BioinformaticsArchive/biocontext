package uk.ac.man.biocontext.dataanalysis;

import java.io.File;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import martin.common.ArgParser;
import martin.common.Misc;
import martin.common.StreamIterator;

public class RemoveMedlinePMCDuplicates {
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		ArgParser ap = new ArgParser(args);

		System.out.print("Loading map...");
		Map<String,String> pmc_pmid_map = Misc.loadMap(ap.getFile("map"), ",", 8, 9);
		System.out.println(" done, loaded " + pmc_pmid_map.size() + " mappings.");

		int[][] counters = new int[2][2];
		Set<String>[][] ids = new Set[2][2];
		ids[0][0] = new HashSet<String>();
		ids[0][1] = new HashSet<String>();
		ids[1][0] = new HashSet<String>();
		ids[1][1] = new HashSet<String>();

		Set<String> PMIDs = new HashSet<String>();

		for (File f: ap.getFiles("files")){
			StreamIterator data = new StreamIterator(f);
			int[][] localcounters = new int[2][2];
			Set<String>[][] localids = new Set[2][2];
			localids[0][0] = new HashSet<String>();
			localids[0][1] = new HashSet<String>();
			localids[1][0] = new HashSet<String>();
			localids[1][1] = new HashSet<String>();

			for (String l : data){
				if (!l.startsWith("doc_id")){
					String[] fs = l.split("\t");
					if  (fs[0].startsWith("PMC") && (fs[0].contains(".0.") || !fs[0].contains("."))){
						String id = fs[0];
						if (fs[0].contains(".0."))
							id = id.substring(0,fs[0].indexOf('.'));
						String pmid = pmc_pmid_map.get(id);
						if (pmid != null && pmid.length() > 0)
							PMIDs.add(pmid);
					}

					int a = fs[2] != null && !fs[2].equals("NULL") ? Integer.parseInt(fs[2]) : 0;
					int b = fs[3] != null && !fs[3].equals("NULL") ? Integer.parseInt(fs[3]) : 0;

					localcounters[a][b]++;
					localids[a][b].add(fs[1]);
					if (!PMIDs.contains(fs[0])){
						counters[a][b]++;		
						ids[a][b].add(fs[1]);
					}
				}
			}
			
			print(f.getAbsolutePath(), localcounters, localids);
		}

		print("all files", counters, ids);
	}

	private static void print(String label, int[][] counters, Set<String>[][] ids) {
		System.out.println(label);
		System.out.println("\ttotal");
		for (int i = 0; i < counters.length; i++)
			for (int j = 0; j < counters[i].length; j++)
				System.out.println("\t\t" + i + "\t" + j + "\t" + counters[i][j]);
		System.out.println();
		
		System.out.println("\t\t1\t?\t" + (counters[1][0] + counters[1][1]));
		System.out.println("\t\t?\t1\t" + (counters[0][1] + counters[1][1]));
		System.out.println("\t\t?\t?\t" + (counters[0][0] + counters[0][1] + counters[1][0] + counters[1][1]));
		System.out.println();
		
		System.out.println("\tunique");
		for (int i = 0; i < ids.length; i++)
			for (int j = 0; j < ids[i].length; j++)
				System.out.println("\t\t" + i + "\t" + j + "\t" + ids[i][j].size());
		
		System.out.println();
		Set<String> set = new HashSet<String>();
		set.addAll(ids[1][0]);
		set.addAll(ids[1][1]);
		System.out.println("\t\t1\t?\t" + set.size());
		set.clear();

		set.addAll(ids[0][1]);
		set.addAll(ids[1][1]);
		System.out.println("\t\t?\t1\t" + set.size());
		set.clear();
		
		set.addAll(ids[0][0]);
		set.addAll(ids[0][1]);
		set.addAll(ids[1][0]);
		set.addAll(ids[1][1]);
		System.out.println("\t\t?\t?\t" + set.size());
	}
}
