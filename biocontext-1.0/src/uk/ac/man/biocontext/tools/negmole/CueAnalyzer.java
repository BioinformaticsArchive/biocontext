package uk.ac.man.biocontext.tools.negmole;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import martin.common.ArgParser;
import uk.ac.man.biocontext.wrappers.GDepWrapper;
import uk.ac.man.documentparser.DocumentParser;
import uk.ac.man.documentparser.dataholders.Document;
import uk.ac.man.documentparser.input.DocumentIterator;
import uk.ac.man.textpipe.annotators.PrecomputedAnnotator;

public class CueAnalyzer {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		ArgParser ap = new ArgParser(args);
		DocumentIterator documents = DocumentParser.getDocuments(ap, null);
		
		PrecomputedAnnotator gdep = new PrecomputedAnnotator(GDepWrapper.outputFields, "farzin", "data_p_gdep", ap);
		
		Set<String> negSet = new HashSet<String>();
		negSet.addAll(Arrays.asList(Negation.NEGATION_CUES));

		Set<String> specSet = new HashSet<String>();
		specSet.addAll(Arrays.asList(Negation.SPECULATION_CUES));

		for (Document d : documents){
			
			List<Map<String,String>> parses = gdep.processDoc(d);

			int sentCounter = 0;
			
			for (Map<String,String> parse : parses){

				int numNegCues = 0;
				int numSpecCues = 0;

				String[] tokenlines = parse.get("gdep_data").split("\n");
				for (String tokenline : tokenlines){
					String token = tokenline.split("\t")[1];
					token = Stemmer.stemToken(token);
					
					if (negSet.contains(token)){
						numNegCues++;
					}
					if (specSet.contains(token)){
						numSpecCues++;
					}
				}
				
				System.out.println(d.getID() + "\t" + sentCounter++ + "\t" + numNegCues + "\t" + numSpecCues);
			}
		}		
	}
}
