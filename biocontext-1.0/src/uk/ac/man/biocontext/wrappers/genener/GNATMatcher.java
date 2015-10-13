package uk.ac.man.biocontext.wrappers.genener;

import gnat.ConstantsNei;
import gnat.ISGNProperties;
import gnat.ConstantsNei.OUTPUT_LEVELS;
import gnat.client.Run;
import gnat.filter.Filter;
import gnat.filter.nei.AlignmentFilter;
import gnat.filter.nei.BANNERValidationFilter;
import gnat.filter.nei.GeneRepositoryLoader;
import gnat.filter.nei.IdentifyAllFilter;
import gnat.filter.nei.ImmediateContextFilter;
import gnat.filter.nei.LeftRightContextFilter;
import gnat.filter.nei.MultiSpeciesDisambiguationFilter;
import gnat.filter.nei.NameValidationFilter;
import gnat.filter.nei.RecognizedEntityUnifier;
import gnat.filter.nei.SpeciesValidationFilter;
import gnat.filter.nei.StopWordFilter;
import gnat.filter.nei.UnambiguousMatchFilter;
import gnat.filter.nei.UnspecificNameFilter;
import gnat.filter.ner.GnatServiceNer;
import gnat.filter.ner.LinnaeusSpeciesServiceNer;
import gnat.preprocessing.NameRangeExpander;
import gnat.representation.IdentifiedGene;
import gnat.representation.Text;
import gnat.representation.TextRepository;
import gnat.server.GnatService;
import gnat.utils.AlignmentHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import martin.common.ArgParser;

import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.entitytagger.Mention;
import uk.ac.man.entitytagger.matching.Matcher;
import uk.ac.man.textpipe.TextPipe;
import uk.ac.man.textpipe.TextPipe.VerbosityLevel;

public class GNATMatcher extends Matcher {
	private Run run;

	public GNATMatcher(File GNATDir, File propsFile) {
		ISGNProperties.loadProperties(propsFile);

		ArgParser ap = ArgParser.getParser();
		if (ap.containsKey("level"))
			this.run = getRun(ap.getInt("level"));
		else
			this.run = getRun();
	}

	private Run getRun(){
		return getRun(Integer.MAX_VALUE);
	}

	private Run getRun(int level){
		run = new Run();
		run.verbosity = 0;
		ConstantsNei.OUTPUT_LEVEL = OUTPUT_LEVELS.WARNINGS;

		run.addFilter(new NameRangeExpander());
		run.addFilter(new LinnaeusSpeciesServiceNer());
		GnatServiceNer gnatServiceNer = new GnatServiceNer(GnatService.Tasks.GENE_NER);
		gnatServiceNer.useDefaultSpecies = true;
		run.addFilter(gnatServiceNer);
		run.addFilter(new FilterNonGenes());
		run.addFilter(new RecognizedEntityUnifier());
		run.addFilter(new GeneRepositoryLoader(GeneRepositoryLoader.RetrievalMethod.SERVICE));

		List<Filter> optionalFilters = new LinkedList<Filter>();

		
		//1
		optionalFilters.add(new RemoveNonRepGenesFilter());

		//2
		String f1 = new File("data/strictFPs_2_2_context_all.object").getAbsolutePath();
		String f2 = new File("data/nonStrictFPs_2_2_context_all.object").getAbsolutePath();
		optionalFilters.add(new LeftRightContextFilter(f1, f2, 0d, 2, 2));

		//3
		optionalFilters.add(new ImmediateContextFilter());

		//4
		optionalFilters.add(new StopWordFilter(ISGNProperties.get("stopWords")));

		//5
		optionalFilters.add(new BANNERValidationFilter());

		//6
//		optionalFilters.add(new UnambiguousMatchFilter());

		//6
		optionalFilters.add(new UnspecificNameFilter());

		//7
		optionalFilters.add(new AlignmentFilter(AlignmentHelper.globalAlignment, 0.7f));
		
		//8
		optionalFilters.add(new NameValidationFilter());

		//
//		optionalFilters.add(new SpeciesValidationFilter());
		
		//9
		optionalFilters.add(new MultiSpeciesDisambiguationFilter(
				Integer.parseInt(ISGNProperties.get("disambiguationThreshold")),
				Integer.parseInt(ISGNProperties.get("maxIdsForCandidatePrediction"))));

		//9
//		optionalFilters.add(new UnambiguousMatchFilter());


		for (int i = 0; i < Math.min(level,optionalFilters.size()); i++){
			if (TextPipe.verbosity.compareTo(VerbosityLevel.DEBUG) >= 0)
				System.out.println("adding optional filter " + i);
			run.addFilter(optionalFilters.get(i));

		}

		// set all remaining genes as 'identified' so they will be reported in the result
		run.addFilter(new IdentifyAllFilter());
		return run;
	}

	@Override
	public List<Mention> match(String text, Document doc) {
		if (text.length() > 200000)
			return new ArrayList<Mention>();

		String docid = doc != null ? doc.getID() : "none";

		run.clearTextRepository();
		run.context.clear();

		TextRepository tr = new TextRepository();
		try{
			tr.addText(new Text(docid,text));
		} catch (java.lang.ArrayIndexOutOfBoundsException e){
			System.err.println("error\tGNAT crash\t" + doc.getID() + "\t" + e.toString());
			e.printStackTrace();
			return new ArrayList<Mention>();
		}

		run.setTextRepository(tr);

		try{
			//			System.out.println("a");
			run.runFilters();
			//			System.out.println("b");
		} catch (java.lang.Exception e){
			System.err.println("error\tGNAT crash\t" + doc.getID() + "\t" + e.toString());
			e.printStackTrace();
			return new ArrayList<Mention>();
		}


		Iterator<IdentifiedGene> iter = run.context.getIdentifiedGenesIterator();

		Map<String,Set<String>> allIds = new HashMap<String,Set<String>>();

		while (iter.hasNext()){
			IdentifiedGene ig = iter.next();
			String s = ig.getRecognizedEntity().getName();
			if (!allIds.containsKey(s))
				allIds.put(s, new HashSet<String>());
			allIds.get(s).add(ig.getGene().getID());
		}

		List<Mention> mentions = new ArrayList<Mention>(run.context.getIdentifiedGeneList().size());

		for (String k : allIds.keySet()){
			int s = text.indexOf(k);
			String[] ids = allIds.get(k).toArray(new String[0]);
			while (s != -1){
				Mention m = new Mention(ids,s, s+k.length(),k);
				m.setDocid(docid);
				mentions.add(m);
				s = text.indexOf(k,s+1);
			}
		}

		return mentions;
	}
}
